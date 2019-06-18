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
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.JmapCommonRequests.getDraftId;
import static org.apache.james.jmap.JmapCommonRequests.getInboxId;
import static org.apache.james.jmap.JmapCommonRequests.getMailboxId;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.getSetMessagesUpdateOKResponseAssertions;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ALICE;
import static org.apache.james.jmap.TestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.BOB;
import static org.apache.james.jmap.TestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.TestingConstants.NAME;
import static org.apache.james.jmap.TestingConstants.SECOND_ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.SECOND_NAME;
import static org.apache.james.jmap.TestingConstants.calmlyAwait;
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
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.GuiceJamesServer;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.MessageAppender;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.SerializableQuotaValue;
import org.apache.james.mailbox.probe.ACLProbe;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.mailbox.probe.QuotaProbe;
import org.apache.james.mailbox.util.EventCollector;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.ZeroedInputStream;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.JmapGuiceProbe;
import org.apache.james.utils.MessageIdProbe;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.awaitility.Duration;
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

public abstract class SetMessagesMethodTest {
    private static final String FORWARDED = "$Forwarded";
    private static final int _1MB = 1024 * 1024;
    private static final String USERNAME = "username@" + DOMAIN;
    private static final String PASSWORD = "password";
    private static final MailboxPath USER_MAILBOX = MailboxPath.forUser(USERNAME, "mailbox");
    private static final String NOT_UPDATED = ARGUMENTS + ".notUpdated";
    private static final int BIG_MESSAGE_SIZE = 20 * 1024 * 1024;

    private AccessToken bobAccessToken;

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    protected abstract MessageId randomMessageId();

    protected abstract void await();

    private AccessToken accessToken;
    private GuiceJamesServer jmapServer;
    private MailboxProbe mailboxProbe;
    private DataProbe dataProbe;
    private MessageIdProbe messageProbe;
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
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, PASSWORD);
        dataProbe.addUser(BOB, BOB_PASSWORD);
        mailboxProbe.createMailbox("#private", USERNAME, DefaultMailboxes.INBOX);
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
                hasEntry(equalTo("properties"), isEmptyOrNullString())))
            );
    }

    @Test
    public void setMessagesShouldReturnNotDestroyedWhenNoMatchingMessage() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        String messageId = randomMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
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
                hasEntry(equalTo("properties"), isEmptyOrNullString())))
            );
    }

    @Test
    public void setMessagesShouldReturnDestroyedWhenMatchingMessage() throws Exception {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        // When
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + message.getMessageId().serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200);

        // Then
        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message1 = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        ComposedMessageId message3 = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String missingMessageId = randomMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
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
            .body(ARGUMENTS + ".destroyed", contains(message1.getMessageId().serialize(), message3.getMessageId().serialize()))
            .body(ARGUMENTS + ".notDestroyed", hasEntry(equalTo(missingMessageId), Matchers.allOf(
                hasEntry("type", "notFound"),
                hasEntry("description", "The message " + missingMessageId + " can't be found")))
            );
    }

    @Test
    public void setMessagesShouldDeleteMatchingMessagesWhenMixed() throws Exception {
        // Given
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message1 = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        ComposedMessageId message2 = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        ComposedMessageId message3 = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        // When
        with()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"destroy\": [\"%s\", \"%s\", \"%s\"]}, \"#0\"]]",
                message1.getMessageId().serialize(),
                randomMessageId().serialize(),
                message3.getMessageId().serialize()))
        .post("/jmap");

        // Then
        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();

        // When
        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : false } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        // Then
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));
    }

    @Test
    public void setMessagesWithUpdateShouldReturnAnErrorWhenBothIsFlagAndKeywordsArePassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Seen\": true, \"$Flagged\": true} } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));

        with()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Seen\": true, \"$Forwarded\": true} } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));

        with()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        Flags flags = FlagsBuilder.builder()
                .add(Flag.SEEN)
                .add(FORWARDED)
                .build();
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, flags);
        await();

        String serializedMessageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Seen\": true} } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));

        with()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.ANSWERED));
        await();

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.ANSWERED));
        await();

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.DELETED));
        await();

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");
        Flags flags = FlagsBuilder.builder()
            .add(Flag.DELETED, Flag.RECENT)
            .build();
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, flags);
        await();

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        Flags currentFlags = FlagsBuilder.builder()
                .add(Flag.DRAFT, Flag.ANSWERED)
                .build();
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, currentFlags);
        await();

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"keywords\": {\"$Answered\": true, \"$Flagged\": true} } } }, \"#0\"]]", messageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(messageId));

        with()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : false } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap");
        // Then
        with()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));
        await();

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));
        await();

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap");
        // Then
        with()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isFlagged\" : true } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap");
        // Then
        with()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");
        mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        await();

        String messageId = randomMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
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
            .body(NOT_UPDATED + "[\"" + messageId + "\"].description", containsString("{\"isUnread\":\"123\"}"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    @Ignore("Jackson json deserializer stops after first error found")
    public void setMessagesShouldRejectUpdateWhenPropertiesHaveWrongTypes() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");
        mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());

        await();

        String messageId = USERNAME + "|mailbox|1";

        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();
        // When
        given()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isAnswered\" : true } } }, \"#0\"]]", serializedMessageId))
        // When
        .when()
            .post("/jmap");
        // Then
        with()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isForwarded\" : true } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .spec(getSetMessagesUpdateOKResponseAssertions(serializedMessageId));
    }

    @Test
    public void setMessagesShouldMarkAsForwardedWhenIsForwardedPassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String serializedMessageId = message.getMessageId().serialize();
        given()
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isForwarded\" : true } } }, \"#0\"]]", serializedMessageId))
        .when()
            .post("/jmap");

        with()
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        String nonExistingMessageId = randomMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
                hasEntry(equalTo("id"), not(isEmptyOrNullString())),
                hasEntry(equalTo("blobId"), not(isEmptyOrNullString())),
                hasEntry(equalTo("threadId"), not(isEmptyOrNullString())),
                hasEntry(equalTo("size"), not(isEmptyOrNullString()))
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

    @Test
    public void setMessagesShouldReturnCreatedMessageWithEmptySubjectWhenSubjectIsNull() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", bobAccessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        dataProbe.addUser(ALICE, ALICE_PASSWORD);
        MailboxId aliceOutbox = mailboxProbe.createMailbox("#private", ALICE, DefaultMailboxes.OUTBOX);

        aclProbe.replaceRights(MailboxPath.forUser(ALICE, DefaultMailboxes.OUTBOX), USERNAME, MailboxACL.FULL_RIGHTS);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String inboxQuotaRoot = quotaProbe.getQuotaRoot("#private", USERNAME, DefaultMailboxes.INBOX);
        quotaProbe.setMaxStorage(inboxQuotaRoot, SerializableQuotaValue.valueOf(Optional.of(QuotaSize.size(100))));

        MessageAppender.fillMailbox(mailboxProbe, USERNAME, MailboxConstants.INBOX);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
                .header("Authorization", accessToken.serialize())
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
            .pollDelay(Duration.FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS).until(() -> hasANewMailWithBody(accessToken, body));
    }

    private boolean hasANewMailWithBody(AccessToken recipientToken, String body) {
        try {
            String inboxId = getMailboxId(accessToken, Role.INBOX);
            String receivedMessageId =
                with()
                    .header("Authorization", accessToken.serialize())
                    .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
                    .post("/jmap")
                .then()
                    .extract()
                    .path(ARGUMENTS + ".messageIds[0]");

            given()
                .header("Authorization", accessToken.serialize())
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
        String inboxQuotaRoot = quotaProbe.getQuotaRoot("#private", USERNAME, DefaultMailboxes.INBOX);
        quotaProbe.setMaxStorage(inboxQuotaRoot, SerializableQuotaValue.valueOf(Optional.of(QuotaSize.size(100))));

        List<ComposedMessageId> composedMessageIds = MessageAppender.fillMailbox(mailboxProbe, USERNAME, MailboxConstants.INBOX);

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
            .header("Authorization", accessToken.serialize())
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
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        with()
            .header("Authorization", accessToken.serialize())
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
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        with()
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;

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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
                .header("Authorization", accessToken.serialize())
                .body(requestBody)
                .post("/jmap")
                .then()
                .extract()
                .path(ARGUMENTS + ".created[\"" + messageCreationId + "\"].id");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
        Attachment attachment = Attachment.builder()
            .bytes("attachment".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedBlobId = uploadAttachment(attachment);
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
            "                {\"blobId\" : \"" + uploadedBlobId + "\", " +
            "                 \"type\" : \"" + attachment.getType() + "\"," +
            "                 \"size\" : " + attachment.getSize() + "}" +
            "             ]," +
            "        \"mailboxIds\": [\"" + getDraftId(accessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", bobAccessToken.serialize())
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

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, DefaultMailboxes.DRAFTS);
        aclProbe.addRights(
                MailboxPath.forUser(USERNAME, DefaultMailboxes.DRAFTS),
                BOB,
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
            .header("Authorization", bobAccessToken.serialize())
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
        String fromAddress = USERNAME;
        String createDraft = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + draftCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + BOB + "\"}]," +
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
                .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
            .body(moveDraftToOutBox)
            .post("/jmap");

        calmlyAwait
            .pollDelay(Duration.FIVE_HUNDRED_MILLISECONDS)
            .atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
    }

    @Test
    public void setMessagesShouldRejectDraftCopyToOutbox() {
        String draftCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String createDraft = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + draftCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + BOB + "\"}]," +
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
                .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
        jmapServer.getProbe(JmapGuiceProbe.class).addMailboxListener(eventCollector);

        String messageId = with()
            .header("Authorization", accessToken.serialize())
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
        if (!(event instanceof MailboxListener.Added)) {
            return false;
        }
        MailboxListener.Added added = (MailboxListener.Added) event;
        return added.getMailboxId().serialize().equals(outboxId)
            && added.getUids().size() == 1
            && added.getMetaData(added.getUids().iterator().next()).getMessageId().serialize().equals(messageId);
    }

    @Category(BasicFeature.class)
    @Test
    public void setMessagesShouldMoveMessageInSentWhenMessageIsSent() {
        // Given
        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
                .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            .body(ARGUMENTS + ".notCreated." + messageCreationId + ".description", equalTo("Invalid 'from' field. Must be " + USERNAME));
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
            .header("Authorization", accessToken.serialize())
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
                .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            .body(ARGUMENTS + ".notCreated[\"" + messageCreationId + "\"].description", endsWith("Invalid 'from' field. Must be username@domain.tld"))
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
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), recipientAddress, password);
        await();

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
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
        String inboxQuotaRoot = quotaProbe.getQuotaRoot("#private", USERNAME, DefaultMailboxes.INBOX);
        quotaProbe.setMaxStorage(inboxQuotaRoot, SerializableQuotaValue.valueOf(Optional.of(QuotaSize.size(100))));

        MessageAppender.fillMailbox(mailboxProbe, USERNAME, MailboxConstants.INBOX);

        String recipientAddress = "recipient" + "@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);
        HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), recipientAddress, password);
        await();

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
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
        String bccRecipient = BOB;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);
        await();
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), recipientAddress, password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(recipientToken));
        with()
            .header("Authorization", recipientToken.serialize())
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
        await();

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> messageHasBeenMovedToSentBox(sentMailboxId));
        with()
            .header("Authorization", this.accessToken.serialize())
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

        String bccAddress = BOB;
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, bccAddress, DefaultMailboxes.INBOX);
        await();

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInRecipientsMailboxes(bobAccessToken));
        with()
            .header("Authorization", bobAccessToken.serialize())
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


    @Test
    public void setMessagesShouldSendAReadableHtmlMessage() throws Exception {
        // Recipient
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(recipientAddress, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, DefaultMailboxes.INBOX);
        await();
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), recipientAddress, password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
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
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), recipientAddress, recipientPassword);
        await();

        String senderDraftsMailboxId = getMailboxId(accessToken, Role.DRAFTS);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
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
        String sender = USERNAME;
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, sender, "regular");
        String recipientAddress = "recipient" + "@" + DOMAIN;
        String recipientPassword = "password";
        dataProbe.addUser(recipientAddress, recipientPassword);
        await();

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
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
                .header("Authorization", recipientToken.serialize())
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
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), recipientAddress, password);
        await();

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
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
                .header("Authorization", recipientToken.serialize())
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
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
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
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.serialize())
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
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.serialize())
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
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.serialize())
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
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.serialize())
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
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "any");
        String mailboxId = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, USERNAME, "any")
            .serialize();
        mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "any");

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
            .header("Authorization", accessToken.serialize())
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
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
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
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
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
        String heartFolderId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, newMailboxName).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.serialize())
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
        String trashId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, DefaultMailboxes.TRASH).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, newMailboxName);
        String trashId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, DefaultMailboxes.TRASH).serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        String firstMessage = ARGUMENTS + ".list[0]";
        given()
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(notCreatedPath + ".type", equalTo("invalidProperties"))
            .body(notCreatedPath + ".properties", contains("attachments"))
            .body(notCreatedPath + ".attachmentsNotFound", contains("brokenId1", "brokenId2"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldReturnAttachmentsWhenMessageHasAttachment() throws Exception {
        Attachment attachment = Attachment.builder()
            .bytes("attachment".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment1 = uploadAttachment(attachment);
        Attachment attachment2 = Attachment.builder()
            .bytes("attachment2".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment2 = uploadAttachment(attachment2);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            "               {\"blobId\" : \"" + uploadedAttachment1 + "\", " +
            "               \"type\" : \"" + attachment.getType() + "\", " +
            "               \"size\" : " + attachment.getSize() + "}," +
            "               {\"blobId\" : \"" + uploadedAttachment2 + "\", " +
            "               \"type\" : \"" + attachment2.getType() + "\", " +
            "               \"size\" : " + attachment2.getSize() + ", " +
            "               \"cid\" : \"123456789\", " +
            "               \"isInline\" : true }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String createdPath = ARGUMENTS + ".created[\"" + messageCreationId + "\"]";
        String firstAttachment = createdPath + ".attachments[0]";
        String secondAttachment = createdPath + ".attachments[1]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(createdPath + ".attachments", hasSize(2))
            .body(firstAttachment + ".blobId", equalTo(uploadedAttachment1))
            .body(firstAttachment + ".type", equalTo("application/octet-stream; charset=UTF-8"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
            .body(firstAttachment + ".cid", nullValue())
            .body(firstAttachment + ".isInline", equalTo(false))
            .body(secondAttachment + ".blobId", equalTo(uploadedAttachment2))
            .body(secondAttachment + ".type", equalTo("application/octet-stream; charset=UTF-8"))
            .body(secondAttachment + ".size", equalTo((int) attachment2.getSize()))
            .body(secondAttachment + ".cid", equalTo("123456789"))
            .body(secondAttachment + ".isInline", equalTo(true));
    }

    @Test
    public void setMessagesShouldReturnAttachmentsWithNonASCIINames() throws Exception {
        Attachment attachment = Attachment.builder()
            .bytes("attachment".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment1 = uploadAttachment(attachment);
        Attachment attachment2 = Attachment.builder()
            .bytes("attachment2".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment2 = uploadAttachment(attachment2);
        Attachment attachment3 = Attachment.builder()
            .bytes("attachment3".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment3 = uploadAttachment(attachment3);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            "              \"blobId\" : \"" + uploadedAttachment1 + "\", " +
            "              \"type\" : \"" + attachment.getType() + "\", " +
            "              \"size\" : " + attachment.getSize() + "," +
            "              \"name\" : \"ديناصور.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment2 + "\", " +
            "              \"type\" : \"" + attachment2.getType() + "\", " +
            "              \"size\" : " + attachment2.getSize() + "," +
            "              \"name\" : \"эволюционировать.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment3 + "\", " +
            "              \"type\" : \"" + attachment3.getType() + "\", " +
            "              \"size\" : " + attachment3.getSize() + "," +
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
            .header("Authorization", accessToken.serialize())
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
        Attachment attachment = Attachment.builder()
            .bytes("attachment".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment1 = uploadAttachment(attachment);

        Attachment attachment2 = Attachment.builder()
            .bytes("attachment2".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment2 = uploadAttachment(attachment2);

        Attachment attachment3 = Attachment.builder()
            .bytes("attachment3".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment3 = uploadAttachment(attachment3);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            "              \"blobId\" : \"" + uploadedAttachment1 + "\", " +
            "              \"type\" : \"" + attachment.getType() + "\", " +
            "              \"size\" : " + attachment.getSize() + "," +
            "              \"name\" : \"ديناصور.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment2 + "\", " +
            "              \"type\" : \"" + attachment2.getType() + "\", " +
            "              \"size\" : " + attachment2.getSize() + "," +
            "              \"name\" : \"эволюционировать.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + uploadedAttachment3 + "\", " +
            "              \"type\" : \"" + attachment3.getType() + "\", " +
            "              \"size\" : " + attachment3.getSize() + "," +
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
            .header("Authorization", accessToken.serialize())
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
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        given()
            .header("Authorization", accessToken.serialize())
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

    private String uploadAttachment(Attachment attachment) throws IOException {
        return with()
            .header("Authorization", accessToken.serialize())
            .contentType(attachment.getType())
            .body(attachment.getStream())
            .post("/upload")
        .then()
            .extract()
            .body()
            .jsonPath()
            .getString("blobId");
    }

    private String uploadTextAttachment(Attachment attachment) throws IOException {
        return with()
            .header("Authorization", accessToken.serialize())
            .contentType(attachment.getType())
            .body(new String(IOUtils.toByteArray(attachment.getStream()), StandardCharsets.UTF_8))
            .post("/upload")
        .then()
            .extract()
            .body()
            .jsonPath()
            .getString("blobId");
    }

    @Test
    public void attachmentsShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesBinaryAttachment() throws Exception {
        byte[] rawBytes = new byte[]{-128,-127,-126,-125,-124,-123,-122,-121,-120,-119,-118,-117,-116,-115,-114,-113,-112,-111,-110,-109,-108,-107,-106,-105,-104,-103,-102,-101,-100,
            -99,-98,-97,-96,-95,-94,-93,-92,-91,-90,-89,-88,-87,-86,-85,-84,-83,-82,-81,-80,-79,-78,-77,-76,-75,-74,-73,-72,-71,-70,-69,-68,-67,-66,-65,-64,-63,-62,-61,-60,-59,-58,-57,-56,-55,-54,-53,-52,-51,
            -50,-49,-48,-47,-46,-45,-44,-43,-42,-41,-40,-39,-38,-37,-36,-35,-34,-33,-32,-31,-30,-29,-28,-27,-26,-25,-24,-23,-22,-21,-20,-19,-18,-17,-16,-15,-14,-13,-12,-11,-10,-9,-8,-7,-6,-5,-4,-3,-2,-1,
            0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,
            50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,
            100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127};

        Attachment attachment = Attachment.builder()
            .bytes(rawBytes)
            .type("application/octet-stream")
            .build();
        String uploadedAttachment = uploadAttachment(attachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            "               {\"blobId\" : \"" + uploadedAttachment + "\", " +
            "               \"type\" : \"" + attachment.getType() + "\", " +
            "               \"size\" : " + attachment.getSize() + ", " +
            "               \"cid\" : \"123456789\", " +
            "               \"isInline\" : true }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo("application/octet-stream"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
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
        Attachment attachment = Attachment.builder()
            .bytes(rawBytes)
            .type("application/octet-stream")
            .build();
        String uploadedAttachment = uploadAttachment(attachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            "               {\"blobId\" : \"" + uploadedAttachment + "\", " +
            "               \"type\" : \"" + attachment.getType() + "\", " +
            "               \"size\" : " + attachment.getSize() + ", " +
            "               \"cid\" : \"123456789\", " +
            "               \"isInline\" : true }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo("application/octet-stream"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
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
                .header("Authorization", recipientToken.serialize())
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
        byte[] rawBytes = ("<html>\n" +
            "  <body>attachment</body>\n" + // needed indentation, else restassured is adding some
            "</html>").getBytes(StandardCharsets.UTF_8);
        Attachment attachment = Attachment.builder()
            .bytes(rawBytes)
            .type("text/html; charset=UTF-8")
            .build();
        String uploadedBlobId = uploadTextAttachment(attachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            "               {\"blobId\" : \"" + uploadedBlobId + "\", " +
            "               \"type\" : \"" + attachment.getType() + "\", " +
            "               \"size\" : " + attachment.getSize() + ", " +
            "               \"isInline\" : false }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.serialize())
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
            .body(firstAttachment + ".type", equalTo("text/html"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
            .extract()
            .jsonPath()
            .getString(firstAttachment + ".blobId");

        checkBlobContent(blobId, rawBytes);
    }

    @Test
    public void attachmentsAndBodyShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesWithTextBodyAndHtmlAttachment() throws Exception {
        byte[] rawBytes = ("<html>\n" +
            "  <body>attachment</body>\n" + // needed indentation, else restassured is adding some
            "</html>").getBytes(StandardCharsets.UTF_8);
        Attachment attachment = Attachment.builder()
            .bytes(rawBytes)
            .type("text/html; charset=UTF-8")
            .build();
        String uploadedBlobId = uploadTextAttachment(attachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            "               {\"blobId\" : \"" + uploadedBlobId + "\", " +
            "               \"type\" : \"" + attachment.getType() + "\", " +
            "               \"size\" : " + attachment.getSize() + ", " +
            "               \"isInline\" : false }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".textBody", equalTo("Test body, plain text version"))
            .body(firstMessage + ".htmlBody", isEmptyOrNullString())
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo("text/html"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
            .extract()
            .jsonPath()
            .getString(firstAttachment + ".blobId");

        checkBlobContent(blobId, rawBytes);
    }

    public void checkBlobContent(String blobId, byte[] rawBytes) {
        byte[] attachmentBytes = with()
            .header("Authorization", accessToken.serialize())
            .get("/download/" + blobId)
        .then()
            .extract()
            .body()
            .asByteArray();

        assertThat(attachmentBytes).containsExactly(rawBytes);
    }

    @Test
    public void attachmentAndEmptyBodyShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesWithTextAttachmentWithoutMailBody() throws Exception {
        byte[] rawBytes = ("some text").getBytes(StandardCharsets.UTF_8);
        Attachment attachment = Attachment.builder()
            .bytes(rawBytes)
            .type("text/plain; charset=UTF-8")
            .build();
        String uploadedBlobId = uploadTextAttachment(attachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            "               {\"blobId\" : \"" + uploadedBlobId + "\", " +
            "               \"type\" : \"" + attachment.getType() + "\", " +
            "               \"size\" : " + attachment.getSize() + ", " +
            "               \"isInline\" : false }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
            .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        String firstMessage = ARGUMENTS + ".list[0]";
        String firstAttachment = firstMessage + ".attachments[0]";
        String blobId = given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".textBody", isEmptyOrNullString())
            .body(firstMessage + ".htmlBody", isEmptyOrNullString())
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo("text/plain"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
            .extract()
            .jsonPath()
            .getString(firstAttachment + ".blobId");

        checkBlobContent(blobId, rawBytes);
    }

    @Test
    public void setMessagesShouldVerifyHeaderOfMessageInInbox() throws Exception {
        String toUsername = "username1@" + DOMAIN;
        String password = "password";
        dataProbe.addUser(toUsername, password);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, toUsername, DefaultMailboxes.INBOX);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(jmapServer), toUsername, password);
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
                .header("Authorization", accessToken.serialize())
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
                .map((String header) -> hasEntry(equalTo(header), not(isEmptyOrNullString())))
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
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}]," +
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}]," +
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}," +
            "        \"to\": [{ \"name\": \"Bob\", \"email\": \"" + BOB + "\"}]," +
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(bobAccessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.serialize())
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
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}," +
            "        \"to\": [{ \"name\": \"Bob\", \"email\": \"" + BOB + "\"}]," +
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(bobAccessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.serialize())
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
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}," +
            "        \"to\": [{ \"name\": \"Bob\", \"email\": \"" + BOB + "\"}]," +
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
                .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
            .body(moveDraftToOutBox)
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(bobAccessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.serialize())
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
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}," +
            "        \"to\": [{ \"name\": \"Bob\", \"email\": \"" + BOB + "\"}]," +
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
                .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
            .body(moveDraftToOutBox)
            .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(bobAccessToken));

        String message = ARGUMENTS + ".list[0]";

        with()
            .header("Authorization", accessToken.serialize())
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
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}]," +
            "        \"subject\": \"Hi!\"," +
            "        \"textBody\": \"How are you?\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", bobAccessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String jmapMessageId = with()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .extract()
            .<String>path(ARGUMENTS + ".messageIds[0]");

        String mimeMessageId = with()
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> messageHasBeenMovedToSentBox(sentMailboxId));

        String message = SECOND_ARGUMENTS + ".list[0]";
        with()
            .header("Authorization", this.accessToken.serialize())
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
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}]," +
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}]," +
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        try (IMAPMessageReader imapMessageReader = new IMAPMessageReader()) {
            imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(USERNAME, PASSWORD)
                .select(MailboxConstants.INBOX);
            assertThat(imapMessageReader.readFirstMessage())
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
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}]," +
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
                hasEntry("From", "Me <" + USERNAME + ">")));
    }

    @Test
    public void setMessagesShouldCreateMessageWhenSendingMessageWithNonIndexableAttachment() throws Exception {
        Attachment nonIndexableAttachment = Attachment.builder()
                .bytes(ClassLoaderUtils.getSystemResourceAsByteArray("attachment/nonIndexableAttachment.html"))
                .type("text/html")
                .build();
        String uploadedBlobId = uploadTextAttachment(nonIndexableAttachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
                "               {\"blobId\" : \"" + uploadedBlobId + "\", " +
                "               \"type\" : \"" + nonIndexableAttachment.getType() + "\", " +
                "               \"name\" : \"nonIndexableAttachment.html\", " +
                "               \"size\" : " + nonIndexableAttachment.getSize() + "}" +
                "           ]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        String createdPath = ARGUMENTS + ".created[\"" + messageCreationId + "\"]";
        String singleAttachment = createdPath + ".attachments[0]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(createdPath + ".attachments", hasSize(1))
            .body(singleAttachment + ".blobId", equalTo(uploadedBlobId))
            .body(singleAttachment + ".type", equalTo("text/html; charset=UTF-8"))
            .body(singleAttachment + ".size", equalTo((int) nonIndexableAttachment.getSize()));
    }

    @Test
    public void messageWithNonIndexableAttachmentShouldBeRetrievedWhenChainingSetMessagesAndGetMessages() throws Exception {
        Attachment nonIndexableAttachment = Attachment.builder()
                .bytes(ClassLoaderUtils.getSystemResourceAsByteArray("attachment/nonIndexableAttachment.html"))
                .type("text/html")
                .build();
        String uploadedBlobId = uploadTextAttachment(nonIndexableAttachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
                "               {\"blobId\" : \"" + uploadedBlobId + "\", " +
                "               \"type\" : \"" + nonIndexableAttachment.getType() + "\", " +
                "               \"name\" : \"nonIndexableAttachment.html\", " +
                "               \"size\" : " + nonIndexableAttachment.getSize() + "}" +
                "           ]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        String messageId = with()
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
        Attachment nonIndexableAttachment = Attachment.builder()
                .bytes(ClassLoaderUtils.getSystemResourceAsByteArray("attachment/nonIndexableAttachment.html"))
                .type("text/html")
                .build();
        String uploadedBlobId = uploadTextAttachment(nonIndexableAttachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
                "               {\"blobId\" : \"" + uploadedBlobId + "\", " +
                "               \"type\" : \"" + nonIndexableAttachment.getType() + "\", " +
                "               \"name\" : \"nonIndexableAttachment.html\", " +
                "               \"size\" : " + nonIndexableAttachment.getSize() + "}" +
                "           ]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .post("/jmap");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        given()
            .header("Authorization", accessToken.serialize())
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
        Attachment attachment = Attachment.builder()
            .bytes("attachment".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment1 = uploadAttachment(attachment);
        Attachment attachment2 = Attachment.builder()
            .bytes("attachment2".getBytes(StandardCharsets.UTF_8))
            .type("application/octet-stream")
            .build();
        String uploadedAttachment2 = uploadAttachment(attachment2);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
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
            "               {\"blobId\" : \"" + uploadedAttachment1 + "\", " +
            "               \"type\" : \"" + attachment.getType() + "\", " +
            "               \"size\" : " + attachment.getSize() + "}," +
            "               {\"blobId\" : \"" + uploadedAttachment2 + "\", " +
            "               \"type\" : \"" + attachment2.getType() + "\", " +
            "               \"size\" : " + attachment2.getSize() + ", " +
            "               \"isInline\" : true }" +
            "           ]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String createdPath = ARGUMENTS + ".created[\"" + messageCreationId + "\"]";
        String firstAttachment = createdPath + ".attachments[0]";
        String secondAttachment = createdPath + ".attachments[1]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(0))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(createdPath + ".attachments", hasSize(2))
            .body(firstAttachment + ".blobId", equalTo(uploadedAttachment1))
            .body(firstAttachment + ".type", equalTo("application/octet-stream; charset=UTF-8"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
            .body(firstAttachment + ".cid", nullValue())
            .body(firstAttachment + ".isInline", equalTo(false))
            .body(secondAttachment + ".blobId", equalTo(uploadedAttachment2))
            .body(secondAttachment + ".type", equalTo("application/octet-stream; charset=UTF-8"))
            .body(secondAttachment + ".size", equalTo((int) attachment2.getSize()))
            .body(secondAttachment + ".cid", nullValue())
            .body(secondAttachment + ".isInline", equalTo(true));
    }

    @Test
    public void setMessageWithUpdateShouldBeOKWhenKeywordsWithCustomFlagArePassed() throws MailboxException {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = mailboxProbe.appendMessage(USERNAME, USER_MAILBOX,
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
        await();

        String messageId = message.getMessageId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;

        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(calendarMessage)
            .sender(fromAddress)
            .recipient(fromAddress)
            .build();
        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, jmapServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue(), DOMAIN)) {
            messageSender.sendMessage(mail);
        }

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String message = ARGUMENTS + ".list[0]";
        String firstAttachment = message + ".attachments[0]";

        String inboxId = getMailboxId(accessToken, Role.INBOX);
        String receivedMessageId =
            with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + inboxId + "\"]}}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(message + ".attachments", hasSize(1))
            .body(firstAttachment + ".type", equalTo("text/calendar"))
            .body(firstAttachment + ".blobId", not(isEmptyOrNullString()));
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
        await();

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
        // When
        .when()
            .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> messageHasBeenMovedToSentBox(sentMailboxId));
        with()
            .header("Authorization", this.accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");

        with()
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
            .body(updateRequestBody)
        .when()
            .post("/jmap");

        with()
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
            .body(updateRequestBody)
        .when()
            .post("/jmap");

        with()
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
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
        String fromAddress = USERNAME;
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
            .header("Authorization", accessToken.serialize())
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
            .header("Authorization", accessToken.serialize())
            .body(updateRequestBody)
        .when()
            .post("/jmap");

        with()
            .header("Authorization", accessToken.serialize())
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
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + USERNAME + "\"}]," +
            "        \"subject\": \"Hi!\"," +
            "        \"textBody\": \"How are you?\"," +
            "        \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String creationMimeMessageId = given()
            .header("Authorization", bobAccessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".headers['Message-ID']");

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until(() -> isAnyMessageFoundInInbox(accessToken));

        String jmapMessageId = with()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .extract()
            .path(ARGUMENTS + ".messageIds[0]");

        String receivedMimeMessageId = with()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + jmapMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .extract()
            .path(ARGUMENTS + ".list[0].headers['Message-ID']");

        assertThat(receivedMimeMessageId).isEqualTo(creationMimeMessageId);
    }

}
