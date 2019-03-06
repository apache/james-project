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
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.TestingConstants.calmlyAwait;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.JmapGuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Disabled;

import com.google.common.base.Strings;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

public abstract class DeletedMessagesVaultTest {
    private static final String HOMER = "homer@" + DOMAIN;
    private static final String BART = "bart@" + DOMAIN;
    private static final String PASSWORD = "password";
    private static final String BOB_PASSWORD = "bobPassword";
    private static final ConditionFactory WAIT_TWO_MINUTES = calmlyAwait.atMost(Duration.TWO_MINUTES);
    private static final String SUBJECT = "This mail will be restored from the vault!!";
    private static final String MAILBOX_NAME = "toBeDeleted";
    private MailboxId otherMailboxId;

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();

    private AccessToken homerAccessToken;
    private AccessToken bartAccessToken;
    private GuiceJamesServer jmapServer;
    private RequestSpecification webAdminApi;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
            .build();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(HOMER, PASSWORD);
        dataProbe.addUser(BART, BOB_PASSWORD);
        mailboxProbe.createMailbox("#private", HOMER, DefaultMailboxes.INBOX);
        otherMailboxId = mailboxProbe.createMailbox("#private", HOMER, MAILBOX_NAME);
        homerAccessToken = authenticateJamesUser(baseUri(jmapServer), HOMER, PASSWORD);
        bartAccessToken = authenticateJamesUser(baseUri(jmapServer), BART, BOB_PASSWORD);

        WebAdminGuiceProbe webAdminGuiceProbe = jmapServer.getProbe(WebAdminGuiceProbe.class);
        webAdminGuiceProbe.await();
        webAdminApi = given()
            .spec(WebAdminUtils
                .buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
                .build());
    }

    @After
    public void tearDown() {
        jmapServer.stop();
    }

    @Category(BasicFeature.class)
    @Test
    public void postShouldRestoreJmapDeletedEmail() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        given()
            .header("Authorization", homerAccessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Category(BasicFeature.class)
    @Test
    public void postShouldRestoreImapDeletedEmail() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        imapMessageReader.expunge();

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        given()
            .header("Authorization", homerAccessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Disabled("MAILBOX-379 PreDeletionHook are not yet triggered upon mailbox deletion")
    @Category(BasicFeature.class)
    @Test
    public void postShouldRestoreImapDeletedMailbox() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX);

        imapMessageReader.moveFirstMessage(MAILBOX_NAME);

        imapMessageReader.delete(MAILBOX_NAME);

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        given()
            .header("Authorization", homerAccessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Test
    public void imapMovedMessageShouldNotEndUpInTheVault() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX);

        imapMessageReader.moveFirstMessage(MAILBOX_NAME);

        //Moved messages should not be moved to the vault
        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);


        // No messages restored for bart
        assertThat(listMessageIdsForAccount(bartAccessToken).size()).isEqualTo(1);
    }

    @Test
    public void jmapMovedMessageShouldNotEndUpInTheVault() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);
        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);

        homerMovesTheMailInAnotherMailbox(messageId);

        //Moved messages should not be moved to the vault
        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);


        // No messages restored for bart
        assertThat(listMessageIdsForAccount(bartAccessToken).size()).isEqualTo(1);
    }

    @Test
    public void restoreShouldNotImpactOtherUsers() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        bartDeletesMessages(listMessageIdsForAccount(bartAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(bartAccessToken).size() == 0);

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        // No messages restored for bart
        assertThat(listMessageIdsForAccount(bartAccessToken).size()).isEqualTo(0);
    }

    @Test
    public void restoredMessagesShouldNotBeRemovedFromTheVault() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 2);
    }

    @Test
    public void postShouldNotRestoreItemsWhenTheVaultIsEmpty() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        restoreAllMessagesOfHomer();
        Thread.sleep(Duration.FIVE_SECONDS.getValueInMS());

        // No additional had been restored as the vault is empty
        assertThat(listMessageIdsForAccount(homerAccessToken).size())
            .isEqualTo(1);
    }

    private void bartSendMessageToHomer() {
        String messageCreationId = "creationId";
        String outboxId = getOutboxId(bartAccessToken);
        String bigEnoughBody = Strings.repeat("123456789\n", 12 * 100);
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"headers\":{\"Disposition-Notification-To\":\"" + BART + "\"}," +
            "        \"from\": { \"name\": \"Bob\", \"email\": \"" + BART + "\"}," +
            "        \"to\": [{ \"name\": \"User\", \"email\": \"" + HOMER + "\"}]," +
            "        \"subject\": \"" + SUBJECT + "\"," +
            "        \"textBody\": \"" + bigEnoughBody + "\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"] " +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", bartAccessToken.serialize())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");
    }

    private void homerDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(homerAccessToken, idsToDestroy);
    }

    private void bartDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(bartAccessToken, idsToDestroy);
    }

    private void deleteMessages(AccessToken accessToken, List<String> idsToDestroy) {
        String idString = idsToDestroy.stream()
            .map(id -> "\"" + id + "\"")
            .collect(Collectors.joining(","));

        with()
            .header("Authorization", accessToken.serialize())
            .body("[[\"setMessages\", {\"destroy\": [" + idString + "]}, \"#0\"]]")
            .post("/jmap");
    }

    private void restoreAllMessagesOfHomer() {
        String taskId = webAdminApi.with()
            .post("/deletedMessages/user/" + HOMER + "?action=restore")
            .jsonPath()
            .get("taskId");

        webAdminApi.given()
            .get("/tasks/" + taskId + "/await")
            .then()
            .body("status", is("completed"));
    }

    private void homerMovesTheMailInAnotherMailbox(String messageId) {
        String updateRequestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"update\": { \"" + messageId  + "\" : {" +
            "        \"mailboxIds\": [\"" + otherMailboxId.serialize() + "\"]" +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        given()
            .header("Authorization", bartAccessToken.serialize())
            .body(updateRequestBody)
            .when()
            .post("/jmap");
    }
}
