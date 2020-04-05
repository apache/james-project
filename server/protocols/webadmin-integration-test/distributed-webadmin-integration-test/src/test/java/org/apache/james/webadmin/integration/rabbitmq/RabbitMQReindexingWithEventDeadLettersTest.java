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

package org.apache.james.webadmin.integration.rabbitmq;

import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapCommonRequests.getDraftId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;

import java.util.List;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.LocalHostURIBuilder;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.jmap.draft.JmapJamesServerContract;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.objectstorage.PayloadCodecFactory;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.integration.WebadminIntegrationTestModule;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

@Tag(BasicFeature.TAG)
class RabbitMQReindexingWithEventDeadLettersTest {

    private static final String ELASTICSEARCH_LISTENER_GROUP = "org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex$ElasticSearchListeningMessageSearchIndexGroup";

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .atMost(Duration.FIVE_MINUTES)
        .await();

    private static final DockerElasticSearchExtension dockerElasticSearch =
        new DockerElasticSearchExtension().withRequestTimeout(java.time.Duration.ofSeconds(5));

    private static final JamesServerBuilder.ServerProvider CONFIGURATION_BUILDER = configuration -> GuiceJamesServer
        .forConfiguration(configuration)
        .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
        .overrideWith(TestJMAPServerModule.limitToTenMessages())
        .overrideWith(JmapJamesServerContract.DOMAIN_LIST_CONFIGURATION_MODULE)
        .overrideWith(new WebadminIntegrationTestModule());

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(dockerElasticSearch)
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension(PayloadCodecFactory.AES256))
        .server(CONFIGURATION_BUILDER)
        .build();

    private RequestSpecification webAdminApi;
    private AccessToken aliceAccessToken;

    @BeforeEach
    void setUp(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(ALICE.asString(), ALICE_PASSWORD);

        Port jmapPort = jamesServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();
        RestAssured.defaultParser = Parser.JSON;

        webAdminApi = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());

        aliceAccessToken = authenticateJamesUser(LocalHostURIBuilder.baseUri(jmapPort), ALICE, ALICE_PASSWORD);

        dockerElasticSearch.getDockerES().pause();
        Thread.sleep(Duration.TEN_SECONDS.getValueInMS()); // Docker pause is asynchronous and we found no way to poll for it
    }

    @Disabled("JAMES-3011 It's already fails for a long time, but CI didn't detect this when it's not marked as BasicFeature")
    @Test
    void indexationShouldBeFailingWhenElasticSearchContainerIsPaused() throws Exception {
        aliceSavesADraft();

        CALMLY_AWAIT.until(() -> listElasticSearchFailedEvents().size() == 1);

        unpauseElasticSearch();
        assertThat(listMessageIdsForAccount(aliceAccessToken)).isEmpty();
    }

    @Test
    void redeliverShouldReIndexFailedMessages() throws Exception {
        aliceSavesADraft();
        CALMLY_AWAIT.until(() -> listElasticSearchFailedEvents().size() == 1);

        unpauseElasticSearch();
        redeliverAllFailedEvents();

        CALMLY_AWAIT.until(() -> listMessageIdsForAccount(aliceAccessToken).size() == 1);
    }

    @Test
    void redeliverShouldCleanEventDeadLetter() throws Exception {
        aliceSavesADraft();
        CALMLY_AWAIT.until(() -> listElasticSearchFailedEvents().size() == 1);

        unpauseElasticSearch();
        redeliverAllFailedEvents();
        CALMLY_AWAIT.until(() -> listMessageIdsForAccount(aliceAccessToken).size() == 1);

        assertThat(listElasticSearchFailedEvents()).isEmpty();
    }

    private void unpauseElasticSearch() throws Exception {
        dockerElasticSearch.getDockerES().unpause();
        Thread.sleep(Duration.FIVE_SECONDS.getValueInMS()); // Docker unpause is asynchronous and we found no way to poll for it
    }

    private void aliceSavesADraft() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + ALICE.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(aliceAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", aliceAccessToken.asString())
            .body(requestBody)
            .post("/jmap");
    }

    private List<String> listElasticSearchFailedEvents() {
        return webAdminApi.with()
            .get("/events/deadLetter/groups/" + ELASTICSEARCH_LISTENER_GROUP)
        .andReturn()
            .body()
            .jsonPath()
            .getList(".");
    }

    private void redeliverAllFailedEvents() {
        webAdminApi.with()
            .queryParam("action", "reDeliver")
            .post("/events/deadLetter");
    }
}
