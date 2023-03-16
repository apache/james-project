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

package org.apache.james.jmap.draft.methods.integration;

import static io.restassured.RestAssured.given;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.FIRST_MAILBOX;
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.SECOND_MAILBOX;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.jmap.draft.model.mailbox.MailboxNamespace;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.ACLProbe;
import org.apache.james.mailbox.probe.QuotaProbe;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.AllMatching;
import org.apache.james.utils.DataProbeImpl;
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
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
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
            .header("Authorization", accessToken.asString())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "name");
        String removedId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "quicklyRemoved").serialize();
        mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "quicklyRemoved");

        given()
            .header("Authorization", accessToken.asString())
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
            .header("Authorization", accessToken.asString())
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
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX).serialize();
        String mailboxId2 = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "myMailbox").serialize();

        given()
            .header("Authorization", accessToken.asString())
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
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX).serialize();
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "myMailbox");

        given()
            .header("Authorization", accessToken.asString())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX);

        given()
            .header("Authorization", accessToken.asString())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "myMailbox");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "myMailbox2");

        List<String> expectedMailboxes = ImmutableList.<String>builder()
                .addAll(DefaultMailboxes.DEFAULT_MAILBOXES)
                .add("myMailbox")
                .add("myMailbox2")
                .build();

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"ids\": null}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(expectedMailboxes.size()))
            .body(ARGUMENTS + ".list.name", hasItems(expectedMailboxes.toArray()));
    }

    @Test
    public void getMailboxesShouldReturnSharedWithProperty() throws Exception {
        String mailboxName = "myMailbox";
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), mailboxName).serialize();
        String targetUser1 = "touser1@" + DOMAIN;
        String targetUser2 = "touser2@" + DOMAIN;

        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), targetUser1, new Rfc4314Rights(Right.Lookup, Right.Administer));
        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), targetUser2, new Rfc4314Rights(Right.Read, Right.Lookup));

        given()
            .header("Authorization", accessToken.asString())
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
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), mailboxName).serialize();
        String targetUser1 = "touser1@" + DOMAIN;

        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), ALICE.asString(), new Rfc4314Rights(Right.Read, Right.Administer));
        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), targetUser1, new Rfc4314Rights(Right.Read, Right.Lookup));

        given()
            .header("Authorization", accessToken.asString())
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
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), mailboxName).serialize();

        given()
            .header("Authorization", accessToken.asString())
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
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), mailboxName).serialize();
        String targetUser1 = "touser1@" + DOMAIN;

        aclProbe.replaceRights(MailboxPath.forUser(ALICE, mailboxName), targetUser1, new Rfc4314Rights(Right.Lookup, Right.Post));

        given()
            .header("Authorization", accessToken.asString())
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
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"ids\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("Cannot deserialize value of type `java.util.ArrayList<org.apache.james.mailbox.model.MailboxId>` from Boolean value (token `JsonToken.VALUE_TRUE`)"))
            .body(ARGUMENTS + ".description", containsString("(through reference chain: org.apache.james.jmap.draft.model.GetMailboxesRequest$Builder[\"ids\"])"));
    }

    @Category(BasicFeature.class)
    @Test
    public void getMailboxesShouldReturnDefaultMailboxesWhenAuthenticatedUserDoesntHaveAnAccountYet() {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMzM3QGRvbWFpbi50bGQiLCJuYW1lIjoiTmV3IFVzZXIif" +
            "Q.bPeoAWAOyu4teilj27cL4uZ9jBO9zJOus3mJdqZYEeDe9OZeweopqkIE7ljuh-iTxBDE-f3OqaEqGa_mi6eOYT7XXAEPolcEAe4EooU" +
            "lI8anYMrSlbMPBKGymH-QTWIcPS5P8XNFnIn94XheuEIXeaRHNvO5-SgENbFYS19kgyzOV54umQtIAewLyMdvG6aooLSk6lEy3AhVPIfl" +
            "QJm6MLOsNzHMj7F7mM0Wm6QBfYeWIHzQLtEgGFFLyqeSXyzbgbbec2WYpVjXHNyWhAFcq7OMjOb2UEmb9gKr1fkL_XZgXna2CW46hzwaG" +
            "jNO-6ZWpHjLfDEP7oHui51Hn_wJfw";

        given()
            .header("Authorization", "Bearer " + token)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(DefaultMailboxes.DEFAULT_MAILBOXES.size()))
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "name");

        mailboxProbe.appendMessage(ALICE.asString(), MailboxPath.forUser(ALICE, "name"), AppendCommand.from(message));

        given()
            .header("Authorization", accessToken.asString())
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
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "name").serialize();

        mailboxProbe.appendMessage(ALICE.asString(), MailboxPath.forUser(ALICE, "name"), AppendCommand.from(message));

        given()
            .header("Authorization", accessToken.asString())
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
        String myMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "name").serialize();

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + myMailboxId + "\"], \"properties\" : [\"unreadMessages\", \"sortOrder\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".id", not(is(nullValue())))
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "name");

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"properties\" : []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".id", not(is(nullValue())))
            .body(FIRST_MAILBOX + ".name", nullValue());
    }

    @Test
    public void getMailboxesShouldIgnoreUnknownPropertiesWhenRequestContainsUnknownPropertyListFilter() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "name");

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"properties\" : [\"unknown\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".id", not(is(nullValue())))
            .body(FIRST_MAILBOX + ".name", nullValue());
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithSortOrder() {
        MailboxId inboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX);
        MailboxId trashId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.TRASH);

        given()
            .header("Authorization", accessToken.asString())
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
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.OUTBOX);

        given()
            .header("Authorization", accessToken.asString())
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
        MailboxId bobMailbox = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), mailboxName);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, mailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup));
        aclProbe.replaceRights(bobMailboxPath, CEDRIC.asString(), new Rfc4314Rights(Right.Lookup));

        Map<String, String> sharedWith = given()
            .header("Authorization", accessToken.asString())
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

        assertThat(sharedWith).containsOnlyKeys(ALICE.asString());
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithFullSharedWithWhenHasAdminRight() throws Exception {
        String mailboxName = "name";
        MailboxId bobMailbox = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), mailboxName);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, mailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup, Right.Administer));
        aclProbe.replaceRights(bobMailboxPath, CEDRIC.asString(), new Rfc4314Rights(Right.Lookup));

        Map<String, String> sharedWith = given()
            .header("Authorization", accessToken.asString())
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

        assertThat(sharedWith).containsOnlyKeys(ALICE.asString(), CEDRIC.asString());
    }

    @Category(BasicFeature.class)
    @Test
    public void getMailboxesShouldReturnAllAccessibleMailboxesWhenEmptyIds() throws Exception {
        String sharedMailboxName = "BobShared";
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), sharedMailboxName);

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup));

        List<String> expectedMailboxes = ImmutableList.<String>builder()
            .addAll(DefaultMailboxes.DEFAULT_MAILBOXES)
            .add(sharedMailboxName)
            .build();

        given()
            .header("Authorization", accessToken.asString())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), sharedReadMailboxName);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), sharedAdministerMailboxName);

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX);
        MailboxPath bobSharedReadMailboxPath = MailboxPath.forUser(BOB, sharedReadMailboxName);
        MailboxPath bobSharedAdministerMailboxPath = MailboxPath.forUser(BOB, sharedAdministerMailboxName);

        aclProbe.replaceRights(bobSharedReadMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup));
        aclProbe.replaceRights(bobSharedAdministerMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Administer));

        List<String> expectedMailboxes = ImmutableList.<String>builder()
            .addAll(DefaultMailboxes.DEFAULT_MAILBOXES)
            .add(sharedReadMailboxName)
            .build();

        given()
            .header("Authorization", accessToken.asString())
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
    public void getMailboxesShouldReturnExactUserInbox() throws Exception {
        String mailboxName = "BobShared";
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), mailboxName);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), DefaultMailboxes.INBOX);
        MailboxId aliceInboxMailbox = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, mailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup));

        given()
            .header("Authorization", accessToken.asString())
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
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), DefaultMailboxes.INBOX);
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), sharedMailboxName);

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX);
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup));

        given()
            .header("Authorization", accessToken.asString())
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
    public void getMailboxesShouldNotExposeRoleOfSharedMailboxToSharee() throws Exception {
        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, DefaultMailboxes.INBOX);
        MailboxId mailboxId = mailboxProbe.createMailbox(bobMailboxPath);

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX);

        aclProbe.replaceRights(bobMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup));

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(FIRST_MAILBOX + ".role", nullValue())
            .body(FIRST_MAILBOX + ".sortOrder", equalTo(1000));
    }

    @Test
    public void getMailboxesShouldReturnDelegatedNamespaceWhenSharedMailbox() throws Exception {
        String sharedMailboxName = "BobShared";
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), sharedMailboxName);

        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup));

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".namespace.type", equalTo(MailboxNamespace.Type.Delegated.toString()))
            .body(FIRST_MAILBOX + ".namespace.owner", equalTo(BOB.asString()));
    }

    @Test
    public void getMailboxesShouldReturnPersonalNamespaceWhenOwnerMailbox() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX);

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(FIRST_MAILBOX + ".namespace.type", equalTo(MailboxNamespace.Type.Personal.toString()))
            .body(FIRST_MAILBOX + ".namespace.owner", is(nullValue()));
    }


    @Test
    public void getMailboxesShouldReturnAllowedForAllMayPropertiesWhenOwner() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX);

        given()
            .header("Authorization", accessToken.asString())
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
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), sharedMailboxName);

        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup, Right.Read));

        given()
            .header("Authorization", accessToken.asString())
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
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX).serialize();

        given()
            .header("Authorization", accessToken.asString())
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
        quotaProbe.setGlobalMaxStorage(QuotaSizeLimit.size(42));
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX).serialize();

        given()
            .header("Authorization", accessToken.asString())
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
        quotaProbe.setGlobalMaxMessageCount(QuotaCountLimit.count(43));
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX).serialize();

        given()
            .header("Authorization", accessToken.asString())
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
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX).serialize();
        String sharedMailboxName = "BobShared";
        MailboxId sharedMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, BOB.asString(), sharedMailboxName);

        MailboxPath bobMailboxPath = MailboxPath.forUser(BOB, sharedMailboxName);
        aclProbe.replaceRights(bobMailboxPath, ALICE.asString(), new Rfc4314Rights(Right.Lookup, Right.Read));

        quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(MailboxPath.inbox(ALICE)), QuotaCountLimit.count(42));
        quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(MailboxPath.inbox(BOB)), QuotaCountLimit.count(43));

        given()
            .header("Authorization", accessToken.asString())
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
            .header("Authorization", accessToken.asString())
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
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX).serialize();

        given()
            .header("Authorization", accessToken.asString())
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
        String mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), DefaultMailboxes.INBOX).serialize();

        mailboxProbe.appendMessage(ALICE.asString(), MailboxPath.forUser(ALICE, DefaultMailboxes.INBOX), AppendCommand.from(message));

        calmlyAwait.untilAsserted(() ->
            given()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("mailboxes"))
                .body(ARGUMENTS + ".list", hasSize(1))
                .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['STORAGE'].used", equalTo(85))
                .body(FIRST_MAILBOX + ".quotas['#private&alice@domain.tld']['MESSAGE'].used", equalTo(1)));
    }
}
