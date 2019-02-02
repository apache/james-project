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
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.FIRST_MAILBOX;
import static org.apache.james.jmap.TestingConstants.NAME;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.categories.BasicFeature;
import org.apache.james.jmap.categories.CassandraAndElasticSearchCategory;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.restassured.RestAssured;

public abstract class SetMailboxesMethodTest {

    private static final String ADMINISTER = String.valueOf(Right.Administer.asCharacter());
    private static final String WRITE = String.valueOf(Right.Write.asCharacter());
    private static final String DELETE_MESSAGES = String.valueOf(Right.DeleteMessages.asCharacter());

    private static int MAILBOX_NAME_LENGTH_64K = 65536;

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    protected abstract void await();

    private AccessToken accessToken;
    private String username;
    private GuiceJamesServer jmapServer;
    private MailboxProbe mailboxProbe;
    private MailboxId inboxId;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        
        RestAssured.requestSpecification = jmapRequestSpecBuilder
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        username = "username@" + DOMAIN;
        String password = "password";
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(username, password);
        inboxId = mailboxProbe.createMailbox("#private", username, DefaultMailboxes.INBOX);
        accessToken = authenticateJamesUser(baseUri(jmapServer), username, password);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    @Category(CassandraAndElasticSearchCategory.class)
    public void setMailboxesShouldNotCreateWhenOverLimitName() {
        String overLimitName = StringUtils.repeat("a", MAILBOX_NAME_LENGTH_64K);
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"" + overLimitName + "\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasEntry(equalTo("create-id01"), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The mailbox name length is too long")))));
    }

    @Test
    @Category(CassandraAndElasticSearchCategory.class)
    public void setMailboxesShouldNotUpdateMailboxWhenOverLimitName() {
        String overLimitName = StringUtils.repeat("a", MAILBOX_NAME_LENGTH_64K);
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"name\" : \"" + overLimitName + "\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", aMapWithSize(1))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId.serialize()), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The mailbox name length is too long")))));
    }

    @Test
    public void setMailboxesCreateShouldFailWhenOverLimitName() {
        String overLimitName = StringUtils.repeat("a", MailboxManager.MAX_MAILBOX_NAME_LENGTH + 1);
        String creationId = "create-id01";
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"" + creationId + "\" : {" +
                "          \"name\" : \"" + overLimitName + "\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notCreated", hasKey(creationId))
            .body(ARGUMENTS + ".notCreated." + creationId + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".notCreated." + creationId + ".description", is("The mailbox name length is too long"));
    }

    @Test
    public void setMailboxesUpdateShouldFailWhenOverLimitName() throws Exception {
        String overLimitName = StringUtils.repeat("a", MailboxManager.MAX_MAILBOX_NAME_LENGTH + 1);
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"name\" : \"" + overLimitName + "\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";
        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", hasKey(mailboxId.serialize()))
            .body(ARGUMENTS + ".notUpdated." + mailboxId.serialize() + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".notUpdated." + mailboxId.serialize() + ".description", is("The mailbox name length is too long"));

        assertThat(mailboxProbe.listSubscriptions(username)).doesNotContain(overLimitName);
    }

    @Category(BasicFeature.class)
    @Test
    public void userShouldBeSubscribedOnCreatedMailboxWhenCreateMailbox() throws Exception {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".created", hasKey("create-id01"));

        assertThat(mailboxProbe.listSubscriptions(username)).contains("foo");
    }

    @Test
    public void userShouldBeSubscribedOnCreatedMailboxWhenCreateChildOfInboxMailbox() throws Exception {
        MailboxId inboxId = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);

        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"," +
                "          \"parentId\" : \"" + inboxId.serialize() + "\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        assertThat(mailboxProbe.listSubscriptions(username)).contains(DefaultMailboxes.INBOX + ".foo");
    }

    @Test
    public void subscriptionUserShouldBeChangedWhenUpdateMailbox() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "root");

        String initialMailboxName = "root.myBox";
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, initialMailboxName);

        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"name\" : \"mySecondBox\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";
        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        assertThat(mailboxProbe.listSubscriptions(username))
            .contains("mySecondBox")
            .doesNotContain(initialMailboxName);
    }

    @Test
    public void subscriptionUserShouldBeChangedWhenCreateThenUpdateMailboxNameWithJMAP() throws Exception {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".created", hasKey("create-id01"));

        String mailboxId = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, username, "foo").serialize();

        requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
                "          \"name\" : \"mySecondBox\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        assertThat(mailboxProbe.listSubscriptions(username))
            .contains(DefaultMailboxes.OUTBOX,
                DefaultMailboxes.SENT,
                DefaultMailboxes.TRASH,
                DefaultMailboxes.DRAFTS,
                "mySecondBox");
    }

    @Test
    public void subscriptionUserShouldBeDeletedWhenDestroyMailbox() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + mailboxId.serialize() + "\"]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        assertThat(mailboxProbe.listSubscriptions(username))
            .contains(DefaultMailboxes.OUTBOX,
            DefaultMailboxes.SENT,
            DefaultMailboxes.TRASH,
            DefaultMailboxes.DRAFTS);
    }

    @Category(BasicFeature.class)
    @Test
    public void subscriptionUserShouldBeDeletedWhenCreateThenDestroyMailboxWithJMAP() throws Exception {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".created", hasKey("create-id01"));

        MailboxId mailbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, username, "foo");

        requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + mailbox.serialize() + "\"]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        assertThat(mailboxProbe.listSubscriptions(username))
            .contains(DefaultMailboxes.OUTBOX,
                DefaultMailboxes.SENT,
                DefaultMailboxes.TRASH,
                DefaultMailboxes.DRAFTS);
    }

    @Test
    public void setMailboxesShouldErrorNotSupportedWhenRoleGiven() {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"," +
                "          \"role\" : \"Inbox\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("The field 'role' of 'MailboxCreateRequest' is not supported"));
    }

    @Test
    public void setMailboxesShouldErrorNotSupportedWhenSortOrderGiven() {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"," +
                "          \"sortOrder\" : 11" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("The field 'sortOrder' of 'MailboxCreateRequest' is not supported"));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMailboxesShouldReturnCreatedMailbox() {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".created", hasKey("create-id01"));
    }

    @Test
    public void setMailboxesShouldCreateMailbox() {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list.name", hasItem("foo"));
    }

    @Test
    public void setMailboxesShouldReturnCreatedMailboxWhenChildOfInboxMailbox() {
        String inboxId =
            with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMailboxes\", {}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .extract()
                .jsonPath()
                .getString(ARGUMENTS + ".list[0].id");

        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"," +
                "          \"parentId\" : \"" + inboxId + "\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(1))
            .body(ARGUMENTS + ".created", hasEntry(equalTo("create-id01"), Matchers.allOf(
                    hasEntry(equalTo("parentId"), equalTo(inboxId)),
                    hasEntry(equalTo("name"), equalTo("foo")))));
    }

    @Test
    public void setMailboxesShouldCreateMailboxWhenChildOfInboxMailbox() {
        MailboxId inboxId = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);

        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"," +
                "          \"parentId\" : \"" + inboxId.serialize() + "\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list.name", hasItem("foo"));
    }

    @Test
    public void setMailboxesShouldReturnNotCreatedWhenInvalidParentId() {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"foo\"," +
                "          \"parentId\" : \"123\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasEntry(equalTo("create-id01"), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The parent mailbox '123' was not found.")))));
    }

    @Test
    public void setMailboxesShouldReturnCreatedMailboxWhenCreatingParentThenChildMailboxes() {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id00\" : {" +
                "          \"name\" : \"parent\"" +
                "        }," +
                "        \"create-id01\" : {" +
                "          \"name\" : \"child\"," +
                "          \"parentId\" : \"create-id00\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(2))
            .body(ARGUMENTS + ".created", hasEntry(equalTo("create-id00"), Matchers.allOf(
                    hasEntry(equalTo("parentId"), isEmptyOrNullString()),
                    hasEntry(equalTo("name"), equalTo("parent")))))
            .body(ARGUMENTS + ".created", hasEntry(equalTo("create-id01"), Matchers.allOf(
                    hasEntry(equalTo("parentId"), not(isEmptyOrNullString())),
                    hasEntry(equalTo("name"), equalTo("child")))));
    }

    @Test
    public void setMailboxesShouldReturnCreatedMailboxWhenCreatingChildThenParentMailboxes() {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"child\"," +
                "          \"parentId\" : \"create-id00\"" +
                "        }," +
                "        \"create-id00\" : {" +
                "          \"name\" : \"parent\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".created", aMapWithSize(2))
            .body(ARGUMENTS + ".created", hasEntry(equalTo("create-id00"), Matchers.allOf(
                    hasEntry(equalTo("parentId"), isEmptyOrNullString()),
                    hasEntry(equalTo("name"), equalTo("parent")))))
            .body(ARGUMENTS + ".created", hasEntry(equalTo("create-id01"), Matchers.allOf(
                    hasEntry(equalTo("parentId"), not(isEmptyOrNullString())),
                    hasEntry(equalTo("name"), equalTo("child")))));
    }

    @Test
    public void setMailboxesShouldReturnNotCreatedWhenMailboxAlreadyExists() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"myBox\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
            .body(ARGUMENTS + ".notCreated", hasEntry(equalTo("create-id01"), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The mailbox 'create-id01' already exists.")))));
    }

    @Test
    public void setMailboxesShouldReturnNotCreatedWhenCycleDetected() {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"create\": {" +
                "        \"create-id01\" : {" +
                "          \"name\" : \"A\"," +
                "          \"parentId\" : \"create-id02\"" +
                "        }," +
                "        \"create-id02\" : {" +
                "          \"name\" : \"B\"," +
                "          \"parentId\" : \"create-id01\"" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notCreated", aMapWithSize(2))
            .body(ARGUMENTS + ".notCreated", Matchers.allOf(
                    hasEntry(equalTo("create-id01"), Matchers.allOf(
                        hasEntry(equalTo("type"), equalTo("invalidArguments")),
                        hasEntry(equalTo("description"), equalTo("The created mailboxes introduce a cycle.")))),
                    hasEntry(equalTo("create-id02"), Matchers.allOf(
                            hasEntry(equalTo("type"), equalTo("invalidArguments")),
                            hasEntry(equalTo("description"), equalTo("The created mailboxes introduce a cycle."))))
                    ));
    }

    @Test
    public void setMailboxesShouldReturnNotCreatedWhenMailboxNameContainsPathDelimiter() {
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"create\": {" +
                    "        \"create-id01\" : {" +
                    "          \"name\" : \"A.B.C.D\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

            given()
                .header("Authorization", accessToken.serialize())
                .body(requestBody)
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("mailboxesSet"))
                .body(ARGUMENTS + ".notCreated", aMapWithSize(1))
                .body(ARGUMENTS + ".notCreated", hasEntry(equalTo("create-id01"), Matchers.allOf(
                            hasEntry(equalTo("type"), equalTo("invalidArguments")),
                            hasEntry(equalTo("description"), equalTo("The mailbox 'A.B.C.D' contains an illegal character: '.'")))
                        ));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMailboxesShouldReturnDestroyedMailbox() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + mailboxId.serialize() + "\"]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".destroyed", contains(mailboxId.serialize()));
    }

    @Test
    public void setMailboxesShouldDestroyMailbox() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + mailboxId.serialize() + "\"]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list.name", not(hasItem("myBox")));
    }

    @Test
    public void setMailboxesShouldReturnNotDestroyedWhenMailboxDoesntExist() {
        String nonExistantMailboxId = getRemovedMailboxId().serialize();
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + nonExistantMailboxId + "\"]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notDestroyed", aMapWithSize(1))
            .body(ARGUMENTS + ".notDestroyed", hasEntry(equalTo(nonExistantMailboxId), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("notFound")),
                    hasEntry(equalTo("description"), equalTo("The mailbox '" + nonExistantMailboxId + "' was not found.")))));
    }

    @Test
    public void setMailboxesShouldReturnNotDestroyedWhenMailboxHasChild() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox.child");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + mailboxId.serialize() + "\"]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notDestroyed", aMapWithSize(1))
            .body(ARGUMENTS + ".notDestroyed", hasEntry(equalTo(mailboxId.serialize()), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("mailboxHasChild")),
                    hasEntry(equalTo("description"), equalTo("The mailbox '" + mailboxId.serialize() + "' has a child.")))));
    }

    @Test
    public void setMailboxesShouldReturnNotDestroyedWhenSystemMailbox() {
        String mailboxId = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX).serialize();
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + mailboxId + "\"]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notDestroyed", aMapWithSize(1))
            .body(ARGUMENTS + ".notDestroyed", hasEntry(equalTo(mailboxId), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The mailbox '" + mailboxId + "' is a system mailbox.")))));
    }

    @Test
    public void setMailboxesShouldReturnDestroyedWhenParentThenChildMailboxes() {
        MailboxId parentMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "parent");
        MailboxId childMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "parent.child");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + parentMailboxId.serialize() + "\",\"" + childMailboxId.serialize() + "\"]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".destroyed", containsInAnyOrder(parentMailboxId.serialize(), childMailboxId.serialize()));
    }

    @Test
    public void setMailboxesShouldReturnDestroyedWhenChildThenParentMailboxes() {
        MailboxId parentMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "parent");
        MailboxId childMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "parent.child");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + childMailboxId.serialize() + "\",\"" + parentMailboxId.serialize() + "\"]" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".destroyed", containsInAnyOrder(parentMailboxId.serialize(), childMailboxId.serialize()));
    }

    private MailboxId getRemovedMailboxId() {
        MailboxId removedId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved");
        mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved");
        return removedId;
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenUnknownMailbox() {
        String unknownMailboxId = getRemovedMailboxId().serialize();
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + unknownMailboxId + "\" : {" +
                    "          \"name\" : \"yolo\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(unknownMailboxId), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("notFound")),
                    hasEntry(equalTo("description"), containsString(unknownMailboxId)))));
    }

    @Test
    public void setMailboxesShouldReturnUpdatedMailboxIdWhenNoUpdateAskedOnExistingMailbox() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId.serialize()));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMailboxesShouldReturnUpdatedWhenNameUpdateAskedOnExistingMailbox() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"myRenamedBox\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId.serialize()));
    }

    @Test
    public void updateShouldReturnOkWhenClearingSharedWith() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId.serialize()));
    }

    @Test
    public void updateShouldReturnOkWhenSettingNewACL() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"user@" + DOMAIN + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId.serialize()));
    }

    @Test
    public void updateShouldRejectInvalidRights() {
        String mailboxId = jmapServer.getProbe(MailboxProbeImpl.class)
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox")
            .serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
                "          \"sharedWith\" : {\"user\": [\"aw\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
        .when()
            .post("/jmap").prettyPeek()
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("Rights should be represented as single value characters"));
    }

    @Test
    public void updateShouldRejectUnhandledRight() {
        String mailboxId = jmapServer.getProbe(MailboxProbeImpl.class)
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox")
            .serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
                "          \"sharedWith\" : {\"user\": [\"p\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("No matching right for 'p'"));
    }

    @Test
    public void updateShouldRejectNonExistingRights() {
        String mailboxId = jmapServer.getProbe(MailboxProbeImpl.class)
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox")
            .serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
                "          \"sharedWith\" : {\"user\": [\"z\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", is("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("No matching right for 'z'"));
    }

    @Test
    public void updateShouldApplyWhenSettingNewACL() {
        String myBox = "myBox";
        String user = "user@" + DOMAIN;
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, myBox);
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"" + user + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(myBox))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(user, ImmutableList.of(ADMINISTER, WRITE)));
    }

    @Category(BasicFeature.class)
    @Test
    public void updateShouldModifyStoredDataWhenUpdatingACL() {
        String myBox = "myBox";
        String user = "user@" + DOMAIN;
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, myBox);

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"" + user + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
            .post("/jmap");

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"" + user + "\": [\"a\", \"t\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(myBox))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(user, ImmutableList.of(ADMINISTER, DELETE_MESSAGES)));
    }

    @Test
    public void updateShouldClearStoredDataWhenDeleteACL() {
        String myBox = "myBox";
        String user = "user";
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, myBox);

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"" + user + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
            .post("/jmap");

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(myBox))
            .body(FIRST_MAILBOX + ".sharedWith", is(ImmutableMap.of()));
    }

    @Test
    public void updateShouldModifyStoredDataWhenSwitchingACLUser() {
        String myBox = "myBox";
        String user1 = "user1@" + DOMAIN;
        String user2 = "user2@" + DOMAIN;
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, myBox);

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"" + user1 + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
            .post("/jmap");

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"" + user2 + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(myBox))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(user2, ImmutableList.of(ADMINISTER, WRITE)));
    }

    @Test
    public void updateShouldFilterOwnerACL() throws Exception {
        String myBox = "myBox";
        String user2 = "user2";
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, myBox);

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"" + username + "\": [\"a\", \"w\"]," +
                "                            \"" + user2 + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
            .post("/jmap");

        MailboxACL acl = jmapServer.getProbe(ACLProbeImpl.class)
            .retrieveRights(MailboxPath.forUser(username, myBox));

        assertThat(acl.getEntries())
            .doesNotContainKeys(MailboxACL.EntryKey.createUserEntryKey(username));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxNameWhenNameUpdateAskedOnExistingMailbox() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"myRenamedBox\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].name", equalTo("myRenamedBox"));
    }

    @Category(BasicFeature.class)
    @Test
    public void setMailboxesShouldReturnMailboxIdWhenMovingToAnotherParentMailbox() {
        MailboxId mailboxId = mailboxProbe
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");

        MailboxId chosenMailboxParentId = mailboxProbe
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myChosenParentBox");
        
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"parentId\" : \"" + chosenMailboxParentId.serialize() + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId.serialize()));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxParentIdWhenMovingToAnotherParentMailbox() {
        MailboxId mailboxId = mailboxProbe
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");

        MailboxId newParentMailboxId = mailboxProbe
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"parentId\" : \"" + newParentMailboxId.serialize() + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].parentId", equalTo(newParentMailboxId.serialize()));
    }

    @Test
    public void setMailboxesShouldReturnMailboxIdWhenParentIdUpdateAskedOnExistingMailbox() {
        mailboxProbe
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        MailboxId mailboxId = mailboxProbe
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");

        MailboxId newParentMailboxId = mailboxProbe
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"parentId\" : \"" + newParentMailboxId.serialize() + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId.serialize()));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxParentIdWhenParentIdUpdateAskedOnExistingMailbox() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");

        MailboxId newParentMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"parentId\" : \"" + newParentMailboxId.serialize() + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].parentId", equalTo(newParentMailboxId.serialize()));
    }

    @Test
    public void setMailboxesShouldReturnMailboxIdWhenParentIdUpdateAskedAsOrphanForExistingMailbox() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"parentId\" : null" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId.serialize()));
    }

    @Test
    public void setMailboxesShouldUpdateParentIdWhenAskedAsOrphanForExistingMailbox() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"parentId\" : null" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].parentId", nullValue());
    }

    @Test
    public void setMailboxesShouldReturnMailboxIdWhenNameAndParentIdUpdateForExistingMailbox() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        MailboxId newParentMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"myRenamedBox\", " +
                    "          \"parentId\" : \"" + newParentMailboxId.serialize() + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId.serialize()));
    }

    @Test
    public void setMailboxesShoulUpdateMailboxIAndParentIddWhenBothUpdatedForExistingMailbox() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        MailboxId newParentMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"myRenamedBox\", " +
                    "          \"parentId\" : \"" + newParentMailboxId.serialize() + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].parentId",equalTo(newParentMailboxId.serialize()))
            .body(ARGUMENTS + ".list[0].name",equalTo("myRenamedBox"));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenNameContainsPathDelimiter() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"my.Box\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId.serialize()), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The mailbox 'my.Box' contains an illegal character: '.'"))))); 
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenNewParentDoesntExist() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String badParentId = getRemovedMailboxId().serialize();
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"parentId\" : \"" + badParentId + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId.serialize()), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("notFound")),
                    hasEntry(equalTo("description"), containsString(badParentId)))));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenUpdatingParentIdOfAParentMailbox() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "root");

        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox.child");
        MailboxId newParentMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");



        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"parentId\" : \"" + newParentMailboxId.serialize() + "\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId.serialize()), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("Cannot update a parent mailbox."))))); 
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenRenamingAMailboxToAnAlreadyExistingMailbox() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "mySecondBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"mySecondBox\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId.serialize()), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("Cannot rename a mailbox to an already existing mailbox."))))); 
    }

    @Test
    public void setMailboxesShouldReturnUpdatedWhenRenamingAChildMailbox() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "root");
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"mySecondBox\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId.serialize()));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxNameWhenRenamingAChildMailbox() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "root");
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"mySecondBox\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        with()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].name", equalTo("mySecondBox"));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenRenamingSystemMailbox() {
        String mailboxId = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX).serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"name\" : \"renamed\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("Cannot update a system mailbox.")))));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenRenameToSystemMailboxName() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId.serialize() + "\" : {" +
                    "          \"name\" : \"Outbox\"" +
                    "        }" +
                    "      }" +
                    "    }," +
                    "    \"#0\"" +
                    "  ]" +
                    "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId.serialize()), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The mailbox 'Outbox' is a system mailbox.")))));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedErrorWhenMovingMailboxTriggersNameConflict() {
        MailboxId mailboxRootAId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "A");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "A.B");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "A.C");
        MailboxId mailboxChildToMoveCId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "A.B.C");

        String requestBody =
              "[" +
                  "  [ \"setMailboxes\"," +
                  "    {" +
                  "      \"update\": {" +
                  "        \"" + mailboxChildToMoveCId.serialize() + "\" : {" +
                  "          \"parentId\" : \"" + mailboxRootAId.serialize() + "\"" +
                  "        }" +
                  "      }" +
                  "    }," +
                  "    \"#0\"" +
                  "  ]" +
                  "]";

      given()
          .header("Authorization", accessToken.serialize())
          .body(requestBody)
      .when()
          .post("/jmap")
      .then()
          .statusCode(200)
          .body(NAME, equalTo("mailboxesSet"))
          .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxChildToMoveCId.serialize()), Matchers.allOf(
                  hasEntry(equalTo("type"), equalTo("invalidArguments")),
                  hasEntry(equalTo("description"), equalTo("Cannot rename a mailbox to an already existing mailbox.")))));
    }

    @Test
    public void setMailboxesShouldReturnUpdatedWhenShareSystemMailbox() {
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + inboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"user@" + DOMAIN + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(inboxId.serialize()));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenShareOutboxMailbox() {
        MailboxId outboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, DefaultMailboxes.OUTBOX);
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + outboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"user@" + DOMAIN + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", aMapWithSize(1))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(outboxId.serialize()), Matchers.allOf(
                hasEntry(equalTo("type"), equalTo("invalidArguments")),
                hasEntry(equalTo("description"), equalTo("Sharing 'Outbox' is forbidden")))));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenShareDraftMailbox() {
        MailboxId draftId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, DefaultMailboxes.DRAFTS);
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + draftId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"user@" + DOMAIN + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".notUpdated", aMapWithSize(1))
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(draftId.serialize()), Matchers.allOf(
                hasEntry(equalTo("type"), equalTo("invalidArguments")),
                hasEntry(equalTo("description"), equalTo("Sharing 'Draft' is forbidden")))));
    }
}
