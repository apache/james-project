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
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.JmapServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public abstract class GetMessageListMethodTest {
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
    private String domain;

    @Before
    public void setup() throws Exception {
        RestAssured.port = jmapServer.getPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));

        this.domain = "domain.tld";
        this.username = "username@" + domain;
        String password = "password";
        jmapServer.serverProbe().addDomain(domain);
        jmapServer.serverProbe().addUser(username, password);
        this.accessToken = JmapAuthentication.authenticateJamesUser(username, password);
    }

    @Test
    public void getMessageListShouldErrorInvalidArgumentsWhenRequestIsInvalid() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("Can not instantiate value of type [simple type, class org.apache.james.jmap.model.FilterCondition$Builder] from Boolean value (true); no single-boolean/Boolean-arg constructor/factory method\n" + 
                    " at [Source: {\"filter\":true}; line: 1, column: 2] (through reference chain: org.apache.james.jmap.model.Builder[\"filter\"])"));
    }

    @Test
    public void getMessageListShouldReturnAllMessagesWhenSingleMailboxNoParameters() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder("username@domain.tld|mailbox|1", "username@domain.tld|mailbox|2"));
    }

    @Test
    public void getMessageListShouldReturnAllMessagesWhenMultipleMailboxesAndNoParameters() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox2");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox2"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder("username@domain.tld|mailbox|1", "username@domain.tld|mailbox2|1"));
    }

    @Test
    public void getMessageListShouldReturnAllMessagesOfCurrentUserOnlyWhenMultipleMailboxesAndNoParameters() throws Exception {
        String otherUser = "other@" + domain;
        String password = "password";
        jmapServer.serverProbe().addUser(otherUser, password);

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox2");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox2"),
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, otherUser, "mailbox");
        jmapServer.serverProbe().appendMessage(otherUser, new MailboxPath(MailboxConstants.USER_NAMESPACE, otherUser, "mailbox"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder("username@domain.tld|mailbox|1", "username@domain.tld|mailbox2|1"));
    }

    @Test
    public void getMessageListShouldFilterMessagesWhenInMailboxesFilterMatches() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        String mailboxId = 
                with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMailboxes\", {}, \"#0\"]]")
                .post("/jmap")
                .path("[0][1].list[0].id");
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains("username@domain.tld|mailbox|1"));
    }

    @Test
    public void getMessageListShouldFilterMessagesWhenMultipleInMailboxesFilterMatches() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox2");
        embeddedElasticSearch.awaitForElasticSearch();

        List<String> mailboxIds = 
                with()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMailboxes\", {}, \"#0\"]]")
                .post("/jmap")
                .path("[0][1].list.id");
        
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body(String.format("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"%s\", \"%s\"]}}, \"#0\"]]", mailboxIds.get(0), mailboxIds.get(1)))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains("username@domain.tld|mailbox|1"));
    }

    @Test
    public void getMessageListShouldFilterMessagesWhenInMailboxesFilterDoesntMatches() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"mailbox2\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(ARGUMENTS + ".messageIds", empty());
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateDefault() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"date\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains("username@domain.tld|mailbox|1", "username@domain.tld|mailbox|2"));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateAsc() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"date asc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains("username@domain.tld|mailbox|2", "username@domain.tld|mailbox|1"));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateDesc() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"sort\":[\"date desc\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains("username@domain.tld|mailbox|1", "username@domain.tld|mailbox|2"));
    }

    @Test
    public void getMessageListShouldWorkWhenCollapseThreadIsFalse() {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
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
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"collapseThreads\":true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"));
    }
    
    @Test
    public void getMessageListShouldReturnAllMessagesWhenPositionIsNotGiven() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder("username@domain.tld|mailbox|1", "username@domain.tld|mailbox|2"));
    }

    @Test
    public void getMessageListShouldReturnSkipMessagesWhenPositionIsGiven() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"position\":1}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains("username@domain.tld|mailbox|2"));
    }

    @Test
    public void getMessageListShouldReturnAllMessagesWhenLimitIsNotGiven() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder("username@domain.tld|mailbox|1", "username@domain.tld|mailbox|2"));
    }

    @Test
    public void getMessageListShouldReturnLimitMessagesWhenLimitGiven() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"limit\":1}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", contains("username@domain.tld|mailbox|1"));
    }

    @Test
    public void getMessageListShouldReturnLimitMessagesWithDefaultValueWhenLimitIsNotGiven() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test4\r\n\r\ntestmail".getBytes()), new Date(date.toEpochDay()), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder("username@domain.tld|mailbox|1", "username@domain.tld|mailbox|2", "username@domain.tld|mailbox|3"));
    }

    @Test
    public void getMessageListShouldChainFetchingMessagesWhenAskedFor() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"fetchMessages\":true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body("[0][0]", equalTo("messageList"))
            .body("[1][0]", equalTo("messages"))
            .body("[0][1].messageIds", hasSize(1))
            .body("[0][1].messageIds[0]", equalTo("username@domain.tld|mailbox|1"))
            .body("[1][1].list", hasSize(1))
            .body("[1][1].list[0].id", equalTo("username@domain.tld|mailbox|1"));
    }
}
