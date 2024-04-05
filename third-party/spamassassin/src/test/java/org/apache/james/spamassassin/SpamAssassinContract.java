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

package org.apache.james.spamassassin;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static io.restassured.http.ContentType.JSON;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JmapRFCCommonRequests.ACCEPT_JMAP_RFC_HEADER;
import static org.apache.james.jmap.JmapRFCCommonRequests.UserCredential;
import static org.apache.james.jmap.JmapRFCCommonRequests.getMailboxId;
import static org.apache.james.jmap.JmapRFCCommonRequests.getUserCredential;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.JmapRFCCommonRequests;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.mailbox.Role;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.hamcrest.Matchers;
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
            .addHeader(ACCEPT_JMAP_RFC_HEADER.getName(), ACCEPT_JMAP_RFC_HEADER.getValue())
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

    @Test
    default void spamShouldBeDeliveredInSpamMailboxWhenSameMessageHasAlreadyBeenMovedToSpam(
        GuiceJamesServer jamesServer,
        SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {

        spamAssassin.train(ALICE);
        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);
        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        String aliceInboxId = JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.INBOX);
        List<String> msgIds = JmapRFCCommonRequests.listMessageIdsInMailbox(aliceCredential, aliceInboxId);

        String aliceSpamMailboxId = JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.SPAM);

        Consumer<String> moveMessageToSpamMailbox = messageId -> given()
            .auth().basic(aliceCredential.username().asString(), aliceCredential.password())
            .body("""
                {
                    "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                    "methodCalls": [
                        ["Email/set", {
                            "accountId": "%s",
                            "update": {
                                "%s":{
                                    "mailboxIds": { "%s" : true}
                                }
                            }
                        }, "c1"]]
                }""".formatted(aliceCredential.accountId(), messageId, aliceSpamMailboxId))
            .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .contentType(JSON);

        msgIds.forEach(moveMessageToSpamMailbox);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));

        // Bob is sending again the same message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        // This message is delivered in Alice Spam mailbox (she now must have 2 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 2));
    }

    @Test
    default void imapCopiesToSpamMailboxShouldBeConsideredAsSpam(GuiceJamesServer jamesServer,
                                                                 SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);

        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);
        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));


        // Alice is copying this message to Spam -> learning in SpamAssassin
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(ALICE, ALICE_PASSWORD)
                .select(TestIMAPClient.INBOX);

            testIMAPClient.copyFirstMessage("Spam");
        }
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));

        // Bob is sending again the same message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        // This message is delivered in Alice Spam mailbox (she now must have 2 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 2));
    }

    @Test
    default void imapMovesToSpamMailboxShouldBeConsideredAsSpam(GuiceJamesServer jamesServer,
                                                                SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);
        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        bobSendSpamEmailToAlice(bobCredential);
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(ALICE, ALICE_PASSWORD)
                .select(TestIMAPClient.INBOX);

            testIMAPClient.moveFirstMessage("Spam");
        }
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));

        // Bob is sending again the same message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        // This message is delivered in Alice Spam mailbox (she now must have 2 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 2));
    }

    @Test
    default void spamAssassinShouldForgetMessagesMovedOutOfSpamFolderUsingJMAP(GuiceJamesServer jamesServer,
                                                                               SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);
        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        bobSendSpamEmailToAlice(bobCredential);
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        String aliceInboxId = JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.INBOX);
        List<String> msgIds = JmapRFCCommonRequests.listMessageIdsInMailbox(aliceCredential, aliceInboxId);

        String aliceSpamMailboxId = JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.SPAM);

        Consumer<String> moveMessageToSpamMailbox = messageId -> given()
            .auth().basic(aliceCredential.username().asString(), aliceCredential.password())
            .body("""
                {
                    "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                    "methodCalls": [
                        ["Email/set", {
                            "accountId": "%s",
                            "update": {
                                "%s":{
                                    "mailboxIds": { "%s" : true}
                                }
                            }
                        }, "c1"]]
                }""".formatted(aliceCredential.accountId(), messageId, aliceSpamMailboxId))
            .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .contentType(JSON);

        msgIds.forEach(moveMessageToSpamMailbox);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));

        // Alice is moving this message out of Spam -> forgetting in SpamAssassin
        Consumer<String> moveMessageOutOfSpamMailbox = messageId -> given()
            .auth().basic(aliceCredential.username().asString(), aliceCredential.password())
            .body("""
                {
                    "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                    "methodCalls": [
                        ["Email/set", {
                            "accountId": "%s",
                            "update": {
                                "%s":{
                                    "mailboxIds": { "%s" : true}
                                }
                            }
                        }, "c1"]]
                }""".formatted(aliceCredential.accountId(), messageId, aliceInboxId))
            .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .contentType(JSON);
        msgIds.forEach(moveMessageOutOfSpamMailbox);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));

        // Bob is sending again the same message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        // This message is delivered in Alice INBOX mailbox (she now must have 2 messages in her Inbox mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 2));
    }

    @Test
    default void movingAMailToTrashShouldNotImpactSpamassassinLearning(GuiceJamesServer jamesServer,
                                                                       SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);
        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);

        // Bob is sending a message to Alice
        bobSendSpamEmailToAlice(bobCredential);
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        String aliceInboxId = JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.INBOX);
        List<String> msgIds = JmapRFCCommonRequests.listMessageIdsInMailbox(aliceCredential, aliceInboxId);

        String aliceSpamMailboxId = JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.SPAM);
        moveMessagesToNewMailbox(msgIds, aliceSpamMailboxId, aliceCredential);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));

        // Alice is moving this message to trash
        moveMessagesToNewMailbox(msgIds, getTrashId(aliceCredential), aliceCredential);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getTrashId(aliceCredential), 1));

        // Bob is sending again the same message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        // This message is delivered in Alice Spam mailbox (she now must have 1 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));
    }

    @Test
    default void spamAssassinShouldForgetMessagesMovedOutOfSpamFolderUsingIMAP(GuiceJamesServer jamesServer,
                                                                               SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);

        // Bob is sending a message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = JmapRFCCommonRequests.listMessageIdsInMailbox(aliceCredential, JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.INBOX));
        moveMessagesToNewMailbox(messageIds, getMailboxId(aliceCredential, Role.SPAM), aliceCredential);
        
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));

        // Alice is moving this message out of Spam -> forgetting in SpamAssassin
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(ALICE, ALICE_PASSWORD)
                .select("Spam");

            testIMAPClient.moveFirstMessage(TestIMAPClient.INBOX);
        }
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));

        // Bob is sending again the same message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        // This message is delivered in Alice INBOX mailbox (she now must have 2 messages in her Inbox mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 2));
    }

    @Test
    default void expungingSpamMessageShouldNotImpactSpamAssassinState(GuiceJamesServer jamesServer,
                                                                      SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);

        // Bob is sending a message to Alice
        bobSendSpamEmailToAlice(bobCredential);
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = JmapRFCCommonRequests.listMessageIdsInMailbox(aliceCredential, JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.INBOX));
        moveMessagesToNewMailbox(messageIds, getMailboxId(aliceCredential, Role.SPAM), aliceCredential);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));

        // Alice is deleting this message
        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(ALICE, ALICE_PASSWORD)
                .select("Spam");

            testIMAPClient.setFlagsForAllMessagesInMailbox("\\Deleted");
            testIMAPClient.expunge();
        }
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 0));

        // Bob is sending again the same message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        // This message is delivered in Alice SPAM mailbox (she now must have 1 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));
    }

    @Test
    default void deletingSpamMessageShouldNotImpactSpamAssassinState(GuiceJamesServer jamesServer,
                                                                     SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);

        // Bob is sending a message to Alice
        bobSendSpamEmailToAlice(bobCredential);
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertEveryListenerGotCalled(jamesServer));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        List<String> messageIds = JmapRFCCommonRequests.listMessageIdsInMailbox(aliceCredential, JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.INBOX));
        moveMessagesToNewMailbox(messageIds, getMailboxId(aliceCredential, Role.SPAM), aliceCredential);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));

        // Alice is deleting this message
        JmapRFCCommonRequests.deleteMessages(aliceCredential, messageIds);

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 0));

        // Bob is sending again the same message to Alice
        bobSendSpamEmailToAlice(bobCredential);

        // This message is delivered in Alice SPAM mailbox (she now must have 1 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));
    }

    default void bobSendSpamEmailToAlice(UserCredential bobCredential) {
        String bobOutboxId = JmapRFCCommonRequests.getOutboxId(bobCredential);
        String requestBody =
            "{" +
                "    \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\", \"urn:ietf:params:jmap:submission\"]," +
                "    \"methodCalls\": [" +
                "        [\"Email/set\", {" +
                "            \"accountId\": \"" + bobCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"e1526\": {" +
                "                    \"mailboxIds\": { \"" + bobOutboxId + "\": true }," +
                "                    \"subject\": \"Happy News\"," +
                "                    \"textBody\": [{" +
                "                        \"partId\": \"a49d\"," +
                "                        \"type\": \"text/plain\"" +
                "                    }]," +
                "                    \"bodyValues\": {" +
                "                        \"a49d\": {" +
                "                            \"value\": \"This is a SPAM!!!\"" +
                "                        }" +
                "                    }," +
                "                    \"to\": [{" +
                "                        \"email\": \"" + ALICE + "\"" +
                "                    }]," +
                "                    \"from\": [{" +
                "                        \"email\": \"" + BOB + "\"" +
                "                    }]" +
                "                }" +
                "            }" +
                "        }, \"c1\"]," +
                "        [\"Email/get\", {" +
                "            \"accountId\": \"" + bobCredential.accountId() + "\"," +
                "            \"ids\": [\"#e1526\"]," +
                "            \"properties\": [\"sentAt\"]" +
                "        }, \"c2\"]," +
                "        [\"EmailSubmission/set\", {" +
                "            \"accountId\": \"" + bobCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"k1490\": {" +
                "                    \"emailId\": \"#e1526\"," +
                "                    \"envelope\": {" +
                "                        \"mailFrom\": {\"email\": \"" + BOB + "\"}," +
                "                        \"rcptTo\": [{" +
                "                            \"email\": \"" + ALICE + "\"" +
                "                        }]" +
                "                    }" +
                "                }" +
                "            }" +
                "        }, \"c3\"]" +
                "    ]" +
                "}";

        with()
            .auth().basic(bobCredential.username().asString(), bobCredential.password())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("methodResponses[2][1].created", Matchers.is(notNullValue()));

    }

    @Test
    default void spamShouldBeDeliveredInSpamMailboxOrInboxWhenMultipleRecipientsConfigurations(GuiceJamesServer jamesServer,
                                                                                               SpamAssassinExtension.SpamAssassin spamAssassin) throws Exception {
        spamAssassin.train(ALICE);
        UserCredential aliceCredential = getUserCredential(ALICE, ALICE_PASSWORD);
        UserCredential bobCredential = getUserCredential(BOB, BOB_PASSWORD);
        UserCredential paulCredential = getUserCredential(PAUL, PAUL_PASSWORD);

        // Bob is sending a message to Alice & Paul
        String bobSendSpamEmailToAliceAndPaulRequest =
            "{" +
                "    \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\", \"urn:ietf:params:jmap:submission\"]," +
                "    \"methodCalls\": [" +
                "        [\"Email/set\", {" +
                "            \"accountId\": \"" + bobCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"e1526\": {" +
                "                    \"mailboxIds\": { \"" + JmapRFCCommonRequests.getOutboxId(bobCredential) + "\": true }," +
                "                    \"subject\": \"Happy News\"," +
                "                    \"textBody\": [{" +
                "                        \"partId\": \"a49d\"," +
                "                        \"type\": \"text/plain\"" +
                "                    }]," +
                "                    \"bodyValues\": {" +
                "                        \"a49d\": {" +
                "                            \"value\": \"This is a SPAM!!!\"" +
                "                        }" +
                "                    }," +
                "                    \"to\": [{\"email\": \"" + ALICE + "\"}, {\"email\": \"" + PAUL + "\"}]," +
                "                    \"from\": [{" +
                "                        \"email\": \"" + BOB + "\"" +
                "                    }]" +
                "                }" +
                "            }" +
                "        }, \"c1\"]," +
                "        [\"Email/get\", {" +
                "            \"accountId\": \"" + bobCredential.accountId() + "\"," +
                "            \"ids\": [\"#e1526\"]," +
                "            \"properties\": [\"sentAt\"]" +
                "        }, \"c2\"]," +
                "        [\"EmailSubmission/set\", {" +
                "            \"accountId\": \"" + bobCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"k1490\": {" +
                "                    \"emailId\": \"#e1526\"," +
                "                    \"envelope\": {" +
                "                        \"mailFrom\": {\"email\": \"" + BOB + "\"}," +
                "                        \"rcptTo\": [{\"email\": \"" + ALICE + "\"}, {\"email\": \"" + PAUL + "\"}]" +
                "                    }" +
                "                }" +
                "            }" +
                "        }, \"c3\"]" +
                "    ]" +
                "}";

        Consumer<String> bobSendAndEmail = requestBody -> with()
            .auth().basic(bobCredential.username().asString(), bobCredential.password())
            .body(requestBody)
            .post("/jmap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .body("methodResponses[2][1].created", Matchers.is(notNullValue()));
        
        bobSendAndEmail.accept(bobSendSpamEmailToAliceAndPaulRequest);
   
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 1));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(paulCredential, getInboxId(paulCredential), 1));

        // Alice is moving this message to Spam -> learning in SpamAssassin
        String aliceInboxId = JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.INBOX);
        List<String> msgIds = JmapRFCCommonRequests.listMessageIdsInMailbox(aliceCredential, aliceInboxId);
        moveMessagesToNewMailbox(msgIds, JmapRFCCommonRequests.getMailboxId(aliceCredential, Role.SPAM), aliceCredential);
        
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 1));

        // Bob is sending again the same message to Alice & Paul
        bobSendAndEmail.accept(bobSendSpamEmailToAliceAndPaulRequest);

        // This message is delivered in Alice Spam mailbox (she now must have 2 messages in her Spam mailbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getSpamId(aliceCredential), 2));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(aliceCredential, getInboxId(aliceCredential), 0));
        // This message is delivered in Paul Inbox (he now must have 2 messages in his Inbox)
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(paulCredential, getInboxId(paulCredential), 2));
        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> assertMessagesFoundInMailbox(paulCredential, getSpamId(paulCredential), 0));
    }

    default void assertMessagesFoundInMailbox(UserCredential userCredential, String mailboxId, int expectedNumberOfMessages) {
        assertThat(JmapRFCCommonRequests.listMessageIdsInMailbox(userCredential, mailboxId))
            .hasSize(expectedNumberOfMessages);
    }

    default String getInboxId(UserCredential userCredential) {
        return JmapRFCCommonRequests.getMailboxId(userCredential, Role.INBOX);
    }

    default String getSpamId(UserCredential userCredential) {
        return JmapRFCCommonRequests.getMailboxId(userCredential,Role.SPAM);
    }

    default String getTrashId(UserCredential userCredential) {
        return JmapRFCCommonRequests.getMailboxId(userCredential,Role.TRASH);
    }

    default void assertEveryListenerGotCalled(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(SpoolerProbe.class).processingFinished())
            .describedAs("waiting that every listener get called")
            .isTrue();
    }

    private void moveMessagesToNewMailbox(List<String> messageIds, String newMailboxId, UserCredential userCredential) {
        Consumer<String> moveMessagesToNewMailbox = messageId -> given()
            .auth().basic(userCredential.username().asString(), userCredential.password())
            .body("""
                {
                    "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                    "methodCalls": [
                        ["Email/set", {
                            "accountId": "%s",
                            "update": {
                                "%s":{
                                    "mailboxIds": { "%s" : true}
                                }
                            }
                        }, "c1"]]
                }""".formatted(userCredential.accountId(), messageId, newMailboxId))
            .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .contentType(JSON);

        messageIds.forEach(moveMessagesToNewMailbox);
    }
}
