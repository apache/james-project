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

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ALICE;
import static org.apache.james.jmap.TestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.BOB;
import static org.apache.james.jmap.TestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.NAME;
import static org.apache.james.jmap.TestingConstants.calmlyAwait;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.transport.mailets.remote.delivery.HeloNameProvider.LOCALHOST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.categories.BasicFeature;
import org.apache.james.jmap.categories.CassandraAndElasticSearchCategory;
import org.apache.james.jmap.model.Number;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.message.SingleBodyBuilder;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.date.ImapDateTimeFormatter;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.JmapGuiceProbe;
import org.awaitility.Duration;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import io.restassured.RestAssured;

public abstract class GetMessageListMethodTest {
    public static final int LIMIT_TO_3_MESSAGES = 3;
    private static final String FORWARDED = "$Forwarded";
    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Paris");
    private ACLProbeImpl aclProbe;

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    protected abstract void await();

    private AccessToken aliceAccessToken;
    private AccessToken bobAccessToken;
    private GuiceJamesServer jmapServer;
    private MailboxProbeImpl mailboxProbe;
    private DataProbe dataProbe;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        aclProbe = jmapServer.getProbe(ACLProbeImpl.class);

        RestAssured.requestSpecification = jmapRequestSpecBuilder
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(ALICE, ALICE_PASSWORD);
        this.aliceAccessToken = authenticateJamesUser(baseUri(jmapServer), ALICE, ALICE_PASSWORD);
        dataProbe.addUser(BOB, BOB_PASSWORD);
        this.bobAccessToken = authenticateJamesUser(baseUri(jmapServer), BOB, BOB_PASSWORD);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void getMessageListShouldNotListMessageIfTheUserHasOnlyLookupRight() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, "delegated");
        MailboxPath delegatedMailboxPath = MailboxPath.forUser(BOB, "delegated");
        mailboxProbe.appendMessage(BOB, delegatedMailboxPath,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        await();

        aclProbe.replaceRights(delegatedMailboxPath,
            ALICE,
            new Rfc4314Rights(Right.Lookup));

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessagesListShouldListMessageWhenTheUserHasOnlyReadRight() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, "delegated");
        MailboxPath delegatedMailboxPath = MailboxPath.forUser(BOB, "delegated");
        ComposedMessageId message = mailboxProbe.appendMessage(BOB, delegatedMailboxPath,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        await();

        aclProbe.replaceRights(delegatedMailboxPath,
            ALICE,
            new Rfc4314Rights(Right.Read));

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldNotFilterMessagesWhenTextFilterMatchesBodyAfterTheMessageMailboxHasBeenChanged() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        MailboxId otherMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "otherMailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags());
        await();

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"setMessages\", {\"update\":{\"" + messageId + "\":{\"mailboxIds\": [\"" + otherMailboxId.serialize() + "\"]}}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"text\":\"tiramisu\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", contains(messageId));
    }

    @Category(BasicFeature.class)
    @Test
    public void searchByFromFieldShouldSupportUTF8FromName() throws Exception {
        String toUsername = "username1@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(toUsername, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, toUsername, DefaultMailboxes.INBOX);

        String messageCreationId = "creationId1337";
        String fromName = "Üsteliğhan Maşrapa";
        String fromAddress = ALICE;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"" + fromName + "\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + BOB + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(aliceAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = with()
            .header("Authorization", aliceAccessToken.serialize())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        String searchedMessageId = calmlyAwait.atMost(Duration.TEN_SECONDS)
            .until(() -> searchFirstMessageByFromField(fromName), Matchers.notNullValue());

        assertThat(searchedMessageId)
            .isEqualTo(messageId);
    }

    private String searchFirstMessageByFromField(String from) {
        String searchRequest = "[" +
            "  [" +
            "    \"getMessageList\"," +
            "    {" +
            "      \"filter\": {" +
            "        \"from\": \"" + from + "\"" +
            "      }," +
            "      \"sort\": [" +
            "        \"date desc\"" +
            "      ]," +
            "      \"collapseThreads\": false," +
            "      \"fetchMessages\": false," +
            "      \"position\": 0," +
            "      \"limit\": 1" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        return with()
            .header("Authorization", aliceAccessToken.serialize())
            .body(searchRequest)
            .post("/jmap")
        .then()
            .statusCode(200)
            .extract()
            .path(ARGUMENTS + ".messageIds[0]");
    }

    @Test
    public void getMessageListShouldListMessageThatHasBeenMovedInAMailboxWhereTheUserHasOnlyReadRight() throws Exception {
        MailboxId delegatedMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, "delegated");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, "not_delegated");

        MailboxPath notDelegatedMailboxPath = MailboxPath.forUser(BOB, "not_delegated");
        MailboxPath delegatedMailboxPath = MailboxPath.forUser(BOB, "delegated");

        ComposedMessageId message = mailboxProbe.appendMessage(BOB, notDelegatedMailboxPath,
            new ByteArrayInputStream("Subject: chaussette\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        await();

        aclProbe.replaceRights(delegatedMailboxPath,
            ALICE,
            new Rfc4314Rights(Right.Read));

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", bobAccessToken.serialize())
            .body("[[\"setMessages\", {\"update\":{\"" + messageId + "\":{\"mailboxIds\": [\"" + delegatedMailboxId.serialize() + "\"]}}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"subject\":\"chaussette\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListShouldNotDuplicateMessagesInSeveralMailboxes() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        MailboxId mailboxId2 = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox2");

        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        await();

        jmapServer.getProbe(JmapGuiceProbe.class).setInMailboxes(message.getMessageId(), ALICE, mailboxId, mailboxId2);

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1));
    }

    @Test
    public void getMessageListSetFlaggedFilterShouldResultFlaggedMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.FLAGGED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isFlagged\":\"true\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageFlagged.getMessageId().serialize()),
                    not(containsInAnyOrder(messageNotFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListUnsetFlaggedFilterShouldReturnNotFlaggedMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.FLAGGED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isFlagged\":\"false\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageNotFlagged.getMessageId().serialize()),
                    not(containsInAnyOrder(messageFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListReadFilterShouldReturnOnlyReadMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotRead = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageRead = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.SEEN));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isUnread\":\"false\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageRead.getMessageId().serialize()),
                    not(containsInAnyOrder(messageNotRead.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListUnreadFilterShouldReturnOnlyUnreadMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotRead = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageRead = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.SEEN));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isUnread\":\"true\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageNotRead.getMessageId().serialize()),
                    not(containsInAnyOrder(messageRead.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListSetDraftFilterShouldReturnOnlyDraftMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotDraft = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageDraft = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.DRAFT));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isDraft\":\"true\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageDraft.getMessageId().serialize()),
                    not(containsInAnyOrder(messageNotDraft.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListUnsetDraftFilterShouldReturnOnlyNonDraftMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotDraft = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageDraft = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.DRAFT));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isDraft\":\"false\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageNotDraft.getMessageId().serialize()),
                    not(containsInAnyOrder(messageDraft.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListSetAnsweredFilterShouldReturnOnlyAnsweredMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotAnswered = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageAnswered = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.ANSWERED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isAnswered\":\"true\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageAnswered.getMessageId().serialize()),
                    not(containsInAnyOrder(messageNotAnswered.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListUnsetAnsweredFilterShouldReturnOnlyNotAnsweredMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotAnswered = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageAnswered = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.ANSWERED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isAnswered\":\"false\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageNotAnswered.getMessageId().serialize()),
                    not(containsInAnyOrder(messageAnswered.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListSetForwardedFilterShouldReturnOnlyForwardedMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotForwarded = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageForwarded = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(FORWARDED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isForwarded\":\"true\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                containsInAnyOrder(messageForwarded.getMessageId().serialize()),
                not(containsInAnyOrder(messageNotForwarded.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListUnsetForwardedFilterShouldReturnOnlyNotForwardedMessages() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotForwarded = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageForwarded = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(FORWARDED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"isForwarded\":\"false\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                containsInAnyOrder(messageNotForwarded.getMessageId().serialize()),
                not(containsInAnyOrder(messageForwarded.getMessageId().serialize()))));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListANDOperatorShouldReturnMessagesWhichMatchAllConditions() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotSeenNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageNotSeenFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.FLAGGED));
        ComposedMessageId messageSeenNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, new Flags(Flags.Flag.SEEN));
        ComposedMessageId messageSeenFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, FlagsBuilder.builder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build());

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"operator\":\"AND\",\"conditions\":[{\"isFlagged\":\"true\"},{\"isUnread\":\"true\"}]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageNotSeenFlagged.getMessageId().serialize()),
                    not(containsInAnyOrder(messageNotSeenNotFlagged.getMessageId().serialize(),
                            messageSeenNotFlagged.getMessageId().serialize(),
                            messageSeenFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListOROperatorShouldReturnMessagesWhichMatchOneOfAllConditions() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotSeenNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageNotSeenFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.FLAGGED));
        ComposedMessageId messageSeenNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, new Flags(Flags.Flag.SEEN));
        ComposedMessageId messageSeenFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, FlagsBuilder.builder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build());

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"operator\":\"OR\",\"conditions\":[{\"isFlagged\":\"true\"},{\"isUnread\":\"true\"}]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageNotSeenFlagged.getMessageId().serialize(),
                            messageSeenFlagged.getMessageId().serialize(),
                            messageNotSeenNotFlagged.getMessageId().serialize()),
                    not(containsInAnyOrder(messageSeenNotFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListNOTOperatorShouldReturnMessagesWhichNotMatchAnyCondition() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotSeenNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageNotSeenFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.FLAGGED));
        ComposedMessageId messageSeenNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, new Flags(Flags.Flag.SEEN));
        ComposedMessageId messageSeenFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, FlagsBuilder.builder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build());

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"operator\":\"NOT\",\"conditions\":[{\"isFlagged\":\"true\"},{\"isUnread\":\"true\"}]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageSeenNotFlagged.getMessageId().serialize()),
                    not(containsInAnyOrder(messageNotSeenFlagged.getMessageId().serialize(),
                            messageSeenFlagged.getMessageId().serialize(),
                            messageNotSeenNotFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListNestedOperatorsShouldReturnMessagesWhichMatchConditions() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotSeenNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageNotSeenFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.FLAGGED));
        ComposedMessageId messageSeenNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, new Flags(Flags.Flag.SEEN));
        ComposedMessageId messageSeenFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, FlagsBuilder.builder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build());

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"operator\":\"OR\",\"conditions\":[" +
                "{\"operator\":\"AND\", \"conditions\":[{\"isFlagged\":\"true\"},{\"isUnread\":\"true\"}]}," +
                "{\"operator\":\"AND\", \"conditions\":[{\"isFlagged\":\"true\"},{\"isUnread\":\"false\"}]}" +
                "]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                    containsInAnyOrder(messageSeenFlagged.getMessageId().serialize(),
                            messageNotSeenFlagged.getMessageId().serialize()),
                    not(containsInAnyOrder(messageNotSeenNotFlagged.getMessageId().serialize(),
                            messageSeenNotFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListShouldReturnErrorInvalidArgumentsWhenRequestIsInvalid() {
        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"));
    }

    @Test
    public void getMessageListShouldReturnErrorInvalidArgumentsWhenHeaderIsInvalid() {
        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"header\":[\"132\", \"456\", \"789\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"));
    }

    @Test
    public void getMessageListShouldSupportHasAttachmentSetToTrue() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags());
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"hasAttachment\":\"true\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSupportHasAttachmentSetToFalse() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags());
        ComposedMessageId message3 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedImage.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"hasAttachment\":\"false\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message1.getMessageId().serialize(), message3.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldNotFailWhenHeaderIsValid() {
        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"header\":[\"132\", \"456\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"));
    }

    @Test
    public void getMessageListShouldReturnAllMessagesWhenSingleMailboxNoParameters() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldReturnAllMessagesWhenMultipleMailboxesAndNoParameters() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox2");
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox2"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListShouldReturnAllMessagesOfCurrentUserOnlyWhenMultipleMailboxesAndNoParameters() throws Exception {
        String otherUser = "other@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(otherUser, password);

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox2");
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox2"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, otherUser, "mailbox");
        mailboxProbe.appendMessage(otherUser, MailboxPath.forUser(otherUser, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListShouldExcludeMessagesWhenInMailboxesFilterMatches() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId.serialize() + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenMultipleInMailboxesFilterMatches() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        MailboxId mailboxId2 = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox2");
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body(String.format("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"%s\", \"%s\"]}}, \"#0\"]]", mailboxId.serialize(), mailboxId2.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenNotInMailboxesFilterMatches() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body(String.format("[[\"getMessageList\", {\"filter\":{\"notInMailboxes\":[\"%s\"]}}, \"#0\"]]", mailboxId.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenNotInMailboxesFilterMatchesTwice() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        MailboxId mailbox2Id = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox2");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox2"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body(String.format("[[\"getMessageList\", {\"filter\":{\"notInMailboxes\":[\"%s\", \"%s\"]}}, \"#0\"]]", mailboxId.serialize(), mailbox2Id.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenIdenticalNotInMailboxesAndInMailboxesFilterMatch() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body(String.format("[[\"getMessageList\", {\"filter\":{\"notInMailboxes\":[\"%s\"], \"inMailboxes\":[\"%s\"]}}, \"#0\"]]", mailboxId.serialize(), mailboxId.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    public void getMessageListShouldIncludeMessagesWhenNotInMailboxesFilterDoesNotMatch() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        MailboxId mailbox2Id = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox2");
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body(String.format("[[\"getMessageList\", {\"filter\":{\"notInMailboxes\":[\"%s\"]}}, \"#0\"]]", mailbox2Id.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldIncludeMessagesWhenEmptyNotInMailboxesFilter() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox2");
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"notInMailboxes\":[]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenInMailboxesFilterDoesntMatches() throws Exception {
        MailboxId emptyMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "emptyMailbox");

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body(String.format("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"%s\"]}}, \"#0\"]]", emptyMailboxId.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenTextFilterDoesntMatches() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"text\":\"bad\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListShouldIncludeMessagesWhenTextFilterMatchesBody() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"text\":\"html\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    @Category(CassandraAndElasticSearchCategory.class)
    public void getMessageListShouldIncludeMessagesWhenTextFilterMatchesBodyWithStemming() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                ClassLoader.getSystemResourceAsStream("eml/htmlMail.eml"), new Date(), false, new Flags());
        await();
        // text/html contains: "This is a mail with beautifull html content which contains a banana."

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"text\":\"contain banana\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldIncludeMessagesWhenSubjectFilterMatchesSubject() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"subject\":\"Image\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldIncludeMessagesWhenFromFilterMatchesFrom() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/mailWithRecipients.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"from\":\"from@james.org\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenFromFilterDoesntMatchFrom() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/mailWithRecipients.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"from\":\"to@james.org\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    public void getMessageListShouldIncludeMessagesWhenToFilterMatchesTo() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/mailWithRecipients.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"to\":\"to@james.org\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenToFilterDoesntMatchTo() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/mailWithRecipients.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"to\":\"from@james.org\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    public void getMessageListShouldIncludeMessagesWhenCcFilterMatchesCc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/mailWithRecipients.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"cc\":\"cc@james.org\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenCcFilterDoesntMatchCc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/mailWithRecipients.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"cc\":\"bcc@james.org\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    public void getMessageListShouldIncludeMessagesWhenBccFilterMatchesBcc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/mailWithRecipients.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"bcc\":\"bcc@james.org\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldExcludeMessagesWhenBccFilterDoesntMatchBcc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            ClassLoader.getSystemResourceAsStream("eml/mailWithRecipients.eml"), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"bcc\":\"to@james.org\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    @Category(CassandraAndElasticSearchCategory.class)
    public void getMessageListShouldExcludeMessagesWhenAttachmentFilterDoesntMatch() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        byte[] attachmentContent = ClassLoaderUtils.getSystemResourceAsByteArray("eml/attachment.pdf");
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(BodyPartBuilder.create()
                    .setBody(attachmentContent, "application/pdf")
                    .setContentDisposition("attachment"))
                .addBodyPart(BodyPartBuilder.create()
                    .setBody("The message has a PDF attachment.", "plain", StandardCharsets.UTF_8))
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream(DefaultMessageWriter.asBytes(message)), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"attachments\":\"no apple inside\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    @Category(CassandraAndElasticSearchCategory.class)
    public void getMessageListShouldIncludeMessagesWhenAttachmentFilterMatches() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        byte[] attachmentContent = ClassLoaderUtils.getSystemResourceAsByteArray("eml/attachment.pdf");
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(BodyPartBuilder.create()
                    .setBody(attachmentContent, "application/pdf")
                    .setContentDisposition("attachment"))
                .addBodyPart(BodyPartBuilder.create()
                    .setBody("The message has a PDF attachment.", "plain", StandardCharsets.UTF_8))
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        ComposedMessageId composedMessageId = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream(DefaultMessageWriter.asBytes(message)), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"attachments\":\"beautiful banana\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", contains(composedMessageId.getMessageId().serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateDefault() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"date\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenDateDoesntHaveCentury() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.parse("Wed, 28 Jun 17 09:23:01 +0200", ImapDateTimeFormatter.rfc5322());
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        LocalDate date2 = LocalDate.parse("Tue, 27 Jun 2017 09:23:01 +0200", ImapDateTimeFormatter.rfc5322());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date2), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"date desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateAsc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"date asc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedBySubjectAsc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: a subject\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: b subject\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"subject asc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedBySubjectDesc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: a subject\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: b subject\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"subject desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize(), message1.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByFromAsc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: subject\r\nFrom: bbb\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: subject\r\nFrom: aaa\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"from asc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize(), message1.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByFromDesc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: subject\r\nFrom: aaa\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: subject\r\nFrom: bbb\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"from desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize(), message1.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByToAsc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: subject\r\nTo: bbb\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: subject\r\nTo: aaa\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"to asc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize(), message1.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByToDesc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: subject\r\nTo: aaa\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: subject\r\nTo: bbb\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"to desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize(), message1.getMessageId().serialize()));
    }


    @Test
    public void getMessageListShouldSortMessagesWhenSortedBySizeAsc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: subject\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: subject\r\n\r\ntestmail bigger".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"size asc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedBySizeDesc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: subject\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: subject\r\n\r\ntestmail bigger".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"size desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize(), message1.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedBySizeAndDateAsc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: test\r\n\r\ntestmail really bigger".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 14:54:59 +0200\r\nSubject: test\r\n\r\ntestmail smaller".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message3 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 15:54:59 +0200\r\nSubject: test\r\n\r\ntestmail really bigger".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"size asc\", \"date desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize(), message3.getMessageId().serialize(), message1.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateAndSizeAsc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: test\r\n\r\ntestmail really bigger".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 13:54:59 +0200\r\nSubject: test\r\n\r\ntestmail smaller".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message3 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Date: Fri, 02 Jun 2017 15:54:59 +0200\r\nSubject: test\r\n\r\ntestmail really bigger".getBytes()), convertToDate(date), false, new Flags());

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"date desc\", \"size asc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message3.getMessageId().serialize(), message2.getMessageId().serialize(), message1.getMessageId().serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListShouldSupportIdSorting() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"id\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder(message2.getMessageId().serialize(), message1.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateDesc() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"date desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldWorkWhenCollapseThreadIsFalse() {
        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"collapseThreads\":false}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"));
    }

    @Test
    public void getMessageListShouldWorkWhenCollapseThreadIsTrue() {
        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"collapseThreads\":true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"));
    }

    @Test
    public void getMessageListShouldReturnAllMessagesWhenPositionIsNotGiven() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldReturnSkipMessagesWhenPositionIsGiven() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"position\":1, \"sort\":[\"date desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListShouldReturnSkipMessagesWhenPositionAndLimitGiven() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(2)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"position\":1, \"limit\":1, \"sort\":[\"date desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldReturnAllMessagesWhenLimitIsNotGiven() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder(message1.getMessageId().serialize(), message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldReturnLimitMessagesWhenLimitGiven() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"limit\":1}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldReturnLimitMessagesWithDefaultValueWhenLimitIsNotGiven() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        ComposedMessageId message3 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test4\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder(message1.getMessageId().serialize(), message2.getMessageId().serialize(), message3.getMessageId().serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListShouldChainFetchingMessagesWhenAskedFor() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"fetchMessages\":true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body("[0][0]", equalTo("messageList"))
            .body("[1][0]", equalTo("messages"))
            .body("[0][1].messageIds", hasSize(1))
            .body("[0][1].messageIds[0]", equalTo(message.getMessageId().serialize()))
            .body("[1][1].list", hasSize(1))
            .body("[1][1].list[0].id", equalTo(message.getMessageId().serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMessageListShouldComputeTextBodyWhenNoTextBodyButHtmlBody() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        String mailContent = "Content-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + "Hello <b>someone</b>, and thank you for joining example.com!";
        LocalDate date = LocalDate.now();
        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream(mailContent.getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"fetchMessages\": true, \"fetchMessageProperties\": [\"htmlBody\", \"textBody\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body("[0][0]", equalTo("messageList"))
            .body("[1][0]", equalTo("messages"))
            .body("[0][1].messageIds", hasSize(1))
            .body("[1][1].list[0].htmlBody", equalTo("Hello <b>someone</b>, and thank you for joining example.com!"))
            .body("[1][1].list[0].textBody", equalTo("Hello someone, and thank you for joining example.com!"));
    }

    @Test
    public void getMessageListHasKeywordFilterShouldReturnMessagesWithKeywords() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.FLAGGED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"hasKeyword\":\"$Flagged\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                containsInAnyOrder(messageFlagged.getMessageId().serialize()),
                not(containsInAnyOrder(messageNotFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListHasKeywordFilterShouldReturnMessagesWithUserKeywords() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        Flags flags = FlagsBuilder.builder()
            .add(Flags.Flag.FLAGGED)
            .add(FORWARDED)
            .build();

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, flags);

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"operator\":\"AND\",\"conditions\":[{\"hasKeyword\":\"$Flagged\"},{\"hasKeyword\":\"$Forwarded\"}]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
            containsInAnyOrder(messageFlagged.getMessageId().serialize()),
            not(containsInAnyOrder(messageNotFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListNotKeywordFilterShouldReturnMessagesWithoutKeywords() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.FLAGGED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"notKeyword\":\"$Flagged\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                containsInAnyOrder(messageNotFlagged.getMessageId().serialize()),
                not(containsInAnyOrder(messageFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListNotKeywordFilterShouldReturnMessagesWithoutUserKeywords() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        Flags flags = FlagsBuilder.builder()
            .add(Flags.Flag.FLAGGED)
            .add(FORWARDED)
            .build();

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, flags);

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"operator\":\"AND\",\"conditions\":[{\"notKeyword\":\"$Flagged\"},{\"notKeyword\":\"$Forwarded\"}]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                containsInAnyOrder(messageNotFlagged.getMessageId().serialize()),
                not(containsInAnyOrder(messageFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListNotKeywordFilterShouldReturnMessagesWithoutKeywordsWhenMultipleNotKeywordAndFilterOperator() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        Flags flags = FlagsBuilder.builder()
            .add(FORWARDED)
            .add(Flags.Flag.DRAFT)
            .build();

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.FLAGGED));
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, flags);

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"operator\":\"OR\",\"conditions\":[{\"notKeyword\":\"$Flagged\"},{\"notKeyword\":\"$Forwarded\"}]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds",
                containsInAnyOrder(messageNotFlagged.getMessageId().serialize(), messageFlagged.getMessageId().serialize()));
    }

    @Test
    public void getMessageListHasKeywordAndNotKeywordFilterShouldReturnMessagesWithAndWithoutKeywords() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.FLAGGED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"hasKeyword\":\"$Flagged\", \"notKeyword\":\"$Draft\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", allOf(
                containsInAnyOrder(messageFlagged.getMessageId().serialize()),
                not(containsInAnyOrder(messageNotFlagged.getMessageId().serialize()))));
    }

    @Test
    public void getMessageListHasKeywordShouldIgnoreDeleted() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.DELETED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"hasKeyword\":\"$Deleted\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds",
                containsInAnyOrder(messageFlagged.getMessageId().serialize(), messageNotFlagged.getMessageId().serialize()));
    }

    @Test
    public void getMessageListHasKeywordShouldIgnoreRecent() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.RECENT));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"hasKeyword\":\"$Recent\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds",
                containsInAnyOrder(messageFlagged.getMessageId().serialize(), messageNotFlagged.getMessageId().serialize()));
    }

    @Test
    public void getMessageListNotKeywordShouldIgnoreDeleted() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.DELETED));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"notKeyword\":\"$Deleted\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds",
                containsInAnyOrder(messageFlagged.getMessageId().serialize(), messageNotFlagged.getMessageId().serialize()));
    }

    @Test
    public void getMessageListNotKeywordShouldIgnoreRecent() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        ComposedMessageId messageNotFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        ComposedMessageId messageFlagged = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.RECENT));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"notKeyword\":\"$Recent\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds",
                containsInAnyOrder(messageFlagged.getMessageId().serialize(), messageNotFlagged.getMessageId().serialize()));
    }

    @Test
    public void getMessageListShouldSortUsingInternalDateWhenNoDateHeader() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        LocalDate date = LocalDate.now();
        ComposedMessageId message1 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), convertToDate(date.plusDays(1)), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), convertToDate(date), false, new Flags());

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"date asc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize(), message1.getMessageId().serialize()));
    }

    @Test
    public void getMessageListFileNameFilterShouldReturnOnlyMessagesWithMatchingAttachmentFileNames() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setSubject("test")
                    .setBody("content", StandardCharsets.UTF_8)));
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setBody(
                        MultipartBuilder.create("alternative")
                            .addBodyPart(BodyPartBuilder.create()
                                .setContentDisposition("attachment", "matchme.txt")
                                .setBody(SingleBodyBuilder.create()
                                    .setText("this is the file content...")
                                    .setCharset(StandardCharsets.UTF_8)
                                    .build())
                                .build())
                            .build())));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"attachmentFileName\":\"matchme.txt\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize()));
    }

    @Test
    public void getMessageListFileNameFilterShouldNotReturnMessagesWithOnlyAttachmentContentMatching() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setBody(
                        MultipartBuilder.create("alternative")
                            .addBodyPart(BodyPartBuilder.create()
                                .setContentDisposition("attachment", "nomatch.md")
                                .setBody(SingleBodyBuilder.create()
                                    .setText("matchme.txt ...")
                                    .setCharset(StandardCharsets.UTF_8)
                                    .build())
                                .build())
                            .build())));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"attachmentFileName\":\"matchme.txt\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(0));
    }

    @Test
    public void getMessageListTextFilterShouldReturnOnlyMessagesWithMatchingAttachmentFileNames() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");

        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setSubject("test")
                    .setBody("content", StandardCharsets.UTF_8)));
        ComposedMessageId message2 = mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setBody(
                        MultipartBuilder.create("alternative")
                            .addBodyPart(BodyPartBuilder.create()
                                .setContentDisposition("attachment", "matchme.txt")
                                .setBody(SingleBodyBuilder.create()
                                    .setText("this is the file content...")
                                    .setCharset(StandardCharsets.UTF_8)
                                    .build())
                                .build())
                            .build())));

        await();

        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"text\":\"matchme.txt\"}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize()));
    }

    private Date convertToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZONE_ID).toInstant());
    }

    @Test
    public void getMessageListShouldAcceptLessThan2Pow53NumberForPosition() {
        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"position\":" + Number.MAX_VALUE + "}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"));
    }

    @Test
    public void getMessageListShouldErrorWhenPositionOver2Pow53() {
        given()
            .header("Authorization", aliceAccessToken.serialize())
            .body("[[\"getMessageList\", {\"position\":" + Number.MAX_VALUE + 1 + "}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("value should be positive and less than 2^53"));
    }

    @Test
    public void getMessageListShouldReturnTwoMessagesWhenCopiedAtOnceViaIMAP() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "mailbox");
        MailboxId otherMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "otherMailbox");

        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
            MessageManager.AppendCommand.builder()
            .build(Message.Builder.of()
                    .setSubject("test 1")
                    .setBody("content 1", StandardCharsets.UTF_8)));

        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "mailbox"),
                MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                        .setSubject("test 2")
                        .setBody("content 2", StandardCharsets.UTF_8)));

        try (IMAPMessageReader imap = new IMAPMessageReader()) {
            imap.connect(LOCALHOST, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ALICE, ALICE_PASSWORD)
            .select("mailbox")
            .copyAllMessagesInMailboxTo("otherMailbox");
        }

        await();

        calmlyAwait
            .atMost(30, TimeUnit.SECONDS)
            .until(() -> twoMessagesFoundInMailbox(otherMailboxId));
    }

    private boolean twoMessagesFoundInMailbox(MailboxId mailboxId) {
        try {
            with()
                .header("Authorization", aliceAccessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId.serialize() + "\"]}}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messageList"))
                .body(ARGUMENTS + ".messageIds", hasSize(2));
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }
}
