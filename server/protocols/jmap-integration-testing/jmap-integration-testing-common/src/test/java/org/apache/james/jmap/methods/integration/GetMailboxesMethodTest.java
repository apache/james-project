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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.mail.Flags;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.DefaultMailboxes;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.probe.ACLProbe;
import org.apache.james.mailbox.store.probe.MailboxProbe;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public abstract class GetMailboxesMethodTest {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String FIRST_MAILBOX = ARGUMENTS + ".list[0]";

    public static final String READ = String.valueOf(Right.Read.asCharacter());
    public static final String LOOKUP = String.valueOf(Right.Lookup.asCharacter());
    public static final String ADMINISTER = String.valueOf(Right.Administer.asCharacter());

    protected abstract GuiceJamesServer createJmapServer();

    private AccessToken accessToken;
    private String alice;
    private String bob;
    private String cedric;
    private GuiceJamesServer jmapServer;
    private MailboxProbe mailboxProbe;
    private ACLProbe aclProbe;
    
    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        aclProbe = jmapServer.getProbe(ACLProbeImpl.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8)))
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        String domain = "domain.tld";
        alice = "alice@" + domain;
        String alicePassword = "aliceSecret";
        bob = "bob@" + domain;
        cedric = "cedric@" + domain;
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(domain);
        dataProbe.addUser(alice, alicePassword);
        dataProbe.addUser(bob, "bobSecret");
        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), alice, alicePassword);
    }

    private URIBuilder baseUri() {
        return new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class)
                .getJmapPort())
            .setCharset(Charsets.UTF_8);
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "name");
        String removedId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "quicklyRemoved").serialize();
        mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, alice, "quicklyRemoved");

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
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, DefaultMailboxes.INBOX).serialize();
        String mailboxId2 = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "myMailbox").serialize();

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
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, DefaultMailboxes.INBOX).serialize();
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "myMailbox");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".id", equalTo(mailboxId));
    }

    @Test
    public void getMailboxesShouldReturnEmptyWhenIdsIsEmpty() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, DefaultMailboxes.INBOX);

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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "myMailbox");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "myMailbox2");

        List<String> expectedMailboxes = ImmutableList.<String> builder()
                .addAll(DefaultMailboxes.DEFAULT_MAILBOXES)
                .add("myMailbox")
                .add("myMailbox2")
                .build();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": null}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(7))
            .body(ARGUMENTS + ".list.name", hasItems(expectedMailboxes.toArray()));
    }

    @Test
    public void getMailboxesShouldReturnSharedWithProperty() throws Exception {
        String mailboxName = "myMailbox";
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, mailboxName).serialize();
        String targetUser1 = "toUser1@domain.com";
        String targetUser2 = "toUser2@domain.com";
        Mailbox myMailbox = mailboxProbe.getMailbox(MailboxConstants.USER_NAMESPACE, alice, mailboxName);
        aclProbe.replaceRights(myMailbox.generateAssociatedPath(), targetUser1, new Rfc4314Rights(Right.Read, Right.Administer));
        aclProbe.replaceRights(myMailbox.generateAssociatedPath(), targetUser2, new Rfc4314Rights(Right.Read, Right.Lookup));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + myMailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(mailboxName))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(targetUser1, ImmutableList.of(ADMINISTER, READ)))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(targetUser2, ImmutableList.of(LOOKUP, READ)));
    }

    @Test
    public void getMailboxShouldReturnEmptySharedWithWhenNoDelegation() throws Exception {
        String mailboxName = "myMailbox";
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, mailboxName).serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + myMailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(mailboxName))
            .body(FIRST_MAILBOX + ".sharedWith", is(ImmutableMap.of()));
    }

    @Test
    public void nonHandledRightsShouldBeFilteredOut() throws Exception {
        String mailboxName = "myMailbox";
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, mailboxName).serialize();
        String targetUser1 = "toUser1@domain.com";
        Mailbox myMailbox = mailboxProbe.getMailbox(MailboxConstants.USER_NAMESPACE, alice, mailboxName);
        aclProbe.replaceRights(myMailbox.generateAssociatedPath(), targetUser1, new Rfc4314Rights(Right.Read, Right.Post));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + myMailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(mailboxName))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(targetUser1, ImmutableList.of(READ)));
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
            .body(ARGUMENTS + ".list", hasSize(5))
            .body(ARGUMENTS + ".list.name", hasItems(DefaultMailboxes.DEFAULT_MAILBOXES.toArray()));
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "name");

        mailboxProbe.appendMessage(alice, MailboxPath.forUser(alice, "name"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list.name", hasItem("name"));
    }

    @Test
    public void getMailboxesShouldReturnMailboxPropertiesWhenAvailable() throws Exception {
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "name").serialize();

        mailboxProbe.appendMessage(alice, MailboxPath.forUser(alice, "name"),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + myMailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list.name", hasItem("name"))
            .body(FIRST_MAILBOX + ".parentId", nullValue())
            .body(FIRST_MAILBOX + ".role", nullValue())
            .body(FIRST_MAILBOX + ".sortOrder", equalTo(1000))
            .body(FIRST_MAILBOX + ".mustBeOnlyMailbox", equalTo(false))
            .body(FIRST_MAILBOX + ".mayReadItems", equalTo(false))
            .body(FIRST_MAILBOX + ".mayAddItems", equalTo(false))
            .body(FIRST_MAILBOX + ".mayRemoveItems", equalTo(false))
            .body(FIRST_MAILBOX + ".mayCreateChild", equalTo(false))
            .body(FIRST_MAILBOX + ".mayRename", equalTo(false))
            .body(FIRST_MAILBOX + ".mayDelete", equalTo(false))
            .body(FIRST_MAILBOX + ".totalMessages", equalTo(1))
            .body(FIRST_MAILBOX + ".unreadMessages", equalTo(1))
            .body(FIRST_MAILBOX + ".unreadThreads", equalTo(0));
    }

    @Test
    public void getMailboxesShouldReturnFilteredMailboxesPropertiesWhenRequestContainsFilterProperties() throws Exception {
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "name").serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + myMailboxId + "\"], \"properties\" : [\"unreadMessages\", \"sortOrder\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".id", not(isEmptyOrNullString()))
            .body(FIRST_MAILBOX + ".name", nullValue())
            .body(FIRST_MAILBOX + ".parentId", nullValue())
            .body(FIRST_MAILBOX + ".role", nullValue())
            .body(FIRST_MAILBOX + ".sortOrder", equalTo(1000))
            .body(FIRST_MAILBOX + ".mustBeOnlyMailbox", nullValue())
            .body(FIRST_MAILBOX + ".mayReadItems", nullValue())
            .body(FIRST_MAILBOX + ".mayAddItems", nullValue())
            .body(FIRST_MAILBOX + ".mayRemoveItems", nullValue())
            .body(FIRST_MAILBOX + ".mayCreateChild", nullValue())
            .body(FIRST_MAILBOX + ".mayRename", nullValue())
            .body(FIRST_MAILBOX + ".mayDelete", nullValue())
            .body(FIRST_MAILBOX + ".totalMessages", nullValue())
            .body(FIRST_MAILBOX + ".unreadMessages", equalTo(0))
            .body(FIRST_MAILBOX + ".unreadThreads", nullValue());
    }

    @Test
    public void getMailboxesShouldReturnIdWhenRequestContainsEmptyPropertyListFilter() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "name");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"properties\" : []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".id", not(isEmptyOrNullString()))
            .body(FIRST_MAILBOX + ".name", nullValue());
    }

    @Test
    public void getMailboxesShouldIgnoreUnknownPropertiesWhenRequestContainsUnknownPropertyListFilter() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, "name");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"properties\" : [\"unknown\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".id", not(isEmptyOrNullString()))
            .body(FIRST_MAILBOX + ".name", nullValue());
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithSortOrder() throws Exception {
        MailboxId inboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, DefaultMailboxes.INBOX);
        MailboxId trashId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, DefaultMailboxes.TRASH);

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + inboxId.serialize() + "\", \"" + trashId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(2))
            .body(ARGUMENTS + ".list[0].name", equalTo(DefaultMailboxes.INBOX))
            .body(ARGUMENTS + ".list[0].sortOrder", equalTo(10))
            .body(ARGUMENTS + ".list[1].name", equalTo(DefaultMailboxes.TRASH))
            .body(ARGUMENTS + ".list[1].sortOrder", equalTo(60));
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithRolesInLowerCase() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, DefaultMailboxes.OUTBOX);

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".role", equalTo(DefaultMailboxes.OUTBOX.toLowerCase(Locale.US)));
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithFilteredSharedWithWhenShared() throws Exception {
        String mailboxName = "name";
        MailboxId bobMailbox = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, bob, mailboxName);
        MailboxPath bobMailboxPath = MailboxPath.forUser(bob, mailboxName);
        aclProbe.replaceRights(bobMailboxPath, alice, new Rfc4314Rights(Right.Read));
        aclProbe.replaceRights(bobMailboxPath, cedric, new Rfc4314Rights(Right.Read));

        Map<String, String> sharedWith = given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + bobMailbox.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list.name", hasSize(1))
        .extract()
            .jsonPath()
            .get(FIRST_MAILBOX + ".sharedWith");

        assertThat(sharedWith).containsOnlyKeys(alice);
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithFullSharedWithWhenHasAdminRight() throws Exception {
        String mailboxName = "name";
        MailboxId bobMailbox = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, bob, mailboxName);
        MailboxPath bobMailboxPath = MailboxPath.forUser(bob, mailboxName);
        aclProbe.replaceRights(bobMailboxPath, alice, new Rfc4314Rights(Right.Read, Right.Administer));
        aclProbe.replaceRights(bobMailboxPath, cedric, new Rfc4314Rights(Right.Read));

        Map<String, String> sharedWith = given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + bobMailbox.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list.name", hasSize(1))
        .extract()
            .jsonPath()
            .get(FIRST_MAILBOX + ".sharedWith");

        assertThat(sharedWith).containsOnlyKeys(alice, cedric);
    }
}
