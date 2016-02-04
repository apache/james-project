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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayInputStream;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.JmapServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public abstract class GetMailboxesMethodTest {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);
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
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);
    }

    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullAccountId() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
    }

    @Test
    public void getMailboxesShouldReturnEmptyWhenIdsDoesntMatch() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "name");

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"notAMailboxId\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(0));
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWhenIdsMatch() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "myMailbox");

        Mailbox<CassandraId> mailbox = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
        Mailbox<CassandraId> mailbox2 = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "myMailbox");

        String mailboxId = mailbox.getMailboxId().serialize();
        String mailboxId2 = mailbox2.getMailboxId().serialize();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\", \"" + mailboxId2 + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(2))
            .body(ARGUMENTS + ".list[0].id", equalTo(mailboxId))
            .body(ARGUMENTS + ".list[1].id", equalTo(mailboxId2));
    }

    @Test
    public void getMailboxesShouldReturnOnlyMatchingMailboxesWhenIdsGiven() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "myMailbox");

        Mailbox<CassandraId> mailbox = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");

        String mailboxId = mailbox.getMailboxId().serialize();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].id", equalTo(mailboxId));
    }

    @Test
    public void getMailboxesShouldReturnEmptyWhenIdsIsEmpty() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", empty());
    }

    @Test
    public void getMailboxesShouldReturnAllMailboxesWhenIdsIsNull() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "myMailbox");

        Mailbox<CassandraId> mailbox = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
        Mailbox<CassandraId> mailbox2 = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "myMailbox");

        String mailboxId = mailbox.getMailboxId().serialize();
        String mailboxId2 = mailbox2.getMailboxId().serialize();

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": null}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(2))
            .body(ARGUMENTS + ".list[0].id", equalTo(mailboxId))
            .body(ARGUMENTS + ".list[1].id", equalTo(mailboxId2));
    }
    
    @Test
    public void getMailboxesShouldErrorInvalidArgumentsWhenRequestIsInvalid() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"));
    }

    @Test
    public void getMailboxesShouldReturnEmptyListWhenNoMailboxes() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", empty());
    }

    @Test
    public void getMailboxesShouldReturnEmptyListWhenNoMailboxesWithJWTAuthWorkflow() {

        String authToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.T04BTk" +
                "LXkJj24coSZkK13RfG25lpvmSl2MJ7N10KpBk9_-95EGYZdog-BDAn3PJzqVw52z-Bwjh4VOj1-j7cURu0cT4jXehhUrlCxS4n7QHZ" +
                "DN_bsEYGu7KzjWTpTsUiHe-rN7izXVFxDGG1TGwlmBCBnPW-EFCf9ylUsJi0r2BKNdaaPRfMIrHptH1zJBkkUziWpBN1RNLjmvlAUf" +
                "49t1Tbv21ZqYM5Ht2vrhJWczFbuC-TD-8zJkXhjTmA1GVgomIX5dx1cH-dZX1wANNmshUJGHgepWlPU-5VIYxPEhb219RMLJIELMY2" +
                "qNOR8Q31ydinyqzXvCSzVJOf6T60-w";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authToken)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".accountId", isEmptyOrNullString())
            .body(ARGUMENTS + ".state", isEmptyOrNullString())
            .body(ARGUMENTS + ".notFound", isEmptyOrNullString())
            .body(ARGUMENTS + ".list", hasSize(0));
    }


    @Test
    public void getMailboxesShouldErrorWithBadJWTToken() {

        String badAuthToken = "BADTOKENOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.T04BTk" +
                "LXkJj24coSZkK13RfG25lpvmSl2MJ7N10KpBk9_-95EGYZdog-BDAn3PJzqVw52z-Bwjh4VOj1-j7cURu0cT4jXehhUrlCxS4n7QHZ" +
                "DN_bsEYGu7KzjWTpTsUiHe-rN7izXVFxDGG1TGwlmBCBnPW-EFCf9ylUsJi0r2BKNdaaPRfMIrHptH1zJBkkUziWpBN1RNLjmvlAUf" +
                "49t1Tbv21ZqYM5Ht2vrhJWczFbuC-TD-8zJkXhjTmA1GVgomIX5dx1cH-dZX1wANNmshUJGHgepWlPU-5VIYxPEhb219RMLJIELMY2" +
                "qNOR8Q31ydinyqzXvCSzVJOf6T60-w";

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + badAuthToken)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(401);
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWhenAvailable() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "name");

        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "name"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list[0].name", equalTo("name"))
            .body(ARGUMENTS + ".list[0].parentId", nullValue())
            .body(ARGUMENTS + ".list[0].role", nullValue())
            .body(ARGUMENTS + ".list[0].sortOrder", equalTo(1000))
            .body(ARGUMENTS + ".list[0].mustBeOnlyMailbox", equalTo(false))
            .body(ARGUMENTS + ".list[0].mayReadItems", equalTo(false))
            .body(ARGUMENTS + ".list[0].mayAddItems", equalTo(false))
            .body(ARGUMENTS + ".list[0].mayRemoveItems", equalTo(false))
            .body(ARGUMENTS + ".list[0].mayCreateChild", equalTo(false))
            .body(ARGUMENTS + ".list[0].mayRename", equalTo(false))
            .body(ARGUMENTS + ".list[0].mayDelete", equalTo(false))
            .body(ARGUMENTS + ".list[0].totalMessages", equalTo(1))
            .body(ARGUMENTS + ".list[0].unreadMessages", equalTo(1))
            .body(ARGUMENTS + ".list[0].unreadThreads", equalTo(0));
    }

    @Test
    public void getMailboxesShouldReturnFilteredMailboxesPropertiesWhenRequestContainsFilterProperties() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "name");

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"properties\" : [\"unreadMessages\", \"sortOrder\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list[0].id", not(isEmptyOrNullString()))
            .body(ARGUMENTS + ".list[0].name", nullValue())
            .body(ARGUMENTS + ".list[0].parentId", nullValue())
            .body(ARGUMENTS + ".list[0].role", nullValue())
            .body(ARGUMENTS + ".list[0].sortOrder", equalTo(1000))
            .body(ARGUMENTS + ".list[0].mustBeOnlyMailbox", nullValue())
            .body(ARGUMENTS + ".list[0].mayReadItems", nullValue())
            .body(ARGUMENTS + ".list[0].mayAddItems", nullValue())
            .body(ARGUMENTS + ".list[0].mayRemoveItems", nullValue())
            .body(ARGUMENTS + ".list[0].mayCreateChild", nullValue())
            .body(ARGUMENTS + ".list[0].mayRename", nullValue())
            .body(ARGUMENTS + ".list[0].mayDelete", nullValue())
            .body(ARGUMENTS + ".list[0].totalMessages", nullValue())
            .body(ARGUMENTS + ".list[0].unreadMessages", equalTo(0))
            .body(ARGUMENTS + ".list[0].unreadThreads", nullValue());
    }

    @Test
    public void getMailboxesShouldReturnIdWhenRequestContainsEmptyPropertyListFilter() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "name");

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"properties\" : []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list[0].id", not(isEmptyOrNullString()))
            .body(ARGUMENTS + ".list[0].name", nullValue());
    }

    @Test
    public void getMailboxesShouldIgnoreUnknownPropertiesWhenRequestContainsUnknownPropertyListFilter() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "name");

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"properties\" : [\"unknown\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list[0].id", not(isEmptyOrNullString()))
            .body(ARGUMENTS + ".list[0].name", nullValue());
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithSortOrder() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "trash");

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(2))
            .body(ARGUMENTS + ".list[0].name", equalTo("inbox"))
            .body(ARGUMENTS + ".list[0].sortOrder", equalTo(10))
            .body(ARGUMENTS + ".list[1].name", equalTo("trash"))
            .body(ARGUMENTS + ".list[1].sortOrder", equalTo(60));
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithRolesInLowerCase() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "outbox");

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].role", equalTo("outbox"));
    }

}
