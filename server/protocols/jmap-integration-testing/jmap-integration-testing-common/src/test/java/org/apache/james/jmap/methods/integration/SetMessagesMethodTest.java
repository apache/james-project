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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.ResponseSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.ResponseSpecification;

public abstract class SetMessagesMethodTest {

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String SECOND_NAME = "[1][0]";
    private static final String SECOND_ARGUMENTS = "[1][1]";
    private static final String USERS_DOMAIN = "domain.tld";

    private ConditionFactory calmlyAwait;

    protected abstract GuiceJamesServer createJmapServer();

    protected abstract void await();

    private AccessToken accessToken;
    private String username;
    private GuiceJamesServer jmapServer;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        RestAssured.port = jmapServer.getJmapPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));

        username = "username@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addDomain(USERS_DOMAIN);
        jmapServer.serverProbe().addUser(username, password);
        jmapServer.serverProbe().createMailbox("#private", username, "inbox");
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "outbox");
        await();

        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with().pollInterval(slowPacedPollInterval).and().with().pollDelay(slowPacedPollInterval).await();
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }
    
    private String getOutboxId() {
        // Find username's outbox (using getMailboxes command on /jmap endpoint)
        return getAllMailboxesIds(accessToken).stream()
                .filter(x -> x.get("role").equals("outbox"))
                .map(x -> x.get("id"))
                .findFirst().get();
    }

    private List<Map<String, String>> getAllMailboxesIds(AccessToken accessToken) {
        return with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
    }

    @Test
    public void setMessagesShouldReturnErrorNotSupportedWhenRequestContainsNonNullIfInState() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"ifInState\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
    }

    @Test
    public void setMessagesShouldReturnNotDestroyedWhenUnknownMailbox() throws Exception {

        String unknownMailboxMessageId = username + "|unknown|12345";
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
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
                    hasEntry("type", "anErrorOccurred"),
                    hasEntry("description", "An error occurred while deleting message " + unknownMailboxMessageId),
                    hasEntry(equalTo("properties"), isEmptyOrNullString())))
            );
    }

    @Test
    public void setMessagesShouldReturnNotDestroyedWhenNoMatchingMessage() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        String messageId = username + "|mailbox|12345";
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + username + "|mailbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notDestroyed", anEmptyMap())
            .body(ARGUMENTS + ".destroyed", hasSize(1))
            .body(ARGUMENTS + ".destroyed", contains(username + "|mailbox|1"));
    }

    @Test
    public void setMessagesShouldDeleteMessageWhenMatchingMessage() throws Exception {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        // When
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + username + "|mailbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        // Then
        given()
           .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|mailbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", empty());
    }

    @Test
    public void setMessagesShouldReturnDestroyedNotDestroyWhenMixed() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        String missingMessageId = username + "|mailbox|4";
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + username + "|mailbox|1\", \"" + missingMessageId + "\", \"" + username + "|mailbox|3\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".destroyed", hasSize(2))
            .body(ARGUMENTS + ".notDestroyed", aMapWithSize(1))
            .body(ARGUMENTS + ".destroyed", contains(username + "|mailbox|1", username + "|mailbox|3"))
            .body(ARGUMENTS + ".notDestroyed", hasEntry(equalTo(missingMessageId), Matchers.allOf(
                    hasEntry("type", "notFound"),
                    hasEntry("description", "The message " + missingMessageId + " can't be found")))
            );
    }

    @Test
    public void setMessagesShouldDeleteMatchingMessagesWhenMixed() throws Exception {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        // When
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [\"" + username + "|mailbox|1\", \"" + username + "|mailbox|4\", \"" + username + "|mailbox|3\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        // Then
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|mailbox|1\", \"" + username + "|mailbox|2\", \"" + username + "|mailbox|3\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1));
    }

    @Test
    public void setMessagesShouldReturnUpdatedIdAndNoErrorWhenIsUnreadPassedToFalse() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        String presumedMessageId = username + "|mailbox|1";

        // When
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : false } } }, \"#0\"]]", presumedMessageId))
        .when()
            .post("/jmap")
        // Then
        .then()
            .spec(getSetMessagesUpdateOKResponseAssertions(presumedMessageId))
            .log().ifValidationFails();
    }

    private ResponseSpecification getSetMessagesUpdateOKResponseAssertions(String messageId) {
        ResponseSpecBuilder builder = new ResponseSpecBuilder()
                .expectStatusCode(200)
                .expectBody(NAME, equalTo("messagesSet"))
                .expectBody(ARGUMENTS + ".updated", hasSize(1))
                .expectBody(ARGUMENTS + ".updated", contains(messageId))
                .expectBody(ARGUMENTS + ".error", isEmptyOrNullString())
                .expectBody(ARGUMENTS + ".notUpdated", not(hasKey(messageId)));
        return builder.build();
    }

    @Test
    public void setMessagesShouldMarkAsReadWhenIsUnreadPassedToFalse() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        String presumedMessageId = username + "|mailbox|1";
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : false } } }, \"#0\"]]", presumedMessageId))
        // When
        .when()
                .post("/jmap");
        // Then
        with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + presumedMessageId + "\"]}, \"#0\"]]")
                .post("/jmap")
        .then()
                .statusCode(200)
                .body(NAME, equalTo("messages"))
                .body(ARGUMENTS + ".list", hasSize(1))
                .body(ARGUMENTS + ".list[0].isUnread", equalTo(false))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldReturnUpdatedIdAndNoErrorWhenIsUnreadPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.SEEN));
        await();

        String presumedMessageId = username + "|mailbox|1";
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
        .when()
                .post("/jmap")
        // Then
        .then()
                .spec(getSetMessagesUpdateOKResponseAssertions(presumedMessageId))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldMarkAsUnreadWhenIsUnreadPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags(Flags.Flag.SEEN));
        await();

        String presumedMessageId = username + "|mailbox|1";
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
        .when()
                .post("/jmap");
        // Then
        with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + presumedMessageId + "\"]}, \"#0\"]]")
                .post("/jmap")
        .then()
                .body(NAME, equalTo("messages"))
                .body(ARGUMENTS + ".list", hasSize(1))
                .body(ARGUMENTS + ".list[0].isUnread", equalTo(true))
                .log().ifValidationFails();
    }


    @Test
    public void setMessagesShouldReturnUpdatedIdAndNoErrorWhenIsFlaggedPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        String presumedMessageId = username + "|mailbox|1";
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isFlagged\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
        .when()
                .post("/jmap")
        // Then
        .then()
                .spec(getSetMessagesUpdateOKResponseAssertions(presumedMessageId))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldMarkAsFlaggedWhenIsFlaggedPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        String presumedMessageId = username + "|mailbox|1";
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isFlagged\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
        .when()
                .post("/jmap");
        // Then
        with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + presumedMessageId + "\"]}, \"#0\"]]")
                .post("/jmap")
        .then()
                .body(NAME, equalTo("messages"))
                .body(ARGUMENTS + ".list", hasSize(1))
                .body(ARGUMENTS + ".list[0].isFlagged", equalTo(true))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldRejectUpdateWhenPropertyHasWrongType() throws MailboxException {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        await();

        String messageId = username + "|mailbox|1";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : \"123\" } } }, \"#0\"]]", messageId))
        .when()
                .post("/jmap")
        .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".notUpdated", hasKey(messageId))
                .body(ARGUMENTS + ".notUpdated[\""+messageId+"\"].type", equalTo("invalidProperties"))
                .body(ARGUMENTS + ".notUpdated[\""+messageId+"\"].properties[0]", equalTo("isUnread"))
                .body(ARGUMENTS + ".notUpdated[\""+messageId+"\"].description", equalTo("isUnread: Can not construct instance of java.lang.Boolean from String value '123': only \"true\" or \"false\" recognized\n" +
                        " at [Source: {\"isUnread\":\"123\"}; line: 1, column: 2] (through reference chain: org.apache.james.jmap.model.Builder[\"isUnread\"])"))
                .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    @Ignore("Jackson json deserializer stops after first error found")
    public void setMessagesShouldRejectUpdateWhenPropertiesHaveWrongTypes() throws MailboxException {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        await();

        String messageId = username + "|mailbox|1";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : \"123\", \"isFlagged\" : 456 } } }, \"#0\"]]", messageId))
        .when()
                .post("/jmap")
        .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body(NAME, equalTo("messagesSet"))
                .body(ARGUMENTS + ".notUpdated", hasKey(messageId))
                .body(ARGUMENTS + ".notUpdated[\""+messageId+"\"].type", equalTo("invalidProperties"))
                .body(ARGUMENTS + ".notUpdated[\""+messageId+"\"].properties", hasSize(2))
                .body(ARGUMENTS + ".notUpdated[\""+messageId+"\"].properties[0]", equalTo("isUnread"))
                .body(ARGUMENTS + ".notUpdated[\""+messageId+"\"].properties[1]", equalTo("isFlagged"))
                .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void setMessagesShouldMarkMessageAsAnsweredWhenIsAnsweredPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        String presumedMessageId = username + "|mailbox|1";
        // When
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isAnswered\" : true } } }, \"#0\"]]", presumedMessageId))
        .when()
                .post("/jmap")
        // Then
        .then()
                .spec(getSetMessagesUpdateOKResponseAssertions(presumedMessageId))
                .log().ifValidationFails();
    }

    @Test
    public void setMessagesShouldMarkAsAnsweredWhenIsAnsweredPassed() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        await();

        String presumedMessageId = username + "|mailbox|1";
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isAnswered\" : true } } }, \"#0\"]]", presumedMessageId))
        // When
        .when()
                .post("/jmap");
        // Then
        with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + presumedMessageId + "\"]}, \"#0\"]]")
                .post("/jmap")
        .then()
                .body(NAME, equalTo("messages"))
                .body(ARGUMENTS + ".list", hasSize(1))
                .body(ARGUMENTS + ".list[0].isAnswered", equalTo(true))
                .log().ifValidationFails();
    }

    @Test
    public void setMessageShouldReturnNotFoundWhenUpdateUnknownMessage() {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        String nonExistingMessageId = username + "|mailbox|12345";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"setMessages\", {\"update\": {\"%s\" : { \"isUnread\" : true } } }, \"#0\"]]", nonExistingMessageId))
        .when()
            .post("/jmap")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body(NAME, equalTo("messagesSet"))
            .body(ARGUMENTS + ".notUpdated", hasKey(nonExistingMessageId))
            .body(ARGUMENTS + ".notUpdated[\""+nonExistingMessageId+"\"].type", equalTo("notFound"))
            .body(ARGUMENTS + ".notUpdated[\""+nonExistingMessageId+"\"].description", equalTo("message not found"))
            .body(ARGUMENTS + ".updated", hasSize(0));
    }

    @Test
    public void setMessageShouldReturnCreatedMessageWhenSendingMessage() {
        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\","+
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
                "        \"subject\": \"Thank you for joining example.com!\"," +
                "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
    public void setMessagesShouldCreateMessageInOutboxWhenSendingMessage() throws MailboxException {
        // Given
        String messageCreationId = "user|inbox|1";
        String presumedMessageId = "username@domain.tld|outbox|1";
        String fromAddress = username;
        String messageSubject = "Thank you for joining example.com!";
        String outboxId = getOutboxId();
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

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(requestBody)
        // When
        .when()
                .post("/jmap");

        // Then
        with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + presumedMessageId + "\"]}, \"#0\"]]")
        .post("/jmap")
        .then()
                .body(NAME, equalTo("messages"))
                .body(ARGUMENTS + ".list", hasSize(1))
                .body(ARGUMENTS + ".list[0].subject", equalTo(messageSubject))
                .body(ARGUMENTS + ".list[0].mailboxIds", contains(outboxId))
                ;
    }

    @Test
    public void setMessagesShouldMoveMessageInSentWhenMessageIsSent() throws MailboxException {
        // Given
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "sent");
        String sentMailboxId = getAllMailboxesIds(accessToken).stream()
                .filter(x -> x.get("role").equals("sent"))
                .map(x -> x.get("id"))
                .findFirst().get();

        String fromAddress = username;
        String messageCreationId = "user|inbox|1";
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
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
                    .accept(ContentType.JSON)
                    .contentType(ContentType.JSON)
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
        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
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
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
        String messageCreationId = "user|inbox|1";
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\","+
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
                "        \"cc\": [{ \"name\": \"ALICE\"}]," +
                "        \"subject\": \"Thank you for joining example.com!\"," +
                "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
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
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
                .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].headers.from", equalTo(fromAddress))
                .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].from.name", equalTo(fromAddress))
                .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].from.email", equalTo(fromAddress));
    }

    @Test
    public void setMessagesShouldSucceedWithHtmlBody() {
        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
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
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
                .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].headers.from", equalTo(fromAddress))
                .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].from.name", equalTo(fromAddress))
                .body(ARGUMENTS + ".created[\""+messageCreationId+"\"].from.email", equalTo(fromAddress));
    }

    @Test
    public void setMessagesShouldMoveToSentWhenSendingMessageWithOnlyFromAddress() {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "sent");
        String sentMailboxId = getAllMailboxesIds(accessToken).stream()
                .filter(x -> x.get("role").equals("sent"))
                .map(x -> x.get("id"))
                .findFirst().get();

        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
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
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        // Given
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body(requestBody)
        // When
        .when()
                .post("/jmap");
        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> messageHasBeenMovedToSentBox(sentMailboxId));
    }


    @Test
    public void setMessagesShouldRejectWhenSendingMessageHasMissingSubject() {
        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\","+
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"name\": \"Me\", \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
                "        \"cc\": [{ \"name\": \"ALICE\"}]," +
                "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
                .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].properties", contains("subject"))
                .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].description", endsWith("'subject' is missing"))
                .body(ARGUMENTS + ".created", aMapWithSize(0));
    }


    @Test
    public void setMessagesShouldRejectWhenSendingMessageUseSomeoneElseFromAddress() {
        String messageCreationId = "user|inbox|1";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\","+
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"Me\", \"email\": \"other@domain.tld\"}," +
            "        \"to\": [{ \"name\": \"BOB\", \"email\": \"someone@example.com\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
            "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
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
            .body(ARGUMENTS + ".notCreated[\""+messageCreationId+"\"].description", endsWith("Invalid 'from' field. Must be one of [username@domain.tld]"))
            .body(ARGUMENTS + ".created", aMapWithSize(0));
    }

    @Test
    public void setMessagesShouldDeliverMessageToRecipient() throws Exception {
        // Sender
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "sent");
        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();
        AccessToken recipientToken = JmapAuthentication.authenticateJamesUser(recipientAddress, password);

        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
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
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        // Given
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "sent");
        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();
        AccessToken recipientToken = JmapAuthentication.authenticateJamesUser(recipientAddress, password);

        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\","+
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"recipient\", \"email\": \"" + recipientAddress + "\"}]," +
                "        \"bcc\": [{ \"name\": \"BOB\", \"email\": \"bob@" + USERS_DOMAIN + "\"}]," +
                "        \"cc\": [{ \"name\": \"ALICE\"}]," +
                "        \"subject\": \"Thank you for joining example.com!\"," +
                "        \"textBody\": \"Hello someone, and thank you for joining example.com!\"," +
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        // Given
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", this.accessToken.serialize())
                .body(requestBody)
        // When
        .when()
                .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInRecipientsMailboxes(recipientToken));
        with()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "sent");
        String sentMailboxId = getAllMailboxesIds(accessToken).stream()
                .filter(x -> x.get("role").equals("sent"))
                .map(x -> x.get("id"))
                .findFirst().get();

        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();

        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
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
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        // Given
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", this.accessToken.serialize())
                .body(requestBody)
        // When
        .when()
                .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> messageHasBeenMovedToSentBox(sentMailboxId));
        with()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "sent");

        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");

        String bccAddress = "bob" + "@" + USERS_DOMAIN;
        jmapServer.serverProbe().addUser(bccAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, bccAddress, "inbox");
        await();
        AccessToken bccToken = JmapAuthentication.authenticateJamesUser(bccAddress, password);

        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
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
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        // Given
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", this.accessToken.serialize())
                .body(requestBody)
        // When
        .when()
                .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isAnyMessageFoundInRecipientsMailboxes(bccToken));
        with()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
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
                    .accept(ContentType.JSON)
                    .contentType(ContentType.JSON)
                    .header("Authorization", recipientToken.serialize())
                    .body("[[\"getMessageList\", {}, \"#0\"]]")
            .when()
                    .post("/jmap")
            .then()
                    .statusCode(200)
                    .body(NAME, equalTo("messageList"))
                    .body(ARGUMENTS + ".messageIds", hasSize(1))
            ;
            return true;
        } catch(AssertionError e) {
            return false;
        }
    }


    @Test
    public void setMessagesShouldSendAReadableHtmlMessage() throws Exception {
        // Sender
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "sent");
        // Recipient
        String recipientAddress = "recipient" + "@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addUser(recipientAddress, password);
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, recipientAddress, "inbox");
        await();
        AccessToken recipientToken = JmapAuthentication.authenticateJamesUser(recipientAddress, password);

        String messageCreationId = "user|inbox|1";
        String fromAddress = username;
        String requestBody = "[" +
                "  [" +
                "    \"setMessages\","+
                "    {" +
                "      \"create\": { \"" + messageCreationId  + "\" : {" +
                "        \"from\": { \"email\": \"" + fromAddress + "\"}," +
                "        \"to\": [{ \"name\": \"BOB\", \"email\": \"" + recipientAddress + "\"}]," +
                "        \"subject\": \"Thank you for joining example.com!\"," +
                "        \"htmlBody\": \"Hello <b>someone</b>, and thank you for joining example.com!\"," +
                "        \"mailboxIds\": [\"" + getOutboxId() + "\"]" +
                "      }}" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        // Given
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", this.accessToken.serialize())
                .body(requestBody)
        // When
        .when()
                .post("/jmap");

        // Then
        calmlyAwait.atMost(30, TimeUnit.SECONDS).until( () -> isHtmlMessageReceived(recipientToken));
    }

    private boolean isHtmlMessageReceived(AccessToken recipientToken) {
        try {
            with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
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
}
