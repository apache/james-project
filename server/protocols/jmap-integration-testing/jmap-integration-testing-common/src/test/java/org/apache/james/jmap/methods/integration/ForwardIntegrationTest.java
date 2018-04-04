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
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.TestingConstants.calmlyAwait;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.Role;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.RequestSpecification;

public abstract class ForwardIntegrationTest {

    private static final String DOMAIN = "domain";
    private static final String BOB = "bob@" + DOMAIN;
    private static final String BOB_PASSWORD = "123456";
    private static final String ALICE = "alice@" + DOMAIN;
    private static final String ALICE_PASSWORD = "789123";
    private static final String CEDRIC = "cedric@" + DOMAIN;
    private static final String CEDRIC_PASSWORD = "456789";

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";

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

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
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

        AccessToken cedricAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), CEDRIC, CEDRIC_PASSWORD);
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

        AccessToken bobAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), BOB, BOB_PASSWORD);
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

        AccessToken cedricAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), CEDRIC, CEDRIC_PASSWORD);
        AccessToken aliceAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), ALICE, ALICE_PASSWORD);
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

        AccessToken bobAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), BOB, BOB_PASSWORD);
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
    public void baseRecipientShouldNotReceiveEmailOnDefaultForward() {
        webAdminApi.put(String.format("/address/forwards/%s/targets/%s", ALICE, BOB));

        AccessToken cedricAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), CEDRIC, CEDRIC_PASSWORD);
        AccessToken aliceAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), ALICE, ALICE_PASSWORD);
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

        AccessToken bobAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), BOB, BOB_PASSWORD);
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

    private URIBuilder baseUri() {
        return new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class)
                .getJmapPort())
            .setCharset(StandardCharsets.UTF_8);
    }

    private String getOutboxId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.OUTBOX);
    }

    private String getMailboxId(AccessToken accessToken, Role role) {
        return getAllMailboxesIds(accessToken).stream()
            .filter(x -> x.get("role").equalsIgnoreCase(role.serialize()))
            .map(x -> x.get("id"))
            .findFirst().get();
    }

    private List<Map<String, String>> getAllMailboxesIds(AccessToken accessToken) {
        return with()
                    .header("Authorization", accessToken.serialize())
                    .body("[[\"getMailboxes\", {\"properties\": [\"role\", \"id\"]}, \"#0\"]]")
                .post("/jmap")
                    .andReturn()
                    .body()
                    .jsonPath()
                    .getList(ARGUMENTS + ".list");
    }

    private boolean isAnyMessageFoundInRecipientsMailboxes(AccessToken recipientToken) {
        try {
            with()
                .header("Authorization", recipientToken.serialize())
                .body("[[\"getMessageList\", {}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messageList"))
                .body(ARGUMENTS + ".messageIds", hasSize(1));
            return true;

        } catch (AssertionError e) {
            return false;
        }
    }
}
