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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.james.JmapJamesServer;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.util.ZeroedInputStream;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.builder.ResponseSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.ResponseSpecification;

public abstract class SetMessagesMethodTest {

    private static final int _1MB = 1024*1024;
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String SECOND_NAME = "[1][0]";
    private static final String SECOND_ARGUMENTS = "[1][1]";
    private static final String USERS_DOMAIN = "domain.tld";
    private static final String USERNAME = "username@" + USERS_DOMAIN;
    public static final MailboxPath USER_MAILBOX = new MailboxPath(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");
    private static final String NOT_UPDATED = ARGUMENTS + ".notUpdated";

    private ConditionFactory calmlyAwait;

    protected abstract JmapJamesServer createJmapServer();

    protected abstract MessageId randomMessageId();
    
    protected abstract void await();

    private AccessToken accessToken;
    private JmapJamesServer jmapServer;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8)))
                .setPort(jmapServer.getJmapProbe()
                    .getJmapPort())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        String password = "password";
        jmapServer.serverProbe().addDomain(USERS_DOMAIN);
        jmapServer.serverProbe().addUser(USERNAME, password);
        jmapServer.serverProbe().createMailbox("#private", USERNAME, "inbox");
        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USERNAME, password);

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "outbox");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "trash");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "draft");
        await();

        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with().pollInterval(slowPacedPollInterval).and().with().pollDelay(slowPacedPollInterval).await();
    }

    private URIBuilder baseUri() {
        return new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(jmapServer.getJmapProbe()
                .getJmapPort())
            .setCharset(Charsets.UTF_8);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }
    
    private String getOutboxId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.OUTBOX);
    }

    private String getMailboxId(AccessToken accessToken, Role role) {
        return getAllMailboxesIds(accessToken).stream()
            .filter(x -> x.get("role").equals(role.serialize()))
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

    @Test
    public void setMessagesShouldReturnErrorNotSupportedWhenRequestContainsNonNullAccountId() throws Exception {
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
    }

    @Test
    public void setMessagesShouldReturnErrorNotSupportedWhenRequestContainsNonNullIfInState() throws Exception {
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"ifInState\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
    }

    @Test
    public void setMessagesShouldReturnNotDestroyedWhenUnknownMailbox() throws Exception {

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
    public void setMessagesShouldReturnNotDestroyedWhenNoMatchingMessage() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
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

    @Test
    public void setMessagesShouldDeleteMessageWhenMatchingMessage() throws Exception {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
        await();

        // When
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\""+ message.getMessageId().serialize() +"\"]}, \"#0\"]]")
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message1 = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());

        jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());

        ComposedMessageId message3 = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message1 = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());

        ComposedMessageId message2 = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());

        ComposedMessageId message3 = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
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

    private ResponseSpecification getSetMessagesUpdateOKResponseAssertions(String messageId) {
        ResponseSpecBuilder builder = new ResponseSpecBuilder()
            .expectStatusCode(200)
            .expectBody(NAME, equalTo("messagesSet"))
            .expectBody(ARGUMENTS + ".updated", hasSize(1))
            .expectBody(ARGUMENTS + ".updated", contains(messageId))
            .expectBody(ARGUMENTS + ".error", isEmptyOrNullString())
            .expectBody(NOT_UPDATED, not(hasKey(messageId)));
        return builder.build();
    }

    @Test
    public void setMessagesShouldMarkAsReadWhenIsUnreadPassedToFalse() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags(Flags.Flag.SEEN));
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");
        jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());

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
            .body(NOT_UPDATED + "[\""+messageId+"\"].type", equalTo("invalidProperties"))
            .body(NOT_UPDATED + "[\""+messageId+"\"].properties[0]", equalTo("isUnread"))
            .body(NOT_UPDATED + "[\""+messageId+"\"].description", equalTo("isUnread: Can not construct instance of java.lang.Boolean from String value '123': only \"true\" or \"false\" recognized\n" +
                    " at [Source: {\"isUnread\":\"123\"}; line: 1, column: 2] (through reference chain: org.apache.james.jmap.model.Builder[\"isUnread\"])"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    @Ignore("Jackson json deserializer stops after first error found")
    public void setMessagesShouldRejectUpdateWhenPropertiesHaveWrongTypes() throws MailboxException {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");
        jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());

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
            .body(NOT_UPDATED + "[\""+messageId+"\"].type", equalTo("invalidProperties"))
            .body(NOT_UPDATED + "[\""+messageId+"\"].properties", hasSize(2))
            .body(NOT_UPDATED + "[\""+messageId+"\"].properties[0]", equalTo("isUnread"))
            .body(NOT_UPDATED + "[\""+messageId+"\"].properties[1]", equalTo("isFlagged"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void setMessagesShouldMarkMessageAsAnsweredWhenIsAnsweredPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, USER_MAILBOX,
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), new Date(), false, new Flags());
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
    public void setMessageShouldReturnNotFoundWhenUpdateUnknownMessage() {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "mailbox");

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
            .body(NOT_UPDATED + "[\""+nonExistingMessageId+"\"].type", equalTo("notFound"))
            .body(NOT_UPDATED + "[\""+nonExistingMessageId+"\"].description", equalTo("message not found"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void setMessageShouldReturnCreatedMessageWhenSendingMessage() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            // assert that message flags are all unset
            .body(ARGUMENTS + ".created", hasEntry(equalTo(messageCreationId), Matchers.allOf(
                hasEntry(equalTo("isDraft"), equalTo(false)),
                hasEntry(equalTo("isUnread"), equalTo(false)),
                hasEntry(equalTo("isFlagged"), equalTo(false)),
                hasEntry(equalTo("isAnswered"), equalTo(false))
            )))
            ;
    }

    @Test
    public void setMessageShouldReturnCreatedMessageWithEmptySubjectWhenSubjectIsNull() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].subject", equalTo(""));
    }

    @Test
    public void setMessageShouldReturnCreatedMessageWithEmptySubjectWhenSubjectIsEmpty() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].subject", equalTo(""));
    }
    
    @Test
    public void setMessageShouldReturnCreatedMessageWithNonASCIICharactersInSubjectWhenPresent() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].subject", equalTo("تصور واضح للعلاقة بين النموذج الرياضي المثالي ومنظومة الظواهر"));
    }

    @Test
    public void setMessageShouldSupportArbitraryMessageId() {
        String messageCreationId = "1717fcd1-603e-44a5-b2a6-1234dbcd5723";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            "    \"setMessages\","+
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

        String messageId = with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        // When
        .post("/jmap")
        .then()
            .extract()
            .body()
            .<String>path(ARGUMENTS + ".created."+ messageCreationId +".id");

        // Then
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].subject", equalTo(messageSubject))
            .body(ARGUMENTS + ".list[0].mailboxIds", contains(outboxId))
            ;
    }

    @Test
    public void setMessagesShouldMoveMessageInSentWhenMessageIsSent() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        String fromAddress = USERNAME;
        String messageCreationId = "creationId1337";
        String messageSubject = "Thank you for joining example.com!";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> messageHasBeenMovedToSentBox(sentMailboxId));
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
        } catch(AssertionError e) {
            return false;
        }
    }

    @Test
    public void setMessagesShouldRejectWhenSendingMessageHasNoValidAddress() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].description", endsWith("no recipient address set"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldRejectWhenSendingMessageHasMissingFrom() {
        String messageCreationId = "creationId1337";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].description", endsWith("'from' address is mandatory"))
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].properties", hasSize(1))
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].properties", contains("from"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldSucceedWhenSendingMessageWithOnlyFromAddress() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].headers.From", equalTo(fromAddress))
            .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].from.name", equalTo(fromAddress))
            .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].from.email", equalTo(fromAddress));
    }

    @Test
    public void setMessagesShouldSucceedWithHtmlBody() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].headers.From", equalTo(fromAddress))
            .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].from.name", equalTo(fromAddress))
            .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].from.email", equalTo(fromAddress));
    }

    @Test
    public void setMessagesShouldMoveToSentWhenSendingMessageWithOnlyFromAddress() {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> messageHasBeenMovedToSentBox(sentMailboxId));
    }

    @Test
    public void setMessagesShouldNotRejectWhenSendingMessageHasMissingSubject() {
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            "    \"setMessages\","+
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
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].type", equalTo("invalidProperties"))
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].properties", hasSize(1))
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].properties", contains("from"))
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].description", endsWith("Invalid 'from' field. Must be one of username@domain.tld"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldDeliverMessageToRecipient() throws Exception {
        // Sender
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), recipientAddress, password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInRecipientsMailboxes(recipientToken));
    }

    @Test
    public void setMessagesShouldStripBccFromDeliveredEmail() throws Exception {
        // Sender
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String bccRecipient = "bob@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().addUser(bccRecipient, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), recipientAddress, password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInRecipientsMailboxes(recipientToken));
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        String sentMailboxId = getMailboxId(accessToken, Role.SENT);

        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"recipient\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"bcc\": [{ \"name\": \"BOB\", \"email\": \"bob@" + USERS_DOMAIN + "\" }]," +
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
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> messageHasBeenMovedToSentBox(sentMailboxId));
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");

        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");

        String bccAddress = "bob" + "@" + USERS_DOMAIN;
        jmapServer.serverProbe().addUser(bccAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, bccAddress, "inbox");
        await();
        AccessToken bccToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), bccAddress, password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInRecipientsMailboxes(bccToken));
        with()
            .header("Authorization", bccToken.serialize())
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
        // Sender
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), recipientAddress, password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isHtmlMessageReceived(recipientToken));
    }


    @Test
    public void setMessagesWhenSavingToDraftsShouldNotSendMessage() throws Exception {
        String sender = USERNAME;
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, sender, "sent");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, sender, "drafts");
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String recipientPassword = "password";
        jmapServer.serverProbe().addUser(recipientAddress, recipientPassword);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), recipientAddress, recipientPassword);

        String senderDraftsMailboxId = getMailboxId(accessToken, Role.DRAFTS);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, sender, "sent");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, sender, "drafts");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, sender, "regular");
        Mailbox regularMailbox = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, sender, "regular");
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String recipientPassword = "password";
        jmapServer.serverProbe().addUser(recipientAddress, recipientPassword);
        await();

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + recipientAddress + "\"}]," +
            "        \"cc\": [{ \"name\": \"ALICE\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + regularMailbox.getMailboxId().serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        String notCreatedMessage = ARGUMENTS + ".notCreated[\""+messageCreationId+"\"]";
        given()
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".notCreated", hasKey(messageCreationId))
            .body(notCreatedMessage + ".type", equalTo("invalidProperties"))
            .body(notCreatedMessage + ".description", equalTo("Not yet implemented"))
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
        } catch(AssertionError e) {
            return false;
        }
    }
    
    @Test
    public void setMessagesShouldSendAReadableTextPlusHtmlMessage() throws Exception {
        // Sender
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();
        AccessToken recipientToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), recipientAddress, password);

        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isTextPlusHtmlMessageReceived(recipientToken));
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
        } catch(AssertionError e) {
            return false;
        }
    }

    @Test
    public void mailboxIdsShouldReturnUpdatedWhenNoChange() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String mailboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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

    @Test
    public void mailboxIdsShouldBeInDestinationWhenUsingForMove() throws Exception {
        String newMailboxName = "heartFolder";
        jmapServer.serverProbe().createMailbox("#private", USERNAME, newMailboxName);
        Mailbox heartFolder = jmapServer.serverProbe().getMailbox("#private", USERNAME, newMailboxName);
        String heartFolderId = heartFolder.getMailboxId().serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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


    @Test
    public void mailboxIdsShouldBeInDestinationWhenUsingForMoveWithoutTrashFolder() throws Exception {
        jmapServer.serverProbe().deleteMailbox("#private", USERNAME, "trash");
        String newMailboxName = "heartFolder";
        jmapServer.serverProbe().createMailbox("#private", USERNAME, newMailboxName);
        Mailbox heartFolder = jmapServer.serverProbe().getMailbox("#private", USERNAME, newMailboxName);
        String heartFolderId = heartFolder.getMailboxId().serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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

    @Test
    public void mailboxIdsShouldNotBeAnymoreInSourceWhenUsingForMove() throws Exception {
        String newMailboxName = "heartFolder";
        jmapServer.serverProbe().createMailbox("#private", USERNAME, newMailboxName);
        Mailbox heartFolder = jmapServer.serverProbe().getMailbox("#private", USERNAME, newMailboxName);
        String heartFolderId = heartFolder.getMailboxId().serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String inboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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

    @Test
    public void mailboxIdsShouldBeInBothMailboxWhenUsingForCopy() throws Exception {
        String newMailboxName = "heartFolder";
        jmapServer.serverProbe().createMailbox("#private", USERNAME, newMailboxName);
        Mailbox heartFolder = jmapServer.serverProbe().getMailbox("#private", USERNAME, newMailboxName);
        String heartFolderId = heartFolder.getMailboxId().serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String inboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String mailboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "any");
        String mailboxId = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "any")
            .getMailboxId()
            .serialize();
        jmapServer.serverProbe().deleteMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "any");

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            .body(NOT_UPDATED + "[\""+messageToMoveId+"\"].type", equalTo("anErrorOccurred"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void mailboxIdsShouldReturnErrorWhenSetToEmpty() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
            .body(NOT_UPDATED + "[\""+messageToMoveId+"\"].type", equalTo("invalidProperties"))
            .body(NOT_UPDATED + "[\""+messageToMoveId+"\"].properties", hasSize(1))
            .body(NOT_UPDATED + "[\""+messageToMoveId+"\"].properties[0]", equalTo("mailboxIds"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void updateShouldNotReturnErrorWithFlagsAndMailboxUpdate() throws Exception {
        String newMailboxName = "heartFolder";
        jmapServer.serverProbe().createMailbox("#private", USERNAME, newMailboxName);
        Mailbox heartFolder = jmapServer.serverProbe().getMailbox("#private", USERNAME, newMailboxName);
        String heartFolderId = heartFolder.getMailboxId().serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        jmapServer.serverProbe().createMailbox("#private", USERNAME, newMailboxName);
        Mailbox heartFolder = jmapServer.serverProbe().getMailbox("#private", USERNAME, newMailboxName);
        String heartFolderId = heartFolder.getMailboxId().serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        String trashId = jmapServer.serverProbe().getMailbox("#private", USERNAME, "trash").getMailboxId().serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
        jmapServer.serverProbe().createMailbox("#private", USERNAME, newMailboxName);
        String trashId = jmapServer.serverProbe().getMailbox("#private", USERNAME, "trash").getMailboxId().serialize();

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        ComposedMessageId message = jmapServer.serverProbe().appendMessage(USERNAME, new MailboxPath("#private", USERNAME, "inbox"),
            new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes(Charsets.UTF_8)), Date.from(dateTime.toInstant()), false, new Flags());

        String messageToMoveId = message.getMessageId().serialize();
        String mailboxId = message.getMailboxId().serialize();
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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
    public void setMessagesShouldReturnAttachmentsNotFoundWhenBlobIdDoesntExist() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        await();
        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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

        String notCreatedPath = ARGUMENTS + ".notCreated[\""+messageCreationId+"\"]";

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
            .body(notCreatedPath + ".attachmentsNotFound", contains("brokenId1", "brokenId2"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldReturnAttachmentsWhenMessageHasAttachment() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");

        Attachment attachment = Attachment.builder()
            .bytes("attachment".getBytes(Charsets.UTF_8))
            .type("application/octet-stream")
            .build();
        uploadAttachment(attachment);
        Attachment attachment2 = Attachment.builder()
            .bytes("attachment2".getBytes(Charsets.UTF_8))
            .type("application/octet-stream")
            .build();
        uploadAttachment(attachment2);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Message with two attachments\"," +
            "        \"textBody\": \"Test body\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + attachment.getAttachmentId().getId() + "\", " +
            "               \"type\" : \"" + attachment.getType() + "\", " +
            "               \"size\" : " + attachment.getSize() + "}," +
            "               {\"blobId\" : \"" + attachment2.getAttachmentId().getId() + "\", " +
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

        String createdPath = ARGUMENTS + ".created[\""+messageCreationId+"\"]";
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
            .body(firstAttachment + ".blobId", equalTo(attachment.getAttachmentId().getId()))
            .body(firstAttachment + ".type", equalTo("application/octet-stream; charset=UTF-8"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
            .body(firstAttachment + ".cid", nullValue())
            .body(firstAttachment + ".isInline", equalTo(false))
            .body(secondAttachment + ".blobId", equalTo(attachment2.getAttachmentId().getId()))
            .body(secondAttachment + ".type", equalTo("application/octet-stream; charset=UTF-8"))
            .body(secondAttachment + ".size", equalTo((int) attachment2.getSize()))
            .body(secondAttachment + ".cid", equalTo("123456789"))
            .body(secondAttachment + ".isInline", equalTo(true));
    }

    @Test
    public void setMessagesShouldReturnAttachmentsWithNonASCIINames() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");

        Attachment attachment = Attachment.builder()
            .bytes("attachment".getBytes(Charsets.UTF_8))
            .type("application/octet-stream")
            .build();
        uploadAttachment(attachment);
        Attachment attachment2 = Attachment.builder()
            .bytes("attachment2".getBytes(Charsets.UTF_8))
            .type("application/octet-stream")
            .build();
        uploadAttachment(attachment2);
        Attachment attachment3 = Attachment.builder()
            .bytes("attachment3".getBytes(Charsets.UTF_8))
            .type("application/octet-stream")
            .build();
        uploadAttachment(attachment3);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\":" +
            "      {" +
            "        \"" + messageCreationId  + "\" : "+
            "        {" +
            "          \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "          \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "          \"subject\": \"Message with three attachments with non ASCII name\"," +
            "          \"textBody\": \"Test body\"," +
            "          \"mailboxIds\": [\"" + outboxId + "\"], " +
            "          \"attachments\":" +
            "          [" +
            "            {" +
            "              \"blobId\" : \"" + attachment.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + attachment.getType() + "\", " +
            "              \"size\" : " + attachment.getSize() + "," +
            "              \"name\" : \"ديناصور.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + attachment2.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + attachment2.getType() + "\", " +
            "              \"size\" : " + attachment2.getSize() + "," +
            "              \"name\" : \"эволюционировать.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + attachment3.getAttachmentId().getId() + "\", " +
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

        String createdPath = ARGUMENTS + ".created[\""+messageCreationId+"\"]";
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");

        Attachment attachment = Attachment.builder()
            .bytes("attachment".getBytes(Charsets.UTF_8))
            .type("application/octet-stream")
            .build();
        uploadAttachment(attachment);

        Attachment attachment2 = Attachment.builder()
            .bytes("attachment2".getBytes(Charsets.UTF_8))
            .type("application/octet-stream")
            .build();
        uploadAttachment(attachment2);

        Attachment attachment3 = Attachment.builder()
            .bytes("attachment3".getBytes(Charsets.UTF_8))
            .type("application/octet-stream")
            .build();
        uploadAttachment(attachment3);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\":" +
            "      {" +
            "        \"" + messageCreationId  + "\" : "+
            "        {" +
            "          \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "          \"to\": [{ \"name\": \"BOB\", \"email\": \"" + fromAddress + "\"}]," +
            "          \"subject\": \"Message with three attachments with non ASCII name\"," +
            "          \"textBody\": \"Test body\"," +
            "          \"mailboxIds\": [\"" + outboxId + "\"], " +
            "          \"attachments\":" +
            "          [" +
            "            {" +
            "              \"blobId\" : \"" + attachment.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + attachment.getType() + "\", " +
            "              \"size\" : " + attachment.getSize() + "," +
            "              \"name\" : \"ديناصور.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + attachment2.getAttachmentId().getId() + "\", " +
            "              \"type\" : \"" + attachment2.getType() + "\", " +
            "              \"size\" : " + attachment2.getSize() + "," +
            "              \"name\" : \"эволюционировать.png\", " +
            "              \"isInline\" : false" +
            "            }," +
            "            {" +
            "              \"blobId\" : \"" + attachment3.getAttachmentId().getId() + "\", " +
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

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInInbox(accessToken));

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

    private void uploadAttachment(Attachment attachment) throws IOException {
        with()
            .header("Authorization", accessToken.serialize())
            .contentType(attachment.getType())
            .content(attachment.getStream())
            .post("/upload");
    }

    private void uploadTextAttachment(Attachment attachment) throws IOException {
        with()
            .header("Authorization", accessToken.serialize())
            .contentType(attachment.getType())
            .content(new String(IOUtils.toByteArray(attachment.getStream()), Charsets.UTF_8))
            .post("/upload");
    }

    @Test
    public void attachmentsShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesBinaryAttachment() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");

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
        uploadAttachment(attachment);
        String expectedBlobId = attachment.getAttachmentId().getId();

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + attachment.getAttachmentId().getId() + "\", " +
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

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInInbox(accessToken));

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
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".blobId", equalTo(expectedBlobId))
            .body(firstAttachment + ".type", equalTo("application/octet-stream"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
            .body(firstAttachment + ".cid", equalTo("123456789"))
            .body(firstAttachment + ".isInline", equalTo(true));
    }

    @Test
    public void attachmentsShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesTextAttachment() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");

        Attachment attachment = Attachment.builder()
            .bytes(ByteStreams.toByteArray(new ZeroedInputStream(_1MB)))
            .type("application/octet-stream")
            .build();
        uploadAttachment(attachment);
        String expectedBlobId = attachment.getAttachmentId().getId();

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + attachment.getAttachmentId().getId() + "\", " +
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

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInInbox(accessToken));

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
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + receivedMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".blobId", equalTo(expectedBlobId))
            .body(firstAttachment + ".type", equalTo("application/octet-stream"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()))
            .body(firstAttachment + ".cid", equalTo("123456789"))
            .body(firstAttachment + ".isInline", equalTo(true));
    }

    private boolean isAnyMessageFoundInInbox(AccessToken recipientToken) {
        try {
            String inboxId = getMailboxId(accessToken, Role.INBOX);
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");

        Attachment attachment = Attachment.builder()
            .bytes(("<html>\n" +
                    "  <body>attachment</body>\n" + // needed indentation, else restassured is adding some
                    "</html>").getBytes(Charsets.UTF_8))
            .type("text/html; charset=UTF-8")
            .build();
        uploadTextAttachment(attachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body, plain text version\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + attachment.getAttachmentId().getId() + "\", " +
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

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInInbox(accessToken));

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
            .body(firstMessage + ".textBody", equalTo("Test body, plain text version"))
            .body(firstMessage + ".htmlBody", equalTo("Test <b>body</b>, HTML version"))
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".blobId", equalTo(attachment.getAttachmentId().getId()))
            .body(firstAttachment + ".type", equalTo("text/html"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()));
    }

    @Test
    public void attachmentsAndBodyShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesWithTextBodyAndHtmlAttachment() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");

        Attachment attachment = Attachment.builder()
            .bytes(("<html>\n" +
                    "  <body>attachment</body>\n" + // needed indentation, else restassured is adding some
                    "</html>").getBytes(Charsets.UTF_8))
            .type("text/html; charset=UTF-8")
            .build();
        uploadTextAttachment(attachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"textBody\": \"Test body, plain text version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + attachment.getAttachmentId().getId() + "\", " +
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

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInInbox(accessToken));

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
            .body(firstMessage + ".textBody", equalTo("Test body, plain text version"))
            .body(firstMessage + ".htmlBody", isEmptyOrNullString())
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".blobId", equalTo(attachment.getAttachmentId().getId()))
            .body(firstAttachment + ".type", equalTo("text/html"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()));
    }
    @Test
    public void attachmentAndEmptyBodyShouldBeRetrievedWhenChainingSetMessagesAndGetMessagesWithTextAttachmentWithoutMailBody() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");

        Attachment attachment = Attachment.builder()
            .bytes(("some text").getBytes(Charsets.UTF_8))
            .type("text/plain; charset=UTF-8")
            .build();
        uploadTextAttachment(attachment);

        String messageCreationId = "creationId";
        String fromAddress = USERNAME;
        String outboxId = getOutboxId(accessToken);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
            "        \"to\": [{ \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}]," +
            "        \"subject\": \"Message with an attachment\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"], " +
            "        \"attachments\": [" +
            "               {\"blobId\" : \"" + attachment.getAttachmentId().getId() + "\", " +
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

        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInInbox(accessToken));

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
            .body(firstMessage + ".textBody", isEmptyOrNullString())
            .body(firstMessage + ".htmlBody", isEmptyOrNullString())
            .body(firstMessage + ".attachments", hasSize(1))
            .body(firstAttachment + ".blobId", equalTo(attachment.getAttachmentId().getId()))
            .body(firstAttachment + ".type", equalTo("text/plain"))
            .body(firstAttachment + ".size", equalTo((int) attachment.getSize()));
    }
    @Test
    public void setMessageShouldVerifyHeaderOfMessageInInbox() throws Exception {
        String toUsername = "username1@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(toUsername, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, toUsername, "inbox");

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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

        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), toUsername, password);
        String inboxMailboxId = getMailboxId(accessToken, Role.INBOX);

        calmlyAwait.atMost(60, TimeUnit.SECONDS).until( () -> messageInMailboxHasHeaders(inboxMailboxId, buildExpectedHeaders()));

    }

    @Test
    public void setMessageShouldVerifyHeaderOfMessageInSent() throws Exception {
        String toUsername = "username1@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(toUsername, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, toUsername, "inbox");

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "sent");
        String messageCreationId = "creationId1337";
        String fromAddress = USERNAME;
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
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

        calmlyAwait.atMost(60, TimeUnit.SECONDS).until( () -> messageInMailboxHasHeaders(sentMailboxId, buildExpectedHeaders()));

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
                    .body(SECOND_ARGUMENTS + ".list[0]", hasEntry(equalTo("headers"), allHeadersMatcher(expectedHeaders)))
            ;
            return true;
        } catch(AssertionError e) {
            e.printStackTrace();
            return false;
        }

    }

    private Matcher<Map<? extends String, ? extends String>> allHeadersMatcher(ImmutableList<String> expectedHeaders) {
        return Matchers.allOf(expectedHeaders.stream()
            .map((String header) -> hasEntry(equalTo(header), not(isEmptyOrNullString())))
            .collect(Collectors.toList()));
    }

}
