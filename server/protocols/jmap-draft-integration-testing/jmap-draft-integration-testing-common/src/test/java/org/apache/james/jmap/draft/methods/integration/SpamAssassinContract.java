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
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.mailbox.Role;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.spamassassin.SpamAssassinExtension;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;

public interface SpamAssassinContract {

    String BOBS_DOMAIN = "spamer.com";
    String BOB = "bob@" + BOBS_DOMAIN;
    String BOB_PASSWORD = "bobPassword";
    String RECIPIENTS_DOMAIN = "angels.org";
    String ALICE = "alice@" + RECIPIENTS_DOMAIN;
    String ALICE_PASSWORD = "alicePassword";
    String PAUL = "paul@" + RECIPIENTS_DOMAIN;
    String PAUL_PASSWORD = "paulPassword";

    @BeforeEach
    default void setup(GuiceJamesServer jamesServer) throws Throwable {
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(jamesServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
                .build();
        RestAssured.defaultParser = Parser.JSON;

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(BOBS_DOMAIN)
            .addDomain(RECIPIENTS_DOMAIN)
            .addUser(BOB, BOB_PASSWORD)
            .addUser(ALICE, ALICE_PASSWORD)
            .addUser(PAUL, PAUL_PASSWORD);
    }

    @AfterEach
    default void tearDown(SpamAssassinExtension.SpamAssassin spamAssassin, GuiceJamesServer jamesServer) throws Exception {
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));
        spamAssassin.clear(ALICE);
    }

    default AccessToken accessTokenFor(GuiceJamesServer james, String user, String password) {
        return authenticateJamesUser(baseUri(james), Username.of(user), password);
    }

    @Test
    default void spamShouldBeDeliveredInSpamMailboxWhenSameMessageHasAlreadyBeenMovedToSpam(
        GuiceJamesServer jamesServer,
        SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {

        spamAssassin.train(ALICE);
        AccessToken aliceAccessToken = accessTokenFor(jamesServer, ALICE, ALICE_PASSWORD);
        AccessToken bobAccessToken = accessTokenFor(jamesServer, BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = with()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + getInboxId(aliceAccessToken) + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1))
            .extract()
            .path(ARGUMENTS + ".messageIds");

        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"mailboxIds\": [\"" + getSpamId(aliceAccessToken) + "\"] } } }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".updated", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));

        // Bob is sending again the same message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");

        // This message is delivered in Alice Spam mailbox (she now must have 2 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 2));
    }

    @Test
    default void imapCopiesToSpamMailboxShouldBeConsideredAsSpam(GuiceJamesServer jamesServer,
                                                                 SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        AccessToken aliceAccessToken = accessTokenFor(jamesServer, ALICE, ALICE_PASSWORD);
        AccessToken bobAccessToken = accessTokenFor(jamesServer, BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));


        // Alice is copying this message to Spam -> learning in SpamAssassin
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(ALICE, ALICE_PASSWORD)
                .select(TestIMAPClient.INBOX);

            testIMAPClient.copyFirstMessage("Spam");
        }
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));

        // Bob is sending again the same message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");

        // This message is delivered in Alice Spam mailbox (she now must have 2 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 2));
    }

    @Test
    default void imapMovesToSpamMailboxShouldBeConsideredAsSpam(GuiceJamesServer jamesServer,
                                                                SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        AccessToken aliceAccessToken = accessTokenFor(jamesServer, ALICE, ALICE_PASSWORD);
        AccessToken bobAccessToken = accessTokenFor(jamesServer, BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(ALICE, ALICE_PASSWORD)
                .select(TestIMAPClient.INBOX);

            testIMAPClient.moveFirstMessage("Spam");
        }
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));

        // Bob is sending again the same message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");

        // This message is delivered in Alice Spam mailbox (she now must have 2 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 2));
    }

    @Test
    default void spamAssassinShouldForgetMessagesMovedOutOfSpamFolderUsingJMAP(GuiceJamesServer jamesServer,
                                                                               SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        AccessToken aliceAccessToken = accessTokenFor(jamesServer, ALICE, ALICE_PASSWORD);
        AccessToken bobAccessToken = accessTokenFor(jamesServer, BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
            .when()
            .post("/jmap");
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = with()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + getInboxId(aliceAccessToken) + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1))
            .extract()
            .path(ARGUMENTS + ".messageIds");

        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"mailboxIds\": [\"" + getSpamId(aliceAccessToken) + "\"] } } }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".updated", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));

        // Alice is moving this message out of Spam -> forgetting in SpamAssassin
        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"mailboxIds\": [\"" + getInboxId(aliceAccessToken) + "\"] } } }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".updated", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));

        // Bob is sending again the same message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");

        // This message is delivered in Alice INBOX mailbox (she now must have 2 messages in her Inbox mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 2));
    }

    @Test
    default void movingAMailToTrashShouldNotImpactSpamassassinLearning(GuiceJamesServer jamesServer,
                                                                       SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        AccessToken aliceAccessToken = accessTokenFor(jamesServer, ALICE, ALICE_PASSWORD);
        AccessToken bobAccessToken = accessTokenFor(jamesServer, BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
            .when()
            .post("/jmap");
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = with()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + getInboxId(aliceAccessToken) + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1))
            .extract()
            .path(ARGUMENTS + ".messageIds");

        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"mailboxIds\": [\"" + getSpamId(aliceAccessToken) + "\"] } } }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".updated", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));

        // Alice is moving this message to trash
        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"mailboxIds\": [\"" + getTrashId(aliceAccessToken) + "\"] } } }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".updated", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getTrashId(aliceAccessToken), 1));

        // Bob is sending again the same message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");

        // This message is delivered in Alice Spam mailbox (she now must have 1 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));
    }

    @Test
    default void spamAssassinShouldForgetMessagesMovedOutOfSpamFolderUsingIMAP(GuiceJamesServer jamesServer,
                                                                               SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        AccessToken aliceAccessToken = accessTokenFor(jamesServer, ALICE, ALICE_PASSWORD);
        AccessToken bobAccessToken = accessTokenFor(jamesServer, BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
            .when()
            .post("/jmap");
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = with()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + getInboxId(aliceAccessToken) + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1))
            .extract()
            .path(ARGUMENTS + ".messageIds");

        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"mailboxIds\": [\"" + getSpamId(aliceAccessToken) + "\"] } } }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".updated", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));

        // Alice is moving this message out of Spam -> forgetting in SpamAssassin
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(ALICE, ALICE_PASSWORD)
                .select("Spam");

            testIMAPClient.moveFirstMessage(TestIMAPClient.INBOX);
        }
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));

        // Bob is sending again the same message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");

        // This message is delivered in Alice INBOX mailbox (she now must have 2 messages in her Inbox mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 2));
    }

    @Test
    default void expungingSpamMessageShouldNotImpactSpamAssassinState(GuiceJamesServer jamesServer,
                                                                      SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        AccessToken aliceAccessToken = accessTokenFor(jamesServer, ALICE, ALICE_PASSWORD);
        AccessToken bobAccessToken = accessTokenFor(jamesServer, BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
            .when()
            .post("/jmap");
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = with()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + getInboxId(aliceAccessToken) + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1))
            .extract()
            .path(ARGUMENTS + ".messageIds");

        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"mailboxIds\": [\"" + getSpamId(aliceAccessToken) + "\"] } } }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".updated", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));

        // Alice is deleting this message
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(ALICE, ALICE_PASSWORD)
                .select("Spam");

            testIMAPClient.setFlagsForAllMessagesInMailbox("\\Deleted");
            testIMAPClient.expunge();
        }
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 0));

        // Bob is sending again the same message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");

        // This message is delivered in Alice SPAM mailbox (she now must have 1 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));
    }

    @Test
    default void deletingSpamMessageShouldNotImpactSpamAssassinState(GuiceJamesServer jamesServer,
                                                                     SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        AccessToken aliceAccessToken = accessTokenFor(jamesServer, ALICE, ALICE_PASSWORD);
        AccessToken bobAccessToken = accessTokenFor(jamesServer, BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
            .when()
            .post("/jmap");
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = with()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + getInboxId(aliceAccessToken) + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1))
            .extract()
            .path(ARGUMENTS + ".messageIds");

        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"mailboxIds\": [\"" + getSpamId(aliceAccessToken) + "\"] } } }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".updated", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));

        // Alice is deleting this message
        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"destroy\": [\"%s\"] }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".destroyed", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 0));

        // Bob is sending again the same message to Alice
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreate(bobAccessToken))
        .when()
            .post("/jmap");

        // This message is delivered in Alice SPAM mailbox (she now must have 1 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));
    }

    default String setMessageCreate(AccessToken accessToken) {
        return "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"creationId1337\" : {" +
            "        \"from\": { \"email\": \"" + BOB + "\"}," +
            "        \"to\": [{ \"name\": \"recipient\", \"email\": \"" + ALICE + "\"}]," +
            "        \"subject\": \"Happy News\"," +
            "        \"textBody\": \"This is a SPAM!!!\r\n\r\n\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";
    }

    @Test
    default void spamShouldBeDeliveredInSpamMailboxOrInboxWhenMultipleRecipientsConfigurations(GuiceJamesServer jamesServer,
                                                                                               SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        AccessToken aliceAccessToken = accessTokenFor(jamesServer, ALICE, ALICE_PASSWORD);
        AccessToken bobAccessToken = accessTokenFor(jamesServer, BOB, BOB_PASSWORD);
        AccessToken paulAccessToken = accessTokenFor(jamesServer, PAUL, PAUL_PASSWORD);

        // Bob is sending a message to Alice & Paul
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreateToMultipleRecipients(bobAccessToken))
        .when()
            .post("/jmap");
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(paulAccessToken, getInboxId(paulAccessToken), 1));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = with()
            .header("Authorization", aliceAccessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + getInboxId(aliceAccessToken) + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1))
            .extract()
            .path(ARGUMENTS + ".messageIds");

        messageIds
            .forEach(messageId -> given()
                .header("Authorization", aliceAccessToken.asString())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"mailboxIds\": [\"" + getSpamId(aliceAccessToken) + "\"] } } }, \"#0\"]]", messageId))
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".updated", hasSize(1)));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 1));

        // Bob is sending again the same message to Alice & Paul
        given()
            .header("Authorization", bobAccessToken.asString())
            .body(setMessageCreateToMultipleRecipients(bobAccessToken))
        .when()
            .post("/jmap");

        // This message is delivered in Alice Spam mailbox (she now must have 2 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getSpamId(aliceAccessToken), 2));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceAccessToken, getInboxId(aliceAccessToken), 0));
        // This message is delivered in Paul Inbox (he now must have 2 messages in his Inbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(paulAccessToken, getInboxId(paulAccessToken), 2));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(paulAccessToken, getSpamId(paulAccessToken), 0));
    }

    default String setMessageCreateToMultipleRecipients(AccessToken accessToken) {
        return "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"creationId1337\" : {" +
            "        \"from\": { \"email\": \"" + BOB + "\"}," +
            "        \"to\": [{ \"name\": \"alice\", \"email\": \"" + ALICE + "\"}, " +
            "                 { \"name\": \"paul\", \"email\": \"" + PAUL + "\"}]," +
            "        \"subject\": \"Happy News\"," +
            "        \"textBody\": \"This is a SPAM!!!\r\n\r\n\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";
    }

    default void assertMessagesFoundInMailbox(AccessToken accessToken, String mailboxId, int expectedNumberOfMessages) {
        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(expectedNumberOfMessages));
    }

    default String getMailboxId(AccessToken accessToken, Role role) {
        return getAllMailboxesIds(accessToken).stream()
            .filter(x -> x.get("role").equalsIgnoreCase(role.serialize()))
            .map(x -> x.get("id"))
            .findFirst().get();
    }

    default List<Map<String, String>> getAllMailboxesIds(AccessToken accessToken) {
        return with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"properties\": [\"role\", \"id\"]}, \"#0\"]]")
        .post("/jmap")
            .andReturn()
            .body()
            .jsonPath()
            .getList(ARGUMENTS + ".list");
    }

    default String getInboxId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.INBOX);
    }

    default String getOutboxId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.OUTBOX);
    }

    default String getSpamId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.SPAM);
    }

    default String getTrashId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.TRASH);
    }

    default void assertEveryListenerGotCalled(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .describedAs("waiting that every listener get called")
            .isTrue();
    }
}
