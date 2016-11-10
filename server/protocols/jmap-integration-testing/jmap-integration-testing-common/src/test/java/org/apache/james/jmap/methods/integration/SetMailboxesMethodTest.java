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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JmapServer;
import org.apache.james.WebAdminServer;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.utils.JmapGuiceProbe;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public abstract class SetMailboxesMethodTest<T extends GuiceJamesServer & JmapServer & WebAdminServer> {

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String USERS_DOMAIN = "domain.tld";

    protected abstract T createJmapServer();

    protected abstract void await();

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
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        username = "username@" + USERS_DOMAIN;
        String password = "password";
        jmapServer.serverProbe().addDomain(USERS_DOMAIN);
        jmapServer.serverProbe().addUser(username, password);
        jmapServer.serverProbe().createMailbox("#private", username, "inbox");
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);

        await();
    }

    @After
    public void teardown() {
        jmapServer.stop();
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
            .header("Authorization", this.accessToken.serialize())
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
            .header("Authorization", this.accessToken.serialize())
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
            .header("Authorization", this.accessToken.serialize())
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
            .then()
            .post("/jmap");

        given()
            .header("Authorization", this.accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(2))
            .body(ARGUMENTS + ".list.name", hasItems("foo"));
    }

    @Test
    public void setMailboxesShouldReturnCreatedMailboxWhenChildOfInboxMailbox() {
        String inboxId =
            with()
                .header("Authorization", this.accessToken.serialize())
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
            .header("Authorization", this.accessToken.serialize())
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
        String inboxId =
            with()
                .header("Authorization", this.accessToken.serialize())
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");

        given()
            .header("Authorization", this.accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(2))
            .body(ARGUMENTS + ".list.name", hasItems("foo"));
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
            .header("Authorization", this.accessToken.serialize())
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
            .header("Authorization", this.accessToken.serialize())
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
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
            .header("Authorization", this.accessToken.serialize())
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
                .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", this.accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(1));
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        jmapServer.serverProbe().createMailbox("#private", username, "myBox.child");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "inbox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "parent");
        Mailbox parentMailbox = jmapServer.serverProbe().getMailbox("#private", username, "parent");
        String parentMailboxId = parentMailbox.getMailboxId().serialize();
        jmapServer.serverProbe().createMailbox("#private", username, "parent.child");
        Mailbox childMailbox = jmapServer.serverProbe().getMailbox("#private", username, "parent.child");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "parent");
        Mailbox parentMailbox = jmapServer.serverProbe().getMailbox("#private", username, "parent");
        String parentMailboxId = parentMailbox.getMailboxId().serialize();
        jmapServer.serverProbe().createMailbox("#private", username, "parent.child");
        Mailbox childMailbox = jmapServer.serverProbe().getMailbox("#private", username, "parent.child");
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".destroyed", containsInAnyOrder(parentMailboxId, childMailboxId));
    }

    private MailboxId getRemovedMailboxId() {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved");
        MailboxId removedId = jmapServer.serverProbe().getMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved").getMailboxId();
        jmapServer.serverProbe().deleteMailbox(MailboxConstants.USER_NAMESPACE, username, "quicklyRemoved");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxesSet"))
            .body(ARGUMENTS + ".updated", contains(mailboxId));
    }

    @Test
    public void setMailboxesShouldUpdateMailboxNameWhenNameUpdateAskedOnExistingMailbox() {
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.serverProbe().createMailbox("#private", username, "myChosenParentBox");
        Mailbox chosenMailboxParent = jmapServer.serverProbe().getMailbox("#private", username, "myChosenParentBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        
        jmapServer.serverProbe().createMailbox("#private", username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.serverProbe().getMailbox("#private", username, "myNewParentBox");
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox");

        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        
        jmapServer.serverProbe().createMailbox("#private", username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.serverProbe().getMailbox("#private", username, "myNewParentBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox");

        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();
        
        jmapServer.serverProbe().createMailbox("#private", username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.serverProbe().getMailbox("#private", username, "myNewParentBox");
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox");

        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myPreviousParentBox.myBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox");

        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myPreviousParentBox.myBox");
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox");

        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.serverProbe().createMailbox("#private", username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.serverProbe().getMailbox("#private", username, "myNewParentBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox");

        jmapServer.serverProbe().createMailbox("#private", username, "myPreviousParentBox.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myPreviousParentBox.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.serverProbe().createMailbox("#private", username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.serverProbe().getMailbox("#private", username, "myNewParentBox");
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "root");

        jmapServer.serverProbe().createMailbox("#private", username, "root.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "root.myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.serverProbe().createMailbox("#private", username, "root.myBox.child");

        jmapServer.serverProbe().createMailbox("#private", username, "myNewParentBox");
        Mailbox newParentMailbox = jmapServer.serverProbe().getMailbox("#private", username, "myNewParentBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
        String mailboxId = mailbox.getMailboxId().serialize();

        jmapServer.serverProbe().createMailbox("#private", username, "mySecondBox");

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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "root");

        jmapServer.serverProbe().createMailbox("#private", username, "root.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "root.myBox");
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
            .header("Authorization", this.accessToken.serialize())
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
        jmapServer.serverProbe().createMailbox("#private", username, "root");

        jmapServer.serverProbe().createMailbox("#private", username, "root.myBox");
        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "root.myBox");
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
            .header("Authorization", this.accessToken.serialize())
            .body(requestBody)
            .post("/jmap");

        given()
            .header("Authorization", this.accessToken.serialize())
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

        Mailbox mailbox = jmapServer.serverProbe().getMailbox("#private", username, "inbox");
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
            .header("Authorization", this.accessToken.serialize())
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

        jmapServer.serverProbe().createMailbox("#private", username, "myBox");
        Mailbox mailboxMyBox = jmapServer.serverProbe().getMailbox("#private", username, "myBox");
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
            .header("Authorization", this.accessToken.serialize())
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

        jmapServer.serverProbe().createMailbox("#private", username, "A");
        Mailbox mailboxRootA = jmapServer.serverProbe().getMailbox("#private", username, "A");
        String mailboxRootAId = mailboxRootA.getMailboxId().serialize();

        jmapServer.serverProbe().createMailbox("#private", username, "A.B");
        jmapServer.serverProbe().createMailbox("#private", username, "A.C");

        jmapServer.serverProbe().createMailbox("#private", username, "A.B.C");
        Mailbox mailboxChildToMoveC = jmapServer.serverProbe().getMailbox("#private", username, "A.B.C");
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
          .header("Authorization", this.accessToken.serialize())
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
