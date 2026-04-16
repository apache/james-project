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
import static io.restassured.http.ContentType.JSON;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapRFCCommonRequests.ACCEPT_JMAP_RFC_HEADER;
import static org.apache.james.jmap.JmapRFCCommonRequests.UserCredential;
import static org.apache.james.jmap.JmapRFCCommonRequests.deleteMessages;
import static org.apache.james.jmap.JmapRFCCommonRequests.getAllMailboxesIds;
import static org.apache.james.jmap.JmapRFCCommonRequests.getLatestMessageId;
import static org.apache.james.jmap.JmapRFCCommonRequests.getMessageContent;
import static org.apache.james.jmap.JmapRFCCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapRFCCommonRequests.getUserCredential;
import static org.apache.james.jmap.JmapRFCCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.JmapRFCCommonRequests.listMessageIdsInMailbox;
import static org.apache.james.mailbox.backup.ZipAssert.EntryChecks.hasName;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.deleteFromVault;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.exportVaultContent;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.purgeVault;
import static org.apache.james.webadmin.integration.vault.DeletedMessagesVaultRequests.restoreMessagesForUserWithQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TWO_MINUTES;
import static org.hamcrest.Matchers.notNullValue;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.apache.james.GuiceJamesServer;
import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.search.Query;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.integration.probe.DeletedMessageVaultProbe;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

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
    private static final String OWNER_ONLY_MAILBOX_NAME = "ownerOnly";
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
    private RequestSpecification webAdminApi;
    private MailboxId otherMailboxId;
    private MailboxId ownerOnlyMailboxId;
    private MailboxProbeImpl mailboxProbe;

    private UserCredential homerCredential;
    private UserCredential bartCredential;
    private UserCredential jackCredential;

    @BeforeEach
    void setup(GuiceJamesServer jmapServer) throws Throwable {
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);

        Port jmapPort = jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .addHeader(ACCEPT_JMAP_RFC_HEADER.getName(), ACCEPT_JMAP_RFC_HEADER.getValue())
            .build();
        RestAssured.defaultParser = Parser.JSON;

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(HOMER, PASSWORD);
        dataProbe.addUser(BART, BOB_PASSWORD);
        dataProbe.addUser(JACK, PASSWORD);
        mailboxProbe.createMailbox("#private", HOMER, DefaultMailboxes.INBOX);
        otherMailboxId = mailboxProbe.createMailbox("#private", HOMER, MAILBOX_NAME);
        ownerOnlyMailboxId = mailboxProbe.createMailbox("#private", HOMER, OWNER_ONLY_MAILBOX_NAME);

        homerCredential = getUserCredential(HOMER, PASSWORD);
        bartCredential = getUserCredential(BART, BOB_PASSWORD);
        jackCredential = getUserCredential(JACK, PASSWORD);

        testIMAPClient = new TestIMAPClient();

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort())
            .config(WebAdminUtils.defaultConfig()
                .paramConfig(new ParamConfig().replaceAllParameters()));

    }

    @AfterEach
    void tearDown() throws IOException {
        testIMAPClient.close();
    }

    protected abstract void awaitSearchUpToDate();

    @Tag(BasicFeature.TAG)
    @Test
    void vaultEndpointShouldRestoreJmapDeletedEmail(GuiceJamesServer jmapServer) {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        assertThat(getMessageContent(homerCredential, messageId)
            .getString("methodResponses[0][1].list[0].subject")).isEqualTo(SUBJECT);
    }

    @Tag(BasicFeature.TAG)
    @Test
    void vaultEndpointShouldRestoreImapDeletedEmail(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        testIMAPClient.expunge();

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        assertThat(getMessageContent(homerCredential, messageId)
            .getString("methodResponses[0][1].list[0].subject")).isEqualTo(SUBJECT);
    }

    @Tag(BasicFeature.TAG)
    @Test
    void vaultEndpointShouldRestoreImapDeletedMailbox(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX);

        testIMAPClient.moveFirstMessage(MAILBOX_NAME);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsInMailbox(homerCredential, otherMailboxId.serialize())).hasSize(1));

        testIMAPClient.delete(MAILBOX_NAME);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        assertThat(getMessageContent(homerCredential, messageId)
            .getString("methodResponses[0][1].list[0].subject")).isEqualTo(SUBJECT);
    }

    @Test
    void restoreShouldCreateRestoreMessagesMailbox(GuiceJamesServer jmapServer) {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        assertThat(homerHasMailboxWithRole(Role.RESTORED_MESSAGES)).isTrue();
    }

    @Test
    void postShouldRestoreMatchingMessages(GuiceJamesServer jmapServer) {
        bartSendMessageToHomerWithSubject("aaaaa");
        bartSendMessageToHomerWithSubject("bbbbb");
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(2));

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

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        assertThat(getMessageContent(homerCredential, messageId)
            .getString("methodResponses[0][1].list[0].subject")).isEqualTo("aaaaa");
    }

    @Test
    void postShouldNotRestoreWhenNoMatchingMessages(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomerWithSubject("aaaaa");
        bartSendMessageToHomerWithSubject("bbbbb");
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(2));

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
        assertThat(listMessageIdsForAccount(homerCredential).size())
            .isEqualTo(0);
    }

    @Test
    void postShouldRestoreMatchingMessagesWhenQueryLimit(GuiceJamesServer jmapServer) {
        bartSendMessageToHomerWithSubject("aaaa");
        bartSendMessageToHomerWithSubject("aaaa");
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(2));

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

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));
    }

    @Test
    void imapMovedMessageShouldNotEndUpInTheVault(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX);

        testIMAPClient.moveFirstMessage(MAILBOX_NAME);

        //Moved messages should not be moved to the vault
        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));


        // No messages restored for bart
        assertThat(listMessageIdsForAccount(homerCredential).size()).isEqualTo(1);
    }

    @Test
    void jmapMovedMessageShouldNotEndUpInTheVault() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));
        String messageId = listMessageIdsForAccount(homerCredential).get(0);

        homerMovesTheMailInAnotherMailbox(messageId);

        //Moved messages should not be moved to the vault
        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));


        // No messages restored for bart
        assertThat(listMessageIdsForAccount(homerCredential).size()).isEqualTo(1);
    }

    @Test
    void restoreShouldNotImpactOtherUsers(GuiceJamesServer jmapServer) {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        bartDeletesMessages(listMessageIdsForAccount(bartCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(bartCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfUserFromVault(jmapServer, Username.of(BART))).hasSize(1));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        // No messages restored for bart
        assertThat(listMessageIdsForAccount(bartCredential).size()).isEqualTo(0);
    }

    @Test
    void restoredMessagesShouldNotBeRemovedFromTheVault(GuiceJamesServer jmapServer) {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(2));
    }

    @Test
    void vaultEndpointShouldNotRestoreItemsWhenTheVaultIsEmpty() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        restoreAllMessagesOfHomer();
        awaitSearchUpToDate();

        // No additional had been restored as the vault is empty
        assertThat(listMessageIdsForAccount(homerCredential).size())
            .isEqualTo(1);
    }

    @Test
    void vaultEndpointShouldNotRestoreMessageForSharee(GuiceJamesServer jmapServer) {
        // GIVEN a message in Homer's mailbox shared with Bart
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(bartCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);

        homerSharesHisMailboxWithBart();

        // WHEN Bart deletes the shared message
        bartDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        // THEN Bart should not restore anything from his own DMV
        restoreMessagesFor(BART);
        awaitSearchUpToDate();

        // No additional had been restored for Bart as the vault is empty
        assertThat(listMessageIdsForAccount(bartCredential).size())
            .isEqualTo(1);
    }

    @Test
    void vaultEndpointShouldRestoreMessageForSharer(GuiceJamesServer jmapServer) {
        // GIVEN a message in Homer's mailbox shared with Bart
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);

        homerSharesHisMailboxWithBart();

        // WHEN Bart deletes the shared message
        bartDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        // THEN Homer should be able to restore it from his DMV
        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String newMessageId = listMessageIdsForAccount(homerCredential).get(0);

        assertThat(getMessageContent(homerCredential, newMessageId)
            .getString("methodResponses[0][1].list[0].subject")).isEqualTo(SUBJECT);
    }

    @Test
    void vaultEndpointShouldRestoreMessageForOwnerWhenShareeCopiedSharedMessageToOwnMailbox(GuiceJamesServer jmapServer) {
        // GIVEN a message in Homer's mailbox shared with Bart
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);
        homerSharesHisMailboxWithBart();

        bartCopiesSharedMessageToOwnInbox();

        // WHEN Homer deletes the original shared-mailbox reference
        homerDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        // THEN Homer should still get a DMV entry for the message he lost access to
        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String restoredMessageId = getLatestMessageId(homerCredential, Role.RESTORED_MESSAGES);
        assertThat(getMessageContent(homerCredential, restoredMessageId)
            .getString("methodResponses[0][1].list[0].subject")).isEqualTo(SUBJECT);
    }

    @Test
    void vaultEndpointShouldNotRestoreMessageForOwnerWhenOwnerStillHasAnotherReference() {
        // GIVEN a message in Homer's mailbox shared with Bart
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);
        homerSharesHisMailboxWithBart();

        // Homer copies the shared message to his own mailbox
        homerCopiesSharedMessageToOwnerOnlyMailbox();

        // WHEN Bart deletes the shared message
        bartDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsInMailbox(homerCredential, ownerOnlyMailboxId.serialize())).hasSize(1));

        // THEN neither Homer nor Bart should restore anything from DMV
        restoreAllMessagesOfHomer();
        restoreMessagesFor(BART);
        awaitSearchUpToDate();

        assertThat(restoredMessagesCount(homerCredential)).isEqualTo(0);
        assertThat(restoredMessagesCount(bartCredential)).isEqualTo(0);
    }

    @Test
    void vaultEndpointShouldRestoreMailboxDeletionMessagesForOwnerAndNotForSharee(GuiceJamesServer jmapServer) throws Exception {
        // GIVEN a message in Homer's mailbox shared with Bart
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);
        homerSharesHisMailboxWithBart();

        // WHEN Homer deletes the shared mailbox
        homerDeletesMailbox(jmapServer);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        // THEN Homer should be able to restore the message, but Bart should not
        restoreAllMessagesOfHomer();
        restoreMessagesFor(BART);
        awaitSearchUpToDate();

        awaitRestoredMessagesCount(homerCredential, 1);
        assertThat(restoredMessagesCount(bartCredential)).isEqualTo(0);

        String restoredMessageId = getLatestMessageId(homerCredential, Role.RESTORED_MESSAGES);
        assertThat(getMessageContent(homerCredential, restoredMessageId)
            .getString("methodResponses[0][1].list[0].subject")).isEqualTo(SUBJECT);
    }

    @Test
    void vaultEndpointShouldRestoreMailboxDeletionMessageForOwnerWhenShareeStillHasAnotherReference(GuiceJamesServer jmapServer) throws Exception {
        // GIVEN a message in Homer's mailbox shared with Bart
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageId = listMessageIdsForAccount(homerCredential).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);
        homerSharesHisMailboxWithBart();
        bartCopiesSharedMessageToOwnInbox();

        // WHEN Homer deletes the shared mailbox
        homerDeletesMailbox(jmapServer);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        // THEN Homer should be able to restore the message, but Bart should not
        restoreAllMessagesOfHomer();
        restoreMessagesFor(BART);
        awaitSearchUpToDate();

        awaitRestoredMessagesCount(homerCredential, 1);
        assertThat(restoredMessagesCount(bartCredential)).isEqualTo(0);

        String restoredMessageId = getLatestMessageId(homerCredential, Role.RESTORED_MESSAGES);
        assertThat(getMessageContent(homerCredential, restoredMessageId)
            .getString("methodResponses[0][1].list[0].subject")).isEqualTo(SUBJECT);
    }

    @Tag(BasicFeature.TAG)
    @Test
    void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenJmapDeleteMessage(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));
        String messageIdOfHomer = listMessageIdsForAccount(homerCredential).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Tag(BasicFeature.TAG)
    @Test
    void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenImapDeleteMessage(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));
        String messageIdOfHomer = listMessageIdsForAccount(homerCredential).get(0);

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        testIMAPClient.expunge();

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Tag(BasicFeature.TAG)
    @Test
    public void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenImapDeletedMailbox(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));
        String messageIdOfHomer = listMessageIdsForAccount(homerCredential).get(0);

        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX);

        testIMAPClient.moveFirstMessage(MAILBOX_NAME);

        testIMAPClient.delete(MAILBOX_NAME);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Test
    void vaultExportShouldExportZipContainsOnlyMatchedMessages(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomerWithSubject(FIRST_SUBJECT);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));
        String firstMessageIdOfHomer = listMessageIdsForAccount(homerCredential).get(0);

        bartSendMessageToHomerWithSubject(SECOND_SUBJECT);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(2));

        ExportRequest exportRequest = ExportRequest
            .userExportFrom(HOMER)
            .exportTo(BART)
            .query("""
                {
                    "fieldName": "subject",
                    "operator": "equals",
                    "value": "%s"
                }
                """.formatted(FIRST_SUBJECT));
        String fileLocation = exportAndGetFileLocationFromLastMail(exportRequest, bartCredential);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.containsOnlyEntriesMatching(hasName(firstMessageIdOfHomer + ".eml"));
        }
    }

    @Test
    void vaultExportShouldExportEmptyZipWhenQueryDoesntMatch(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomerWithSubject(FIRST_SUBJECT);
        bartSendMessageToHomerWithSubject(SECOND_SUBJECT);
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(2));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(2));

        ExportRequest exportRequest = ExportRequest
            .userExportFrom(HOMER)
            .exportTo(BART)
            .query("{" +
                "  \"fieldName\": \"subject\"," +
                "  \"operator\": \"equals\"," +
                "  \"value\": \"non matching\"" +
                "}");
        String fileLocation = exportAndGetFileLocationFromLastMail(exportRequest, bartCredential);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultExportShouldExportEmptyZipWhenVaultIsEmpty() throws Exception {
        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultExportShouldResponseIdempotentSideEffect(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        String fileLocationFirstExport = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);
        String fileLocationSecondExport = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationFirstExport))) {
            zipAssert.hasSameContentWith(new FileInputStream(fileLocationSecondExport));
        }
    }

    @Test
    void vaultPurgeShouldMakeExportProduceEmptyZipWhenAllMessagesAreExpired(GuiceJamesServer jmapServer, UpdatableTickingClock clock) throws Exception {
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(3));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(3));

        clock.setInstant(TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION.toInstant());
        purgeVault(webAdminApi);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultPurgeShouldMakeExportProduceAZipWhenOneMessageIsNotExpired(GuiceJamesServer jmapServer, UpdatableTickingClock clock) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageIdOfNotExpiredMessage = listMessageIdsForAccount(homerCredential).get(0);

        clock.setInstant(TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION.toInstant());
        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(2));

        purgeVault(webAdminApi);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfNotExpiredMessage + ".eml"));
        }
    }

    @Test
    void vaultPurgeShouldMakeExportProduceZipWhenAllMessagesAreNotExpired(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(3));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(3));

        purgeVault(webAdminApi);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(3);
        }
    }

    @Test
    void vaultPurgeShouldNotAppendMessageToTheUserMailbox(GuiceJamesServer jmapServer, UpdatableTickingClock clock) {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        clock.setInstant(TWO_MONTH_AFTER_ONE_YEAR_EXPIRATION.toInstant());
        purgeVault(webAdminApi);

        assertThat(listMessageIdsForAccount(homerCredential))
            .hasSize(0);
    }

    @Test
    void vaultDeleteShouldDeleteMessageThenExportWithNoEntry(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageIdOfHomer = listMessageIdsForAccount(homerCredential).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        deleteFromVault(webAdminApi, HOMER, messageIdOfHomer);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultDeleteShouldNotDeleteEmptyVaultThenExportNoEntry() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageIdOfHomer = listMessageIdsForAccount(homerCredential).get(0);

        deleteFromVault(webAdminApi, HOMER, messageIdOfHomer);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultDeleteShouldNotDeleteNotMatchedMessageInVaultThenExportAnEntry(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));
        String messageIdOfHomer = listMessageIdsForAccount(homerCredential).get(0);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(bartCredential)).hasSize(1));
        String messageIdOfBart = listMessageIdsForAccount(bartCredential).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        deleteFromVault(webAdminApi, HOMER, messageIdOfBart);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartCredential);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Test
    void vaultDeleteShouldNotAppendMessageToTheUserMailbox(GuiceJamesServer jmapServer) {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String messageIdOfHomer = listMessageIdsForAccount(homerCredential).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerCredential));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        deleteFromVault(webAdminApi, HOMER, messageIdOfHomer);

        assertThat(listMessageIdsForAccount(homerCredential))
            .hasSize(0);
    }

    @Disabled("JAMES-4156")
    @Test
    public void vaultDeleteShouldDeleteAllMessagesHavingSameBlobContent(GuiceJamesServer jmapServer) throws Exception {
        bartSendMessageToHomerAndJack();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String homerInboxMessageId = listMessageIdsForAccount(homerCredential).get(0);
        homerDeletesMessages(ImmutableList.of(homerInboxMessageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        // the message same with homer's one in inbox
        String jackInboxMessageId = listMessageIdsForAccount(jackCredential).get(0);
        jackDeletesMessages(ImmutableList.of(jackInboxMessageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(jackCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfUserFromVault(jmapServer, Username.of(JACK))).hasSize(1));

        // delete from homer's vault, expecting the message contains the same blob in jack's vault will be deleted
        deleteFromVault(webAdminApi, HOMER, homerInboxMessageId);

        String fileLocationOfBartMessages = exportAndGetFileLocationFromLastMail(EXPORT_ALL_JACK_MESSAGES_TO_HOMER, homerCredential);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationOfBartMessages))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void vaultDeleteShouldNotDeleteAllMessagesHavingSameBlobContentWhenMessageNotDeletedWithinTheSameMonth(GuiceJamesServer jmapServer, UpdatableTickingClock clock) throws Exception {
        bartSendMessageToHomerAndJack();
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(1));

        String homerInboxMessageId = listMessageIdsForAccount(homerCredential).get(0);
        homerDeletesMessages(ImmutableList.of(homerInboxMessageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(homerCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfHomerFromVault(jmapServer)).hasSize(1));

        // one year later, delete jack's message
        clock.setInstant(NOW.plusYears(1).toInstant());
        // the message same with homer's one in inbox
        String jackInboxMessageId = listMessageIdsForAccount(jackCredential).get(0);
        jackDeletesMessages(ImmutableList.of(jackInboxMessageId));
        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(jackCredential)).hasSize(0));

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessagesOfUserFromVault(jmapServer, Username.of(JACK))).hasSize(1));

        // delete from homer's vault, expecting jack's vault still be intact
        deleteFromVault(webAdminApi, HOMER, homerInboxMessageId);

        String fileLocationOfBartMessages = exportAndGetFileLocationFromLastMail(EXPORT_ALL_JACK_MESSAGES_TO_HOMER, homerCredential);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationOfBartMessages))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> hasName(jackInboxMessageId + ".eml"));
        }
    }

    private String exportAndGetFileLocationFromLastMail(ExportRequest exportRequest, UserCredential shareeCredential) {
        int currentNumberOfMessages = listMessageIdsForAccount(shareeCredential).size();
        exportVaultContent(webAdminApi, exportRequest);

        WAIT_TWO_MINUTES.untilAsserted(() -> assertThat(listMessageIdsForAccount(shareeCredential)).hasSize(currentNumberOfMessages + 1));
        String exportingMessageId = getLatestMessageId(shareeCredential, Role.INBOX);

        return exportedFileLocationFromMailHeader(exportingMessageId, shareeCredential);
    }

    private String exportedFileLocationFromMailHeader(String messageId, UserCredential userCredential) {
        List<Map<String, String>> headers = with()
            .auth().basic(userCredential.username().asString(), userCredential.password())
            .body("""
                {
                    "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
                    "methodCalls": [
                        ["Email/get", {
                            "accountId": "%s",
                            "ids": ["%s"],
                            "properties":["bodyStructure"],
                            "bodyProperties":["name", "type","headers"]
                        }, "c2"]
                    ]
                }
                """.formatted(userCredential.accountId(), messageId))
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .extract()
            .body()
            .path("methodResponses[0][1].list[0].bodyStructure.headers");

        return headers.stream()
            .filter(header -> header.get("name").equals("corresponding-file"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No corresponding-file header found"))
            .get("value").trim();
    }

    private void homerSharesHisMailboxWithBart() {
        with()
            .auth().basic(homerCredential.username().asString(), homerCredential.password())
            .body("""
                    {
                        "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
                        "methodCalls": [
                            [
                                "Mailbox/set",
                                {
                                    "accountId": "%s",
                                    "update": {
                                        "%s": {
                                            "sharedWith": {
                                                "%s":["r", "l", "w", "t"]
                                            }
                                        }
                                    }
                                },
                                "c1"
                            ]
                        ]
                    }
                """.formatted(homerCredential.accountId(), otherMailboxId.serialize(), BART))
            .post("/jmap");
    }

    private void bartSendMessageToHomer() {
        bartSendMessageToHomerWithSubject(SUBJECT);
    }

    private void bartSendMessageToHomerAndJack() {
        String outboxId = getOutboxId(bartCredential);

        String requestBody =
            "{" +
                "    \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\", \"urn:ietf:params:jmap:submission\"]," +
                "    \"methodCalls\": [" +
                "        [\"Email/set\", {" +
                "            \"accountId\": \"" + bartCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"e1526\": {" +
                "                    \"mailboxIds\": { \"" + outboxId + "\": true }," +
                "                    \"subject\": \"" + SUBJECT + "\"," +
                "                    \"htmlBody\": [{" +
                "                        \"partId\": \"a49d\"," +
                "                        \"type\": \"text/html\"" +
                "                    }]," +
                "                    \"bodyValues\": {" +
                "                        \"a49d\": {" +
                "                            \"value\": \"Test <b>body</b>, HTML version\"" +
                "                        }" +
                "                    }," +
                "                    \"to\": [{" +
                "                        \"email\": \"" + HOMER + "\"" +
                "                    }, {" +
                "                        \"email\": \"" + JACK + "\"" +
                "                    }]," +
                "                    \"from\": [{" +
                "                        \"email\": \"" + BART + "\"" +
                "                    }]" +
                "                }" +
                "            }" +
                "        }, \"c1\"]," +
                "        [\"Email/get\", {" +
                "            \"accountId\": \"" + bartCredential.accountId() + "\"," +
                "            \"ids\": [\"#e1526\"]," +
                "            \"properties\": [\"sentAt\"]" +
                "        }, \"c2\"]," +
                "        [\"EmailSubmission/set\", {" +
                "            \"accountId\": \"" + bartCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"k1490\": {" +
                "                    \"emailId\": \"#e1526\"," +
                "                    \"envelope\": {" +
                "                        \"mailFrom\": {\"email\": \"" + BART + "\"}," +
                "                        \"rcptTo\": [{" +
                "                            \"email\": \"" + HOMER + "\"" +
                "                        }, {" +
                "                            \"email\": \"" + JACK + "\"" +
                "                        }]" +
                "                    }" +
                "                }" +
                "            }" +
                "        }, \"c3\"]" +
                "    ]" +
                "}";

        with()
            .auth().basic(bartCredential.username().asString(), bartCredential.password())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("methodResponses[2][1].created", Matchers.is(notNullValue()));
    }

    private void bartSendMessageToHomerWithSubject(String subject) {
        String outboxId = getOutboxId(bartCredential);
        String requestBody =
            "{" +
                "    \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\", \"urn:ietf:params:jmap:submission\"]," +
                "    \"methodCalls\": [" +
                "        [\"Email/set\", {" +
                "            \"accountId\": \"" + bartCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"e1526\": {" +
                "                    \"mailboxIds\": { \"" + outboxId + "\": true }," +
                "                    \"subject\": \"" + subject + "\"," +
                "                    \"htmlBody\": [{" +
                "                        \"partId\": \"a49d\"," +
                "                        \"type\": \"text/html\"" +
                "                    }]," +
                "                    \"bodyValues\": {" +
                "                        \"a49d\": {" +
                "                            \"value\": \"Test <b>body</b>, HTML version\"" +
                "                        }" +
                "                    }," +
                "                    \"to\": [{\"email\": \"" + HOMER + "\"}]," +
                "                    \"from\": [{\"email\": \"" + BART + "\"}]" +
                "                }" +
                "            }" +
                "        }, \"c1\"]," +
                "        [\"Email/get\", {" +
                "            \"accountId\": \"" + bartCredential.accountId() + "\"," +
                "            \"ids\": [\"#e1526\"]," +
                "            \"properties\": [\"sentAt\"]" +
                "        }, \"c2\"]," +
                "        [\"EmailSubmission/set\", {" +
                "            \"accountId\": \"" + bartCredential.accountId() + "\"," +
                "            \"create\": {" +
                "                \"k1490\": {" +
                "                    \"emailId\": \"#e1526\"," +
                "                    \"envelope\": {" +
                "                        \"mailFrom\": {\"email\": \"" + BART + "\"}," +
                "                        \"rcptTo\": [{\"email\": \"" + HOMER + "\"}]" +
                "                    }" +
                "                }" +
                "            }" +
                "        }, \"c3\"]" +
                "    ]" +
                "}";

        with()
            .auth().basic(bartCredential.username().asString(), bartCredential.password())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("methodResponses[2][1].created", Matchers.is(notNullValue()));
    }

    private void homerDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(homerCredential, idsToDestroy);
        // Grace period for the vault
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void bartDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(bartCredential, idsToDestroy);
    }

    private void jackDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(jackCredential, idsToDestroy);
    }

    private void restoreAllMessagesOfHomer() {
        restoreMessagesFor(HOMER);
    }

    private void restoreMessagesFor(String user) {
        restoreMessagesForUserWithQuery(webAdminApi, user, MATCH_ALL_QUERY);
    }

    private int restoredMessagesCount(UserCredential credential) {
        return getAllMailboxesIds(credential).stream()
            .filter(mailbox -> Role.RESTORED_MESSAGES.serialize().equals(mailbox.get("role")))
            .findFirst()
            .map(mailbox -> listMessageIdsInMailbox(credential, mailbox.get("id")).size())
            .orElse(0);
    }

    private void awaitRestoredMessagesCount(UserCredential credential, int expectedCount) {
        Awaitility.await()
            .atMost(Duration.ofMinutes(1))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertThat(restoredMessagesCount(credential)).isEqualTo(expectedCount));
    }

    private void bartCopiesSharedMessageToOwnInbox() {
        try {
            mailboxProbe.copy(
                Username.of(BART),
                new MailboxPath("#private", Username.of(HOMER), MAILBOX_NAME),
                MailboxPath.forUser(Username.of(BART), DefaultMailboxes.INBOX),
                MessageUid.of(1));
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy shared message to Bart inbox", e);
        }
    }

    private void homerCopiesSharedMessageToOwnerOnlyMailbox() {
        try {
            mailboxProbe.copy(
                Username.of(HOMER),
                new MailboxPath("#private", Username.of(HOMER), MAILBOX_NAME),
                new MailboxPath("#private", Username.of(HOMER), OWNER_ONLY_MAILBOX_NAME),
                MessageUid.of(1));
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy shared message to Homer owner-only mailbox", e);
        }
    }

    private void homerMovesTheMailInAnotherMailbox(String messageId) {
        given()
            .auth().basic(homerCredential.username().asString(), homerCredential.password())
            .body("""
                {
                    "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                    "methodCalls": [
                        ["Email/set", {
                            "accountId": "%s",
                            "update": {
                                "%s":{
                                    "mailboxIds": { "%s" : true}
                                }
                            }
                        }, "c1"]]
                }""".formatted(homerCredential.accountId(), messageId, otherMailboxId.serialize()))
            .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(JSON);
    }

    private void homerDeletesMailbox(GuiceJamesServer jmapServer) throws Exception {
        testIMAPClient.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(TestIMAPClient.INBOX);

        testIMAPClient.delete(MAILBOX_NAME);
    }

    private boolean homerHasMailboxWithRole(Role role) {
        return getAllMailboxesIds(homerCredential).stream()
            .filter(mailbox -> mailbox.get("role") != null)
            .anyMatch(mailbox -> mailbox.get("role").equals(role.serialize())
                && mailbox.get("name").equals(role.getDefaultMailbox()));
    }

    private List<DeletedMessage> listMessagesOfHomerFromVault(GuiceJamesServer jmapServer) {
        return listMessagesOfUserFromVault(jmapServer, Username.of(HOMER));
    }

    private List<DeletedMessage> listMessagesOfUserFromVault(GuiceJamesServer jmapServer, Username username) {
        return jmapServer.getProbe(DeletedMessageVaultProbe.class).search(username, Query.ALL);
    }
}
