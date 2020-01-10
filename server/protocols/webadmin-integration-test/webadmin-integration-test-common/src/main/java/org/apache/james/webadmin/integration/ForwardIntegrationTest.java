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

import static io.restassured.RestAssured.given;
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
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.isAnyMessageFoundInRecipientsMailboxes;
import static org.apache.james.jmap.LocalHostURIBuilder.baseUri;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

public abstract class ForwardIntegrationTest {

    private SMTPMessageSender messageSender;
    private RequestSpecification webAdminApi;
    private Port jmapPort;

    @BeforeEach
    void setUp(GuiceJamesServer jmapServer) throws Exception {
        messageSender = new SMTPMessageSender(DOMAIN);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), CEDRIC_PASSWORD);

        jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());

    }

    @AfterEach
    void tearDown() throws IOException {
        messageSender.close();
    }

    @Tag(BasicFeature.TAG)
    @Test
    void messageShouldBeForwardedWhenDefinedInRESTAPI() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), BOB.asString()));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapPort), CEDRIC, CEDRIC_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isUnread\": true," +
            "        \"isFlagged\": true," +
            "        \"isAnswered\": true," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getOutboxId(cedricAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", cedricAccessToken.asString())
            .body(requestBody)
        .post("/jmap");

        AccessToken bobAccessToken = authenticateJamesUser(baseUri(jmapPort), BOB, BOB_PASSWORD);
        calmlyAwait
            .pollDelay(Duration.FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
        given()
            .header("Authorization", bobAccessToken.asString())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1));
    }

    @Test
    void messageShouldBeForwardedWhenBaseRecipientWhenInDestination() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), BOB.asString()));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), ALICE.asString()));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapPort), CEDRIC, CEDRIC_PASSWORD);
        AccessToken aliceAccessToken = authenticateJamesUser(baseUri(jmapPort), ALICE, ALICE_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isUnread\": true," +
            "        \"isFlagged\": true," +
            "        \"isAnswered\": true," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getOutboxId(cedricAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", cedricAccessToken.asString())
            .body(requestBody)
        .post("/jmap");

        AccessToken bobAccessToken = authenticateJamesUser(baseUri(jmapPort), BOB, BOB_PASSWORD);
        calmlyAwait
            .pollDelay(Duration.FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
        given()
            .header("Authorization", bobAccessToken.asString())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1));

        given()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1));
    }

    @Test
    void recursiveForwardShouldWork() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), CEDRIC.asString()));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", CEDRIC.asString(), BOB.asString()));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapPort), CEDRIC, CEDRIC_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isUnread\": true," +
            "        \"isFlagged\": true," +
            "        \"isAnswered\": true," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getOutboxId(cedricAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", cedricAccessToken.asString())
            .body(requestBody)
        .post("/jmap");

        AccessToken bobAccessToken = authenticateJamesUser(baseUri(jmapPort), BOB, BOB_PASSWORD);
        calmlyAwait
            .pollDelay(Duration.FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
        given()
            .header("Authorization", bobAccessToken.asString())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1));
    }

    @Test
    void recursiveWithRecipientCopyForwardShouldWork() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), ALICE.asString()));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), BOB.asString()));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", BOB.asString(), CEDRIC.asString()));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapPort), CEDRIC, CEDRIC_PASSWORD);
        AccessToken aliceAccessToken = authenticateJamesUser(baseUri(jmapPort), ALICE, ALICE_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isUnread\": true," +
            "        \"isFlagged\": true," +
            "        \"isAnswered\": true," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getOutboxId(cedricAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", cedricAccessToken.asString())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait
            .pollDelay(Duration.FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(aliceAccessToken));
        given()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1));
    }

    @Test
    void baseRecipientShouldNotReceiveEmailOnDefaultForward() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE.asString(), BOB.asString()));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapPort), CEDRIC, CEDRIC_PASSWORD);
        AccessToken aliceAccessToken = authenticateJamesUser(baseUri(jmapPort), ALICE, ALICE_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isUnread\": true," +
            "        \"isFlagged\": true," +
            "        \"isAnswered\": true," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getOutboxId(cedricAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", cedricAccessToken.asString())
            .body(requestBody)
        .post("/jmap");

        AccessToken bobAccessToken = authenticateJamesUser(baseUri(jmapPort), BOB, BOB_PASSWORD);
        calmlyAwait
            .pollDelay(Duration.FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));

        given()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(0));
    }

}
