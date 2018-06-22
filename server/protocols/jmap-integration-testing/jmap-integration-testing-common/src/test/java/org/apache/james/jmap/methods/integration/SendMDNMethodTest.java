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
import static org.apache.james.jmap.JmapCommonRequests.getInboxId;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsInMailbox;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.NAME;
import static org.apache.james.jmap.TestingConstants.calmlyAwait;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.jmap.MessageAppender;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.SerializableQuotaValue;
import org.apache.james.mailbox.store.probe.MailboxProbe;
import org.apache.james.mailbox.store.probe.QuotaProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Iterables;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.parsing.Parser;

public abstract class SendMDNMethodTest {
    private static final String HOMER = "homer@" + DOMAIN;
    private static final String BART = "bart@" + DOMAIN;
    private static final String PASSWORD = "password";
    private static final String BOB_PASSWORD = "bobPassword";

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    protected abstract MessageId randomMessageId();

    private AccessToken homerAccessToken;
    private AccessToken bartAccessToken;
    private GuiceJamesServer jmapServer;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        RestAssured.requestSpecification = jmapRequestSpecBuilder
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(HOMER, PASSWORD);
        dataProbe.addUser(BART, BOB_PASSWORD);
        mailboxProbe.createMailbox("#private", HOMER, DefaultMailboxes.INBOX);
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
            "        \"headers\":{\"Disposition-Notification-To\":\"" + BART + "\"}," +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BART + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + HOMER + "\"}]," +
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
            .header("Authorization", bartAccessToken.serialize())
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
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BART + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + HOMER + "\"}]," +
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
            .header("Authorization", bartAccessToken.serialize())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.until(() -> !listMessageIdsForAccount(homerAccessToken).isEmpty());
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void sendMDNShouldReturnCreatedMessageId() {
        bartSendMessageToHomer();

        List<String> messageIds = listMessageIdsForAccount(homerAccessToken);

        String creationId = "creation-1";
        given()
            .header("Authorization", homerAccessToken.serialize())
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
    public void sendMDNShouldFailOnUnknownMessageId() {
        bartSendMessageToHomer();

        String creationId = "creation-1";
        String randomMessageId = randomMessageId().serialize();
        given()
            .header("Authorization", homerAccessToken.serialize())
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
    public void sendMDNShouldFailOnInvalidMessages() {
        sendAWrongInitialMessage();
        List<String> messageIds = listMessageIdsForAccount(homerAccessToken);

        String creationId = "creation-1";

        given()
            .header("Authorization", homerAccessToken.serialize())
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

    @Test
    public void sendMDNShouldSendAMDNBackToTheOriginalMessageAuthor() {
        String bartSentJmapMessageId = bartSendMessageToHomer();

        String homerReceivedMessageId = Iterables.getOnlyElement(listMessageIdsForAccount(homerAccessToken));

        // HOMER sends a MDN back to BART
        String creationId = "creation-1";
        with()
            .header("Authorization", homerAccessToken.serialize())
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
        calmlyAwait.until(() -> !listMessageIdsInMailbox(bartAccessToken, getInboxId(bartAccessToken)).isEmpty());
        String bartInboxMessageIds = Iterables.getOnlyElement(listMessageIdsInMailbox(bartAccessToken, getInboxId(bartAccessToken)));

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", bartAccessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + bartInboxMessageIds + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(firstMessage + ".from.email", is(HOMER))
            .body(firstMessage + ".to.email", contains(BART))
            .body(firstMessage + ".hasAttachment", is(true))
            .body(firstMessage + ".textBody", is("Read confirmation"))
            .body(firstMessage + ".subject", is("subject"))
            .body(firstMessage + ".headers.Content-Type", startsWith("multipart/report;"))
            .body(firstMessage + ".headers.X-JAMES-MDN-JMAP-MESSAGE-ID", equalTo(bartSentJmapMessageId))
            .body(firstMessage + ".attachments[0].type", startsWith("message/disposition-notification"));
    }

    @Test
    public void sendMDNShouldPositionTheReportAsAnAttachment() {
        bartSendMessageToHomer();

        List<String> messageIds = listMessageIdsForAccount(homerAccessToken);

        // USER sends a MDN back to BART
        String creationId = "creation-1";
        with()
            .header("Authorization", homerAccessToken.serialize())
            .body("[[\"setMessages\", {\"sendMDN\": {" +
                "\"" + creationId + "\":{" +
                "    \"messageId\":\"" + messageIds.get(0) + "\"," +
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
        calmlyAwait.until(() -> !listMessageIdsInMailbox(bartAccessToken, getInboxId(bartAccessToken)).isEmpty());
        List<String> bobInboxMessageIds = listMessageIdsInMailbox(bartAccessToken, getInboxId(bartAccessToken));

        String blobId = with()
            .header("Authorization", bartAccessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + bobInboxMessageIds.get(0) + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".list[0].attachments[0].blobId");

        given()
            .header("Authorization", bartAccessToken.serialize())
        .when()
            .get("/download/" + blobId)
        .then()
            .statusCode(200)
            .body(containsString("Reporting-UA: reportingUA;"))
            .body(containsString("Final-Recipient: rfc822; homer@domain.tld"))
            .body(containsString("Original-Message-ID: "))
            .body(containsString("Disposition: automatic-action/MDN-sent-automatically;processed"));
    }

    @Test
    public void sendMDNShouldIndicateMissingFields() {
        String creationId = "creation-1";
        // Missing subject
        given()
            .header("Authorization", homerAccessToken.serialize())
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
    public void sendMDNShouldReturnMaxQuotaReachedWhenUserReachedHisQuota() throws MailboxException {
        bartSendMessageToHomer();

        List<String> messageIds = listMessageIdsForAccount(homerAccessToken);

        QuotaProbe quotaProbe = jmapServer.getProbe(QuotaProbesImpl.class);
        String inboxQuotaRoot = quotaProbe.getQuotaRoot("#private", HOMER, DefaultMailboxes.INBOX);
        quotaProbe.setMaxStorage(inboxQuotaRoot, SerializableQuotaValue.valueOf(Optional.of(QuotaSize.size(100))));

        MessageAppender.fillMailbox(jmapServer.getProbe(MailboxProbeImpl.class), HOMER, MailboxConstants.INBOX);

        String creationId = "creation-1";
        given()
            .header("Authorization", homerAccessToken.serialize())
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
    public void sendMDNShouldIndicateMissingFieldsInDisposition() {
        String creationId = "creation-1";
        // Missing actionMode
        given()
            .header("Authorization", homerAccessToken.serialize())
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
    public void invalidEnumValuesInMDNShouldBeReported() {
        String creationId = "creation-1";
        given()
            .header("Authorization", homerAccessToken.serialize())
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
