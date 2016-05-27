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
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public abstract class GetMessagesMethodTest {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String FIRST_MESSAGE = ARGUMENTS + ".list[0]";
    private static final String ATTACHMENTS = FIRST_MESSAGE + ".attachments";
    private static final String FIRST_ATTACHMENT = ATTACHMENTS + "[0]";
    private static final String SECOND_ATTACHMENT = ATTACHMENTS + "[1]";

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
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        String domain = "domain.tld";
        username = "username@" + domain;
        String password = "password";
        jmapServer.serverProbe().addDomain(domain);
        jmapServer.serverProbe().addUser(username, password);
        jmapServer.serverProbe().createMailbox("#private", "username", "inbox");
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }
    
    @Test
    public void getMessagesShouldErrorNotSupportedWhenRequestContainsNonNullAccountId() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
    }
    
    @Test
    public void getMessagesShouldIgnoreUnknownArguments() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"WAT\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".notFound", empty())
            .body(ARGUMENTS + ".list", empty());
    }

    @Test
    public void getMessagesShouldErrorInvalidArgumentsWhenRequestContainsInvalidArgument() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": null}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("N/A (through reference chain: org.apache.james.jmap.model.Builder[\"ids\"])"));
    }

    @Test
    public void getMessagesShouldReturnEmptyListWhenNoMessage() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", empty());
    }

    @Test
    public void getMessagesShouldReturnNoFoundIndicesWhenMessageNotFound() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"username|inbox|12\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".notFound", contains("username|inbox|12"));
    }

    @Test
    public void getMessagesShouldReturnMessagesWhenAvailable() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        await();
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".id", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".threadId", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".subject", equalTo("my test subject"))
            .body(FIRST_MESSAGE + ".textBody", equalTo("testmail"))
            .body(FIRST_MESSAGE + ".isUnread", equalTo(true))
            .body(FIRST_MESSAGE + ".preview", equalTo("testmail"))
            .body(FIRST_MESSAGE + ".headers", equalTo(ImmutableMap.of("subject", "my test subject")))
            .body(FIRST_MESSAGE + ".date", equalTo("2014-10-30T14:12:00Z"))
            .body(FIRST_MESSAGE + ".hasAttachment", equalTo(false))
            .body(ATTACHMENTS, empty());
    }

    @Test
    public void getMessagesShouldReturnMessageWhenHtmlMessage() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Content-Type: text/html\r\nSubject: my test subject\r\n\r\nThis is a <b>HTML</b> mail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        await();
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".id", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".threadId", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".subject", equalTo("my test subject"))
            .body(FIRST_MESSAGE + ".htmlBody", equalTo("This is a <b>HTML</b> mail"))
            .body(FIRST_MESSAGE + ".isUnread", equalTo(true))
            .body(FIRST_MESSAGE + ".preview", equalTo("This is a HTML mail"))
            .body(FIRST_MESSAGE + ".headers", equalTo(ImmutableMap.of("content-type", "text/html", "subject", "my test subject")))
            .body(FIRST_MESSAGE + ".date", equalTo("2014-10-30T14:12:00Z"));
    }
    
    @Test
    public void getMessagesShouldReturnMessageWhenComplexHtmlMessage() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Content-Type: text/html\r\nSubject: my test subject\r\n\r\nThis is a <b>HTML</b> mail containing <u>underlined part</u>, <i>italic part</i> and <u><i>underlined AND italic part</i></u>".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        await();
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".id", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".threadId", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".subject", equalTo("my test subject"))
            .body(FIRST_MESSAGE + ".htmlBody", equalTo("This is a <b>HTML</b> mail containing <u>underlined part</u>, <i>italic part</i> and <u><i>underlined AND italic part</i></u>"))
            .body(FIRST_MESSAGE + ".isUnread", equalTo(true))
            .body(FIRST_MESSAGE + ".preview", equalTo("This is a HTML mail containing underlined part, italic part and underlined AND italic part"))
            .body(FIRST_MESSAGE + ".headers", equalTo(ImmutableMap.of("content-type", "text/html", "subject", "my test subject")))
            .body(FIRST_MESSAGE + ".date", equalTo("2014-10-30T14:12:00Z"));
    }
    
    @Test
    public void messagePreviewShouldContainTagsWhenPlainTextMessageContainsTags() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Content-Type: text/plain\r\nSubject: my test subject\r\n\r\nHere is a listing of HTML tags : <b>jfjfjfj</b>, <i>jfhdgdgdfj</i>, <u>jfjaaafj</u>".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());

        await();
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".preview", equalTo("Here is a listing of HTML tags : <b>jfjfjfj</b>, <i>jfhdgdgdfj</i>, <u>jfjaaafj</u>"));
    }
    
    @Test
    public void getMessagesShouldReturnMessageWhenHtmlMessageWithIncorrectTag() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Content-Type: text/html\r\nSubject: my test subject\r\n\r\nThis is a <ganan>HTML</b> mail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        await();
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".id", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".threadId", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".subject", equalTo("my test subject"))
            .body(FIRST_MESSAGE + ".htmlBody", equalTo("This is a <ganan>HTML</b> mail"))
            .body(FIRST_MESSAGE + ".isUnread", equalTo(true))
            .body(FIRST_MESSAGE + ".preview", equalTo("This is a HTML mail"))
            .body(FIRST_MESSAGE + ".headers", equalTo(ImmutableMap.of("content-type", "text/html", "subject", "my test subject")))
            .body(FIRST_MESSAGE + ".date", equalTo("2014-10-30T14:12:00Z"));
    }
    
    @Test
    public void getMessagesShouldReturnFilteredPropertiesMessagesWhenAsked() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        await();
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"], \"properties\": [\"id\", \"subject\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".id", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".subject", equalTo("my test subject"))
            .body(FIRST_MESSAGE + ".textBody", nullValue())
            .body(FIRST_MESSAGE + ".isUnread", nullValue())
            .body(FIRST_MESSAGE + ".preview", nullValue())
            .body(FIRST_MESSAGE + ".headers", nullValue())
            .body(FIRST_MESSAGE + ".date", nullValue());
    }
    
    @Test
    public void getMessagesShouldReturnFilteredHeaderPropertyWhenAsked() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream(("From: user@domain.tld\r\n"
                        + "header1: Header1Content\r\n"
                        + "HEADer2: Header2Content\r\n"
                        + "Subject: my test subject\r\n"
                        + "\r\n"
                        + "testmail").getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        await();
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"], \"properties\": [\"headers.from\", \"headers.heADER2\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".id", equalTo(username + "|inbox|1"))
            .body(FIRST_MESSAGE + ".subject", nullValue())
            .body(FIRST_MESSAGE + ".textBody", nullValue())
            .body(FIRST_MESSAGE + ".isUnread", nullValue())
            .body(FIRST_MESSAGE + ".preview", nullValue())
            .body(FIRST_MESSAGE + ".headers", equalTo(ImmutableMap.of("from", "user@domain.tld", "header2", "Header2Content")))
            .body(FIRST_MESSAGE + ".date", nullValue());
    }

    @Test
    public void getMessagesShouldReturnNotFoundWhenIdDoesntMatch() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        await();
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"username|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", empty())
            .body(ARGUMENTS + ".notFound", hasSize(1));
    }

    @Test
    public void getMessagesShouldReturnMandatoryPropertiesMessagesWhenNotAsked() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        await();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|mailbox|1\"], \"properties\": [\"subject\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".id", equalTo("username@domain.tld|mailbox|1"))
            .body(FIRST_MESSAGE + ".subject", equalTo("my test subject"));
    }

    @Test
    public void getMessagesShouldReturnAttachmentsWhenSome() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                ClassLoader.getSystemResourceAsStream("twoAttachments.eml"), Date.from(dateTime.toInstant()), false, new Flags());
        
        await();
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".hasAttachment", equalTo(true))
            .body(ATTACHMENTS, hasSize(2))
            .body(FIRST_ATTACHMENT + ".blobId", equalTo("223a76c0e8c1b1762487d8e0598bd88497d73ef2"))
            .body(FIRST_ATTACHMENT + ".type", equalTo("image/jpeg"))
            .body(FIRST_ATTACHMENT + ".size", equalTo(846))
            .body(SECOND_ATTACHMENT + ".blobId", equalTo("58aa22c2ec5770fb9e574ba19008dbfc647eba43"))
            .body(SECOND_ATTACHMENT + ".type", equalTo("image/jpeg"))
            .body(SECOND_ATTACHMENT + ".size", equalTo(597));
    }

    @Test
    public void getMessagesShouldReturnHTMLBodyWhenSomeAttachmentsAndHTMLmail() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                ClassLoader.getSystemResourceAsStream("twoAttachments.eml"), Date.from(dateTime.toInstant()), false, new Flags());
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".hasAttachment", equalTo(true))
            .body(ATTACHMENTS, hasSize(2))
            .body(FIRST_MESSAGE + ".preview", equalTo("html\n"))
            .body(FIRST_MESSAGE + ".textBody", nullValue())
            .body(FIRST_MESSAGE + ".htmlBody", equalTo("<b>html</b>\n"));
    }

    @Test
    public void getMessagesShouldReturnTextBodyWhenSomeAttachmentsAndTextmail() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                ClassLoader.getSystemResourceAsStream("twoAttachmentsTextPlain.eml"), Date.from(dateTime.toInstant()), false, new Flags());
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".hasAttachment", equalTo(true))
            .body(ATTACHMENTS, hasSize(2))
            .body(FIRST_MESSAGE + ".preview", equalTo("html\n"))
            .body(FIRST_MESSAGE + ".textBody", equalTo("html\n"))
            .body(FIRST_MESSAGE + ".htmlBody", nullValue());
    }

    @Test
    public void getMessagesShouldReturnBothHTMLAndTextBodyWhenOneAttachmentAndBothHTMLAndTextmail() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                ClassLoader.getSystemResourceAsStream("htmlAndTextMultipartWithOneAttachment.eml"), Date.from(dateTime.toInstant()), false, new Flags());
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messages"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MESSAGE + ".hasAttachment", equalTo(true))
            .body(ATTACHMENTS, hasSize(1))
            .body(FIRST_MESSAGE + ".preview", equalTo("blabla\nbloblo\n"))
            .body(FIRST_MESSAGE + ".textBody", equalTo("/blabla/\n*bloblo*\n"))
            .body(FIRST_MESSAGE + ".htmlBody", equalTo("<i>blabla</i>\n<b>bloblo</b>\n"));
    }
}
