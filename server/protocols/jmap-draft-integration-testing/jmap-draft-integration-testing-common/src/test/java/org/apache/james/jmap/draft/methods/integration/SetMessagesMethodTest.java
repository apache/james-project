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
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN_ALIAS;
import static org.apache.james.jmap.JMAPTestingConstants.FIRST_MAILBOX;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.SECOND_ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.SECOND_NAME;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JmapCommonRequests.getDraftId;
import static org.apache.james.jmap.JmapCommonRequests.getInboxId;
import static org.apache.james.jmap.JmapCommonRequests.getMailboxId;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.getSentId;
import static org.apache.james.jmap.JmapCommonRequests.getSetMessagesUpdateOKResponseAssertions;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.events.Event;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.JmapCommonRequests;
import org.apache.james.jmap.MessageAppender;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.jmap.draft.MessageIdProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.probe.ACLProbe;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.mailbox.probe.QuotaProbe;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.util.EventCollector;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.io.ZeroedInputStream;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.SoftAssertions;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.path.json.JsonPath;
import net.javacrumbs.jsonunit.core.Option;
import net.javacrumbs.jsonunit.core.internal.Options;

public abstract class SetMessagesMethodTest {
    private static final String FORWARDED = "$Forwarded";
    private static final int _1MB = 1024 * 1024;
    protected static final Username USERNAME = Username.of("username@" + DOMAIN);
    private static final String ALIAS_OF_USERNAME_MAIL = "alias@" + DOMAIN;
    private static final String GROUP_MAIL = "group@" + DOMAIN;
    private static final Username ALIAS_OF_USERNAME = Username.of(ALIAS_OF_USERNAME_MAIL);
    private static final String PASSWORD = "password";
    protected static final MailboxPath USER_MAILBOX = MailboxPath.forUser(USERNAME, "mailbox");
    private static final String NOT_UPDATED = ARGUMENTS + ".notUpdated";
    private static final int BIG_MESSAGE_SIZE = 9 * 1024 * 1024;
    public static final String OCTET_CONTENT_TYPE = "application/octet-stream";
    public static final String OCTET_CONTENT_TYPE_UTF8 = "application/octet-stream; charset=UTF-8";

    private AccessToken bobAccessToken;

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    protected abstract MessageId randomMessageId();

    protected AccessToken accessToken;
    protected GuiceJamesServer jmapServer;
    protected MailboxProbe mailboxProbe;
    private DataProbe dataProbe;
    protected MessageIdProbe messageProbe;
    private ACLProbe aclProbe;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        messageProbe = jmapServer.getProbe(MessageIdProbe.class);
        aclProbe = jmapServer.getProbe(ACLProbeImpl.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME.asString(), PASSWORD);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);

        mailboxProbe.createMailbox("#private", USERNAME.asString(), DefaultMailboxes.INBOX);
        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), USERNAME, PASSWORD);
        bobAccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), BOB, BOB_PASSWORD);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void setMessagesShouldReturnAnErrorNotSupportedWhenRequestContainsNonNullAccountId() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"setMessages\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("The field 'accountId' of 'SetMessagesRequest' is not supported"));
    }

    @Test
    public void setMessagesShouldReturnAnErrorNotSupportedWhenRequestContainsNonNullIfInState() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"setMessages\", {\"ifInState\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("The field 'ifInState' of 'SetMessagesRequest' is not supported"));
    }

    @Test
    public void setMessagesShouldReturnNotDestroyedWhenUnknownMailbox() {

        String unknownMailboxMessageId = randomMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"setMessages\", {\"destroy\": [\"" + unknownMailboxMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".destroyed", empty())
            .body(ARGUMENTS + ".notDestroyed", hasEntry(equalTo(unknownMailboxMessageId), Matchers.allOf(
                hasEntry("type", "notFound"),
                hasEntry("description", "The message " + unknownMailboxMessageId + " can't be found"),
                hasEntry(equalTo("properties"), is(nullValue()))))
            );
    }

    @Test
    public void setMessagesShouldReturnNotDestroyedWhenNoMatchingMessage() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        String messageId = randomMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"setMessages\", {\"destroy\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".destroyed", empty())
            .body(ARGUMENTS + ".notDestroyed", hasEntry(equalTo(messageId), Matchers.allOf(
                hasEntry("type", "notFound"),
                hasEntry("description", "The message " + messageId + " can't be found"),
                hasEntry(equalTo("properties"), is(nullValue()))))
            );
    }

    @Test
    public void setMessagesShouldReturnDestroyedWhenMatchingMessage() throws Exception {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"setMessages\", {\"destroy\": [\"" + message.getMessageId().serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notDestroyed", anEmptyMap())
            .body(ARGUMENTS + ".destroyed", hasSize(1))
            .body(ARGUMENTS + ".destroyed", contains(message.getMessageId().serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldDeleteMessageWhenMatchingMessage() throws Exception {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        // When
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"setMessages\", {\"destroy\": [\"" + message.getMessageId().serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200);

        // Then
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + message.getMessageId().serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", empty());
    }

    @Test
    public void setMessagesShouldReturnDestroyedNotDestroyWhenMixed() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message1 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        ComposedMessageId message3 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String missingMessageId = randomMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"destroy\": [\"%s\", \"%s\", \"%s\"]}, \"#0\"]]",
                message1.getMessageId().serialize(),
                missingMessageId,
                message3.getMessageId().serialize()))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".destroyed", hasSize(2))
            .body(ARGUMENTS + ".notDestroyed", aMapWithSize(1))
            .body(ARGUMENTS + ".destroyed", containsInAnyOrder(message1.getMessageId().serialize(), message3.getMessageId().serialize()))
            .body(ARGUMENTS + ".notDestroyed", hasEntry(equalTo(missingMessageId), Matchers.allOf(
                hasEntry("type", "notFound"),
                hasEntry("description", "The message " + missingMessageId + " can't be found")))
            );
    }

    @Test
    public void setMessagesShouldDeleteMatchingMessagesWhenMixed() throws Exception {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message1 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        ComposedMessageId message2 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        ComposedMessageId message3 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        // When
        with()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"destroy\": [\"%s\", \"%s\", \"%s\"]}, \"#0\"]]",
                message1.getMessageId().serialize(),
                randomMessageId().serialize(),
                message3.getMessageId().serialize()))
        .post("/jmap");

        // Then
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"getMessages\", {\"ids\": [\"%s\", \"%s\", \"%s\"]}, \"#0\"]]",
                message1.getMessageId().serialize(),
                message2.getMessageId().serialize(),
                message3.getMessageId().serialize()))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1));
    }

    @Test
    public void setMessagesShouldReturnUpdatedIdAndNoErrorWhenIsUnreadPassedToFalse() throws MailboxException {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();

        // When
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : false } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        // Then
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));
    }

    @Test
    public void massiveFlagUpdateShouldBeApplied() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message1 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message3 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flag.SEEN));
        ComposedMessageId message4 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message5 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flag.ANSWERED));
        ComposedMessageId message6 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId1 = message1.getMessageId().serialize();
        String serializedMessageId2 = message2.getMessageId().serialize();
        String serializedMessageId3 = message3.getMessageId().serialize();
        String serializedMessageId4 = message4.getMessageId().serialize();
        String serializedMessageId5 = message5.getMessageId().serialize();
        String serializedMessageId6 = message6.getMessageId().serialize();

        // When
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {" +
                "  \"%s\" : { \"isUnread\" : false }, " +
                "  \"%s\" : { \"isUnread\" : false }, " +
                "  \"%s\" : { \"isUnread\" : false }, " +
                "  \"%s\" : { \"isUnread\" : false }, " +
                "  \"%s\" : { \"isUnread\" : false }, " +
                "  \"%s\" : { \"isUnread\" : false } " +
                "} }, \"#0\"]]", serializedMessageId1, serializedMessageId2, serializedMessageId3,
                serializedMessageId4, serializedMessageId5, serializedMessageId6))
        .when()
            .post("/jmap")
        // Then
        .then()
            .log().ifValidationFails().body(ARGUMENTS + ".updated", hasSize(6));

        Flags flags1 = messageProbe.getMessages(message1.getMessageId(), USERNAME).iterator().next().getFlags();
        Flags flags2 = messageProbe.getMessages(message1.getMessageId(), USERNAME).iterator().next().getFlags();
        Flags flags3 = messageProbe.getMessages(message1.getMessageId(), USERNAME).iterator().next().getFlags();
        Flags flags4 = messageProbe.getMessages(message1.getMessageId(), USERNAME).iterator().next().getFlags();
        Flags flags5 = messageProbe.getMessages(message1.getMessageId(), USERNAME).iterator().next().getFlags();
        Flags flags6 = messageProbe.getMessages(message1.getMessageId(), USERNAME).iterator().next().getFlags();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(flags1).isEqualTo(new Flags(Flag.SEEN));
            softly.assertThat(flags2).isEqualTo(new Flags(Flag.SEEN));
            softly.assertThat(flags3).isEqualTo(new Flags(Flag.SEEN));
            softly.assertThat(flags4).isEqualTo(new Flags(Flag.SEEN));
            softly.assertThat(flags5).isEqualTo(new Flags(Flag.SEEN));
            softly.assertThat(flags6).isEqualTo(new Flags(Flag.SEEN));
        });
    }

    @Test
    public void setMessagesWithUpdateShouldReturnAnErrorWhenBothIsFlagAndKeywordsArePassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : false, \"keywords\": {\"$Seen\": true} } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NOT_UPDATED, hasKey(messageId))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].type", equalTo("invalidProperties"))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].description", containsString("Does not support keyword and is* at the same time"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldUpdateKeywordsWhenKeywordsArePassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Seen\": true, \"$Flagged\": true} } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + serializedMessageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].keywords.$Seen", equalTo(true))
            .body(ARGUMENTS + ".list[0].keywords.$Flagged", equalTo(true));
    }

    @Test
    public void setMessagesShouldAddForwardedFlagWhenKeywordsWithForwardedIsPassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Seen\": true, \"$Forwarded\": true} } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + serializedMessageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].keywords.$Seen", equalTo(true))
            .body(ARGUMENTS + ".list[0].keywords.$Forwarded", equalTo(true));
    }

    @Test
    public void setMessagesShouldRemoveForwardedFlagWhenKeywordsWithoutForwardedIsPassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        Flags flags = FlagsBuilder.builder()
                .add(Flag.SEEN)
                .add(FORWARDED)
                .build();
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, flags);

        String serializedMessageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Seen\": true} } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + serializedMessageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].keywords.$Seen", equalTo(true));
    }

    @Test
    public void setMessagesShouldReturnAnErrorWhenKeywordsWithDeletedArePassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.ANSWERED));

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Answered\": true, \"$Deleted\" : true} } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NOT_UPDATED, hasKey(messageId))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].type", equalTo("invalidProperties"))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].description", containsString("Does not allow to update 'Deleted' or 'Recent' flag"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void setMessagesShouldReturnAnErrorWhenKeywordsWithRecentArePassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.ANSWERED));

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Answered\": true, \"$Recent\": true} } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NOT_UPDATED, hasKey(messageId))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].type", equalTo("invalidProperties"))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].description", containsString("Does not allow to update 'Deleted' or 'Recent' flag"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void setMessagesShouldNotChangeOriginDeletedFlag() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.DELETED));

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Answered\": true, \"$Forwarded\": true} } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(messageId));

        List<MessageResult> messages = messageProbe.getMessages(message.getMessageId(), USERNAME);
        Flags expectedFlags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.DELETED)
            .add(FORWARDED)
            .build();

        assertThat(messages)
            .hasSize(1)
            .extracting(MessageResult::getFlags)
            .containsOnly(expectedFlags);
    }

    @Test
    public void setMessagesShouldNotChangeOriginRecentFlag() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");
        Flags flags = FlagsBuilder.builder()
            .add(Flag.DELETED, Flag.RECENT)
            .build();
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, flags);

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Answered\": true, \"$Forwarded\": true} } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(messageId));

        List<MessageResult> messages = messageProbe.getMessages(message.getMessageId(), USERNAME);

        Flags expectedFlags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.DELETED, Flag.RECENT)
            .add(FORWARDED)
            .build();

        assertThat(messages)
            .hasSize(1)
            .extracting(MessageResult::getFlags)
            .containsOnly(expectedFlags);
    }

    @Test
    public void setMessagesShouldReturnNewKeywordsWhenKeywordsArePassedToRemoveAndAddFlag() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        Flags currentFlags = FlagsBuilder.builder()
                .add(Flag.DRAFT, Flag.ANSWERED)
                .build();
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, currentFlags);

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Answered\": true, \"$Flagged\": true} } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(messageId));

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].keywords.$Answered", equalTo(true))
            .body(ARGUMENTS + ".list[0].keywords.$Flagged", equalTo(true));
    }

    @Test
    public void setMessagesShouldMarkAsReadWhenIsUnreadPassedToFalse() throws MailboxException {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : false } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap");
        // Then
        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + serializedMessageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isUnread", equalTo(false));
    }

    @Test
    public void setMessagesShouldReturnUpdatedIdAndNoErrorWhenIsUnreadPassed() throws MailboxException {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap")
        // Then
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));
    }

    @Test
    public void setMessagesShouldMarkAsUnreadWhenIsUnreadPassed() throws MailboxException {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap");
        // Then
        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + serializedMessageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isUnread", equalTo(true));
    }


    @Test
    public void setMessagesShouldReturnUpdatedIdAndNoErrorWhenIsFlaggedPassed() throws MailboxException {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isFlagged\" : true } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap")
        // Then
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));
    }

    @Test
    public void setMessagesShouldMarkAsFlaggedWhenIsFlaggedPassed() throws MailboxException {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isFlagged\" : true } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap");
        // Then
        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + serializedMessageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isFlagged", equalTo(true));
    }

    @Test
    public void setMessagesShouldRejectUpdateWhenPropertyHasWrongType() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");
        mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String messageId = randomMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : \"123\" } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(NOT_UPDATED, hasKey(messageId))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].type", equalTo("invalidProperties"))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].properties[0]", equalTo("isUnread"))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].description", containsString("isUnread: Cannot deserialize value of type `java.lang.Boolean` from String \"123\": only \"true\" or \"false\" recognized"))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].description", containsString("(through reference chain: org.apache.james.jmap.draft.model.UpdateMessagePatch$Builder[\"isUnread\"])"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    @Ignore("Jackson json deserializer stops after first error found")
    public void setMessagesShouldRejectUpdateWhenPropertiesHaveWrongTypes() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");
        mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String messageId = USERNAME.asString() + "|mailbox|1";

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : \"123\", \"isFlagged\" : 456 } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(NOT_UPDATED, hasKey(messageId))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].type", equalTo("invalidProperties"))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].properties", hasSize(2))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].properties[0]", equalTo("isUnread"))
            .body(NOT_UPDATED + "[\"" + messageId + "\"].properties[1]", equalTo("isFlagged"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void setMessagesShouldMarkMessageAsAnsweredWhenIsAnsweredPassed() throws MailboxException {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();
        // When
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isAnswered\" : true } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        // Then
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));
    }

    @Test
    public void setMessagesShouldMarkAsAnsweredWhenIsAnsweredPassed() throws MailboxException {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isAnswered\" : true } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap");
        // Then
        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + serializedMessageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isAnswered", equalTo(true));
    }


    @Test
    public void setMessagesShouldMarkMessageAsForwardWhenIsForwardedPassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isForwarded\" : true } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));
    }

    @Test
    public void setMessagesShouldMarkAsForwardedWhenIsForwardedPassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isForwarded\" : true } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap");

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + serializedMessageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isForwarded", equalTo(true));
    }

    @Test
    public void setMessagesShouldReturnNotFoundWhenUpdateUnknownMessage() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        String nonExistingMessageId = randomMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", nonExistingMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(NOT_UPDATED, hasKey(nonExistingMessageId))
            .body(NOT_UPDATED + "[\"" + nonExistingMessageId + "\"].type", equalTo("notFound"))
            .body(NOT_UPDATED + "[\"" + nonExistingMessageId + "\"].description", equalTo("message not found"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldReturnCreatedMessageWhenSendingMessage() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            // note that assertions on result message had to be split between
            // string-typed values and boolean-typed value assertions on the same .created entry
            // make sure only one creation has been processed
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            // assert server-set attributes are returned
            .body(ARGUMENTS + ".created", hasEntry(equalTo(messageCreationId), Matchers.allOf(
                hasEntry(equalTo("id"), not(is(nullValue()))),
                hasEntry(equalTo("blobId"), not(is(nullValue()))),
                hasEntry(equalTo("threadId"), not(is(nullValue()))),
                hasEntry(equalTo("size"), not(is(nullValue())))
            )))
            // assert that message FLAGS are all unset
            .body(ARGUMENTS + ".created", hasEntry(equalTo(messageCreationId), Matchers.allOf(
                hasEntry(equalTo("isDraft"), equalTo(false)),
                hasEntry(equalTo("isUnread"), equalTo(true)),
                hasEntry(equalTo("isFlagged"), equalTo(false)),
                hasEntry(equalTo("isAnswered"), equalTo(false))
            )))
            ;
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldNotCreateOverSizedMessages() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String longText = "0123456789\\r\\n".repeat(1024 * 1024);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"" + longText + "\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap").prettyPeek()
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".type", equalTo("invalidArguments"))
            // Message size is date-time and matchine (Message-Id) dependant
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".description",
                startsWith("Attempt to create a message of "))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".description",
                endsWith("bytes while the maximum allowed is 10485760"));
    }

    @Category(BasicFeature.class)
    @Test
    public void sendingAMailShouldLeadToAppropriateMailboxCountersOnOutbox() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
            .post("/jmap");

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, getSentId(accessToken)));

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {" +
                "  \"ids\": [\"" + getOutboxId(accessToken) + "\"], " +
                "  \"properties\" : [\"unreadMessages\", \"totalMessages\"]}, " +
                "\"#0\"]]")
            .log().ifValidationFails()
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".totalMessages", equalTo(0))
            .body(FIRST_MAILBOX + ".unreadMessages", equalTo(0));
    }

    @Test
    public void massiveMessageMoveShouldBeApplied() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), DefaultMailboxes.DRAFTS);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), DefaultMailboxes.OUTBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), DefaultMailboxes.SENT);
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), DefaultMailboxes.TRASH);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), DefaultMailboxes.SPAM);

        ComposedMessageId message1 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message2 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message3 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));
        ComposedMessageId message4 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message5 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.ANSWERED));
        ComposedMessageId message6 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message7 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message8 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message9 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));
        ComposedMessageId message10 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        ComposedMessageId message11 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.ANSWERED));
        ComposedMessageId message12 = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String serializedMessageId1 = message1.getMessageId().serialize();
        String serializedMessageId2 = message2.getMessageId().serialize();
        String serializedMessageId3 = message3.getMessageId().serialize();
        String serializedMessageId4 = message4.getMessageId().serialize();
        String serializedMessageId5 = message5.getMessageId().serialize();
        String serializedMessageId6 = message6.getMessageId().serialize();
        String serializedMessageId7 = message7.getMessageId().serialize();
        String serializedMessageId8 = message8.getMessageId().serialize();
        String serializedMessageId9 = message9.getMessageId().serialize();
        String serializedMessageId10 = message10.getMessageId().serialize();
        String serializedMessageId11 = message11.getMessageId().serialize();
        String serializedMessageId12 = message12.getMessageId().serialize();

        // When
        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {" +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]}, " +
                    "  \"%s\" : { \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]} " +
                    "} }, \"#0\"]]", serializedMessageId1, serializedMessageId2, serializedMessageId3,
                serializedMessageId4, serializedMessageId5, serializedMessageId6,
                serializedMessageId7, serializedMessageId8, serializedMessageId9,
                serializedMessageId10, serializedMessageId11, serializedMessageId12))
            .when()
            .post("/jmap")
            // Then
            .then()
            .log().ifValidationFails().body(ARGUMENTS + ".updated", hasSize(12));
    }

    @Category(BasicFeature.class)
    @Test
    public void sendingAMailShouldLeadToAppropriateMailboxCountersOnSent() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
            .post("/jmap");

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, getSentId(accessToken)));

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {" +
                "  \"ids\": [\"" + getSentId(accessToken) + "\"], " +
                "  \"properties\" : [\"unreadMessages\", \"totalMessages\"]}, " +
                "\"#0\"]]")
            .log().ifValidationFails()
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".totalMessages", equalTo(1))
            .body(FIRST_MAILBOX + ".unreadMessages", equalTo(0));
    }

    @Test
    public void setMessagesShouldReturnCreatedMessageWithEmptySubjectWhenSubjectIsNull() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": null," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].subject", equalTo(""));
    }

    @Test
    public void setMessagesShouldReturnCreatedMessageWithEmptySubjectWhenSubjectIsEmpty() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
       .when()
            .post("/jmap")
       .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].subject", equalTo(""));
    }

    @Test
    public void setMessagesShouldReturnValidErrorWhenMailboxNotFound() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(0))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".type", equalTo("anErrorOccurred"))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".description", endsWith("can not be found"));
    }

    @Test
    public void setMessagesShouldReturnCreatedMessageWithNonASCIICharactersInSubjectWhenPresent() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"تصور واضح للعلاقة بين النموذج الرياضي المثالي ومنظومة الظواهر\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
       .when()
            .post("/jmap")
       .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].subject", equalTo("تصور واضح للعلاقة بين النموذج الرياضي المثالي ومنظومة الظواهر"));
    }

    @Test
    public void setMessagesShouldReturnErrorWhenUserIsNotTheOwnerOfOneOfTheMailboxes() throws Exception {
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        MailboxId aliceOutbox = mailboxProbe.createMailbox("#private", ALICE.asString(), DefaultMailboxes.OUTBOX);

        aclProbe.replaceRights(MailboxPath.forUser(ALICE, DefaultMailboxes.OUTBOX), USERNAME.asString(), MailboxACL.FULL_RIGHTS);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"\"," +
            "        \"mailboxIds\": [\"" + aliceOutbox.serialize() + "\", \"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
       .when()
            .post("/jmap")
       .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("anErrorOccurred"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].properties", contains("mailboxIds"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].description", endsWith("MailboxId invalid"));
    }

    @Test
    public void setMessageWithCreatedMessageShouldReturnAnErrorWhenBothIsFlagAndKeywordsPresent() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
                "        \"subject\": \"subject\"," +
                "        \"isDraft\": true," +
                "        \"keywords\": {\"$Answered\": true}," +
                "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("Does not support keyword and is* at the same time"));
    }

    @Test
    public void setMessageWithCreatedMessageShouldSupportKeywordsForFlags() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
                "        \"subject\": \"subject\"," +
                "        \"keywords\": {\"$Answered\": true, \"$Flagged\": true}," +
                "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].keywords.$Answered", equalTo(true))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].keywords.$Flagged", equalTo(true));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldAllowDraftCreation() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldNotAllowDraftCreationWhenOverQuota() throws MailboxException {
        QuotaProbe quotaProbe = jmapServer.getProbe(QuotaProbesImpl.class);
        QuotaRoot inboxQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(USERNAME));
        quotaProbe.setMaxStorage(inboxQuotaRoot, QuotaSizeLimit.size(100));

        MessageAppender.fillMailbox(mailboxProbe, USERNAME.asString(), MailboxConstants.INBOX);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".created", aMapWithSize(0))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("maxQuotaReached"));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesWithABigBodyShouldReturnCreatedMessageWhenSendingMessage() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.HEADERS);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String body = Strings.repeat("d", BIG_MESSAGE_SIZE);
        {
            String requestBody = new StringBuilder(BIG_MESSAGE_SIZE + 10 * 1024)
                .append("[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
                "        \"subject\": \"Thank you for joining example.com!\"," +
                "        \"textBody\": \"")
                .append(body)
                .append("\"," +
                "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
                .toString();

            given()
                .header("Authorization", accessToken.asString())
                .body(requestBody)
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
                .body(ARGUMENTS + ".created", aMapWithSize(1))
                .body(ARGUMENTS + ".created", hasEntry(equalTo(messageCreationId), hasEntry(equalTo("textBody"), equalTo(body))));
        }

        calmlyAwait
            .pollDelay(Duration.ofMillis(500))
            .atMost(30, TimeUnit.SECONDS).until(() -> hasANewMailWithBody(accessToken, body));
    }

    private boolean hasANewMailWithBody(AccessToken recipientToken, String body) {
        try {
            String inboxId = getMailboxId(accessToken, Role.INBOX);
            String receivedMessageId =
                with()
                    .header("Authorization", accessToken.asString())
                    .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
                    .post("/jmap")
                .then()
                    .extract()
                    .path(ARGUMENTS + ".messageIds[0]");

            given()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messages"))
                .body(ARGUMENTS + ".list", hasSize(1))
                .body(ARGUMENTS + ".list[0].textBody", equalTo(body));
            return true;

        } catch (AssertionError e) {
            return false;
        }
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldNotAllowCopyWhenOverQuota() throws MailboxException {
        QuotaProbe quotaProbe = jmapServer.getProbe(QuotaProbesImpl.class);
        QuotaRoot inboxQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(USERNAME));
        quotaProbe.setMaxStorage(inboxQuotaRoot, QuotaSizeLimit.size(100));

        List<ComposedMessageId> composedMessageIds = MessageAppender.fillMailbox(mailboxProbe, USERNAME.asString(), MailboxConstants.INBOX);

        String messageId = composedMessageIds.get(0).getMessageId().serialize();
        String requestBody =  "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageId + "\" : {" +
            "        \"mailboxIds\": [\"" + getInboxId(accessToken) + "\",\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".updated", hasSize(0))
            .body(ARGUMENTS + ".notUpdated", aMapWithSize(1))
            .body(ARGUMENTS + ".notUpdated." + messageId + ".type", equalTo("maxQuotaReached"));
    }

    @Test
    public void setMessagesShouldCreateDraftInSeveralMailboxes() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String draftId = getDraftId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + mailboxId.serialize() + "\", \"" + draftId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].mailboxIds", containsInAnyOrder(mailboxId.serialize(), draftId));
    }

    @Test
    public void setMessagesShouldAllowDraftCreationOutsideOfDraftMailbox() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId));
    }

    @Test
    public void setMessagesShouldRejectMessageCreationWithNoMailbox() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": []" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(0))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".description", equalTo("Message needs to be in at least one mailbox"))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".properties", contains("mailboxIds"));
    }

    @Test
    public void setMessagesShouldNotFailWhenSavingADraftInSeveralMailboxes() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\", \"" + mailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId));
    }

    @Test
    public void setMessagesShouldAllowDraftCreationWhenUsingIsDraftProperty() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isDraft\": true," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId));
    }

    @Test
    public void setMessagesShouldMarkAsDraftWhenIsDraftPassed() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isDraft\": true," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isDraft", equalTo(true));
    }

    @Test
    public void setMessagesShouldRejectCreateInDraftAndOutboxForASingleMessage() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();

        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\", \"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(0))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".description", equalTo("Message creation is only supported in mailboxes with role Draft and Outbox"))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".properties", contains("mailboxIds"));
    }

    @Test
    public void setMessagesShouldStoreDraft() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String receivedMessageId =
            with()
                .header("Authorization", accessToken.asString())
                .body(requestBody)
                .post("/jmap")
                .then()
                .extract()
                .path(ARGUMENTS + ".created[\"" + messageCreationId + "\"].id");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".id", equalTo(receivedMessageId));
    }

    @Test
    public void setMessagesShouldNotCheckFromWhenDraft() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"invalid@domain.com\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId));
    }

    @Test
    public void setMessagesShouldNotCheckFromWhenInvalidEmailWhenDraft() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"invalid\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
            .when()
            .post("/jmap")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId));
    }

    @Test
    public void setMessagesShouldAllowDraftCreationWithoutFrom() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId));
    }

    @Test
    public void setMessagesShouldAllowDraftCreationWithoutRecipients() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"invalid@domain.com\"}," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId));
    }

    @Test
    public void setMessagesShouldRequireDraftFlagWhenSavingDraft() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Flagged\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(0))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].properties", contains("keywords"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].description", equalTo("A draft message should be flagged as Draft"));
    }

    @Test
    public void setMessagesShouldCheckAttachmentsWhenDraft() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"attachments\": [" +
            "                {\"blobId\" : \"wrong\", \"type\" : \"image/jpeg\", \"size\" : 1337}" +
            "             ]," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(0))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].properties", contains("attachments"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].description", equalTo("Attachment not found"));
    }

    @Test
    public void setMessagesShouldAcceptAttachmentsWhenDraft() throws Exception {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String bytes = "attachment";
        AttachmentMetadata uploadedAttachment = uploadAttachment(OCTET_CONTENT_TYPE, bytes.getBytes(StandardCharsets.UTF_8));
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"attachments\": [" +
            "                {\"blobId\" : \"" + uploadedAttachment.getAttachmentId().getId() + "\", " +
            "                 \"type\" : \"" + uploadedAttachment.getType().asString() + "\"," +
            "                 \"size\" : " + uploadedAttachment.getSize() + "}" +
            "             ]," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
            .when()
            .post("/jmap")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId));
    }

    @Test
    public void setMessagesShouldNotAllowDraftCreationInSomeoneElseMailbox() throws Exception {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(0))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("anErrorOccurred"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].description", endsWith("can not be found"));
    }

    @Test
    public void setMessagesShouldNotAllowDraftCreationInADelegatedMailbox() throws Exception {
        String messageCreationId = "creationId1337";

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), DefaultMailboxes.DRAFTS);
        aclProbe.addRights(
                MailboxPath.forUser(USERNAME, DefaultMailboxes.DRAFTS),
                BOB.asString(),
                MailboxACL.FULL_RIGHTS);

        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(0))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("anErrorOccurred"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].properties", contains("mailboxIds"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].description", endsWith("MailboxId invalid"));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldSendMessageByMovingDraftToOutbox() {
        String draftCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String createDraft = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + draftCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + BOB.asString() + "\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String draftId =
            with()
                .header("Authorization", accessToken.asString())
                .body(createDraft)
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".created[\"" + draftCreationId + "\"].id");

        String moveDraftToOutBox = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + draftId + "\" : {" +
            "        \"keywords\": {}," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(moveDraftToOutBox)
            .post("/jmap");

        calmlyAwait
            .pollDelay(Duration.ofMillis(500))
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
    }

    @Test
    public void setMessagesShouldSendMessageByMovingDraftToOutboxForAMailSentFromAnAlias() throws Exception {
        dataProbe.addUserAliasMapping(Username.of(ALIAS_OF_USERNAME_MAIL).getLocalPart(), ALIAS_OF_USERNAME.getDomainPart().get().asString(), USERNAME.asString());

        String draftCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String createDraft = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + draftCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + ALIAS_OF_USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + BOB.asString() + "\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String draftId =
            with()
                .header("Authorization", accessToken.asString())
                .body(createDraft)
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".created[\"" + draftCreationId + "\"].id");

        String moveDraftToOutBox = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + draftId + "\" : {" +
            "        \"keywords\": {}," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(moveDraftToOutBox)
            .post("/jmap");

        calmlyAwait
            .pollDelay(Duration.ofMillis(500))
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
    }

    @Test
    public void setMessagesShouldRejectDraftCopyToOutbox() {
        String draftCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String createDraft = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + draftCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + BOB.asString() + "\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String draftId =
            with()
                .header("Authorization", accessToken.asString())
                .body(createDraft)
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".created[\"" + draftCreationId + "\"].id");

        String copyDraftToOutBox = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + draftId + "\" : {" +
            "        \"keywords\": {\"$Draft\":true}," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\",\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(copyDraftToOutBox)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notUpdated", hasKey(draftId))
            .body(ARGUMENTS + ".notUpdated[\"" + draftId + "\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notUpdated[\"" + draftId + "\"].description", endsWith("When moving a message to Outbox, only Outboxes mailboxes should be targeted."))
            .body(ARGUMENTS + ".notUpdated[\"" + draftId + "\"].properties", hasSize(1))
            .body(ARGUMENTS + ".notUpdated[\"" + draftId + "\"].properties", contains("mailboxIds"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldRejectMovingMessageToOutboxWhenNotInDraft() throws MailboxException {
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String messageId = message.getMessageId().serialize();
        String moveMessageToOutBox = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageId + "\" : {" +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(moveMessageToOutBox)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notUpdated", hasKey(messageId))
            .body(ARGUMENTS + ".notUpdated[\"" + messageId + "\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notUpdated[\"" + messageId + "\"].description", endsWith("Only message with `$Draft` keyword can be moved to Outbox"))
            .body(ARGUMENTS + ".notUpdated[\"" + messageId + "\"].properties", hasSize(1))
            .body(ARGUMENTS + ".notUpdated[\"" + messageId + "\"].properties", contains("mailboxIds"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldSupportArbitraryMessageId() {
        String messageCreationId = "1717fcd1-603e-44a5-b2a6-1234dbcd5723";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1));
    }

    @Test
    public void setMessagesShouldCreateMessageInOutboxWhenSendingMessage() throws MailboxException {
        // Given
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String messageSubject = "Thank you for joining example.com!";
        String outboxId = getOutboxId(accessToken);

        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"" + messageSubject + "\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        EventCollector eventCollector = new EventCollector();
        jmapServer.getProbe(JmapGuiceProbe.class).addEventListener(eventCollector);

        String messageId = with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        // When
        .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");



        calmlyAwait.atMost(5, TimeUnit.SECONDS).until(() -> eventCollector.getEvents().stream()
            .anyMatch(event -> isAddedToOutboxEvent(messageId, event, outboxId)));
    }

    private boolean isAddedToOutboxEvent(String messageId, Event event, String outboxId) {
        if (!(event instanceof Added)) {
            return false;
        }
        Added added = (Added) event;
        return added.getMailboxId().serialize().equals(outboxId)
            && added.getUids().size() == 1
            && added.getMetaData(added.getUids().iterator().next()).getMessageId().serialize().equals(messageId);
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldMoveMessageInSentWhenMessageIsSent() {
        // Given
        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        String fromAddress = USERNAME.asString();
        String messageCreationId = "creationId1337";
        String messageSubject = "Thank you for joining example.com!";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"" + messageSubject + "\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> messageHasBeenMovedToSentBox(sentMailboxId));
    }

    private boolean messageHasBeenMovedToSentBox(String sentMailboxId) {
        try {
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"fetchMessages\":true, \"filter\":{\"inMailboxes\":[\"" + sentMailboxId + "\"]}}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(SECOND_NAME, equalTo("messages"))
                .body(SECOND_ARGUMENTS + ".list", hasSize(1));
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    @Test
    public void setMessagesShouldRejectWhenSendingMessageHasNoValidAddress() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com@example.com\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))

            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].description", endsWith("no recipient address set"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldRejectWhenSendingMessageHasMissingFrom() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].description", endsWith("'from' address is mandatory"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].properties", hasSize(1))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].properties", contains("from"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldReturnNotCreatedWhenSendingMessageWithAnotherFromAddressThanTheConnectedUser() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"wrongaddress@otherdomain.org\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".description", equalTo("Invalid 'from' field. One accepted value is " + USERNAME.asString()));
    }

    @Test
    public void setMessagesShouldSucceedWhenSendingMessageFromAnAliasOfTheConnectedUser() throws Exception {
        dataProbe.addUserAliasMapping(Username.of(ALIAS_OF_USERNAME_MAIL).getLocalPart(), ALIAS_OF_USERNAME.getDomainPart().get().asString(), USERNAME.asString());

        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + ALIAS_OF_USERNAME_MAIL + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + BOB.asString() + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].headers.From", equalTo(ALIAS_OF_USERNAME_MAIL))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].from.name", equalTo(ALIAS_OF_USERNAME_MAIL))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].from.email", equalTo(ALIAS_OF_USERNAME_MAIL));

        calmlyAwait
            .pollDelay(Duration.ofMillis(500))
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));

    }

    @Test
    public void setMessagesShouldSucceedWhenSendingMessageFromADomainAliasOfTheConnectedUser() throws Exception {
        dataProbe.addDomain(DOMAIN_ALIAS);
        dataProbe.addDomainAliasMapping(DOMAIN_ALIAS, DOMAIN);

        String messageCreationId = "creationId1337";
        String alias = USERNAME.withOtherDomain(Domain.of(DOMAIN_ALIAS)).asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId + "\" : {" +
            "        \"from\": { \"email\": \"" + alias + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + BOB.asString() + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].headers.From", equalTo(alias))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].from.name", equalTo(alias))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].from.email", equalTo(alias));

        calmlyAwait
            .pollDelay(Duration.ofMillis(500))
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
    }

    @Test
    public void setMessagesShouldFailWhenSendingMessageFromAGroupAliasOfTheConnectedUser() throws Exception {
        dataProbe.addGroupAliasMapping(GROUP_MAIL, USERNAME.asString());

        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + GROUP_MAIL + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(ARGUMENTS + ".created", anEmptyMap())
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId));

        String outboxId = getMailboxId(accessToken, Role.OUTBOX);
        assertThat(hasNoMessageIn(bobAccessToken, outboxId)).isTrue();
    }

    @Test
    public void setMessagesShouldNotCreateMessageInOutboxWhenSendingMessageWithAnotherFromAddressThanTheConnectedUser() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"wrongaddress@otherdomain.org\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200);

        String outboxId = getMailboxId(accessToken, Role.OUTBOX);
        assertThat(hasNoMessageIn(bobAccessToken, outboxId)).isTrue();
    }

    private boolean hasNoMessageIn(AccessToken accessToken, String mailboxId) {
        try {
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messageList"))
                .body(ARGUMENTS + ".messageIds", empty());
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    @Test
    public void setMessagesShouldSucceedWhenSendingMessageWithOnlyFromAddress() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].headers.From", equalTo(fromAddress))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].from.name", equalTo(fromAddress))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].from.email", equalTo(fromAddress));
    }

    @Test
    public void setMessagesShouldSucceedWithHtmlBody() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"htmlBody\": \"Hello <i>someone</i>, and thank <b>you</b> for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasKey(messageCreationId))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].headers.From", equalTo(fromAddress))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].from.name", equalTo(fromAddress))
            .body(ARGUMENTS + ".created[\"" + messageCreationId + "\"].from.email", equalTo(fromAddress));
    }

    @Test
    public void setMessagesShouldMoveToSentWhenSendingMessageWithOnlyFromAddress() {
        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        // Given
        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");
        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> messageHasBeenMovedToSentBox(sentMailboxId));
    }

    @Test
    public void setMessagesShouldNotRejectWhenSendingMessageHasMissingSubject() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1));
}

    @Test
    public void setMessagesShouldRejectWhenSendingMessageUseSomeoneElseFromAddress() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"other@domain.tld\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].properties", hasSize(1))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].properties", contains("from"))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].description", endsWith("Invalid 'from' field. One accepted value is username@domain.tld"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldDeliverMessageToRecipient() throws Exception {
        // Sender
        // Recipient
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), Username.of(recipientAddress), password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        // Given
        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(recipientToken));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldTriggerMaxQuotaReachedWhenTryingToSendMessageAndQuotaReached() throws Exception {
        QuotaProbe quotaProbe = jmapServer.getProbe(QuotaProbesImpl.class);
        QuotaRoot inboxQuotaRoot = quotaProbe.getQuotaRoot(MailboxPath.inbox(USERNAME));
        quotaProbe.setMaxStorage(inboxQuotaRoot, QuotaSizeLimit.size(100));

        MessageAppender.fillMailbox(mailboxProbe, USERNAME.asString(), MailboxConstants.INBOX);

        String recipientAddress = "recipient" + "@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);
        HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), Username.of(recipientAddress), password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        // Given
        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".created", aMapWithSize(0))
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].type", equalTo("maxQuotaReached"));
    }

    @Test
    public void setMessagesShouldStripBccFromDeliveredEmail() throws Exception {
        // Recipient
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String bccRecipient = BOB.asString();
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), Username.of(recipientAddress), password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"recipient\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"bcc\": [{ \"name\": \"BOB\", \"email\": \"" + bccRecipient + "\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        // Given
        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(recipientToken));
        with()
            .header("Authorization", recipientToken.asString())
            .body("[[\"getMessageList\", {\"fetchMessages\": true, \"fetchMessageProperties\": [\"bcc\"] }, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(SECOND_NAME, equalTo("messages"))
            .body(SECOND_ARGUMENTS + ".list", hasSize(1))
            .body(SECOND_ARGUMENTS + ".list[0].bcc", empty());
    }

    @Test
    public void setMessagesShouldKeepBccInSentMailbox() throws Exception {
        // Sender
        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        // Recipient
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"recipient\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"bcc\": [{ \"name\": \"BOB\", \"email\": \"bob@" + DOMAIN + "\" }]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        // Given
        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> messageHasBeenMovedToSentBox(sentMailboxId));
        with()
            .header("Authorization", this.accessToken.asString())
            .body("[[\"getMessageList\", {\"fetchMessages\":true, \"fetchMessageProperties\": [\"bcc\"], \"filter\":{\"inMailboxes\":[\"" + sentMailboxId + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(SECOND_NAME, equalTo("messages"))
            .body(SECOND_ARGUMENTS + ".list", hasSize(1))
            .body(SECOND_ARGUMENTS + ".list[0].bcc", hasSize(1));
    }

    @Test
    public void setMessagesShouldSendMessageToBcc() throws Exception {
        // Sender

        // Recipient
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);

        String bccAddress = BOB.asString();
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, bccAddress, DefaultMailboxes.INBOX);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"recipient\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"bcc\": [{ \"name\": \"BOB\", \"email\": \"" + bccAddress + "\" }]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        // Given
        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
        with()
            .header("Authorization", bobAccessToken.asString())
            .body("[[\"getMessageList\", {\"fetchMessages\": true, \"fetchMessageProperties\": [\"bcc\"] }, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(SECOND_NAME, equalTo("messages"))
            .body(SECOND_ARGUMENTS + ".list", hasSize(1))
            .body(SECOND_ARGUMENTS + ".list[0].bcc", empty());
    }

    private boolean isAnyMessageFoundInRecipientsMailboxes(AccessToken recipientToken) {
        try {
            with()
                .header("Authorization", recipientToken.asString())
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


    @Test
    public void setMessagesShouldSendAReadableHtmlMessage() throws Exception {
        // Recipient
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), Username.of(recipientAddress), password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"htmlBody\": \"Hello <b>someone</b>, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        // Given
        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isHtmlMessageReceived(recipientToken));
    }


    @Test
    public void setMessagesWhenSavingToDraftsShouldNotSendMessage() throws Exception {
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String recipientPassword = "password";
        dataProbe.addUser(recipientAddress, recipientPassword);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), Username.of(recipientAddress), recipientPassword);

        String senderDraftsMailboxId = getMailboxId(accessToken, Role.DRAFTS);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + senderDraftsMailboxId + "\"], " +
            "        \"isDraft\": false" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        //We need to wait for an async event to not happen, we couldn't found any
        //robust way to check that.
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        assertThat(isAnyMessageFoundInRecipientsMailboxes(recipientToken)).isFalse();
    }

    @Test
    public void setMessagesWhenSavingToRegularMailboxShouldNotSendMessage() throws Exception {
        String sender = USERNAME.asString();
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, sender, "regular");
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String recipientPassword = "password";
        dataProbe.addUser(recipientAddress, recipientPassword);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + mailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String notCreatedMessage = ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"]";
        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(notCreatedMessage + ".type", equalTo("invalidProperties"))
            .body(notCreatedMessage + ".description", equalTo("Message creation is only supported in mailboxes with role Draft and Outbox"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }


    private boolean isHtmlMessageReceived(AccessToken recipientToken) {
        try {
            with()
                .header("Authorization", recipientToken.asString())
                .body("[[\"getMessageList\", {\"fetchMessages\": true, \"fetchMessageProperties\": [\"htmlBody\"]}, \"#0\"]]")
            .post("/jmap")
            .then()
                .statusCode(200)
                .body(SECOND_NAME, equalTo("messages"))
                .body(SECOND_ARGUMENTS + ".list", hasSize(1))
                .body(SECOND_ARGUMENTS + ".list[0].htmlBody", equalTo("Hello <b>someone</b>, and thank you for joining example.com!"))
            ;
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    @Test
    public void setMessagesShouldSendAReadableTextPlusHtmlMessage() throws Exception {
        // Recipient
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), Username.of(recipientAddress), password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"htmlBody\": \"Hello <b>someone</b>, and thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com, text version!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        // Given
        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isTextPlusHtmlMessageReceived(recipientToken));
    }

    private boolean isTextPlusHtmlMessageReceived(AccessToken recipientToken) {
        try {
            with()
                .header("Authorization", recipientToken.asString())
                .body("[[\"getMessageList\", {\"fetchMessages\": true, \"fetchMessageProperties\": [\"htmlBody\", \"textBody\"]}, \"#0\"]]")
            .post("/jmap")
            .then()
                .statusCode(200)
                .body(SECOND_NAME, equalTo("messages"))
                .body(SECOND_ARGUMENTS + ".list", hasSize(1))
                .body(SECOND_ARGUMENTS + ".list[0].htmlBody", equalTo("Hello <b>someone</b>, and thank you for joining example.com!"))
                .body(SECOND_ARGUMENTS + ".list[0].textBody", equalTo("Hello someone, and thank you for joining example.com, text version!"))
            ;
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    @Test
    public void mailboxIdsShouldReturnUpdatedWhenNoChange() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String mailboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + mailboxId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(messageToMoveId));
    }

    @Category(BasicFeature.class)
    @Test
    public void mailboxIdsShouldBeInDestinationWhenUsingForMove() throws Exception {
        String newMailboxName = "heartFolder";
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + heartFolderId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageToMoveId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".mailboxIds", contains(heartFolderId));
    }

    @Category(BasicFeature.class)
    @Test
    public void mailboxIdsShouldNotBeAnymoreInSourceWhenUsingForMove() throws Exception {
        String newMailboxName = "heartFolder";
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String inboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + heartFolderId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageToMoveId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".mailboxIds", not(contains(inboxId)));
    }

    @Category(BasicFeature.class)
    @Test
    public void mailboxIdsShouldBeInBothMailboxWhenUsingForCopy() throws Exception {
        String newMailboxName = "heartFolder";
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String inboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + heartFolderId + "\",\"" + inboxId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageToMoveId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".mailboxIds", containsInAnyOrder(heartFolderId, inboxId));
    }

    @Test
    public void mailboxIdsShouldBeInOriginalMailboxWhenNoChange() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String mailboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + mailboxId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageToMoveId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".mailboxIds", contains(mailboxId));
    }

    @Test
    public void mailboxIdsShouldReturnErrorWhenMovingToADeletedMailbox() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "any");
        String mailboxId = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "any")
            .serialize();
        mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "any");

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + mailboxId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(NOT_UPDATED, hasKey(messageToMoveId))
            .body(NOT_UPDATED + "[\"" + messageToMoveId + "\"].type", equalTo("anErrorOccurred"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void mailboxIdsShouldReturnErrorWhenSetToEmpty() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": []" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(NOT_UPDATED, hasKey(messageToMoveId))
            .body(NOT_UPDATED + "[\"" + messageToMoveId + "\"].type", equalTo("invalidProperties"))
            .body(NOT_UPDATED + "[\"" + messageToMoveId + "\"].properties", hasSize(1))
            .body(NOT_UPDATED + "[\"" + messageToMoveId + "\"].properties[0]", equalTo("mailboxIds"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void updateShouldNotReturnErrorWithFlagsAndMailboxUpdate() throws Exception {
        String newMailboxName = "heartFolder";
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + heartFolderId + "\"]," +
            "        \"isUnread\": true" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(messageToMoveId));
    }

    @Test
    public void updateShouldWorkWithFlagsAndMailboxUpdate() throws Exception {
        String newMailboxName = "heartFolder";
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + heartFolderId + "\"]," +
            "        \"isUnread\": true" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageToMoveId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".mailboxIds", contains(heartFolderId))
            .body(firstMessage + ".isUnread", equalTo(true));
    }

    @Test
    public void setMessagesShouldWorkForMoveToTrash() throws Exception {
        String trashId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), DefaultMailboxes.TRASH).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + trashId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".updated[0]", equalTo(messageToMoveId))
            .body(ARGUMENTS + ".updated", hasSize(1));
    }

    @Test
    public void copyToTrashShouldWork() throws Exception {
        String newMailboxName = "heartFolder";
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), newMailboxName);
        String trashId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), DefaultMailboxes.TRASH).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), MailboxPath.inbox(USERNAME),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String mailboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageToMoveId + "\" : {" +
            "        \"mailboxIds\": [\"" + trashId + "\",\"" + mailboxId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageToMoveId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".mailboxIds", containsInAnyOrder(trashId, mailboxId));
    }

    @Test
    public void setMessagesShouldReturnAttachmentsNotFoundWhenBlobIdDoesntExist() {
        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Message with a broken blobId\"," +
            "        \"textBody\": \"Test body\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "                {\"blobId\" : \"brokenId1\", \"type\" : \"image/gif\", \"size\" : 1337}," +
            "                {\"blobId\" : \"brokenId2\", \"type\" : \"image/jpeg\", \"size\" : 1337}" +
            "             ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String notCreatedPath = ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(notCreatedPath + ".type", equalTo("invalidProperties"))
            .body(notCreatedPath + ".properties", contains("attachments"))
            .body(notCreatedPath + ".attachmentsNotFound", containsInAnyOrder("brokenId1", "brokenId2"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldReturnAttachmentsWhenMessageHasAttachment() throws Exception {
        String bytes1 = "attachment";
        String bytes2 = "attachment2";
        AttachmentMetadata uploadedAttachment1 = uploadAttachment(OCTET_CONTENT_TYPE, bytes1.getBytes(StandardCharsets.UTF_8));
        AttachmentMetadata uploadedAttachment2 = uploadAttachment(OCTET_CONTENT_TYPE, bytes2.getBytes(StandardCharsets.UTF_8));

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Message with two attachments\"," +
            "        \"textBody\": \"Test body\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + uploadedAttachment1.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment1.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment1.getSize() + "}," +
            "               {\"blobId\" : \"" + uploadedAttachment2.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment2.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment2.getSize() + ", " +
            "               \"cid\" : \"123456789\", " +
            "               \"isInline\" : true }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String createdPath = ARGUMENTS + ".created[\"" + messageCreationId + "\"]";

        String json = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(createdPath + ".attachments", hasSize(2))
            .extract().asString();

        assertThatJson(json)
            .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT, Option.IGNORING_ARRAY_ORDER, Option.IGNORING_EXTRA_FIELDS))
            .whenIgnoringPaths(createdPath + ".attachments[0].blobId", createdPath + ".attachments[1].blobId",
                createdPath + ".attachments[0].inlinedWithCid", createdPath + ".attachments[1].inlinedWithCid")
            .inPath(createdPath + ".attachments")
            .isEqualTo("[{" +
                "  \"type\":\"application/octet-stream; charset=UTF-8\"," +
                "  \"size\":" + bytes1.length() + "," +
                "  \"cid\":null," +
                "  \"isInline\":false" +
                "}, {" +
                "  \"type\":\"application/octet-stream; charset=UTF-8\"," +
                "  \"size\":" + bytes2.length() + "," +
                "  \"cid\":\"123456789\"," +
                "  \"isInline\":true" +
                "}]");
    }

    @Test
    public void setMessagesShouldPreserveCharsetOfAttachment() throws Exception {
        String bytes1 = "attachment";
        String bytes2 = "attachment2";
        AttachmentMetadata uploadedAttachment1 = uploadAttachment(OCTET_CONTENT_TYPE_UTF8, bytes1.getBytes(StandardCharsets.UTF_8));
        AttachmentMetadata uploadedAttachment2 = uploadAttachment(OCTET_CONTENT_TYPE_UTF8, bytes2.getBytes(StandardCharsets.UTF_8));

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Message with two attachments\"," +
            "        \"textBody\": \"Test body\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + uploadedAttachment1.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment1.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment1.getSize() + "}," +
            "               {\"blobId\" : \"" + uploadedAttachment2.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment2.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment2.getSize() + ", " +
            "               \"cid\" : \"123456789\", " +
            "               \"isInline\" : true }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String createdPath = ARGUMENTS + ".created[\"" + messageCreationId + "\"]";

        String json = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(createdPath + ".attachments", hasSize(2))
            .extract().asString();

        assertThatJson(json)
            .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT, Option.IGNORING_ARRAY_ORDER, Option.IGNORING_EXTRA_FIELDS))
            .whenIgnoringPaths(createdPath + ".attachments[0].blobId", createdPath + ".attachments[1].blobId",
                createdPath + ".attachments[0].inlinedWithCid", createdPath + ".attachments[1].inlinedWithCid")
            .inPath(createdPath + ".attachments")
            .isEqualTo("[{" +
                "  \"type\":\"application/octet-stream; charset=UTF-8\"," +
                "  \"size\":" + bytes1.length() + "," +
                "  \"cid\":null," +
                "  \"isInline\":false" +
                "}, {" +
                "  \"type\":\"application/octet-stream; charset=UTF-8\"," +
                "  \"size\":" + bytes2.length() + "," +
                "  \"cid\":\"123456789\"," +
                "  \"isInline\":true" +
                "}]");
    }

    @Test
    public void setMessagesShouldReturnAttachmentsWithNonASCIINames() throws Exception {
        String bytes1 = "attachment";
        String bytes2 = "attachment2";
        String bytes3 = "attachment3";
        AttachmentMetadata uploadedAttachment1 = uploadAttachment(OCTET_CONTENT_TYPE, bytes1.getBytes(StandardCharsets.UTF_8));
        AttachmentMetadata uploadedAttachment2 = uploadAttachment(OCTET_CONTENT_TYPE, bytes2.getBytes(StandardCharsets.UTF_8));
        AttachmentMetadata uploadedAttachment3 = uploadAttachment(OCTET_CONTENT_TYPE, bytes3.getBytes(StandardCharsets.UTF_8));

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\":" +
            "      {" +
            "        \"" + messageCreationId  + "\" : " +
            "        {" +
            "          \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "          \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "          \"subject\": \"Message with three attachments with non ASCII name\"," +
            "          \"textBody\": \"Test body\"," +
            "          \"mailboxIds\": [\"" + outboxId + "\"], " +
            "          \"attachments\":" +
            "          [" +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment1.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + uploadedAttachment1.getType().asString() + "\", " +
            "              \"size\" : " + uploadedAttachment1.getSize() + "," +
            "              \"name\" : \"ديناصور.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment2.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + uploadedAttachment2.getType().asString() + "\", " +
            "              \"size\" : " + uploadedAttachment2.getSize() + "," +
            "              \"name\" : \"эволюционировать.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment3.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + uploadedAttachment3.getType().asString() + "\", " +
            "              \"size\" : " + uploadedAttachment3.getSize() + "," +
            "              \"name\" : \"进化还是不.png\"," +
            "              \"isInline\" : false" +
            "            }" +
            "          ]" +
            "        }" +
            "      }" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String createdPath = ARGUMENTS + ".created[\"" + messageCreationId + "\"]";
        String firstAttachment = createdPath + ".attachments[0]";
        String secondAttachment = createdPath + ".attachments[1]";
        String thirdAttachment = createdPath + ".attachments[2]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(createdPath + ".attachments", hasSize(3))
            .body(firstAttachment + ".name", equalTo("ديناصور.png"))
            .body(secondAttachment + ".name", equalTo("эволюционировать.png"))
            .body(thirdAttachment + ".name", equalTo("进化还是不.png"));
    }

    @Test
    public void filenamesAttachmentsWithNonASCIICharactersShouldBeRetrievedWhenChainingSetMessagesAndGetMessages() throws Exception {
        String bytes1 = "attachment";
        String bytes2 = "attachment2";
        String bytes3 = "attachment3";
        AttachmentMetadata uploadedAttachment1 = uploadAttachment(OCTET_CONTENT_TYPE, bytes1.getBytes(StandardCharsets.UTF_8));
        AttachmentMetadata uploadedAttachment2 = uploadAttachment(OCTET_CONTENT_TYPE, bytes2.getBytes(StandardCharsets.UTF_8));
        AttachmentMetadata uploadedAttachment3 = uploadAttachment(OCTET_CONTENT_TYPE, bytes3.getBytes(StandardCharsets.UTF_8));

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\":" +
            "      {" +
            "        \"" + messageCreationId  + "\" : " +
            "        {" +
            "          \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "          \"to\": [{ \"name\": \"BOB\", \"email\": \"" + fromAddress + "\"}]," +
            "          \"subject\": \"Message with three attachments with non ASCII name\"," +
            "          \"textBody\": \"Test body\"," +
            "          \"mailboxIds\": [\"" + outboxId + "\"], " +
            "          \"attachments\":" +
            "          [" +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment1.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + uploadedAttachment1.getType().asString() + "\", " +
            "              \"size\" : " + uploadedAttachment1.getSize() + "," +
            "              \"name\" : \"ديناصور.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment2.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + uploadedAttachment2.getType().asString() + "\", " +
            "              \"size\" : " + uploadedAttachment2.getSize() + "," +
            "              \"name\" : \"эволюционировать.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment3.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + uploadedAttachment3.getType().asString() + "\", " +
            "              \"size\" : " + uploadedAttachment3.getSize() + "," +
            "              \"name\" : \"进化还是不.png\"," +
            "              \"isInline\" : false" +
            "            }" +
            "          ]" +
            "        }" +
            "      }" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap").then();

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String message = ARGUMENTS + ".list[0]";
        String firstAttachment = message + ".attachments[0]";
        String secondAttachment = message + ".attachments[1]";
        String thirdAttachment = message + ".attachments[2]";

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".attachments", hasSize(3))
            .body(firstAttachment + ".name", equalTo("ديناصور.png"))
            .body(secondAttachment + ".name",  equalTo("эволюционировать.png"))
            .body(thirdAttachment + ".name", equalTo("进化还是不.png"));
    }

    private AttachmentMetadata uploadAttachment(String contentType, byte[] content) {
        JsonPath json = with()
            .header("Authorization", accessToken.asString())
            .contentType(contentType)
            .body(new ByteArrayInputStream(content))
            .post("/upload")
        .then()
            .extract()
            .body()
            .jsonPath();

        return AttachmentMetadata.builder()
            .messageId(new DefaultMessageId())
            .attachmentId(AttachmentId.from(json.getString("blobId")))
            .size(json.getLong("size"))
            .type(json.getString("type"))
            .build();
    }

    private AttachmentMetadata uploadTextAttachment(String contentType, String content) {
        JsonPath json = with()
            .header("Authorization", accessToken.asString())
            .contentType(contentType)
            .body(content)
            .post("/upload")
        .then()
            .extract()
            .body()
            .jsonPath();

        return AttachmentMetadata.builder()
            .messageId(new DefaultMessageId())
            .attachmentId(AttachmentId.from(json.getString("blobId")))
            .size(json.getLong("size"))
            .type(json.getString("type"))
            .build();
    }

    @Test
    public void attachmentsShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesBinaryAttachment() throws Exception {
        byte[] rawBytes = new byte[]{-128,-127,-126,-125,-124,-123,-122,-121,-120,-119,-118,-117,-116,-115,-114,-113,-112,-111,-110,-109,-108,-107,-106,-105,-104,-103,-102,-101,-100,
            -99,-98,-97,-96,-95,-94,-93,-92,-91,-90,-89,-88,-87,-86,-85,-84,-83,-82,-81,-80,-79,-78,-77,-76,-75,-74,-73,-72,-71,-70,-69,-68,-67,-66,-65,-64,-63,-62,-61,-60,-59,-58,-57,-56,-55,-54,-53,-52,-51,
            -50,-49,-48,-47,-46,-45,-44,-43,-42,-41,-40,-39,-38,-37,-36,-35,-34,-33,-32,-31,-30,-29,-28,-27,-26,-25,-24,-23,-22,-21,-20,-19,-18,-17,-16,-15,-14,-13,-12,-11,-10,-9,-8,-7,-6,-5,-4,-3,-2,-1,
            0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,
            50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,
            100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127};

        AttachmentMetadata uploadedAttachment = uploadAttachment(OCTET_CONTENT_TYPE, rawBytes);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + uploadedAttachment.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment.getSize() + ", " +
            "               \"cid\" : \"123456789\", " +
            "               \"isInline\" : true }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo(OCTET_CONTENT_TYPE_UTF8))
            .body(firstAttachment + ".size", equalTo(rawBytes.length))
            .body(firstAttachment + ".cid", equalTo("123456789"))
            .body(firstAttachment + ".isInline", equalTo(true))
            .extract()
            .jsonPath()
            .getString(firstAttachment + ".blobId");

        checkBlobContent(blobId, rawBytes);
    }

    @Category(BasicFeature.class)
    @Test
    public void attachmentsShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesTextAttachment() throws Exception {
        byte[] rawBytes = ByteStreams.toByteArray(new ZeroedInputStream(_1MB));
        AttachmentMetadata uploadedAttachment = uploadAttachment(OCTET_CONTENT_TYPE, rawBytes);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + uploadedAttachment.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment.getSize() + ", " +
            "               \"cid\" : \"123456789\", " +
            "               \"isInline\" : true }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo(OCTET_CONTENT_TYPE_UTF8))
            .body(firstAttachment + ".size", equalTo(rawBytes.length))
            .body(firstAttachment + ".cid", equalTo("123456789"))
            .body(firstAttachment + ".isInline", equalTo(true))
            .extract()
            .jsonPath()
            .getString(firstAttachment + ".blobId");

        checkBlobContent(blobId, rawBytes);
    }

    private boolean isAnyMessageFoundInInbox(AccessToken recipientToken) {
        try {
            String inboxId = getMailboxId(recipientToken, Role.INBOX);
            with()
                .header("Authorization", recipientToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
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

    @Test
    public void attachmentsAndBodysShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesWithMixedTextAndHtmlBodyAndHtmlAttachment() throws Exception {
        String text = "<html>\n" +
            "  <body>attachment</body>\n" + // needed indentation, else restassured is adding some
            "</html>";
        String contentType = "text/html; charset=UTF-8";
        AttachmentMetadata uploadedAttachment = uploadTextAttachment(contentType, text);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body, plain text version\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + uploadedAttachment.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment.getSize() + ", " +
            "               \"isInline\" : false }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".textBody", equalTo("Test body, plain text version"))
            .body(firstMessage + ".htmlBody", equalTo("Test <b>body</b>, HTML version"))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo("text/html; charset=UTF-8"))
            .body(firstAttachment + ".size", equalTo(text.length()))
            .extract()
            .jsonPath()
            .getString(firstAttachment + ".blobId");

        checkBlobContent(blobId, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void attachmentsAndBodyShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesWithTextBodyAndHtmlAttachment() throws Exception {
        String text = "<html>\n" +
            "  <body>attachment</body>\n" + // needed indentation, else restassured is adding some
            "</html>";
        String contentType = "text/html; charset=UTF-8";
        AttachmentMetadata uploadedAttachment = uploadTextAttachment(contentType, text);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body, plain text version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + uploadedAttachment.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment.getSize() + ", " +
            "               \"isInline\" : false }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".textBody", equalTo("Test body, plain text version"))
            .body(firstMessage + ".htmlBody", is(nullValue()))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo("text/html; charset=UTF-8"))
            .body(firstAttachment + ".size", equalTo((int) uploadedAttachment.getSize()))
            .extract()
            .jsonPath()
            .getString(firstAttachment + ".blobId");

        checkBlobContent(blobId, text.getBytes(StandardCharsets.UTF_8));
    }

    private void checkBlobContent(String blobId, byte[] rawBytes) {
        byte[] attachmentBytes = with()
            .header("Authorization", accessToken.asString())
            .get("/download/" + blobId)
        .then()
            .extract()
            .body()
            .asByteArray();

        assertThat(new ByteArrayInputStream(attachmentBytes))
            .hasSameContentAs(new ByteArrayInputStream(rawBytes));
    }

    @Test
    public void attachmentAndEmptyBodyShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesWithTextAttachmentWithoutMailBody() throws Exception {
        String text = "some text";
        String contentType = "text/plain; charset=UTF-8";
        AttachmentMetadata uploadedAttachment = uploadTextAttachment(contentType, text);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + uploadedAttachment.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment.getSize() + ", " +
            "               \"isInline\" : false }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".textBody", is(nullValue()))
            .body(firstMessage + ".htmlBody", is(nullValue()))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo("text/plain; charset=UTF-8"))
            .body(firstAttachment + ".size", equalTo((int) uploadedAttachment.getSize()))
            .extract()
            .jsonPath()
            .getString(firstAttachment + ".blobId");

        checkBlobContent(blobId, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void setMessagesShouldVerifyHeaderOfMessageInInbox() throws Exception {
        String toUsername = "username1@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(toUsername, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, toUsername, DefaultMailboxes.INBOX);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + toUsername + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .post("/jmap");

        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), Username.of(toUsername), password);
        String inboxMailboxId = getMailboxId(accessToken, Role.INBOX);

        calmlyAwait.atMost(60, TimeUnit.SECONDS).until(() -> messageInMailboxHasHeaders(inboxMailboxId, buildExpectedHeaders()));

    }

    @Test
    public void setMessagesShouldVerifyHeaderOfMessageInSent() throws Exception {
        String toUsername = "username1@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(toUsername, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, toUsername, DefaultMailboxes.INBOX);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + toUsername + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .post("/jmap");

        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        calmlyAwait.atMost(60, TimeUnit.SECONDS).until(() -> messageInMailboxHasHeaders(sentMailboxId, buildExpectedHeaders()));

    }

    private ImmutableList<String> buildExpectedHeaders() {
        return ImmutableList.<String>builder()
            .add("Sender")
            .add("Content-Transfer-Encoding")
            .add("From")
            .add("To")
            .add("MIME-Version")
            .add("Subject")
            .add("Content-Type")
            .add("Message-ID")
            .add("Date")
            .build();
    }

    private boolean messageInMailboxHasHeaders(String mailboxId, ImmutableList<String> expectedHeaders) {
        try {
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", "
                    + "{"
                    + "\"fetchMessages\": true, "
                    + "\"fetchMessageProperties\": [\"headers\"], "
                    + "\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]} "
                    + "}, \"#0\"]]")
                .when()
                    .post("/jmap")
                .then()
                    .statusCode(200)
                    .body(ARGUMENTS + ".messageIds", hasSize(1))
                    .body(SECOND_NAME, equalTo("messages"))
                    .body(SECOND_ARGUMENTS + ".list[0]", hasEntry(equalTo("headers"), allHeadersMatcher(expectedHeaders)));
            return true;
        } catch (AssertionError e) {
            e.printStackTrace();
            return false;
        }

    }

    private Matcher<Map<? extends String, ? extends String>> allHeadersMatcher(ImmutableList<String> expectedHeaders) {
        return Matchers.allOf(expectedHeaders.stream()
                .map((String header) -> hasEntry(equalTo(header), not(is(nullValue()))))
                .collect(Collectors.toList()));
    }

    @Test
    public void setMessagesShouldSetUserAddedHeaders() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}]," +
            "        \"headers\": { \"X-MY-SPECIAL-HEADER\": \"first header value\", \"OTHER-HEADER\": \"other value\"}," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        // When
        .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".headers", Matchers.allOf(
                hasEntry("X-MY-SPECIAL-HEADER", "first header value"),
                hasEntry("OTHER-HEADER", "other value")));
    }

    @Test
    public void setMessagesShouldSetUserAddedHeadersForReplyAndForwardWhenAskedTo() throws Exception {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}]," +
            "        \"headers\": { \"In-Reply-To\": \"inreplyto value\", \"X-Forwarded-Message-Id\": \"forward value\"}," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .<String>path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".headers", Matchers.allOf(
                hasEntry("In-Reply-To", "inreplyto value"),
                hasEntry("X-Forwarded-Message-Id", "forward value")));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldUpdateIsAnsweredWhenInReplyToHeaderSentViaOutbox() throws Exception {
        OriginalMessage firstMessage = receiveFirstMessage();

        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}]," +
            "        \"headers\": { \"In-Reply-To\": \"" + firstMessage.mimeMessageId + "\"}," +
            "        \"subject\": \"RE: Hi!\"," +
            "        \"textBody\": \"Fine, thank you!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(bobAccessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + firstMessage.jmapMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".keywords.$Answered", equalTo(true))
            .body(message + ".isAnswered", equalTo(true));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldUpdateIsForwardedWhenXForwardedHeaderSentViaOutbox() throws Exception {
        OriginalMessage firstMessage = receiveFirstMessage();

        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}]," +
            "        \"headers\": { \"X-Forwarded-Message-Id\": \"" + firstMessage.mimeMessageId + "\"}," +
            "        \"subject\": \"Fwd: Hi!\"," +
            "        \"textBody\": \"You talking to me?\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(bobAccessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + firstMessage.jmapMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".keywords.$Forwarded", equalTo(true))
            .body(message + ".isForwarded", equalTo(true));
    }

    @Test
    public void setMessagesShouldUpdateIsAnsweredWhenInReplyToHeaderSentViaDraft() throws Exception {
        OriginalMessage firstMessage = receiveFirstMessage();

        String draftCreationId = "creationId1337";
        String createDraft = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + draftCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}]," +
            "        \"headers\": { \"In-Reply-To\": \"" + firstMessage.mimeMessageId + "\"}," +
            "        \"subject\": \"RE: Hi!\"," +
            "        \"textBody\": \"Fine, thank you!\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String draftId =
            with()
                .header("Authorization", accessToken.asString())
                .body(createDraft)
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".created[\"" + draftCreationId + "\"].id");

        String moveDraftToOutBox = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + draftId + "\" : {" +
            "        \"keywords\": {}," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(moveDraftToOutBox)
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(bobAccessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + firstMessage.jmapMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".keywords.$Answered", equalTo(true))
            .body(message + ".isAnswered", equalTo(true));
    }

    @Test
    public void setMessagesShouldUpdateIsForwardedWhenXForwardedHeaderSentViaDraft() throws Exception {
        OriginalMessage firstMessage = receiveFirstMessage();

        String draftCreationId = "creationId1337";
        String createDraft = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + draftCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}]," +
            "        \"headers\": { \"X-Forwarded-Message-Id\": \"" + firstMessage.mimeMessageId + "\"}," +
            "        \"subject\": \"Fwd: Hi!\"," +
            "        \"textBody\": \"You talking to me?\"," +
            "        \"keywords\": {\"$Draft\": true}," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String draftId =
            with()
                .header("Authorization", accessToken.asString())
                .body(createDraft)
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".created[\"" + draftCreationId + "\"].id");

        String moveDraftToOutBox = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + draftId + "\" : {" +
            "        \"keywords\": {}," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(moveDraftToOutBox)
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(bobAccessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + firstMessage.jmapMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".keywords.$Forwarded", equalTo(true))
            .body(message + ".isForwarded", equalTo(true));
    }

    private OriginalMessage receiveFirstMessage() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}]," +
            "        \"subject\": \"Hi!\"," +
            "        \"textBody\": \"How are you?\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String jmapMessageId = with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .extract()
            .<String>path(ARGUMENTS + ".messageIds[0]");

        String mimeMessageId = with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + jmapMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .extract()
            .<String>path(ARGUMENTS + ".list[0].headers['Message-ID']");
        return new OriginalMessage(jmapMessageId, mimeMessageId);
    }

    private static class OriginalMessage {
        final String jmapMessageId;
        final String mimeMessageId;

        OriginalMessage(String jmapMessageId, String mimeMessageId) {
            this.jmapMessageId = jmapMessageId;
            this.mimeMessageId = mimeMessageId;
        }
    }

    @Test
    public void setMessagesShouldSetUserAddedHeadersInSent() throws Exception {
        String toUsername = "username1@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(toUsername, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, toUsername, DefaultMailboxes.INBOX);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + toUsername + "\"}]," +
                "        \"headers\": { \"X-MY-SPECIAL-HEADER\": \"first header value\", \"OTHER-HEADER\": \"other value\"}," +
                "        \"subject\": \"Thank you for joining example.com!\"," +
                "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
                "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .post("/jmap");

        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> messageHasBeenMovedToSentBox(sentMailboxId));

        String message = SECOND_ARGUMENTS + ".list[0]";
        with()
            .header("Authorization", this.accessToken.asString())
            .body("[[\"getMessageList\", {\"fetchMessages\":true, \"fetchMessageProperties\": [\"headers\"], \"filter\":{\"inMailboxes\":[\"" + sentMailboxId + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(SECOND_NAME, equalTo("messages"))
            .body(SECOND_ARGUMENTS + ".list", hasSize(1))
            .body(message + ".headers", Matchers.allOf(
                hasEntry("X-MY-SPECIAL-HEADER", "first header value"),
                hasEntry("OTHER-HEADER", "other value")));
    }

    @Test
    public void setMessagesShouldSetMultivaluedUserAddedHeaders() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}]," +
            "        \"headers\": { \"X-MY-MULTIVALUATED-HEADER\": \"first value\nsecond value\"}," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        // When
        .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".headers", hasEntry("X-MY-MULTIVALUATED-HEADER", "first value\nsecond value"));
    }

    @Test
    public void setMessagesShouldRenderCorrectlyInIMAPMultivaluedUserAddedHeaders() throws Exception {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}]," +
            "        \"headers\": { \"X-MY-MULTIVALUATED-HEADER\": \"first value\nsecond value\"}," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        try (TestIMAPClient testIMAPClient = new TestIMAPClient()) {
            testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(USERNAME, PASSWORD)
                .select(MailboxConstants.INBOX);
            assertThat(testIMAPClient.readFirstMessage())
                .contains("X-MY-MULTIVALUATED-HEADER: first value")
                .contains("X-MY-MULTIVALUATED-HEADER: second value");
        }
    }

    @Test
    public void setMessagesShouldFilterComputedHeadersFromUserAddedHeaders() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}]," +
            "        \"headers\": { \"From\": \"hacker@example.com\", \"X-MY-SPECIAL-HEADER\": \"first header value\", \"OTHER-HEADER\": \"other value\"}," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        // When
        .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String message = ARGUMENTS + ".list[0]";

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".headers", Matchers.allOf(
                hasEntry("X-MY-SPECIAL-HEADER", "first header value"),
                hasEntry("OTHER-HEADER", "other value"),
                not(hasEntry("From", "hacker@example.com")),
                hasEntry("From", "Me <" + USERNAME.asString() + ">")));
    }

    @Test
    public void setMessagesShouldCreateMessageWhenSendingMessageWithNonIndexableAttachment() throws Exception {
        byte[] bytes = ClassLoaderUtils.getSystemResourceAsByteArray("attachment/nonIndexableAttachment.html");
        String contentType = "text/html";
        AttachmentMetadata uploadedAttachment = uploadAttachment(contentType, bytes);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
                "        \"subject\": \"Message with non indexable attachment\"," +
                "        \"textBody\": \"Test body\"," +
                "        \"mailboxIds\": [\"" + outboxId + "\"], " +
                "        \"attachments\": [" +
                "               {\"blobId\" : \"" + uploadedAttachment.getAttachmentId().getId() + "\", " +
                "               \"type\" : \"" + uploadedAttachment.getType().asString() + "\", " +
                "               \"name\" : \"nonIndexableAttachment.html\", " +
                "               \"size\" : " + uploadedAttachment.getSize() + "}" +
                "           ]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        String createdPath = ARGUMENTS + ".created[\"" + messageCreationId + "\"]";
        String singleAttachment = createdPath + ".attachments[0]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(createdPath + ".attachments", hasSize(1))
            .body(singleAttachment + ".type", equalTo("text/html; charset=UTF-8; name=\"=?US-ASCII?Q?nonIndexableAttachment.html?=\""))
            .body(singleAttachment + ".size", equalTo((int) uploadedAttachment.getSize()));
    }

    @Test
    public void messageWithNonIndexableAttachmentShouldBeRetrievedWhenChainingSetMessagesAndGetMessages() throws Exception {
        byte[] bytes = ClassLoaderUtils.getSystemResourceAsByteArray("attachment/nonIndexableAttachment.html");
        String contentType = "text/html";
        AttachmentMetadata uploadedAttachment = uploadAttachment(contentType, bytes);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
                "        \"subject\": \"Message with non indexable attachment\"," +
                "        \"textBody\": \"Test body\"," +
                "        \"mailboxIds\": [\"" + outboxId + "\"], " +
                "        \"attachments\": [" +
                "               {\"blobId\" : \"" + uploadedAttachment.getAttachmentId().getId() + "\", " +
                "               \"type\" : \"" + uploadedAttachment.getType().asString() + "\", " +
                "               \"name\" : \"nonIndexableAttachment.html\", " +
                "               \"size\" : " + uploadedAttachment.getSize() + "}" +
                "           ]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        String messageId = with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        // When
        .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String message = ARGUMENTS + ".list[0]";

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".attachments", hasSize(1));
    }

    @Test
    public void messageWithNonIndexableAttachmentShouldHaveItsEmailBodyIndexed() throws Exception {
        byte[] bytes = ClassLoaderUtils.getSystemResourceAsByteArray("attachment/nonIndexableAttachment.html");
        String contentType = "text/html";
        AttachmentMetadata uploadedAttachment = uploadAttachment(contentType, bytes);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
                "        \"subject\": \"Message with non indexable attachment\"," +
                "        \"textBody\": \"Test body\"," +
                "        \"mailboxIds\": [\"" + outboxId + "\"], " +
                "        \"attachments\": [" +
                "               {\"blobId\" : \"" + uploadedAttachment.getAttachmentId().getId() + "\", " +
                "               \"type\" : \"" + uploadedAttachment.getType().asString() + "\", " +
                "               \"name\" : \"nonIndexableAttachment.html\", " +
                "               \"size\" : " + uploadedAttachment.getSize() + "}" +
                "           ]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        with()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{" +
                "   \"body\": \"Test body\", " +
                "   \"inMailboxes\":[\"" + inboxId  + "\"]}}, " +
                "\"#0\"]]")
            .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1));
    }

    @Test
    public void setMessagesShouldReturnAttachmentsWhenMessageHasInlinedAttachmentButNoCid() throws Exception {
        String bytes = "attachment";
        AttachmentMetadata uploadedAttachment1 = uploadAttachment(OCTET_CONTENT_TYPE_UTF8, bytes.getBytes(StandardCharsets.UTF_8));
        String bytes2 = "attachment2";
        AttachmentMetadata uploadedAttachment2 = uploadAttachment(OCTET_CONTENT_TYPE_UTF8, bytes2.getBytes(StandardCharsets.UTF_8));

        String messageCreationId = "creationId";
        String fromAddress = USERNAME.asString();
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Message with two attachments\"," +
            "        \"textBody\": \"Test body\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + uploadedAttachment1.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment1.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment1.getSize() + "}," +
            "               {\"blobId\" : \"" + uploadedAttachment2.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + uploadedAttachment2.getType().asString() + "\", " +
            "               \"size\" : " + uploadedAttachment2.getSize() + ", " +
            "               \"isInline\" : true }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String createdPath = ARGUMENTS + ".created[\"" + messageCreationId + "\"]";

        String json = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(createdPath + ".attachments", hasSize(2))
            .extract()
            .asString();

        assertThatJson(json)
            .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT, Option.IGNORING_ARRAY_ORDER, Option.IGNORING_EXTRA_FIELDS))
            .inPath(createdPath + ".attachments")
            .isEqualTo("[{" +
                "  \"type\":\"application/octet-stream; charset=UTF-8\"," +
                "  \"size\":" + bytes2.length() + "," +
                "  \"isInline\":false" +
                "}, {" +
                "  \"type\":\"application/octet-stream; charset=UTF-8\"," +
                "  \"size\":" + bytes.length() + "," +
                "  \"isInline\":false" + // See JAMES-2258 inline should be false in case of no Content-ID for inlined attachment
                // Stored attachment will not be considered as having an inlined attachment.
                "}]");
    }

    @Test
    public void setMessageWithUpdateShouldBeOKWhenKeywordsWithCustomFlagArePassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME.asString(), "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME.asString(), USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Seen\": true, \"$Unknown\": true} } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .spec(getSetMessagesUpdateOKResponseAssertions(messageId));
    }

    @Test
    public void setMessageWithCreationShouldBeOKWhenKeywordsWithCustomFlagArePassed() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
                "        \"subject\": \"subject\"," +
                "        \"keywords\": {\"$Answered\": true, \"$Unknown\": true}," +
                "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1));
    }

    @Test
    public void setMessageWithCreationShouldThrowWhenKeywordsWithUnsupportedArePassed() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"keywords\": {\"$Answered\": true, \"$Deleted\": true}," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("Does not allow to update 'Deleted' or 'Recent' flag"));
    }

    @Test
    public void textBodyOfMessageWithTextCalendarShouldBeConvertedToAttachment() throws Exception {
        MimeMessage calendarMessage = MimeMessageUtil.mimeMessageFromStream(ClassLoader.getSystemResourceAsStream("eml/calendar.eml"));
        String fromAddress = USERNAME.asString();

        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(calendarMessage)
            .sender(fromAddress)
            .recipient(fromAddress)
            .build();
        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, jmapServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue(), DOMAIN)) {
            messageSender.authenticate(USERNAME.asString(), PASSWORD).sendMessage(mail);
        }

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String message = ARGUMENTS + ".list[0]";
        String firstAttachment = message + ".attachments[0]";

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo("text/calendar; method=REPLY; charset=UTF-8"))
            .body(firstAttachment + ".blobId", not(is(nullValue())));
    }

    @Test
    public void setMessagesShouldSetTheSeenKeywordOnMessageInSentMailbox() throws Exception {
        // Sender
        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        // Recipient
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"recipient\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        // Given
        given()
            .header("Authorization", this.accessToken.asString())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> messageHasBeenMovedToSentBox(sentMailboxId));
        with()
            .header("Authorization", this.accessToken.asString())
            .body("[[\"getMessageList\", {\"fetchMessages\":true, \"fetchMessageProperties\": [\"keywords\"], \"filter\":{\"inMailboxes\":[\"" + sentMailboxId + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(SECOND_NAME, equalTo("messages"))
            .body(SECOND_ARGUMENTS + ".list", hasSize(1))
            .body(SECOND_ARGUMENTS + ".list[0].keywords.$Seen", equalTo(true));
    }

    @Test
    public void setMessagesShouldCreateMessageWithFlagsWhenFlagsAttributesAreGiven() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isUnread\": true," +
            "        \"isFlagged\": true," +
            "        \"isAnswered\": true," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isUnread", equalTo(true))
            .body(ARGUMENTS + ".list[0].isFlagged", equalTo(true))
            .body(ARGUMENTS + ".list[0].isAnswered", equalTo(true))
            .body(ARGUMENTS + ".list[0].isDraft", equalTo(true))
            .body(ARGUMENTS + ".list[0].isForwarded", equalTo(true));
    }

    @Test
    public void setMessagesShouldUpdateFlagsWhenSomeAreAlreadySet() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        String updateRequestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"update\": { \"" + messageId  + "\" : {" +
                "        \"isUnread\": true," +
                "        \"isFlagged\": true," +
                "        \"isAnswered\": true," +
                "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(updateRequestBody)
        .when()
            .post("/jmap");

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isUnread", equalTo(true))
            .body(ARGUMENTS + ".list[0].isFlagged", equalTo(true))
            .body(ARGUMENTS + ".list[0].isAnswered", equalTo(true))
            .body(ARGUMENTS + ".list[0].isDraft", equalTo(true))
            .body(ARGUMENTS + ".list[0].isForwarded", equalTo(true));
    }

    @Test
    public void setMessagesShouldRemoveFlagsWhenAskedFor() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isUnread\": true," +
            "        \"isFlagged\": true," +
            "        \"isAnswered\": true," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        String updateRequestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"update\": { \"" + messageId  + "\" : {" +
                "        \"isUnread\": false," +
                "        \"isFlagged\": false," +
                "        \"isAnswered\": false," +
                "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(updateRequestBody)
        .when()
            .post("/jmap");

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isUnread", equalTo(false))
            .body(ARGUMENTS + ".list[0].isFlagged", equalTo(false))
            .body(ARGUMENTS + ".list[0].isAnswered", equalTo(false))
            .body(ARGUMENTS + ".list[0].isDraft", equalTo(true))
            .body(ARGUMENTS + ".list[0].isForwarded", equalTo(true));
    }

    @Test
    public void setMessagesShouldNotReturnAnErrorWhenTryingToChangeDraftFlagAmongOthers() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isUnread\": true," +
            "        \"isFlagged\": true," +
            "        \"isAnswered\": true," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        String updateRequestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"update\": { \"" + messageId  + "\" : {" +
                "        \"isUnread\": false," +
                "        \"isFlagged\": false," +
                "        \"isAnswered\": false," +
                "        \"isDraft\": false," +
                "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(updateRequestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".updated", hasSize(1))
            .body(ARGUMENTS + ".updated", contains(messageId));

    }

    @Test
    public void setMessagesShouldModifyTheMessageWhenTryingToChangeDraftFlagAmongOthers() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME.asString();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"subject\"," +
            "        \"isUnread\": true," +
            "        \"isFlagged\": true," +
            "        \"isAnswered\": true," +
            "        \"isDraft\": true," +
            "        \"isForwarded\": true," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String messageId = given()
            .header("Authorization", accessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        String updateRequestBody = "[" +
                "  [" +
                "    \"setMessages\"," +
                "    {" +
                "      \"update\": { \"" + messageId  + "\" : {" +
                "        \"isUnread\": false," +
                "        \"isFlagged\": false," +
                "        \"isAnswered\": false," +
                "        \"isDraft\": false," +
                "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.asString())
            .body(updateRequestBody)
        .when()
            .post("/jmap");

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].isUnread", equalTo(false))
            .body(ARGUMENTS + ".list[0].isFlagged", equalTo(false))
            .body(ARGUMENTS + ".list[0].isAnswered", equalTo(false))
            .body(ARGUMENTS + ".list[0].isDraft", equalTo(false))
            .body(ARGUMENTS + ".list[0].isForwarded", equalTo(true));
    }

    @Test
    public void mimeMessageIdShouldBePreservedWhenSending() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME.asString() + "\"}]," +
            "        \"subject\": \"Hi!\"," +
            "        \"textBody\": \"How are you?\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String creationMimeMessageId = given()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".headers['Message-ID']");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String jmapMessageId = with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .extract()
            .path(ARGUMENTS + ".messageIds[0]");

        String receivedMimeMessageId = with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + jmapMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .extract()
            .path(ARGUMENTS + ".list[0].headers['Message-ID']");

        assertThat(receivedMimeMessageId).isEqualTo(creationMimeMessageId);
    }
}
