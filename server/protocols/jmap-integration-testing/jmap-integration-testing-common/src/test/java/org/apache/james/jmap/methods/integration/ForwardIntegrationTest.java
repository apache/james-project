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

package org.apache.james.jmap.methods.integration;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.isAnyMessageFoundInRecipientsMailboxes;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ALICE;
import static org.apache.james.jmap.TestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.BOB;
import static org.apache.james.jmap.TestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.TestingConstants.CEDRIC;
import static org.apache.james.jmap.TestingConstants.CEDRIC_PASSWORD;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.NAME;
import static org.apache.james.jmap.TestingConstants.calmlyAwait;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.concurrent.TimeUnit;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;

public abstract class ForwardIntegrationTest {

    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DOMAIN);

    protected abstract GuiceJamesServer createJmapServer();

    private GuiceJamesServer jmapServer;
    private RequestSpecification webAdminApi;

    @Before
    public void setUp() throws Exception {
        jmapServer = createJmapServer();
        jmapServer.start();

        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB, BOB_PASSWORD);
        dataProbe.addUser(ALICE, ALICE_PASSWORD);
        dataProbe.addUser(CEDRIC, CEDRIC_PASSWORD);

        RestAssured.requestSpecification = jmapRequestSpecBuilder
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();

        WebAdminGuiceProbe webAdminGuiceProbe = jmapServer.getProbe(WebAdminGuiceProbe.class);
        webAdminGuiceProbe.await();
        webAdminApi = given()
            .spec(WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort()).build());
    }

    @After
    public void tearDown() {
        jmapServer.stop();
    }

    @Test
    public void messageShouldBeForwardedWhenDefinedInRESTAPI() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE, BOB));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapServer), CEDRIC, CEDRIC_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE + "\"}]," +
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
            .header("Authorization", cedricAccessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        AccessToken bobAccessToken = authenticateJamesUser(baseUri(jmapServer), BOB, BOB_PASSWORD);
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
        given()
            .header("Authorization", bobAccessToken.serialize())
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
    public void messageShouldBeForwardedWhenBaseRecipientWhenInDestination() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE, BOB));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE, ALICE));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapServer), CEDRIC, CEDRIC_PASSWORD);
        AccessToken aliceAccessToken = authenticateJamesUser(baseUri(jmapServer), ALICE, ALICE_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE + "\"}]," +
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
            .header("Authorization", cedricAccessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        AccessToken bobAccessToken = authenticateJamesUser(baseUri(jmapServer), BOB, BOB_PASSWORD);
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
        given()
            .header("Authorization", bobAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1));

        given()
            .header("Authorization", aliceAccessToken.serialize())
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
    public void recursiveForwardShouldWork() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE, CEDRIC));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", CEDRIC, BOB));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapServer), CEDRIC, CEDRIC_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE + "\"}]," +
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
            .header("Authorization", cedricAccessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        AccessToken bobAccessToken = authenticateJamesUser(baseUri(jmapServer), BOB, BOB_PASSWORD);
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
        given()
            .header("Authorization", bobAccessToken.serialize())
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
    public void recursiveWithRecipientCopyForwardShouldWork() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE, ALICE));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE, BOB));
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", BOB, CEDRIC));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapServer), CEDRIC, CEDRIC_PASSWORD);
        AccessToken aliceAccessToken = authenticateJamesUser(baseUri(jmapServer), ALICE, ALICE_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE + "\"}]," +
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
            .header("Authorization", cedricAccessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(aliceAccessToken));
        given()
            .header("Authorization", aliceAccessToken.serialize())
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
    public void baseRecipientShouldNotReceiveEmailOnDefaultForward() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE, BOB));

        AccessToken cedricAccessToken = authenticateJamesUser(baseUri(jmapServer), CEDRIC, CEDRIC_PASSWORD);
        AccessToken aliceAccessToken = authenticateJamesUser(baseUri(jmapServer), ALICE, ALICE_PASSWORD);
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + CEDRIC + "\"}," +
            "        \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE + "\"}]," +
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
            .header("Authorization", cedricAccessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        AccessToken bobAccessToken = authenticateJamesUser(baseUri(jmapServer), BOB, BOB_PASSWORD);
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));

        given()
            .header("Authorization", aliceAccessToken.serialize())
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
