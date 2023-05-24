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

package org.apache.james.webadmin.integration.vault;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static io.restassured.config.ParamConfig.UpdateStrategy.REPLACE;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapCommonRequests.deleteMessages;
import static org.apache.james.jmap.JmapCommonRequests.getAllMailboxesIds;
import static org.apache.james.jmap.JmapCommonRequests.getLatestMessageId;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsInMailbox;
import static org.apache.james.jmap.LocalHostURIBuilder.baseUri;
import static org.apache.james.mailbox.backup.ZipAssert.EntryChecks.hasName;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.deleteFromVault;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.exportVaultContent;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.purgeVault;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.restoreMessagesForUserWithQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TWO_MINUTES;
import static org.hamcrest.Matchers.hasItem;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

import io.restassured.RestAssured;
import io.restassured.config.ParamConfig;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

public abstract class DeletedMessageVaultIntegrationTest {

    public static class ClockExtension implements GuiceModuleTestExtension {
        private UpdatableTickingClock clock;

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            clock = new UpdatableTickingClock(NOW.toInstant());
        }

        @Override
        public Module getModule() {
            return binder -> binder.bind(Clock.class).toInstance(clock);
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return parameterContext.getParameter().getType() == UpdatableTickingClock.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return clock;
        }
    }

    private static final ZonedDateTime NOW = ZonedDateTime.now();
    private static final ZonedDateTime TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION = NOW.plusYears(1).plusMonths(2);
    private static final String FIRST_SUBJECT = "first subject";
    private static final String SECOND_SUBJECT = "second subject";
    private static final String HOMER = "homer@" + DOMAIN;
    private static final String BART = "bart@" + DOMAIN;
    private static final String JACK = "jack@" + DOMAIN;
    private static final String PASSWORD = "password";
    private static final String BOB_PASSWORD = "bobPassword";
    private static final ConditionFactory WAIT_TWO_MINUTES = calmlyAwait.atMost(TWO_MINUTES);
    private static final String SUBJECT = "This mail will be restored from the vault!!";
    private static final String MAILBOX_NAME = "toBeDeleted";
    private static final String MATCH_ALL_QUERY = "{" +
        "\"combinator\": \"and\"," +
        "\"criteria\": []" +
        "}";
    private static final ExportRequest EXPORT_ALL_HOMER_MESSAGES_TO_BART = ExportRequest
        .userExportFrom(HOMER)
        .exportTo(BART)
        .query(MATCH_ALL_QUERY);
    private static final ExportRequest EXPORT_ALL_JACK_MESSAGES_TO_HOMER = ExportRequest
        .userExportFrom(JACK)
        .exportTo(HOMER)
        .query(MATCH_ALL_QUERY);

    private TestIMAPClient testIMAPClient;
    private AccessToken homerAccessToken;
    private AccessToken bartAccessToken;
    private AccessToken jackAccessToken;
    private RequestSpecification webAdminApi;
    private MailboxId otherMailboxId;

    @BeforeEach
    void setup(GuiceJamesServer jmapServer) throws Throwable {
        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        Port jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(HOMER, PASSWORD);
        dataProbe.addUser(BART, BOB_PASSWORD);
        dataProbe.addUser(JACK, PASSWORD);
        mailboxProbe.createMailbox("#private", HOMER, DefaultMailboxes.INBOX);
        otherMailboxId = mailboxProbe.createMailbox("#private", HOMER, MAILBOX_NAME);
        homerAccessToken = authenticateJamesUser(baseUri(jmapPort), Username.of(HOMER), PASSWORD);
        bartAccessToken = authenticateJamesUser(baseUri(jmapPort), Username.of(BART), BOB_PASSWORD);
        jackAccessToken = authenticateJamesUser(baseUri(jmapPort), Username.of(JACK), PASSWORD);

        testIMAPClient = new TestIMAPClient();

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort())
            .config(WebAdminUtils.defaultConfig()
                .paramConfig(new ParamConfig(REPLACE, REPLACE, REPLACE)));

    }

    @AfterEach
    void tearDown() throws IOException {
        testIMAPClient.close();
    }

    protected abstract void awaitSearchUpToDate();

    @Tag(BasicFeature.TAG)
    @Test
    void vaultEndpointShouldRestoreJmapDeletedEmail() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Tag(BasicFeature.TAG)
    @Test
    void vaultEndpointShouldRestoreImapDeletedEmail(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        testIMAPClient.expunge();

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Tag(BasicFeature.TAG)
    @Test
    void vaultEndpointShouldRestoreImapDeletedMailbox(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX);

        testIMAPClient.moveFirstMessage(MAILBOX_NAME);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsInMailbox(homerAccessToken, otherMailboxId.serialize())).hasSize(1));

        testIMAPClient.delete(MAILBOX_NAME);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));
        Thread.sleep(1000); // Wait for messages to be moved to the vault

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Test
    void restoreShouldCreateRestoreMessagesMailbox() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        assertThat(homerHasMailboxWithRole(Role.RESTORED_MESSAGES)).isTrue();
    }

    @Test
    void postShouldRestoreMatchingMessages() {
        bartSendMessageToHomerWithSubject("aaaaa");
        bartSendMessageToHomerWithSubject("bbbbb");
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        String query = "{" +
            "  \"combinator\": \"and\"," +
            "  \"criteria\": [" +
            "    {" +
            "      \"fieldName\": \"subject\"," +
            "      \"operator\": \"equals\"," +
            "      \"value\": \"aaaaa\"" +
            "    }" +
            "  ]" +
            "}";
        restoreMessagesForUserWithQuery(webAdminApi, HOMER, query);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem("aaaaa"));
    }

    @Test
    void postShouldNotRestoreWhenNoMatchingMessages() throws Exception {
        bartSendMessageToHomerWithSubject("aaaaa");
        bartSendMessageToHomerWithSubject("bbbbb");
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        String query = "{" +
            "  \"combinator\": \"and\"," +
            "  \"criteria\": [" +
            "    {" +
            "      \"fieldName\": \"subject\"," +
            "      \"operator\": \"equals\"," +
            "      \"value\": \"ccccc\"" +
            "    }" +
            "  ]" +
            "}";
        restoreMessagesForUserWithQuery(webAdminApi, HOMER, query);


        Thread.sleep(FIVE_SECONDS.toMillis());

        // No additional had been restored for Bart as the vault is empty
        assertThat(listMessageIdsForAccount(homerAccessToken).size())
            .isEqualTo(0);
    }

    @Test
    void postShouldRestoreMatchingMessagesWhenQueryLimit() {
        bartSendMessageToHomerWithSubject("aaaa");
        bartSendMessageToHomerWithSubject("aaaa");
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        String query = "{" +
            "  \"combinator\": \"and\"," +
            "  \"limit\": 1," +
            "  \"criteria\": [" +
            "    {" +
            "      \"fieldName\": \"subject\"," +
            "      \"operator\": \"equals\"," +
            "      \"value\": \"aaaa\"" +
            "    }" +
            "  ]" +
            "}";
        restoreMessagesForUserWithQuery(webAdminApi, HOMER, query);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));
    }

    @Test
    void imapMovedMessageShouldNotEndUpInTheVault(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX);

        testIMAPClient.moveFirstMessage(MAILBOX_NAME);

        //Moved messages should not be moved to the vault
        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));


        // No messages restored for bart
        assertThat(listMessageIdsForAccount(bartAccessToken).size()).isEqualTo(1);
    }

    @Test
    void jmapMovedMessageShouldNotEndUpInTheVault() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));
        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);

        homerMovesTheMailInAnotherMailbox(messageId);

        //Moved messages should not be moved to the vault
        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));


        // No messages restored for bart
        assertThat(listMessageIdsForAccount(bartAccessToken).size()).isEqualTo(1);
    }

    @Test
    void restoreShouldNotImpactOtherUsers() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        bartDeletesMessages(listMessageIdsForAccount(bartAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(bartAccessToken)).hasSize(0));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        // No messages restored for bart
        assertThat(listMessageIdsForAccount(bartAccessToken).size()).isEqualTo(0);
    }

    @Test
    void restoredMessagesShouldNotBeRemovedFromTheVault() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(2));
    }

    @Test
    void vaultEndpointShouldNotRestoreItemsWhenTheVaultIsEmpty() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        restoreAllMessagesOfHomer();
        awaitSearchUpToDate();

        // No additional had been restored as the vault is empty
        assertThat(listMessageIdsForAccount(homerAccessToken).size())
            .isEqualTo(1);
    }

    @Test
    void vaultEndpointShouldNotRestoreMessageForSharee() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(bartAccessToken)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);

        homerSharesHisMailboxWithBart();

        bartDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        restoreMessagesFor(BART);
        awaitSearchUpToDate();

        // No additional had been restored for Bart as the vault is empty
        assertThat(listMessageIdsForAccount(bartAccessToken).size())
            .isEqualTo(1);
    }

    @Test
    void vaultEndpointShouldRestoreMessageForSharer() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);

        homerSharesHisMailboxWithBart();

        bartDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String newMessageId = listMessageIdsForAccount(homerAccessToken).get(0);
        given()
            .header("Authorization", homerAccessToken.asString())
            .body("[[\"getMessages\", {\"ids\": [\"" + newMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Tag(BasicFeature.TAG)
    @Test
    void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenJmapDeleteMessage() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));
        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Tag(BasicFeature.TAG)
    @Test
    void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenImapDeleteMessage(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));
        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        testIMAPClient.expunge();

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Tag(BasicFeature.TAG)
    @Test
    public void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenImapDeletedMailbox(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));
        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX);

        testIMAPClient.moveFirstMessage(MAILBOX_NAME);

        testIMAPClient.delete(MAILBOX_NAME);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Test
    void vaultExportShouldExportZipContainsOnlyMatchedMessages() throws Exception {
        bartSendMessageToHomerWithSubject(FIRST_SUBJECT);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));
        String firstMessageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        bartSendMessageToHomerWithSubject(SECOND_SUBJECT);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        ExportRequest exportRequest = ExportRequest
            .userExportFrom(HOMER)
            .exportTo(BART)
            .query("{" +
                "  \"fieldName\": \"subject\"," +
                "  \"operator\": \"equals\"," +
                "  \"value\": \"" + FIRST_SUBJECT + "\"" +
                "}");
        String fileLocation = exportAndGetFileLocationFromLastMail(exportRequest, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.containsOnlyEntriesMatching(hasName(firstMessageIdOfHomer + ".eml"));
        }
    }

    @Test
    void vaultExportShouldExportEmptyZipWhenQueryDoesntMatch() throws Exception {
        bartSendMessageToHomerWithSubject(FIRST_SUBJECT);
        bartSendMessageToHomerWithSubject(SECOND_SUBJECT);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        ExportRequest exportRequest = ExportRequest
            .userExportFrom(HOMER)
            .exportTo(BART)
            .query("{" +
                "  \"fieldName\": \"subject\"," +
                "  \"operator\": \"equals\"," +
                "  \"value\": \"non matching\"" +
                "}");
        String fileLocation = exportAndGetFileLocationFromLastMail(exportRequest, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultExportShouldExportEmptyZipWhenVaultIsEmpty() throws Exception {
        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultExportShouldResponseIdempotentSideEffect() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        String fileLocationFirstExport = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        String fileLocationSecondExport = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationFirstExport))) {
            zipAssert.hasSameContentWith(new FileInputStream(fileLocationSecondExport));
        }
    }

    @Test
    void vaultPurgeShouldMakeExportProduceEmptyZipWhenAllMessagesAreExpired(UpdatableTickingClock clock) throws Exception {
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(3));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        clock.setInstant(TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION.toInstant());
        purgeVault(webAdminApi);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultPurgeShouldMakeExportProduceAZipWhenOneMessageIsNotExpired(UpdatableTickingClock clock) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String messageIdOfNotExpiredMessage = listMessageIdsForAccount(homerAccessToken).get(0);

        clock.setInstant(TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION.toInstant());
        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        purgeVault(webAdminApi);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfNotExpiredMessage + ".eml"));
        }
    }

    @Test
    void vaultPurgeShouldMakeExportProduceZipWhenAllMessagesAreNotExpired() throws Exception {
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(3));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        purgeVault(webAdminApi);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(3);
        }
    }

    @Test
    void vaultPurgeShouldNotAppendMessageToTheUserMailbox(UpdatableTickingClock clock) {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        clock.setInstant(TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION.toInstant());
        purgeVault(webAdminApi);

        assertThat(listMessageIdsForAccount(homerAccessToken))
            .hasSize(0);
    }

    @Test
    void vaultDeleteShouldDeleteMessageThenExportWithNoEntry() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        deleteFromVault(webAdminApi, HOMER, messageIdOfHomer);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultDeleteShouldNotDeleteEmptyVaultThenExportNoEntry() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        deleteFromVault(webAdminApi, HOMER, messageIdOfHomer);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultDeleteShouldNotDeleteNotMatchedMessageInVaultThenExportAnEntry() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));
        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(bartAccessToken)).hasSize(1));
        String messageIdOfBart = listMessageIdsForAccount(bartAccessToken).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        deleteFromVault(webAdminApi, HOMER, messageIdOfBart);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Test
    void vaultDeleteShouldNotAppendMessageToTheUserMailbox() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        deleteFromVault(webAdminApi, HOMER, messageIdOfHomer);

        assertThat(listMessageIdsForAccount(homerAccessToken))
            .hasSize(0);
    }

    @Test
    void vaultDeleteShouldDeleteAllMessagesHavingSameBlobContent() throws Exception {
        bartSendMessageToHomerAndJack();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String homerInboxMessageId = listMessageIdsForAccount(homerAccessToken).get(0);
        homerDeletesMessages(ImmutableList.of(homerInboxMessageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        // the message same with homer's one in inbox
        String jackInboxMessageId = listMessageIdsForAccount(jackAccessToken).get(0);
        jackDeletesMessages(ImmutableList.of(jackInboxMessageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(jackAccessToken)).hasSize(0));

        // delete from homer's vault, expecting the message contains the same blob in jack's vault will be deleted
        deleteFromVault(webAdminApi, HOMER, homerInboxMessageId);

        String fileLocationOfBartMessages = exportAndGetFileLocationFromLastMail(EXPORT_ALL_JACK_MESSAGES_TO_HOMER, homerAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationOfBartMessages))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultDeleteShouldNotDeleteAllMessagesHavingSameBlobContentWhenMessageNotDeletedWithinTheSameMonth(UpdatableTickingClock clock) throws Exception {
        bartSendMessageToHomerAndJack();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(1));

        String homerInboxMessageId = listMessageIdsForAccount(homerAccessToken).get(0);
        homerDeletesMessages(ImmutableList.of(homerInboxMessageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerAccessToken)).hasSize(0));

        // one year later, delete jack's message
        clock.setInstant(NOW.plusYears(1).toInstant());
        // the message same with homer's one in inbox
        String jackInboxMessageId = listMessageIdsForAccount(jackAccessToken).get(0);
        jackDeletesMessages(ImmutableList.of(jackInboxMessageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(jackAccessToken)).hasSize(0));

        // delete from homer's vault, expecting jack's vault still be intact
        deleteFromVault(webAdminApi, HOMER, homerInboxMessageId);

        String fileLocationOfBartMessages = exportAndGetFileLocationFromLastMail(EXPORT_ALL_JACK_MESSAGES_TO_HOMER, homerAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationOfBartMessages))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(jackInboxMessageId + ".eml"));
        }
    }

    private String exportAndGetFileLocationFromLastMail(ExportRequest exportRequest, AccessToken shareeAccessToken) {
        int currentNumberOfMessages = listMessageIdsForAccount(shareeAccessToken).size();
        exportVaultContent(webAdminApi, exportRequest);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(shareeAccessToken)).hasSize(currentNumberOfMessages + 1));
        String exportingMessageId = getLatestMessageId(shareeAccessToken, Role.INBOX);

        return exportedFileLocationFromMailHeader(exportingMessageId, shareeAccessToken);
    }

    private String exportedFileLocationFromMailHeader(String messageId, AccessToken accessToken) {
        return with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
                .post("/jmap")
            .jsonPath()
                .getList(ARGUMENTS + ".list.headers.corresponding-file", String.class)
                .get(0);
    }

    private void homerSharesHisMailboxWithBart() {
        with()
            .header("Authorization", homerAccessToken.asString())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + otherMailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"" + BART + "\": [\"l\", \"w\", \"t\", \"r\"]}" +
                "        }" +
                "      }" +
                "    }," +
                "    \"#0\"" +
                "  ]" +
                "]")
            .post("/jmap");
    }

    private void bartSendMessageToHomer() {
        bartSendMessageToHomerWithSubject(SUBJECT);
    }

    private void bartSendMessageToHomerAndJack() {
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
            "        \"to\": [{ \"name\": \"Homer\", \"email\": \"" + HOMER + "\"}, { \"name\": \"Jack\", \"email\": \"" + JACK + "\"}]," +
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
            .header("Authorization", bartAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");
    }

    private void bartSendMessageToHomerWithSubject(String subject) {
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
            "        \"subject\": \"" + subject + "\"," +
            "        \"textBody\": \"" + bigEnoughBody + "\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"] " +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", bartAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");
    }

    private void homerDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(homerAccessToken, idsToDestroy);
        // Grace period for the vault
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void bartDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(bartAccessToken, idsToDestroy);
    }

    private void jackDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(jackAccessToken, idsToDestroy);
    }

    private void restoreAllMessagesOfHomer() {
        restoreMessagesFor(HOMER);
    }

    private void restoreMessagesFor(String user) {
        restoreMessagesForUserWithQuery(webAdminApi, user, MATCH_ALL_QUERY);
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
            .header("Authorization", homerAccessToken.asString())
            .body(updateRequestBody)
            .when()
            .post("/jmap");
    }

    private boolean homerHasMailboxWithRole(Role role) {
        return getAllMailboxesIds(homerAccessToken).stream()
            .filter(mailbox -> mailbox.get("role") != null)
            .anyMatch(mailbox -> mailbox.get("role").equals(role.serialize())
                && mailbox.get("name").equals(role.getDefaultMailbox()));
    }
}
