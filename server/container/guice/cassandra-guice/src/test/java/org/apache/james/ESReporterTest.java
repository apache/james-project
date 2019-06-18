/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.awaitility.Awaitility.await;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestDockerESMetricReporterModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.awaitility.Duration;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

class ESReporterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ESReporterTest.class);
    private static final int LIMIT_TO_10_MESSAGES = 10;

    static final DockerElasticSearchExtension elasticSearchExtension = new DockerElasticSearchExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(elasticSearchExtension)
        .extension(new CassandraExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(new TestDockerESMetricReporterModule(elasticSearchExtension.getDockerES().getHttpHost())))
        .build();

    private static final int DELAY_IN_MS = 100;
    private static final int PERIOD_IN_MS = 100;

    private static final String DOMAIN = "james.org";
    private static final String USERNAME = "user1@" + DOMAIN;
    private static final String PASSWORD = "secret";

    private Timer timer;
    private AccessToken accessToken;

    @BeforeEach
    void setup(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(USERNAME, PASSWORD);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(server.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        accessToken = authenticateJamesUser(baseUri(server), USERNAME, PASSWORD);

        timer = new Timer();
    }

    @AfterEach
    void tearDown() {
        timer.cancel();
    }

    @Test
    void timeMetricsShouldBeReportedWhenImapCommandsReceived(GuiceJamesServer server) throws Exception {
        IMAPClient client = new IMAPClient();
        client.connect(InetAddress.getLocalHost(), server.getProbe(ImapGuiceProbe.class).getImapPort());
        client.login(USERNAME, PASSWORD);
        
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    client.list("", "*");
                } catch (Exception e) {
                    LOGGER.error("Error while sending LIST command", e);
                }
            }
        };
        timer.schedule(timerTask, DELAY_IN_MS, PERIOD_IN_MS);

        await().atMost(Duration.TEN_MINUTES)
            .until(this::checkMetricRecordedInElasticSearch);
    }

    @Test
    void timeMetricsShouldBeReportedWhenJmapRequestsReceived() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    given()
                        .header("Authorization", accessToken.serialize())
                        .body("[[\"getMailboxes\", {}, \"#0\"]]")
                    .with()
                        .post("/jmap");
                } catch (Exception e) {
                    LOGGER.error("Error while listing mailboxes", e);
                }
            }
        };
        timer.schedule(timerTask, DELAY_IN_MS, PERIOD_IN_MS);

        await().atMost(Duration.TEN_MINUTES)
            .until(this::checkMetricRecordedInElasticSearch);
    }

    private boolean checkMetricRecordedInElasticSearch() {
        try (RestHighLevelClient client = elasticSearchExtension.getDockerES().clientProvider().get()) {
            SearchRequest searchRequest = new SearchRequest()
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery()));
            return !Arrays.stream(client
                    .search(searchRequest)
                    .getHits()
                    .getHits())
                .filter(searchHit -> searchHit.getIndex().startsWith(TestDockerESMetricReporterModule.METRICS_INDEX))
                .collect(Collectors.toList())
                .isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
