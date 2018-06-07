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

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.modules.TestESMetricReporterModule;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.awaitility.Duration;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public class ESReporterTest {

    private static final int IMAP_PORT = 1143;
    private static final int DELAY_IN_MS = 100;
    private static final int PERIOD_IN_MS = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(ESReporterTest.class);

    private static final String DOMAIN = "james.org";
    private static final String USERNAME = "user1@" + DOMAIN;
    private static final String PASSWORD = "secret";

    private EmbeddedElasticSearchRule embeddedElasticSearchRule = new EmbeddedElasticSearchRule();

    private Timer timer;

    @Rule
    public CassandraJmapTestRule cassandraJmap = new CassandraJmapTestRule(embeddedElasticSearchRule, new DockerCassandraRule());

    private GuiceJamesServer server;
    private AccessToken accessToken;

    @Before
    public void setup() throws Exception {
        server = cassandraJmap.jmapServer();
        server.start();
        server.getProbe(DataProbeImpl.class)
            .fluentAddDomain(DOMAIN)
            .fluentAddUser(USERNAME, PASSWORD);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(server.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        accessToken = authenticateJamesUser(baseUri(server), USERNAME, PASSWORD);

        timer = new Timer();
    }

    @After
    public void tearDown() throws Exception {
        timer.cancel();
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void timeMetricsShouldBeReportedWhenImapCommandsReceived() throws Exception {
        IMAPClient client = new IMAPClient();
        client.connect(InetAddress.getLocalHost(), IMAP_PORT);
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
    public void timeMetricsShouldBeReportedWhenJmapRequestsReceived() throws Exception {
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
        try (Client client = embeddedElasticSearchRule.getNode().client()) {
            return !Arrays.stream(client.prepareSearch()
                    .setQuery(QueryBuilders.matchAllQuery())
                    .get().getHits().getHits())
                .filter(searchHit -> searchHit.getIndex().startsWith(TestESMetricReporterModule.METRICS_INDEX))
                .collect(Collectors.toList())
                .isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
