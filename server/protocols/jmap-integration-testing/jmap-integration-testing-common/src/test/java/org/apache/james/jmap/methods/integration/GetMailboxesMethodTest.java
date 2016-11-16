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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayInputStream;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JmapServer;
import org.apache.james.WebAdminServer;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public abstract class GetMailboxesMethodTest<T extends GuiceJamesServer & JmapServer & WebAdminServer> {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";

    protected abstract T createJmapServer();

    private AccessToken accessToken;
    private String username;
    private T jmapServer;

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

        String domain = "domain.tld";
        username = "username@" + domain;
        String password = "password";
        jmapServer.serverProbe().addDomain(domain);
        jmapServer.serverProbe().addUser(username, password);
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }
    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullAccountId() throws Exception {
        given()
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
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved");
        String removedId = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved").getMailboxId().serialize();
        jmapServer.serverProbe().deleteMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + removedId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(0));
    }

    @Test
    public void getMailboxesShouldReturnErrorWhenInvalidMailboxId() throws Exception {
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"invalid id\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"));
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWhenIdsMatch() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "myMailbox");

        Mailbox mailbox = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
        Mailbox mailbox2 = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "myMailbox");

        String mailboxId = mailbox.getMailboxId().serialize();
        String mailboxId2 = mailbox2.getMailboxId().serialize();

        given()
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

        Mailbox mailbox = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");

        String mailboxId = mailbox.getMailboxId().serialize();

        given()
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

        Mailbox mailbox = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
        Mailbox mailbox2 = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "myMailbox");

        String mailboxId = mailbox.getMailboxId().serialize();
        String mailboxId2 = mailbox2.getMailboxId().serialize();

        given()
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
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("Can not deserialize instance of java.util.ArrayList out of VALUE_TRUE token\n"
                    + " at [Source: {\"ids\":true}; line: 1, column: 2] (through reference chain: org.apache.james.jmap.model.Builder[\"ids\"])"));
    }

    @Test
    public void getMailboxesShouldReturnEmptyListWhenNoMailboxes() throws Exception {
        given()
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
    public void getMailboxesShouldReturnDefaultMailboxesWhenAuthenticatedUserDoesntHaveAnAccountYet() throws Exception {

        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMzM3QGRvbWFpbi50bGQiLCJuYW1lIjoiTmV3IFVzZXIif"
                + "Q.fxNWNzksXyCij2ooVi-QfGe9vTicF2N9FDtWSJdjWTjhwoQ_i0dgiT8clp4dtOJzy78hB2UkAW-iq7z3PR_Gz0qFah7EbYoEs"
                + "5lQs1UlhNGCRTvIsyR8qHUXtA6emw9x0nuMnswtyXhzoA-cEHCArrMxMeWhTYi2l4od3G8Irrvu1Yc5hKLwLgPdnImbKyB5a89T"
                + "vzuZE8-FVyMmhlaJA2T1GpbsaUnfE1ki_bBzqMHTD_Ob7oSVzz2UOiOeL-ombn1X9GbYQ2I-Ob4V84WHONYxw0VjPHlj9saZ2n7"
                + "2RJTBsIo6flJT-MchaEvTYBvuV_wlCCQYjI1g7mdeD6aXfw";
        
        given()
            .header("Authorization", "Bearer " + token)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(3))
            .body(ARGUMENTS + ".list.name", hasItems("INBOX", "Outbox", "Sent"));
    }

    @Test
    public void getMailboxesShouldErrorWithBadJWTToken() {

        String badAuthToken = "BADTOKENOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.T04BTk" +
                "LXkJj24coSZkK13RfG25lpvmSl2MJ7N10KpBk9_-95EGYZdog-BDAn3PJzqVw52z-Bwjh4VOj1-j7cURu0cT4jXehhUrlCxS4n7QHZ" +
                "DN_bsEYGu7KzjWTpTsUiHe-rN7izXVFxDGG1TGwlmBCBnPW-EFCf9ylUsJi0r2BKNdaaPRfMIrHptH1zJBkkUziWpBN1RNLjmvlAUf" +
                "49t1Tbv21ZqYM5Ht2vrhJWczFbuC-TD-8zJkXhjTmA1GVgomIX5dx1cH-dZX1wANNmshUJGHgepWlPU-5VIYxPEhb219RMLJIELMY2" +
                "qNOR8Q31ydinyqzXvCSzVJOf6T60-w";

        given()
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
