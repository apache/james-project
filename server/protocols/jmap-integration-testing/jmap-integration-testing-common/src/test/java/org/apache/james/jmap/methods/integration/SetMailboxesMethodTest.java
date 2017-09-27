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
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.DefaultMailboxes;
import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public abstract class SetMailboxesMethodTest {

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String FIRST_MAILBOX = ARGUMENTS + ".list[0]";
    private static final String USERS_DOMAIN = "domain.tld";

    private static final String ADMINISTER = String.valueOf(Right.Administer.asCharacter());
    private static final String WRITE = String.valueOf(Right.Write.asCharacter());
    private static final String DELETE_MESSAGES = String.valueOf(Right.DeleteMessages.asCharacter());

    private static int MAILBOX_NAME_LENGTH_64K = 65536;

    protected abstract GuiceJamesServer createJmapServer();

    protected abstract void await();

    private AccessToken accessToken;
    private String username;
    private GuiceJamesServer jmapServer;
    private MailboxProbe mailboxProbe;
    private DataProbe dataProbe;
    
    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8)))
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        username = "username@" + USERS_DOMAIN;
        String password = "password";
        dataProbe.addDomain(USERS_DOMAIN);
        dataProbe.addUser(username, password);
        mailboxProbe.createMailbox("#private", username, DefaultMailboxes.INBOX);
        accessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), username, password);

        await();
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
                    hasEntry(equalTo("description"), equalTo("The mailbox name length is too long")))
            ));
    }

    @Test
    public void setMailboxesShouldNotUpdateMailboxWhenOverLimitName() {
        String overLimitName = StringUtils.repeat("a", MAILBOX_NAME_LENGTH_64K);
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
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
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The mailbox name length is too long")))
            ));
    }

    @Test
    public void setMailboxesShouldCreateWhenOverLimitName() throws Exception {
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
            .body(ARGUMENTS + ".created", hasKey("create-id01"));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxWhenOverLimitName() throws Exception {
        String overLimitName = StringUtils.repeat("a", MAILBOX_NAME_LENGTH_64K);
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
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
            .body(ARGUMENTS + ".updated", contains(mailboxId));

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class).listSubscriptions(username)).containsOnly(overLimitName);
    }

    @Test
    public void userShouldBeSubscribedOnCreatedMailboxWhenCreateMailbox() throws Exception{
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

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class).listSubscriptions(username)).containsOnly("foo");
    }

    @Test
    public void userShouldBeSubscribedOnCreatedMailboxWhenCreateChildOfInboxMailbox() throws Exception {
        MailboxId inboxId = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX).getMailboxId();

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

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class).listSubscriptions(username)).containsOnly(DefaultMailboxes.INBOX + ".foo");
    }

    @Test
    public void subscriptionUserShouldBeChangedWhenUpdateMailbox() throws Exception {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "root");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        String requestBody =
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

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class).listSubscriptions(username)).containsOnly("mySecondBox");
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

        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "foo");
        String mailboxId = mailbox.getMailboxId().serialize();

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

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class).listSubscriptions(username)).containsOnly("mySecondBox");
    }

    @Test
    public void subscriptionUserShouldBeDeletedWhenDestroyMailbox() throws Exception {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + mailbox.getMailboxId().serialize() + "\"]" +
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

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class).listSubscriptions(username)).isEmpty();
    }

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

        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "foo");

        requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + mailbox.getMailboxId().serialize() + "\"]" +
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

        assertThat(jmapServer.getProbe(MailboxProbeImpl.class).listSubscriptions(username)).isEmpty();
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
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
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
            .body(ARGUMENTS + ".type", equalTo("Not yet implemented"));
    }

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
            .then()
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
        MailboxId inboxId = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX).getMailboxId();

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
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
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

    @Test
    public void setMailboxesShouldReturnDestroyedMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
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
            .body(ARGUMENTS + ".destroyed", contains(mailboxId));
    }

    @Test
    public void setMailboxesShouldDestroyMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + mailbox.getMailboxId().serialize() + "\"]" +
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
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox.child");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
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
                    hasEntry(equalTo("type"), equalTo("mailboxHasChild")),
                    hasEntry(equalTo("description"), equalTo("The mailbox '" + mailboxId + "' has a child.")))));
    }

    @Test
    public void setMailboxesShouldReturnNotDestroyedWhenSystemMailbox() {
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);
        String mailboxId = mailbox.getMailboxId().serialize();
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
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "parent");
        Mailbox parentMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "parent");
        String parentMailboxId = parentMailbox.getMailboxId().serialize();
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "parent.child");
        Mailbox childMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "parent.child");
        String childMailboxId = childMailbox.getMailboxId().serialize();
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + parentMailboxId + "\",\"" + childMailboxId + "\"]" +
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
            .body(ARGUMENTS + ".destroyed", containsInAnyOrder(parentMailboxId, childMailboxId));
    }

    @Test
    public void setMailboxesShouldReturnDestroyedWhenChildThenParentMailboxes() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "parent");
        Mailbox parentMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "parent");
        String parentMailboxId = parentMailbox.getMailboxId().serialize();
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "parent.child");
        Mailbox childMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "parent.child");
        String childMailboxId = childMailbox.getMailboxId().serialize();
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"destroy\": [\"" + childMailboxId + "\",\"" + parentMailboxId + "\"]" +
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
            .body(ARGUMENTS + ".destroyed", containsInAnyOrder(parentMailboxId, childMailboxId));
    }

    private MailboxId getRemovedMailboxId() {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved");
        MailboxId removedId = mailboxProbe.getMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved").getMailboxId();
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
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
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
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void setMailboxesShouldReturnUpdatedWhenNameUpdateAskedOnExistingMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
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
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void updateShouldReturnOkWhenClearingSharedWith() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        String requestBody =
            "[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
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
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void updateShouldReturnOkWhenSettingNewACL() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
                "          \"sharedWith\" : {\"user\": [\"a\", \"w\"]}" +
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
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void updateShouldRejectInvalidRights() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

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
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

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
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

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
        String user = "user";
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, myBox);
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
                "          \"sharedWith\" : {\"" + user + "\": [\"a\", \"w\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(myBox))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(user, ImmutableList.of(ADMINISTER, WRITE)));
    }

    @Test
    public void updateShouldModifyStoredDataWhenUpdatingACL() {
        String myBox = "myBox";
        String user = "user";
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, myBox);
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
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
                "        \"" + mailboxId + "\" : {" +
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
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
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
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, myBox);
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
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
                "        \"" + mailboxId + "\" : {" +
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
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
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
        String user1 = "user1";
        String user2 = "user2";
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, myBox);
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        with()
            .header("Authorization", accessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + mailboxId + "\" : {" +
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
                "        \"" + mailboxId + "\" : {" +
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
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".name", equalTo(myBox))
            .body(FIRST_MAILBOX + ".sharedWith", hasEntry(user2, ImmutableList.of(ADMINISTER, WRITE)));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxNameWhenNameUpdateAskedOnExistingMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
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
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].name", equalTo("myRenamedBox"));
    }

    @Test
    public void setMailboxesShouldReturnMailboxIdWhenMovingToAnotherParentMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myChosenParentBox");
        Mailbox chosenMailboxParent = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myChosenParentBox");
        String chosenMailboxParentId = chosenMailboxParent.getMailboxId().serialize();
        
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"parentId\" : \"" + chosenMailboxParentId + "\"" +
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
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxParentIdWhenMovingToAnotherParentMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        String newParentMailboxId = newParentMailbox.getMailboxId().serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"parentId\" : \"" + newParentMailboxId + "\"" +
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
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].parentId", equalTo(newParentMailboxId));
    }

    @Test
    public void setMailboxesShouldReturnMailboxIdWhenParentIdUpdateAskedOnExistingMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        String newParentMailboxId = newParentMailbox.getMailboxId().serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"parentId\" : \"" + newParentMailboxId + "\"" +
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
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxParentIdWhenParentIdUpdateAskedOnExistingMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        String newParentMailboxId = newParentMailbox.getMailboxId().serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"parentId\" : \"" + newParentMailboxId + "\"" +
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
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].parentId", equalTo(newParentMailboxId));
    }

    @Test
    public void setMailboxesShouldReturnMailboxIdWhenParentIdUpdateAskedAsOrphanForExistingMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
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
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void setMailboxesShouldUpdateParentIdWhenAskedAsOrphanForExistingMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
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
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
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
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        String newParentMailboxId = newParentMailbox.getMailboxId().serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"name\" : \"myRenamedBox\", " +
                    "          \"parentId\" : \"" + newParentMailboxId + "\"" +
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
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void setMailboxesShoulUpdateMailboxIAndParentIddWhenBothUpdatedForExistingMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        String newParentMailboxId = newParentMailbox.getMailboxId().serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"name\" : \"myRenamedBox\", " +
                    "          \"parentId\" : \"" + newParentMailboxId + "\"" +
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
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1))
            .body(ARGUMENTS + ".list[0].parentId",equalTo(newParentMailboxId))
            .body(ARGUMENTS + ".list[0].name",equalTo("myRenamedBox"));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenNameContainsPathDelimiter() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
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
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The mailbox 'my.Box' contains an illegal character: '.'"))))); 
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenNewParentDoesntExist() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        String badParentId = getRemovedMailboxId().serialize();
        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
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
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxId), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("notFound")),
                    hasEntry(equalTo("description"), containsString(badParentId)))));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenUpdatingParentIdOfAParentMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "root");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox.child");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myNewParentBox");
        String newParentMailboxId = newParentMailbox.getMailboxId().serialize();


        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxId + "\" : {" +
                    "          \"parentId\" : \"" + newParentMailboxId + "\"" +
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
                    hasEntry(equalTo("description"), equalTo("Cannot update a parent mailbox."))))); 
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedWhenRenamingAMailboxToAnAlreadyExistingMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "mySecondBox");

        String requestBody =
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
                    hasEntry(equalTo("description"), equalTo("Cannot rename a mailbox to an already existing mailbox."))))); 
    }

    @Test
    public void setMailboxesShouldReturnUpdatedWhenRenamingAChildMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "root");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        String requestBody =
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

        given()
            .header("Authorization", accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxNameWhenRenamingAChildMailbox() {
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "root");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");
        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "root.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        String requestBody =
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

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + mailboxId + "\"]}, \"#0\"]]")
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

        Mailbox mailbox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);
        String mailboxId = mailbox.getMailboxId().serialize();

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

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        Mailbox mailboxMyBox = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "myBox");
        String mailboxIdMyBox = mailboxMyBox.getMailboxId().serialize();

        String requestBody =
                "[" +
                    "  [ \"setMailboxes\"," +
                    "    {" +
                    "      \"update\": {" +
                    "        \"" + mailboxIdMyBox + "\" : {" +
                    "          \"name\" : \"outbox\"" +
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
            .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxIdMyBox), Matchers.allOf(
                    hasEntry(equalTo("type"), equalTo("invalidArguments")),
                    hasEntry(equalTo("description"), equalTo("The mailbox 'outbox' is a system mailbox.")))));
    }

    @Test
    public void setMailboxesShouldReturnNotUpdatedErrorWhenMovingMailboxTriggersNameConflict() {

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "A");
        Mailbox mailboxRootA = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "A");
        String mailboxRootAId = mailboxRootA.getMailboxId().serialize();

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "A.B");
        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "A.C");

        jmapServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "A.B.C");
        Mailbox mailboxChildToMoveC = jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, username, "A.B.C");
        String mailboxChildToMoveCId = mailboxChildToMoveC.getMailboxId().serialize();

        String requestBody =
              "[" +
                  "  [ \"setMailboxes\"," +
                  "    {" +
                  "      \"update\": {" +
                  "        \"" + mailboxChildToMoveCId + "\" : {" +
                  "          \"parentId\" : \"" + mailboxRootAId + "\"" +
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
          .body(ARGUMENTS + ".notUpdated", hasEntry(equalTo(mailboxChildToMoveCId), Matchers.allOf(
                  hasEntry(equalTo("type"), equalTo("invalidArguments")),
                  hasEntry(equalTo("description"), equalTo("Cannot rename a mailbox to an already existing mailbox.")))));
    }
}
