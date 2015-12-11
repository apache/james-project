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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.JmapServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public abstract class GetMessageListMethodTest {

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);
    private JmapServer jmapServer = jmapServer(temporaryFolder, embeddedElasticSearch);

    protected abstract JmapServer jmapServer(TemporaryFolder temporaryFolder, EmbeddedElasticSearch embeddedElasticSearch);

    @Rule
    public RuleChain chain = RuleChain
        .outerRule(temporaryFolder)
        .around(embeddedElasticSearch)
        .around(jmapServer);

    private AccessToken accessToken;

    @Before
    public void setup() throws Exception {
        RestAssured.port = jmapServer.getPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));

        String domain = "domain.tld";
        String username = "username@" + domain;
        String password = "password";
        jmapServer.serverProbe().addDomain(domain);
        jmapServer.serverProbe().addUser(username, password);
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);
    }

    @After
    public void tearDown() {
        jmapServer.clean();
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
            .content(equalTo("[[\"error\",{\"type\":\"invalidArguments\"},\"#0\"]]"));
    }

    @Test
    public void getMessageListShouldReturnAllMessagesWhenSingleMailboxNoParameters() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "name");

        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "name"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "name"), 
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
            .content(startsWith("[[\"getMessageList\","
                    + "{\"accountId\":null,\"filter\":null,\"sort\":[],\"collapseThreads\":false,\"state\":null,"
                    +   "\"canCalculateUpdates\":false,\"position\":0,\"total\":0,\"threadIds\":[],\"messageIds\":[\"1\",\"2\"]},"
                    + "\"#0\"]]"));
    }

    @Ignore("ISSUE-53")
    @Test
    public void getMessageListShouldReturnAllMessagesWhenMultipleMailboxesAndNoParameters() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "mailbox");
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "mailbox2");
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox2"), 
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
            .content(startsWith("[[\"getMessageList\","
                    + "{\"accountId\":null,\"filter\":null,\"sort\":[],\"collapseThreads\":false,\"state\":null,"
                    +   "\"canCalculateUpdates\":false,\"position\":0,\"total\":0,\"threadIds\":[],\"messageIds\":[\"1\",\"2\"]},"
                    + "\"#0\"]]"));
    }

    @Test
    public void getMessageListShouldFilterMessagesWhenInMailboxesFilterMatches() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "mailbox");

        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"mailbox\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"getMessageList\","
                    + "{\"accountId\":null,\"filter\":null,\"sort\":[],\"collapseThreads\":false,\"state\":null,"
                    +   "\"canCalculateUpdates\":false,\"position\":0,\"total\":0,\"threadIds\":[],\"messageIds\":[\"1\"]},"
                    + "\"#0\"]]"));
    }

    @Test
    public void getMessageListShouldFilterMessagesWhenMultipleInMailboxesFilterMatches() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "mailbox");
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "mailbox2");
        embeddedElasticSearch.awaitForElasticSearch();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"mailbox\",\"mailbox2\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"getMessageList\","
                    + "{\"accountId\":null,\"filter\":null,\"sort\":[],\"collapseThreads\":false,\"state\":null,"
                    +   "\"canCalculateUpdates\":false,\"position\":0,\"total\":0,\"threadIds\":[],\"messageIds\":[\"1\"]},"
                    + "\"#0\"]]"));
    }

    @Test
    public void getMessageListShouldFilterMessagesWhenInMailboxesFilterDoesntMatches() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "mailbox");

        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
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
            .content(startsWith("[[\"getMessageList\","
                    + "{\"accountId\":null,\"filter\":null,\"sort\":[],\"collapseThreads\":false,\"state\":null,"
                    +   "\"canCalculateUpdates\":false,\"position\":0,\"total\":0,\"threadIds\":[],\"messageIds\":[]},"
                    + "\"#0\"]]"));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateDefault() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
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
            .content(startsWith("[[\"getMessageList\","
                    + "{\"accountId\":null,\"filter\":null,\"sort\":[],\"collapseThreads\":false,\"state\":null,"
                    +   "\"canCalculateUpdates\":false,\"position\":0,\"total\":0,\"threadIds\":[],\"messageIds\":[\"1\",\"2\"]},"
                    + "\"#0\"]]"));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateAsc() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
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
            .content(startsWith("[[\"getMessageList\","
                    + "{\"accountId\":null,\"filter\":null,\"sort\":[],\"collapseThreads\":false,\"state\":null,"
                    +   "\"canCalculateUpdates\":false,\"position\":0,\"total\":0,\"threadIds\":[],\"messageIds\":[\"2\",\"1\"]},"
                    + "\"#0\"]]"));
    }

    @Test
    public void getMessageListShouldSortMessagesWhenSortedByDateDesc() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "mailbox");

        LocalDate date = LocalDate.now();
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(date.plusDays(1).toEpochDay()), false, new Flags());
        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "mailbox"), 
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
            .content(startsWith("[[\"getMessageList\","
                    + "{\"accountId\":null,\"filter\":null,\"sort\":[],\"collapseThreads\":false,\"state\":null,"
                    +   "\"canCalculateUpdates\":false,\"position\":0,\"total\":0,\"threadIds\":[],\"messageIds\":[\"1\",\"2\"]},"
                    + "\"#0\"]]"));
    }
}
