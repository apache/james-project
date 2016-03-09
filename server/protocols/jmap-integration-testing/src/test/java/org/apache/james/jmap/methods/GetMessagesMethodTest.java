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

package org.apache.james.jmap.methods;

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

import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.JmapServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.elasticsearch.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public abstract class GetMessagesMethodTest {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch();
    private EmbeddedCassandra cassandra = EmbeddedCassandra.createStartServer();
    private JmapServer jmapServer = jmapServer(temporaryFolder, embeddedElasticSearch, cassandra);

    protected abstract JmapServer jmapServer(TemporaryFolder temporaryFolder, EmbeddedElasticSearch embeddedElasticSearch, EmbeddedCassandra cassandra);

    @Rule
    public RuleChain chain = RuleChain
        .outerRule(temporaryFolder)
        .around(embeddedElasticSearch)
        .around(jmapServer);

    private AccessToken accessToken;
    private String username;

    @Before
    public void setup() throws Exception {
        RestAssured.port = jmapServer.getPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));

        String domain = "domain.tld";
        username = "username@" + domain;
        String password = "password";
        jmapServer.serverProbe().addDomain(domain);
        jmapServer.serverProbe().addUser(username, password);
        jmapServer.serverProbe().createMailbox("#private", "username", "inbox");
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);
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
        
        embeddedElasticSearch.awaitForElasticSearch();
        
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
            .body(ARGUMENTS + ".list[0].id", equalTo(username + "|inbox|1"))
            .body(ARGUMENTS + ".list[0].threadId", equalTo(username + "|inbox|1"))
            .body(ARGUMENTS + ".list[0].subject", equalTo("my test subject"))
            .body(ARGUMENTS + ".list[0].textBody", equalTo("testmail"))
            .body(ARGUMENTS + ".list[0].isUnread", equalTo(true))
            .body(ARGUMENTS + ".list[0].preview", equalTo("testmail"))
            .body(ARGUMENTS + ".list[0].headers", equalTo(ImmutableMap.of("subject", "my test subject")))
            .body(ARGUMENTS + ".list[0].date", equalTo("2014-10-30T14:12:00Z"));
    }

    @Test
    public void getMessagesShouldReturnMessageWhenHtmlMessage() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Content-Type: text/html\r\nSubject: my test subject\r\n\r\nThis is a <b>HTML</b> mail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        embeddedElasticSearch.awaitForElasticSearch();
        
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
            .body(ARGUMENTS + ".list[0].id", equalTo(username + "|inbox|1"))
            .body(ARGUMENTS + ".list[0].threadId", equalTo(username + "|inbox|1"))
            .body(ARGUMENTS + ".list[0].subject", equalTo("my test subject"))
            .body(ARGUMENTS + ".list[0].htmlBody", equalTo("This is a <b>HTML</b> mail"))
            .body(ARGUMENTS + ".list[0].isUnread", equalTo(true))
            .body(ARGUMENTS + ".list[0].preview", equalTo("This is a <b>HTML</b> mail"))
            .body(ARGUMENTS + ".list[0].headers", equalTo(ImmutableMap.of("content-type", "text/html", "subject", "my test subject")))
            .body(ARGUMENTS + ".list[0].date", equalTo("2014-10-30T14:12:00Z"));
    }
    
    @Test
    public void getMessagesShouldReturnFilteredPropertiesMessagesWhenAsked() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        embeddedElasticSearch.awaitForElasticSearch();
        
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
            .body(ARGUMENTS + ".list[0].id", equalTo(username + "|inbox|1"))
            .body(ARGUMENTS + ".list[0].subject", equalTo("my test subject"))
            .body(ARGUMENTS + ".list[0].textBody", nullValue())
            .body(ARGUMENTS + ".list[0].isUnread", nullValue())
            .body(ARGUMENTS + ".list[0].preview", nullValue())
            .body(ARGUMENTS + ".list[0].headers", nullValue())
            .body(ARGUMENTS + ".list[0].date", nullValue());
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
        
        embeddedElasticSearch.awaitForElasticSearch();
        
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
            .body(ARGUMENTS + ".list[0].id", equalTo(username + "|inbox|1"))
            .body(ARGUMENTS + ".list[0].subject", nullValue())
            .body(ARGUMENTS + ".list[0].textBody", nullValue())
            .body(ARGUMENTS + ".list[0].isUnread", nullValue())
            .body(ARGUMENTS + ".list[0].preview", nullValue())
            .body(ARGUMENTS + ".list[0].headers", equalTo(ImmutableMap.of("from", "user@domain.tld", "header2", "Header2Content")))
            .body(ARGUMENTS + ".list[0].date", nullValue());
    }

    @Test
    public void getMessagesShouldReturnNotFoundWhenIdDoesntMatch() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        embeddedElasticSearch.awaitForElasticSearch();
        
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
        embeddedElasticSearch.awaitForElasticSearch();

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
            .body(ARGUMENTS + ".list[0].id", equalTo("username@domain.tld|mailbox|1"))
            .body(ARGUMENTS + ".list[0].subject", equalTo("my test subject"));
    }
}
