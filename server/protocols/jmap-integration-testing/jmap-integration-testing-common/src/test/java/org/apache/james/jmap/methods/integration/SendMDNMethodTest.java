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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.parsing.Parser;

public abstract class SendMDNMethodTest {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String USERS_DOMAIN = "domain.tld";
    private static final String USERNAME = "username@" + USERS_DOMAIN;
    private static final String BOB = "bob@" + USERS_DOMAIN;
    private static final String PASSWORD = "password";
    private static final String BOB_PASSWORD = "bobPassword";

    protected abstract GuiceJamesServer createJmapServer();

    protected abstract MessageId randomMessageId();

    private AccessToken accessToken;
    private AccessToken bobAccessToken;
    private GuiceJamesServer jmapServer;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(USERS_DOMAIN);
        dataProbe.addUser(USERNAME, PASSWORD);
        dataProbe.addUser(BOB, BOB_PASSWORD);
        mailboxProbe.createMailbox("#private", USERNAME, DefaultMailboxes.INBOX);
        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USERNAME, PASSWORD);
        bobAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), BOB, BOB_PASSWORD);
    }

    private void sendAnInitialMessage() {
        String messageCreationId = "creationId";
        String outboxId = getOutboxId(bobAccessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"headers\":{\"Disposition-Notification-To\":\"" + BOB + "\"}," +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + USERNAME + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body, plain text version\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"] " +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", bobAccessToken.serialize())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.until(() -> !getMessageIdListForAccount(accessToken.serialize()).isEmpty());
    }

    private void sendAWrongInitialMessage() {
        String messageCreationId = "creationId";
        String outboxId = getOutboxId(bobAccessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + USERNAME + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body, plain text version\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"] " +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", bobAccessToken.serialize())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.until(() -> !getMessageIdListForAccount(accessToken.serialize()).isEmpty());
    }

    private URIBuilder baseUri() {
        return new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class)
                .getJmapPort())
            .setCharset(StandardCharsets.UTF_8);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void sendMDNShouldReturnCreatedMessageId() {
        sendAnInitialMessage();

        List<String> messageIds = getMessageIdListForAccount(accessToken.serialize());

        String creationId = "creation-1";
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + messageIds.get(0) + "\"," +
                    "    \"subject\":\"subject\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"automatic-action\","+
                    "        \"sendingMode\":\"MDN-sent-automatically\","+
                    "        \"type\":\"processed\""+
                    "    }" +
                    "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".MDNSent." + creationId, notNullValue());
    }

    @Test
    public void sendMDNShouldFailOnUnknownMessageId() {
        sendAnInitialMessage();

        String creationId = "creation-1";
        String randomMessageId = randomMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + randomMessageId + "\"," +
                    "    \"subject\":\"subject\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"automatic-action\","+
                    "        \"sendingMode\":\"MDN-sent-automatically\","+
                    "        \"type\":\"processed\""+
                    "    }" +
                    "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".MDNNotSent", hasEntry(
                equalTo(creationId),
                hasEntry("type", "invalidArgument")))
            .body(ARGUMENTS + ".MDNNotSent", hasEntry(
                equalTo(creationId),
                hasEntry("description", "Message with id " + randomMessageId + " not found. Thus could not send MDN.")));
    }

    @Test
    public void sendMDNShouldFailOnInvalidMessages() {
        sendAWrongInitialMessage();
        List<String> messageIds = getMessageIdListForAccount(accessToken.serialize());

        String creationId = "creation-1";

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + messageIds.get(0) + "\"," +
                    "    \"subject\":\"subject\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"automatic-action\","+
                    "        \"sendingMode\":\"MDN-sent-automatically\","+
                    "        \"type\":\"processed\""+
                    "    }" +
                    "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".MDNNotSent", hasEntry(
                equalTo(creationId),
                hasEntry("type", "invalidArgument")))
            .body(ARGUMENTS + ".MDNNotSent", hasEntry(
                equalTo(creationId),
                hasEntry("description", "Origin messageId '" + messageIds.get(0) + "' is invalid. " +
                    "A Message Delivery Notification can not be generated for it. " +
                    "Explanation: Disposition-Notification-To header is missing")));
    }

    @Test
    public void sendMDNShouldSendAMDNBackToTheOriginalMessageAuthor() {
        sendAnInitialMessage();

        List<String> messageIds = getMessageIdListForAccount(accessToken.serialize());

        // USER sends a MDN back to BOB
        String creationId = "creation-1";
        with()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + messageIds.get(0) + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"Read confirmation\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"actionMode\":\"automatic-action\","+
                "        \"sendingMode\":\"MDN-sent-automatically\","+
                "        \"type\":\"processed\""+
                "    }" +
                "}" +
                "}}, \"#0\"]]")
            .post("/jmap");

        // BOB should have received it
        calmlyAwait.until(() -> !listMessagesInMailbox(bobAccessToken, getInboxId(bobAccessToken)).isEmpty());
        List<String> bobInboxMessageIds = listMessagesInMailbox(bobAccessToken, getInboxId(bobAccessToken));

        given()
            .header("Authorization", bobAccessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + bobInboxMessageIds.get(0) + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".list[0].from.email", is(USERNAME))
            .body(ARGUMENTS + ".list[0].to.email", contains(BOB))
            .body(ARGUMENTS + ".list[0].hasAttachment", is(true))
            .body(ARGUMENTS + ".list[0].textBody", is("Read confirmation"))
            .body(ARGUMENTS + ".list[0].subject", is("subject"))
            .body(ARGUMENTS + ".list[0].headers.Content-Type", startsWith("multipart/report;"))
            .body(ARGUMENTS + ".list[0].attachments[0].type", startsWith("message/disposition-notification"));
    }

    @Test
    public void sendMDNShouldPositionTheReportAsAnAttachment() {
        sendAnInitialMessage();

        List<String> messageIds = getMessageIdListForAccount(accessToken.serialize());

        // USER sends a MDN back to BOB
        String creationId = "creation-1";
        with()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + messageIds.get(0) + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"Read confirmation\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"actionMode\":\"automatic-action\","+
                "        \"sendingMode\":\"MDN-sent-automatically\","+
                "        \"type\":\"processed\""+
                "    }" +
                "}" +
                "}}, \"#0\"]]")
            .post("/jmap");

        // BOB should have received it
        calmlyAwait.until(() -> !listMessagesInMailbox(bobAccessToken, getInboxId(bobAccessToken)).isEmpty());
        List<String> bobInboxMessageIds = listMessagesInMailbox(bobAccessToken, getInboxId(bobAccessToken));

        String blobId = with()
            .header("Authorization", bobAccessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + bobInboxMessageIds.get(0) + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".list[0].attachments[0].blobId");

        given()
            .header("Authorization", bobAccessToken.serialize())
        .when()
            .get("/download/" + blobId)
        .then()
            .statusCode(200)
            .body(containsString("Reporting-UA: reportingUA;"))
            .body(containsString("Final-Recipient: rfc822; username@domain.tld"))
            .body(containsString("Original-Message-ID: "))
            .body(containsString("Disposition: automatic-action/MDN-sent-automatically;processed"));
    }

    @Test
    public void sendMDNShouldIndicateMissingFields() {
        String creationId = "creation-1";
        // Missing subject
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"automatic-action\","+
                    "        \"sendingMode\":\"MDN-sent-automatically\","+
                    "        \"type\":\"processed\""+
                    "    }" +
                    "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("problem: 'subject' is mandatory"));
    }

    @Test
    public void sendMDNShouldIndicateMissingFieldsInDisposition() {
        String creationId = "creation-1";
        // Missing actionMode
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"textBody\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"sendingMode\":\"MDN-sent-automatically\","+
                "        \"type\":\"processed\""+
                "    }" +
                "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("problem: 'actionMode' is mandatory"));
    }

    @Test
    public void invalidEnumValuesInMDNShouldBeReported() {
        String creationId = "creation-1";
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"textBody\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"actionMode\":\"invalid\","+
                "        \"sendingMode\":\"MDN-sent-automatically\","+
                "        \"type\":\"processed\""+
                "    }" +
                "}" +
                "}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("Unrecognized MDN Disposition action mode invalid. Should be one of [manual-action, automatic-action]"));
    }

    private String getInboxId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.INBOX);
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

    private List<String> getMessageIdListForAccount(String accessToken) {
        return with()
            .header("Authorization", accessToken)
            .body("[[\"getMessageList\", {}, \"#0\"]]")
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".messageIds");
    }

    private List<String> listMessagesInMailbox(AccessToken accessToken, String mailboxId) {
        return with()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".messageIds");
    }

}
