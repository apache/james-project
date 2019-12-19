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

package org.apache.james.webadmin.integration;

import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapCommonRequests.bodyOfMessage;
import static org.apache.james.jmap.JmapCommonRequests.getLastMessageId;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.LocalHostURIBuilder.baseUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.stream.IntStream;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

public abstract class FastViewProjectionHealthCheckIntegrationContract {
    private static final String MESSAGE_FAST_VIEW_PROJECTION = "MessageFastViewProjection";

    private RequestSpecification webAdminApi;
    private AccessToken bobAccessToken;
    private AccessToken aliceAccessToken;

    @BeforeEach
    void setUp(GuiceJamesServer jamesServer) throws Exception {
        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), CEDRIC_PASSWORD);

        Port jmapPort = jamesServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();

        bobAccessToken = authenticateJamesUser(baseUri(jmapPort), BOB, BOB_PASSWORD);
        aliceAccessToken = authenticateJamesUser(baseUri(jmapPort), ALICE, ALICE_PASSWORD);

        webAdminApi = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @Test
    void checkShouldReturnHealthyWhenNoMessage() {
        webAdminApi.when()
            .get("/healthcheck/checks/" + MESSAGE_FAST_VIEW_PROJECTION)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("componentName", equalTo(MESSAGE_FAST_VIEW_PROJECTION))
            .body("escapedComponentName", equalTo(MESSAGE_FAST_VIEW_PROJECTION))
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()));
    }

    @Test
    void checkShouldReturnHealthyAfterSendingAMessageWithReads() {
        bobSendAMessageToAlice();

        IntStream.rangeClosed(1, 20)
            .forEach(counter -> aliceReadLastMessage());

        webAdminApi.when()
            .get("/healthcheck/checks/" + MESSAGE_FAST_VIEW_PROJECTION)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("componentName", equalTo(MESSAGE_FAST_VIEW_PROJECTION))
            .body("escapedComponentName", equalTo(MESSAGE_FAST_VIEW_PROJECTION))
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()));
    }

    @Test
    void checkShouldReturnDegradedAfterFewReadsOnAMissedProjection(GuiceJamesServer guiceJamesServer) {
        bobSendAMessageToAlice();

        guiceJamesServer.getProbe(JmapGuiceProbe.class)
            .clearMessageFastViewProjection();

        IntStream.rangeClosed(1, 3) // Will miss at the first time as we cleared the preview
            .forEach(counter -> aliceReadLastMessage());

        webAdminApi.when()
            .get("/healthcheck/checks/" + MESSAGE_FAST_VIEW_PROJECTION)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("componentName", equalTo(MESSAGE_FAST_VIEW_PROJECTION))
            .body("escapedComponentName", equalTo(MESSAGE_FAST_VIEW_PROJECTION))
            .body("status", equalTo(ResultStatus.DEGRADED.getValue()));
    }

    @Test
    void checkShouldTurnFromDegradedToHealthyAfterMoreReadsOnAMissedProjection(GuiceJamesServer guiceJamesServer) {
        bobSendAMessageToAlice();
        calmlyAwait.untilAsserted(() -> assertThat(listMessageIdsForAccount(aliceAccessToken))
            .hasSize(1));
        makeHealthCheckDegraded(guiceJamesServer);

        IntStream.rangeClosed(1, 100)
            .forEach(counter -> aliceReadLastMessage());

        webAdminApi.when()
            .get("/healthcheck/checks/" + MESSAGE_FAST_VIEW_PROJECTION)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("componentName", equalTo(MESSAGE_FAST_VIEW_PROJECTION))
            .body("escapedComponentName", equalTo(MESSAGE_FAST_VIEW_PROJECTION))
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()));
    }

    private void makeHealthCheckDegraded(GuiceJamesServer guiceJamesServer) {
        guiceJamesServer.getProbe(JmapGuiceProbe.class)
            .clearMessageFastViewProjection();
        aliceReadLastMessage();
    }

    private void bobSendAMessageToAlice() {
        String messageCreationId = "creationId";
        String outboxId = getOutboxId(bobAccessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "        \"subject\": \"bob to alice\"," +
            "        \"textBody\": \"body\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"] " +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";
        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");


        calmlyAwait.untilAsserted(() -> assertThat(listMessageIdsForAccount(aliceAccessToken))
            .hasSize(1));
    }

    private void aliceReadLastMessage() {
        bodyOfMessage(aliceAccessToken, getLastMessageId(aliceAccessToken));
    }
}
