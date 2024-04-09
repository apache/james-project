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
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapRFCCommonRequests.ACCEPT_JMAP_RFC_HEADER;
import static org.apache.james.jmap.JmapRFCCommonRequests.UserCredential;
import static org.apache.james.jmap.JmapRFCCommonRequests.getLastMessageId;
import static org.apache.james.jmap.JmapRFCCommonRequests.getUserCredential;
import static org.apache.james.jmap.JmapRFCCommonRequests.listMessageIdsForAccount;
import static org.apache.james.webadmin.integration.TestFixture.WAIT_THIRTY_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.stream.IntStream;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.jmap.JmapRFCCommonRequests;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public abstract class FastViewProjectionHealthCheckIntegrationContract {
    private static final String MESSAGE_FAST_VIEW_PROJECTION = "MessageFastViewProjection";

    private RequestSpecification webAdminApi;
    private UserCredential bobCredential;
    private UserCredential aliceCredential;

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
        bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);

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
        bobSendAnEmailToAlice();

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
    void checkShouldReturnDegradedAfterFewReadsOnAMissedProjection(GuiceJamesServer guiceJamesServer) throws Exception {
        bobSendAnEmailToAlice();

        makeHealthCheckDegraded(guiceJamesServer);

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
        bobSendAnEmailToAlice();

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

    private void bobSendAnEmailToAlice() {
        JmapRFCCommonRequests.UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        String bobOutboxId = JmapRFCCommonRequests.getOutboxId(bobCredential);
        String request =
            "{" +
                "    \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\", \"urn:ietf:params:jmap:submission\"]," +
                "    \"methodCalls\": [" +
                "        [\"Email/set\", {" +
                "            \"accountId\": \"" + bobCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"e1526\": {" +
                "                    \"mailboxIds\": { \"" + bobOutboxId + "\": true }," +
                "                    \"subject\": \"subject\"," +
                "                    \"htmlBody\": [{" +
                "                        \"partId\": \"a49d\"," +
                "                        \"type\": \"text/html\"" +
                "                    }]," +
                "                    \"bodyValues\": {" +
                "                        \"a49d\": {" +
                "                            \"value\": \"Test <b>body</b>, HTML version\"" +
                "                        }" +
                "                    }," +
                "                    \"to\": [{\"email\": \"" + ALICE.asString() + "\"}]," +
                "                    \"from\": [{\"email\": \"" + BOB.asString() + "\"}]" +
                "                }" +
                "            }" +
                "        }, \"c1\"]," +
                "        [\"EmailSubmission/set\", {" +
                "            \"accountId\": \"" + bobCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"k1490\": {" +
                "                    \"emailId\": \"#e1526\"," +
                "                    \"envelope\": {" +
                "                        \"mailFrom\": {\"email\": \"" + BOB.asString() + "\"}," +
                "                        \"rcptTo\": [{\"email\": \"" + ALICE.asString() + "\"}]" +
                "                    }" +
                "                }" +
                "            }" +
                "        }, \"c3\"]" +
                "    ]" +
                "}";

        with()
            .auth().basic(bobCredential.username().asString(), bobCredential.password())
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body(request)
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("methodResponses[0][1].created.e1526", Matchers.is(notNullValue()));

        WAIT_THIRTY_SECONDS
            .untilAsserted(() -> assertThat(listMessageIdsForAccount(aliceCredential)).hasSize(1));
    }

    private void aliceReadLastMessage() {
        // read with fast view
        with()
            .auth().basic(aliceCredential.username().asString(), aliceCredential.password())
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body("""
                {
                    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
                    "methodCalls": [
                        [
                            "Email/get",
                            {
                                "accountId": "%s",
                                "ids": ["%s"],
                                "properties": [ "preview", "hasAttachment" ]
                            },
                            "c1"
                        ]
                    ]
                }
                """.formatted(aliceCredential.accountId(), getLastMessageId(aliceCredential)))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .jsonPath();
    }
}
