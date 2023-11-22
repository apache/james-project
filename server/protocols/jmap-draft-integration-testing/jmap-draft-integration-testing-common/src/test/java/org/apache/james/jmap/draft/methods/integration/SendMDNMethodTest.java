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

package org.apache.james.jmap.draft.methods.integration;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapCommonRequests.getInboxId;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsInMailbox;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.awaitility.Durations.TWO_MINUTES;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.MessageAppender;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.mailbox.probe.QuotaProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Iterables;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;

public abstract class SendMDNMethodTest {
    private static final Username HOMER = Username.of("homer@" + DOMAIN);
    private static final Username BART = Username.of("bart@" + DOMAIN);
    private static final String PASSWORD = "password";
    private static final String BOB_PASSWORD = "bobPassword";

    protected abstract MessageId randomMessageId();

    private AccessToken homerAccessToken;
    private AccessToken bartAccessToken;
    private GuiceJamesServer jmapServer;

    @BeforeEach
    void setup(GuiceJamesServer jmapServer) throws Throwable {
        this.jmapServer = jmapServer;
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        RestAssured.requestSpecification = jmapRequestSpecBuilder
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
                .build();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(HOMER.asString(), PASSWORD);
        dataProbe.addUser(BART.asString(), BOB_PASSWORD);
        mailboxProbe.createMailbox("#private", HOMER.asString(), DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox("#private", HOMER.asString(), DefaultMailboxes.OUTBOX);
        homerAccessToken = authenticateJamesUser(baseUri(jmapServer), HOMER, PASSWORD);
        bartAccessToken = authenticateJamesUser(baseUri(jmapServer), BART, BOB_PASSWORD);
    }

    private String bartSendMessageToHomer() {
        String messageCreationId = "creationId";
        String outboxId = getOutboxId(bartAccessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"headers\":{\"Disposition-Notification-To\":\"" + BART.asString() + "\"}," +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BART.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + HOMER.asString() + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body, plain text version\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"] " +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String id = with()
            .header("Authorization", bartAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.until(() -> !listMessageIdsForAccount(homerAccessToken).isEmpty());
        return id;
    }

    private void sendAWrongInitialMessage() {
        String messageCreationId = "creationId";
        String outboxId = getOutboxId(bartAccessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BART.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + HOMER.asString() + "\"}]," +
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
            .header("Authorization", bartAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.until(() -> !listMessageIdsForAccount(homerAccessToken).isEmpty());
    }

    @Test
    void sendMDNShouldReturnCreatedMessageId() {
        bartSendMessageToHomer();

        List<String> messageIds = listMessageIdsForAccount(homerAccessToken);

        String creationId = "creation-1";
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + messageIds.get(0) + "\"," +
                    "    \"subject\":\"subject\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"automatic-action\"," +
                    "        \"sendingMode\":\"MDN-sent-automatically\"," +
                    "        \"type\":\"processed\"" +
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
    void sendMDNShouldFailOnUnknownMessageId() {
        bartSendMessageToHomer();

        String creationId = "creation-1";
        String randomMessageId = randomMessageId().serialize();
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + randomMessageId + "\"," +
                    "    \"subject\":\"subject\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"automatic-action\"," +
                    "        \"sendingMode\":\"MDN-sent-automatically\"," +
                    "        \"type\":\"processed\"" +
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
                hasEntry("type", "invalidArguments")))
            .body(ARGUMENTS + ".MDNNotSent", hasEntry(
                equalTo(creationId),
                hasEntry("description", "Message with id " + randomMessageId + " not found. Thus could not send MDN.")));
    }

    @Test
    void sendMDNShouldFailOnInvalidMessages() {
        sendAWrongInitialMessage();
        List<String> messageIds = listMessageIdsForAccount(homerAccessToken);

        String creationId = "creation-1";

        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + messageIds.get(0) + "\"," +
                    "    \"subject\":\"subject\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"automatic-action\"," +
                    "        \"sendingMode\":\"MDN-sent-automatically\"," +
                    "        \"type\":\"processed\"" +
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
                hasEntry("type", "invalidArguments")))
            .body(ARGUMENTS + ".MDNNotSent", hasEntry(
                equalTo(creationId),
                hasEntry("description", "Origin messageId '" + messageIds.get(0) + "' is invalid. " +
                    "A Message Delivery Notification can not be generated for it. " +
                    "Explanation: Disposition-Notification-To header is missing")));
    }

    @Category(BasicFeature.class)
    @Test
    void sendMDNShouldSendAMDNBackToTheOriginalMessageAuthor() {
        String bartSentJmapMessageId = bartSendMessageToHomer();

        String homerReceivedMessageId = Iterables.getOnlyElement(listMessageIdsForAccount(homerAccessToken));

        // HOMER sends a MDN back to BART
        String creationId = "creation-1";
        with()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + homerReceivedMessageId + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"Read confirmation\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"actionMode\":\"automatic-action\"," +
                "        \"sendingMode\":\"MDN-sent-automatically\"," +
                "        \"type\":\"processed\"" +
                "    }" +
                "}" +
                "}}, \"#0\"]]")
            .post("/jmap");

        // BART should have received it
        calmlyAwait.atMost(TWO_MINUTES).until(() -> !listMessageIdsInMailbox(bartAccessToken, getInboxId(bartAccessToken)).isEmpty());
        String bartInboxMessageIds = Iterables.getOnlyElement(listMessageIdsInMailbox(bartAccessToken, getInboxId(bartAccessToken)));

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", bartAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + bartInboxMessageIds + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(firstMessage + ".from.email", is(HOMER.asString()))
            .body(firstMessage + ".to.email", contains(BART.asString()))
            .body(firstMessage + ".hasAttachment", is(false))
            .body(firstMessage + ".textBody", is("Read confirmation"))
            .body(firstMessage + ".subject", is("subject"))
            .body(firstMessage + ".headers.Content-Type", startsWith("multipart/report;"))
            .body(firstMessage + ".headers.X-JAMES-MDN-JMAP-MESSAGE-ID", equalTo(bartSentJmapMessageId));
    }

    @Test
    void sendMDNShouldIndicateMissingFields() {
        String creationId = "creation-1";
        // Missing subject
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                    "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                    "    \"textBody\":\"textBody\"," +
                    "    \"reportingUA\":\"reportingUA\"," +
                    "    \"disposition\":{" +
                    "        \"actionMode\":\"automatic-action\"," +
                    "        \"sendingMode\":\"MDN-sent-automatically\"," +
                    "        \"type\":\"processed\"" +
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
    void sendMDNShouldReturnMaxQuotaReachedWhenUserReachedHisQuota() throws MailboxException {
        bartSendMessageToHomer();

        List<String> messageIds = listMessageIdsForAccount(homerAccessToken);

        QuotaProbe quotaProbe = jmapServer.getProbe(QuotaProbesImpl.class);
        QuotaRoot inboxQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(HOMER));
        quotaProbe.setMaxStorage(inboxQuotaRoot, QuotaSizeLimit.size(100));

        MessageAppender.fillMailbox(jmapServer.getProbe(MailboxProbeImpl.class), HOMER.asString(), MailboxConstants.INBOX);

        String creationId = "creation-1";
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + messageIds.get(0) + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"textBody\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"actionMode\":\"automatic-action\"," +
                "        \"sendingMode\":\"MDN-sent-automatically\"," +
                "        \"type\":\"processed\"" +
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
                hasEntry("type", "maxQuotaReached")));
    }

    @Test
    void sendMDNShouldIndicateMissingFieldsInDisposition() {
        String creationId = "creation-1";
        // Missing actionMode
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"textBody\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"sendingMode\":\"MDN-sent-automatically\"," +
                "        \"type\":\"processed\"" +
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
    void invalidEnumValuesInMDNShouldBeReported() {
        String creationId = "creation-1";
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + randomMessageId().serialize() + "\"," +
                "    \"subject\":\"subject\"," +
                "    \"textBody\":\"textBody\"," +
                "    \"reportingUA\":\"reportingUA\"," +
                "    \"disposition\":{" +
                "        \"actionMode\":\"invalid\"," +
                "        \"sendingMode\":\"MDN-sent-automatically\"," +
                "        \"type\":\"processed\"" +
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

}
