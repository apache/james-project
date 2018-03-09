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

package org.apache.james.jmap;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.TestingConstants.calmlyAwait;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.VacationPatch;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.store.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public abstract class VacationIntegrationTest {

    private static final String ARGUMENTS = "[0][1]";
    private static final String SECOND_NAME = "[1][0]";
    private static final String SECOND_ARGUMENTS = "[1][1]";
    private static final String DOMAIN = "mydomain.tld";
    private static final String USER_1 = "benwa@" + DOMAIN;
    private static final String USER_2 = "matthieu@" + DOMAIN;
    private static final String PASSWORD = "secret";
    private static final String REASON = "Message explaining my wonderful vacations";
    private static final String HTML_REASON = "<b>" + REASON + "</b>";
    public static final String ORIGINAL_MESSAGE_TEXT_BODY = "Hello someone, and thank you for joining example.com!";

    private GuiceJamesServer guiceJamesServer;
    private JmapGuiceProbe jmapGuiceProbe;

    protected abstract GuiceJamesServer createJmapServer();

    protected abstract void await();

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = createJmapServer();
        guiceJamesServer.start();

        DataProbe dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USER_1, PASSWORD);
        dataProbe.addUser(USER_2, PASSWORD);
        MailboxProbe mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USER_2, DefaultMailboxes.OUTBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USER_1, DefaultMailboxes.SENT);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USER_2, DefaultMailboxes.SENT);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USER_1, DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USER_2, DefaultMailboxes.INBOX);
        await();

        jmapGuiceProbe = guiceJamesServer.getProbe(JmapGuiceProbe.class);
        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig()
                .encoderConfig(
                    encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(jmapGuiceProbe
                .getJmapPort())
            .build();
    }

    private URIBuilder baseUri() {
        return new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(guiceJamesServer.getProbe(JmapGuiceProbe.class)
                .getJmapPort())
            .setCharset(StandardCharsets.UTF_8);
    }

    @After
    public void teardown() {
        guiceJamesServer.stop();
    }

    @Test
    public void jmapVacationShouldGenerateAReplyWhenActive() throws Exception {
        /* Test scenario :
            - User 1 benw@mydomain.tld sets a Vacation on its account
            - User 2 matthieu@mydomain.tld sends User 1 a mail
            - User 1 should well receive this mail
            - User 2 should well receive a notification about user 1 vacation
        */

        // Given
        AccessToken user1AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_1, PASSWORD);
        AccessToken user2AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_2, PASSWORD);
        // User 1 benw@mydomain.tld sets a Vacation on its account
        setVacationResponse(user1AccessToken);

        // When
        // User 2 matthieu@mydomain.tld sends User 1 a mail
        String user2OutboxId = getOutboxId(user2AccessToken);
        sendMail(user2AccessToken, user2OutboxId, "user|inbox|1");

        // Then
        // User 1 should well receive this mail
        calmlyAwait.atMost(30, TimeUnit.SECONDS)
            .until(() -> isTextMessageReceived(user1AccessToken, getInboxId(user1AccessToken), ORIGINAL_MESSAGE_TEXT_BODY, USER_2, USER_1));
        // User 2 should well receive a notification about user 1 vacation
        calmlyAwait.atMost(30, TimeUnit.SECONDS)
            .until(() -> isTextMessageReceived(user2AccessToken, getInboxId(user2AccessToken), REASON, USER_1, USER_2));
    }

    @Test
    public void jmapVacationShouldGenerateAReplyEvenWhenNoText() throws Exception {
        // Given
        AccessToken user1AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_1, PASSWORD);
        AccessToken user2AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_2, PASSWORD);
        jmapGuiceProbe.modifyVacation(
            AccountId.fromString(USER_1),
            VacationPatch.builder()
                .isEnabled(true)
                .build());

        // When
        String user2OutboxId = getOutboxId(user2AccessToken);
        sendMail(user2AccessToken, user2OutboxId, "user|inbox|1");

        // Then
        // User 1 should well receive this mail
        calmlyAwait.atMost(30, TimeUnit.SECONDS)
            .until(() -> isTextMessageReceived(user1AccessToken, getInboxId(user1AccessToken), ORIGINAL_MESSAGE_TEXT_BODY, USER_2, USER_1));
        // User 2 should well receive a notification about user 1 vacation
        calmlyAwait.atMost(30, TimeUnit.SECONDS)
            .until(() -> isTextMessageReceived(user2AccessToken, getInboxId(user2AccessToken), "", USER_1, USER_2));
    }

    @Test
    public void jmapVacationShouldHaveSupportForHtmlMail() throws Exception {
        // Given
        AccessToken user1AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_1, PASSWORD);
        AccessToken user2AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_2, PASSWORD);
        setHtmlVacationResponse(user1AccessToken);

        // When
        String user2OutboxId = getOutboxId(user2AccessToken);
        sendMail(user2AccessToken, user2OutboxId, "user|inbox|1");

        // Then
        calmlyAwait.atMost(10, TimeUnit.SECONDS)
            .until(() -> isTextMessageReceived(user1AccessToken, getInboxId(user1AccessToken), ORIGINAL_MESSAGE_TEXT_BODY, USER_2, USER_1));
        calmlyAwait.atMost(10, TimeUnit.SECONDS)
                .until(() -> assertOneMessageWithHtmlBodyReceived(user2AccessToken, getInboxId(user2AccessToken), HTML_REASON, USER_1, USER_2));
    }

    @Test
    public void jmapVacationShouldNotGenerateAReplyWhenInactive() throws Exception {
        /* Test scenario :
            - User 2 matthieu@mydomain.tld sends User 1 a mail
            - User 1 should well receive this mail
            - User 2 should not receive a notification
        */

        // Given
        AccessToken user1AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_1, PASSWORD);
        AccessToken user2AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_2, PASSWORD);

        // When
        // User 2 matthieu@mydomain.tld sends User 1 a mail
        String user2OutboxId = getOutboxId(user2AccessToken);
        sendMail(user2AccessToken, user2OutboxId, "user|inbox|1");

        // Then
        // User 1 should well receive this mail
        calmlyAwait.atMost(30, TimeUnit.SECONDS)
            .until(() -> isTextMessageReceived(user1AccessToken, getInboxId(user1AccessToken), ORIGINAL_MESSAGE_TEXT_BODY, USER_2, USER_1));
        // User 2 should not receive a notification
        Thread.sleep(1000L);
        with()
            .header("Authorization", user2AccessToken.serialize())
            .body("[[\"getMessageList\", " +
                "{" +
                "  \"fetchMessages\": true, " +
                "  \"filter\": {" +
                "    \"inMailboxes\":[\"" + getInboxId(user2AccessToken) + "\"]" +
                "  }" +
                "}, \"#0\"]]")
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(SECOND_NAME, equalTo("messages"))
            .body(SECOND_ARGUMENTS + ".list", empty());
    }

    @Test
    public void jmapVacationShouldNotSendNotificationTwice() throws Exception {
        /* Test scenario :
            - User 1 benw@mydomain.tld sets a Vacation on its account
            - User 2 matthieu@mydomain.tld sends User 1 a mail
            - User 2 matthieu@mydomain.tld sends User 1 a second mail
            - User 1 should well receive this mail
            - User 2 should well receive only one notification about user 1 vacation
        */

        // Given
        AccessToken user1AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_1, PASSWORD);
        AccessToken user2AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_2, PASSWORD);
        // User 1 benw@mydomain.tld sets a Vacation on its account
        setVacationResponse(user1AccessToken);

        // When
        // User 2 matthieu@mydomain.tld sends User 1 a mail
        String user2OutboxId = getOutboxId(user2AccessToken);
        sendMail(user2AccessToken, user2OutboxId, "user|inbox|1");
        sendMail(user2AccessToken, user2OutboxId, "user|inbox|2");

        // Then
        // User 2 should well receive a notification about user 1 vacation
        calmlyAwait.atMost(30, TimeUnit.SECONDS)
            .until(() -> isTextMessageReceived(user2AccessToken, getInboxId(user2AccessToken), REASON, USER_1, USER_2));
        // User 2 should not receive another notification
        Thread.sleep(1000L);
        assertOneMessageReceived(user2AccessToken, getInboxId(user2AccessToken), REASON, USER_1, USER_2);
    }

    @Test
    public void jmapVacationShouldSendNotificationTwiceWhenVacationReset() throws Exception {
        /* Test scenario :
            - User 1 benw@mydomain.tld sets a Vacation on its account
            - User 2 matthieu@mydomain.tld sends User 1 a mail
            - User 2 matthieu@mydomain.tld sends User 1 a second mail
            - User 1 should well receive this mail
            - User 2 should well receive only one notification about user 1 vacation
        */

        // Given
        AccessToken user1AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_1, PASSWORD);
        AccessToken user2AccessToken = HttpJmapAuthentication.authenticateJamesUser(baseUri(), USER_2, PASSWORD);
        // User 1 benw@mydomain.tld sets a Vacation on its account
        setVacationResponse(user1AccessToken);
        // User 2 matthieu@mydomain.tld sends User 1 a mail
        String user2OutboxId = getOutboxId(user2AccessToken);
        sendMail(user2AccessToken, user2OutboxId, "user|inbox|1");
        // Wait user 1 to receive the eMail before reset of vacation
        calmlyAwait.atMost(30, TimeUnit.SECONDS)
            .until(() -> isTextMessageReceived(user1AccessToken, getInboxId(user1AccessToken), ORIGINAL_MESSAGE_TEXT_BODY, USER_2, USER_1));

        // When
        // User 1 benw@mydomain.tld resets a Vacation on its account
        setVacationResponse(user1AccessToken);
        // User 2 matthieu@mydomain.tld sends User 1 a mail
        sendMail(user2AccessToken, user2OutboxId, "user|inbox|2");

        // Then
        // User 2 should well receive two notification about user 1 vacation
        calmlyAwait.atMost(30, TimeUnit.SECONDS)
            .until(() -> areTwoTextMessageReceived(user2AccessToken, getInboxId(user2AccessToken)));
    }

    private void setVacationResponse(AccessToken user1AccessToken) {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
            "  \"update\":{" +
            "    \"singleton\" : {" +
            "      \"id\": \"singleton\"," +
            "      \"isEnabled\": \"true\"," +
            "      \"textBody\": \"" + REASON + "\"" +
            "    }" +
            "  }" +
            "}, \"#0\"" +
            "]]";
        given()
            .header("Authorization", user1AccessToken.serialize())
            .body(bodyRequest)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);
    }

    private void setHtmlVacationResponse(AccessToken user1AccessToken) {
        String bodyRequest = "[[" +
            "\"setVacationResponse\", " +
            "{" +
            "  \"update\":{" +
            "    \"singleton\" : {" +
            "      \"id\": \"singleton\"," +
            "      \"isEnabled\": \"true\"," +
            "      \"htmlBody\": \"" + HTML_REASON + "\"" +
            "    }" +
            "  }" +
            "}, \"#0\"" +
            "]]";
        given()
            .header("Authorization", user1AccessToken.serialize())
            .body(bodyRequest)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);
    }

    private void sendMail(AccessToken user2AccessToken, String outboxId, String mailId) {
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + mailId + "\" : {" +
            "        \"from\": { \"email\": \"" + USER_2 + "\"}," +
            "        \"to\": [{ \"name\": \"Benwa\", \"email\": \"" + USER_1 + "\"}]," +
            "        \"subject\": \"Thank you for joining example.com!\"," +
            "        \"textBody\": \"" + ORIGINAL_MESSAGE_TEXT_BODY + "\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";
        given()
            .header("Authorization", user2AccessToken.serialize())
            .body(requestBody)
        .when()
            .post("/jmap");
    }

    private boolean areTwoTextMessageReceived(AccessToken recipientToken, String mailboxId) {
        try {
            with()
                .header("Authorization", recipientToken.serialize())
                .body("[[\"getMessageList\", " +
                    "{" +
                    "  \"fetchMessages\": true, " +
                    "  \"filter\": {" +
                    "    \"inMailboxes\":[\"" + mailboxId + "\"]" +
                    "  }" +
                    "}, \"#0\"]]")
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(SECOND_NAME, equalTo("messages"))
                .body(SECOND_ARGUMENTS + ".list", hasSize(2));
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    private boolean isTextMessageReceived(AccessToken recipientToken, String mailboxId, String expectedTextBody, String expectedFrom, String expectedTo) {
        try {
            assertOneMessageReceived(recipientToken, mailboxId, expectedTextBody, expectedFrom, expectedTo);
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    private void assertOneMessageReceived(AccessToken recipientToken, String mailboxId, String expectedTextBody, String expectedFrom, String expectedTo) {
        with()
            .header("Authorization", recipientToken.serialize())
            .body("[[\"getMessageList\", " +
                "{" +
                "  \"fetchMessages\": true, " +
                "  \"fetchMessageProperties\": [\"textBody\", \"from\", \"to\", \"mailboxIds\"]," +
                "  \"filter\": {" +
                "    \"inMailboxes\":[\"" + mailboxId + "\"]" +
                "  }" +
                "}, \"#0\"]]")
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(SECOND_NAME, equalTo("messages"))
            .body(SECOND_ARGUMENTS + ".list", hasSize(1))
            .body(SECOND_ARGUMENTS + ".list[0].textBody", equalTo(expectedTextBody))
            .body(SECOND_ARGUMENTS + ".list[0].from.email", equalTo(expectedFrom))
            .body(SECOND_ARGUMENTS + ".list[0].to.email", hasSize(1))
            .body(SECOND_ARGUMENTS + ".list[0].to.email[0]", equalTo(expectedTo));
    }

    private boolean assertOneMessageWithHtmlBodyReceived(AccessToken recipientToken, String mailboxId, String expectedHtmlBody, String expectedFrom, String expectedTo) {
        try {
            with()
                .header("Authorization", recipientToken.serialize())
                .body("[[\"getMessageList\", " +
                    "{" +
                    "  \"fetchMessages\": true, " +
                    "  \"fetchMessageProperties\": [\"htmlBody\", \"from\", \"to\", \"mailboxIds\"]," +
                    "  \"filter\": {" +
                    "    \"inMailboxes\":[\"" + mailboxId + "\"]" +
                    "  }" +
                    "}, \"#0\"]]")
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(SECOND_NAME, equalTo("messages"))
                .body(SECOND_ARGUMENTS + ".list", hasSize(1))
                .body(SECOND_ARGUMENTS + ".list[0].htmlBody", equalTo(expectedHtmlBody))
                .body(SECOND_ARGUMENTS + ".list[0].from.email", equalTo(expectedFrom))
                .body(SECOND_ARGUMENTS + ".list[0].to.email", hasSize(1))
                .body(SECOND_ARGUMENTS + ".list[0].to.email[0]", equalTo(expectedTo));
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    private String getOutboxId(AccessToken accessToken) {
        return getMailboxIdByRole(accessToken, DefaultMailboxes.OUTBOX);
    }

    private String getInboxId(AccessToken accessToken) {
        return getMailboxIdByRole(accessToken, DefaultMailboxes.INBOX);
    }

    private String getMailboxIdByRole(AccessToken accessToken, String role) {
        return getAllMailboxesIds(accessToken).stream()
            .filter(x -> x.get("role").equalsIgnoreCase(role))
            .map(x -> x.get("id"))
            .findFirst()
            .get();
    }

    private List<Map<String, String>> getAllMailboxesIds(AccessToken accessToken) {
        return with()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"properties\": [\"role\", \"id\"]}, \"#0\"]]")
            .post("/jmap")
        .andReturn()
            .body()
            .jsonPath()
            .getList(ARGUMENTS + ".list");
    }

}
