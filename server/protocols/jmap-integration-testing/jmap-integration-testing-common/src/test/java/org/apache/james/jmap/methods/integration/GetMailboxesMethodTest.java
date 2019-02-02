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
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ALICE;
import static org.apache.james.jmap.TestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.BOB;
import static org.apache.james.jmap.TestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.TestingConstants.CEDRIC;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.FIRST_MAILBOX;
import static org.apache.james.jmap.TestingConstants.NAME;
import static org.apache.james.jmap.TestingConstants.SECOND_MAILBOX;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.categories.BasicFeature;
import org.apache.james.jmap.model.mailbox.MailboxNamespace;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SerializableQuotaValue;
import org.apache.james.mailbox.probe.ACLProbe;
import org.apache.james.mailbox.probe.QuotaProbe;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.AllMatching;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.restassured.RestAssured;

public abstract class GetMailboxesMethodTest {
    public static final String READ = String.valueOf(Right.Read.asCharacter());
    public static final String LOOKUP = String.valueOf(Right.Lookup.asCharacter());
    public static final String ADMINISTER = String.valueOf(Right.Administer.asCharacter());

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    private AccessToken accessToken;
    private GuiceJamesServer jmapServer;
    private MailboxProbeImpl mailboxProbe;
    private ACLProbe aclProbe;
    private QuotaProbe quotaProbe;

    private Message message;
    
    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        aclProbe = jmapServer.getProbe(ACLProbeImpl.class);
        quotaProbe = jmapServer.getProbe(QuotaProbesImpl.class);

        RestAssured.requestSpecification = jmapRequestSpecBuilder
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(ALICE, ALICE_PASSWORD);
        dataProbe.addUser(BOB, BOB_PASSWORD);
        accessToken = authenticateJamesUser(baseUri(jmapServer), ALICE, ALICE_PASSWORD);

        message = Message.Builder.of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build();
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }
    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullAccountId() {
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("The field 'accountId' of 'GetMailboxesRequest' is not supported"));
    }

    @Test
    public void getMailboxesShouldReturnEmptyWhenIdsDoesntMatch() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "name");
        String removedId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "quicklyRemoved").serialize();
        mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "quicklyRemoved");

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
    public void getMailboxesShouldReturnErrorWhenInvalidMailboxId() {
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

    @Category(BasicFeature.class)
    @Test
    public void getMailboxesShouldReturnMailboxesWhenIdsMatch() {
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX).serialize();
        String mailboxId2 = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "myMailbox").serialize();

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
    public void getMailboxesShouldReturnOnlyMatchingMailboxesWhenIdsGiven() {
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX).serialize();
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "myMailbox");

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
    public void getMailboxesShouldReturnEmptyWhenIdsIsEmpty() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX);

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

    @Category(BasicFeature.class)
    @Test
    public void getMailboxesShouldReturnAllMailboxesWhenIdsIsNull() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "myMailbox");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "myMailbox2");

        List<String> expectedMailboxes = ImmutableList.<String>builder()
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
            .body(ARGUMENTS + ".list", hasSize(8))
            .body(ARGUMENTS + ".list.name", hasItems(expectedMailboxes.toArray()));
    }

    @Test
    public void getMailboxesShouldReturnSharedWithProperty() throws Exception {
        String mailboxName = "myMailbox";
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, mailboxName).serialize();
        String targetUser1 = "toUser1@" + DOMAIN;
        String targetUser2 = "toUser2@" + DOMAIN;

        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), targetUser1, new Rfc4314Rights(Right.Lookup, Right.Administer));
        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), targetUser2, new Rfc4314Rights(Right.Read, Right.Lookup));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + myMailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(mailboxName))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(targetUser1, ImmutableList.of(ADMINISTER, LOOKUP)))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(targetUser2, ImmutableList.of(LOOKUP, READ)));
    }

    @Test
    public void getMailboxesShouldRemoveOwnerRight() throws Exception {
        String mailboxName = "myMailbox";
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, mailboxName).serialize();
        String targetUser1 = "toUser1@" + DOMAIN;

        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), ALICE, new Rfc4314Rights(Right.Read, Right.Administer));
        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), targetUser1, new Rfc4314Rights(Right.Read, Right.Lookup));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + myMailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(mailboxName))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(targetUser1, ImmutableList.of(LOOKUP, READ)));
    }

    @Test
    public void getMailboxShouldReturnEmptySharedWithWhenNoDelegation() {
        String mailboxName = "myMailbox";
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, mailboxName).serialize();

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
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, mailboxName).serialize();
        String targetUser1 = "toUser1@" + DOMAIN;

        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), targetUser1, new Rfc4314Rights(Right.Lookup, Right.Post));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + myMailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(mailboxName))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(targetUser1, ImmutableList.of(LOOKUP)));
    }
    
    @Test
    public void getMailboxesShouldErrorInvalidArgumentsWhenRequestIsInvalid() {
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("Cannot deserialize instance of `java.util.ArrayList` out of VALUE_TRUE token"))
            .body(ARGUMENTS + ".description", containsString("{\"ids\":true}"));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMailboxesShouldReturnDefaultMailboxesWhenAuthenticatedUserDoesntHaveAnAccountYet() {
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
            .body(ARGUMENTS + ".list", hasSize(6))
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "name");

        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "name"), AppendCommand.from(message));

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
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "name").serialize();

        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, "name"), AppendCommand.from(message));

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
            .body(FIRST_MAILBOX + ".mayReadItems", equalTo(true))
            .body(FIRST_MAILBOX + ".mayAddItems", equalTo(true))
            .body(FIRST_MAILBOX + ".mayRemoveItems", equalTo(true))
            .body(FIRST_MAILBOX + ".mayCreateChild", equalTo(true))
            .body(FIRST_MAILBOX + ".mayRename", equalTo(true))
            .body(FIRST_MAILBOX + ".mayDelete", equalTo(true))
            .body(FIRST_MAILBOX + ".totalMessages", equalTo(1))
            .body(FIRST_MAILBOX + ".unreadMessages", equalTo(1))
            .body(FIRST_MAILBOX + ".unreadThreads", equalTo(0));
    }

    @Test
    public void getMailboxesShouldReturnFilteredMailboxesPropertiesWhenRequestContainsFilterProperties() {
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "name").serialize();

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
    public void getMailboxesShouldReturnIdWhenRequestContainsEmptyPropertyListFilter() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "name");

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
    public void getMailboxesShouldIgnoreUnknownPropertiesWhenRequestContainsUnknownPropertyListFilter() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, "name");

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
    public void getMailboxesShouldReturnMailboxesWithSortOrder() {
        MailboxId inboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX);
        MailboxId trashId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.TRASH);

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
    public void getMailboxesShouldReturnMailboxesWithRolesInLowerCase() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.OUTBOX);

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

    @Category(BasicFeature.class)
    @Test
    public void getMailboxesShouldReturnMailboxesWithFilteredSharedWithWhenShared() throws Exception {
        String mailboxName = "name";
        MailboxId bobMailbox = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, mailboxName);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, mailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE, new Rfc4314Rights(Right.Lookup));
        aclProbe.replaceRights(bobMailboxPath, CEDRIC, new Rfc4314Rights(Right.Lookup));

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

        assertThat(sharedWith).containsOnlyKeys(ALICE);
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithFullSharedWithWhenHasAdminRight() throws Exception {
        String mailboxName = "name";
        MailboxId bobMailbox = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, mailboxName);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, mailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE, new Rfc4314Rights(Right.Lookup, Right.Administer));
        aclProbe.replaceRights(bobMailboxPath, CEDRIC, new Rfc4314Rights(Right.Lookup));

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

        assertThat(sharedWith).containsOnlyKeys(ALICE, CEDRIC);
    }

    @Category(BasicFeature.class)
    @Test
    public void getMailboxesShouldReturnAllAccessibleMailboxesWhenEmptyIds() throws Exception {
        String sharedMailboxName = "BobShared";
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, sharedMailboxName);

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE, new Rfc4314Rights(Right.Lookup));

        List<String> expectedMailboxes = ImmutableList.<String>builder()
            .addAll(DefaultMailboxes.DEFAULT_MAILBOXES)
            .add(sharedMailboxName)
            .build();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(expectedMailboxes.size()))
            .body(ARGUMENTS + ".list.name", hasItems(expectedMailboxes.toArray()));
    }

    @Test
    public void getMailboxesShouldFilterMailboxesWithLookupRightWhenEmptyIds() throws Exception {
        String sharedReadMailboxName = "BobShared";
        String sharedAdministerMailboxName = "BobShared1";
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, sharedReadMailboxName);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, sharedAdministerMailboxName);

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX);
        MailboxPath bobSharedReadMailboxPath = MailboxPath.forUser(BOB, sharedReadMailboxName);
        MailboxPath bobSharedAdministerMailboxPath = MailboxPath.forUser(BOB, sharedAdministerMailboxName);

        aclProbe.replaceRights(bobSharedReadMailboxPath, ALICE, new Rfc4314Rights(Right.Lookup));
        aclProbe.replaceRights(bobSharedAdministerMailboxPath, ALICE, new Rfc4314Rights(Right.Administer));

        List<String> expectedMailboxes = ImmutableList.<String>builder()
            .addAll(DefaultMailboxes.DEFAULT_MAILBOXES)
            .add(sharedReadMailboxName)
            .build();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(7))
            .body(ARGUMENTS + ".list.name", hasItems(expectedMailboxes.toArray()));
    }

    @Test
    public void getMailboxesShouldReturnExactUserInbox() throws Exception {
        String mailboxName = "BobShared";
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, mailboxName);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, DefaultMailboxes.INBOX);
        MailboxId aliceInboxMailbox = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, mailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE, new Rfc4314Rights(Right.Lookup));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + aliceInboxMailbox.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list.name", hasItems(MailboxConstants.INBOX))
            .body(ARGUMENTS + ".list.id", hasItems(aliceInboxMailbox.serialize()));
    }

    @Test
    public void getMailboxesShouldReturnSharedMailboxesWithRead() throws Exception {
        String sharedMailboxName = "BobShared";
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, DefaultMailboxes.INBOX);
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, sharedMailboxName);

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE, new Rfc4314Rights(Right.Lookup));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list.id", hasItems(mailboxId.serialize()));
    }

    @Test
    public void getMailboxesShouldReturnDelegatedNamespaceWhenSharedMailbox() throws Exception {
        String sharedMailboxName = "BobShared";
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, sharedMailboxName);

        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE, new Rfc4314Rights(Right.Lookup));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".namespace.type", equalTo(MailboxNamespace.Type.Delegated.toString()))
            .body(FIRST_MAILBOX + ".namespace.owner", equalTo(BOB));
    }

    @Test
    public void getMailboxesShouldReturnPersonalNamespaceWhenOwnerMailbox() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX);

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".namespace.type", equalTo(MailboxNamespace.Type.Personal.toString()))
            .body(FIRST_MAILBOX + ".namespace.owner", isEmptyOrNullString());
    }


    @Test
    public void getMailboxesShouldReturnAllowedForAllMayPropertiesWhenOwner() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX);

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".mayReadItems", equalTo(true))
            .body(FIRST_MAILBOX + ".mayAddItems", equalTo(true))
            .body(FIRST_MAILBOX + ".mayRemoveItems", equalTo(true))
            .body(FIRST_MAILBOX + ".mayCreateChild", equalTo(true))
            .body(FIRST_MAILBOX + ".mayDelete", equalTo(true))
            .body(FIRST_MAILBOX + ".mayRename", equalTo(true));
    }

    @Test
    public void getMailboxesShouldReturnPartiallyAllowedMayPropertiesWhenDelegated() throws Exception {
        String sharedMailboxName = "BobShared";
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, sharedMailboxName);

        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE, new Rfc4314Rights(Right.Lookup, Right.Read));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".mayReadItems", equalTo(true))
            .body(FIRST_MAILBOX + ".mayAddItems", equalTo(false))
            .body(FIRST_MAILBOX + ".mayRemoveItems", equalTo(false))
            .body(FIRST_MAILBOX + ".mayCreateChild", equalTo(false))
            .body(FIRST_MAILBOX + ".mayDelete", equalTo(false))
            .body(FIRST_MAILBOX + ".mayRename", equalTo(false));
    }

    @Test
    public void getMailboxesShouldReturnUnlimitedQuotasForInboxByDefault() {
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX).serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['STORAGE'].max", nullValue())
            .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['MESSAGE'].max", nullValue());
    }

    @Category(BasicFeature.class)
    @Test
    public void getMailboxesShouldReturnMaxStorageQuotasForInboxWhenSet() throws Exception {
        quotaProbe.setGlobalMaxStorage(SerializableQuotaValue.valueOf(Optional.of(QuotaSize.size(42))));
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX).serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['STORAGE'].max", equalTo(42));
    }

    @Test
    public void getMailboxesShouldReturnMaxMessageQuotasForInboxWhenSet() throws Exception {
        quotaProbe.setGlobalMaxMessageCount(SerializableQuotaValue.valueOf(Optional.of(QuotaCount.count(43))));
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX).serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['MESSAGE'].max", equalTo(43));
    }

    @Test
    public void getMailboxesShouldDisplayDifferentMaxQuotaPerMailboxWhenSet() throws Exception {
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX).serialize();
        String sharedMailboxName = "BobShared";
        MailboxId sharedMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB, sharedMailboxName);

        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE, new Rfc4314Rights(Right.Lookup, Right.Read));

        quotaProbe.setMaxMessageCount("#private&alice@domain.tld", SerializableQuotaValue.valueOf(Optional.of(QuotaCount.count(42))));
        quotaProbe.setMaxMessageCount("#private&bob@domain.tld", SerializableQuotaValue.valueOf(Optional.of(QuotaCount.count(43))));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\",\"" + sharedMailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(2))
            .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['MESSAGE'].max", equalTo(42))
            .body(SECOND_MAILBOX + ".quotas['#private&bob@domain.tld']['MESSAGE'].max", equalTo(43));
    }

    @Test
    public void getMailboxesShouldReturnQuotaRootForAllMailboxes() {
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list*.quotas", AllMatching.matcher(hasKey("#private&alice@domain.tld")));
    }

    @Test
    public void getMailboxesShouldReturnEmptyQuotasForInboxWhenNoMail() {
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX).serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['STORAGE'].used", equalTo(0))
            .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['MESSAGE'].used", equalTo(0));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMailboxesShouldReturnUpdatedQuotasForInboxWhenMailReceived() throws Exception {
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, DefaultMailboxes.INBOX).serialize();

        mailboxProbe.appendMessage(ALICE, MailboxPath.forUser(ALICE, DefaultMailboxes.INBOX), AppendCommand.from(message));

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['STORAGE'].used", equalTo(85))
            .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['MESSAGE'].used", equalTo(1));
    }
}
