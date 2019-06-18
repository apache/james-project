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
import static io.restassured.config.ParamConfig.UpdateStrategy.REPLACE;
import static org.apache.james.jmap.DeletedMessagesVaultRequests.deleteFromVault;
import static org.apache.james.jmap.DeletedMessagesVaultRequests.exportVaultContent;
import static org.apache.james.jmap.DeletedMessagesVaultRequests.purgeVault;
import static org.apache.james.jmap.DeletedMessagesVaultRequests.restoreMessagesForUserWithQuery;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapCommonRequests.deleteMessages;
import static org.apache.james.jmap.JmapCommonRequests.getLastMessageId;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.TestingConstants.calmlyAwait;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.linshare.LinshareFixture.MATCH_ALL_QUERY;
import static org.apache.james.mailbox.backup.ZipAssert.EntryChecks.hasName;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.ExportRequest;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.backup.ZipAssert.EntryChecks;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.JmapGuiceProbe;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import io.restassured.config.ParamConfig;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

public abstract class DeletedMessagesVaultTest {
    private static final Instant NOW = Instant.now();
    private static final Instant ONE_DAY_AFTER_ONE_YEAR_EXPIRATION = NOW.plus(366, ChronoUnit.DAYS);
    private static final String FIRST_SUBJECT = "first subject";
    private static final String SECOND_SUBJECT = "second subject";
    private static final String HOMER = "homer@" + DOMAIN;
    private static final String BART = "bart@" + DOMAIN;
    private static final String PASSWORD = "password";
    private static final String BOB_PASSWORD = "bobPassword";
    private static final ConditionFactory WAIT_TWO_MINUTES = calmlyAwait.atMost(Duration.TWO_MINUTES);
    private static final String SUBJECT = "This mail will be restored from the vault!!";
    private static final String MAILBOX_NAME = "toBeDeleted";
    private static final ExportRequest EXPORT_ALL_HOMER_MESSAGES_TO_BART = ExportRequest
        .userExportFrom(HOMER)
        .exportTo(BART)
        .query(MATCH_ALL_QUERY);

    private MailboxId otherMailboxId;

    protected abstract GuiceJamesServer createJmapServer(FileSystem fileSystem, Clock clock) throws IOException;

    protected abstract void awaitSearchUpToDate();

    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private AccessToken homerAccessToken;
    private AccessToken bartAccessToken;
    private GuiceJamesServer jmapServer;
    private RequestSpecification webAdminApi;
    private UpdatableTickingClock clock;
    private FileSystem fileSystem;

    @Before
    public void setup() throws Throwable {
        clock = new UpdatableTickingClock(NOW);
        fileSystem = new FileSystemImpl(new JamesServerResourceLoader(tempFolder.getRoot().getPath()));
        jmapServer = createJmapServer(fileSystem, clock);
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

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort())
            .config(WebAdminUtils.defaultConfig()
                .paramConfig(new ParamConfig(REPLACE, REPLACE, REPLACE)));
    }

    @After
    public void tearDown() throws Exception {
        jmapServer.stop();
    }

    @Category(BasicFeature.class)
    @Test
    public void vaultEndpointShouldRestoreJmapDeletedEmail() {
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
    public void vaultEndpointShouldRestoreImapDeletedEmail() throws Exception {
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

    @Category(BasicFeature.class)
    @Test
    public void vaultEndpointShouldRestoreImapDeletedMailbox() throws Exception {
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
    public void postShouldRestoreMatchingMessages() {
        bartSendMessageToHomerWithSubject("aaaaa");
        bartSendMessageToHomerWithSubject("bbbbb");
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 2);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

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
            .body(ARGUMENTS + ".list.subject", hasItem("aaaaa"));
    }

    @Test
    public void postShouldNotRestoreWhenNoMatchingMessages() throws Exception {
        bartSendMessageToHomerWithSubject("aaaaa");
        bartSendMessageToHomerWithSubject("bbbbb");
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 2);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

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


        Thread.sleep(Duration.FIVE_SECONDS.getValueInMS());

        // No additional had been restored for Bart as the vault is empty
        assertThat(listMessageIdsForAccount(homerAccessToken).size())
            .isEqualTo(0);
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
    public void vaultEndpointShouldNotRestoreItemsWhenTheVaultIsEmpty() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        restoreAllMessagesOfHomer();
        awaitSearchUpToDate();

        // No additional had been restored as the vault is empty
        assertThat(listMessageIdsForAccount(homerAccessToken).size())
            .isEqualTo(1);
    }

    @Test
    public void vaultEndpointShouldNotRestoreMessageForSharee() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(bartAccessToken).size() == 1);

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);

        homerSharesHisMailboxWithBart();

        bartDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        restoreMessagesFor(BART);
        awaitSearchUpToDate();

        // No additional had been restored for Bart as the vault is empty
        assertThat(listMessageIdsForAccount(bartAccessToken).size())
            .isEqualTo(1);
    }

    @Test
    public void vaultEndpointShouldRestoreMessageForSharer() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String messageId = listMessageIdsForAccount(homerAccessToken).get(0);
        homerMovesTheMailInAnotherMailbox(messageId);

        homerSharesHisMailboxWithBart();

        bartDeletesMessages(ImmutableList.of(messageId));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        restoreAllMessagesOfHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String newMessageId = listMessageIdsForAccount(homerAccessToken).get(0);
        given()
            .header("Authorization", homerAccessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + newMessageId + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
            .then()
            .statusCode(200)
            .log().ifValidationFails()
            .body(ARGUMENTS + ".list.subject", hasItem(SUBJECT));
    }

    @Category(BasicFeature.class)
    @Test
    public void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenJmapDeleteMessage() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);
        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> EntryChecks.hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Category(BasicFeature.class)
    @Test
    public void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenImapDeleteMessage() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);
        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        imapMessageReader.expunge();

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> EntryChecks.hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Category(BasicFeature.class)
    @Test
    public void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenImapDeletedMailbox() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);
        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, PASSWORD)
            .select(IMAPMessageReader.INBOX);

        imapMessageReader.moveFirstMessage(MAILBOX_NAME);

        imapMessageReader.delete(MAILBOX_NAME);

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> EntryChecks.hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Test
    public void vaultExportShouldExportZipContainsOnlyMatchedMessages() throws Exception {
        bartSendMessageToHomerWithSubject(FIRST_SUBJECT);
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);
        String firstMessageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        bartSendMessageToHomerWithSubject(SECOND_SUBJECT);
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 2);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

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
    public void vaultExportShouldExportEmptyZipWhenQueryDoesntMatch() throws Exception {
        bartSendMessageToHomerWithSubject(FIRST_SUBJECT);
        bartSendMessageToHomerWithSubject(SECOND_SUBJECT);
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 2);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

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
    public void vaultExportShouldExportEmptyZipWhenVaultIsEmpty() throws Exception {
        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    public void vaultExportShouldResponseIdempotentSideEffect() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        String fileLocationFirstExport = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        String fileLocationSecondExport = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);

        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocationFirstExport))) {
            zipAssert.hasSameContentWith(new FileInputStream(fileLocationSecondExport));
        }
    }

    @Test
    public void vaultPurgeShouldMakeExportProduceEmptyZipWhenAllMessagesAreExpired() throws Exception {
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 3);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        clock.setInstant(ONE_DAY_AFTER_ONE_YEAR_EXPIRATION);
        purgeVault(webAdminApi);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    public void vaultPurgeShouldMakeExportProduceAZipWhenOneMessageIsNotExpired() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String messageIdOfNotExpiredMessage = listMessageIdsForAccount(homerAccessToken).get(0);

        clock.setInstant(ONE_DAY_AFTER_ONE_YEAR_EXPIRATION);
        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        purgeVault(webAdminApi);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> EntryChecks.hasName(messageIdOfNotExpiredMessage + ".eml"));
        }
    }

    @Test
    public void vaultPurgeShouldMakeExportProduceZipWhenAllMessagesAreNotExpired() throws Exception {
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 3);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        purgeVault(webAdminApi);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(3);
        }
    }

    @Test
    public void vaultPurgeShouldNotAppendMessageToTheUserMailbox() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        clock.setInstant(ONE_DAY_AFTER_ONE_YEAR_EXPIRATION);
        purgeVault(webAdminApi);

        assertThat(listMessageIdsForAccount(homerAccessToken))
            .hasSize(0);
    }

    @Test
    public void vaultDeleteShouldDeleteMessageThenExportWithNoEntry() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        deleteFromVault(webAdminApi, HOMER, messageIdOfHomer);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    public void vaultDeleteShouldNotDeleteEmptyVaultThenExportNoEntry() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        deleteFromVault(webAdminApi, HOMER, messageIdOfHomer);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    public void vaultDeleteShouldNotDeleteNotMatchedMessageInVaultThenExportAnEntry() throws Exception {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String messageIdOfBart = listMessageIdsForAccount(bartAccessToken).get(0);
        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        deleteFromVault(webAdminApi, HOMER, messageIdOfBart);

        String fileLocation = exportAndGetFileLocationFromLastMail(EXPORT_ALL_HOMER_MESSAGES_TO_BART, bartAccessToken);
        try (ZipAssert zipAssert = assertThatZip(new FileInputStream(fileLocation))) {
            zipAssert.hasEntriesSize(1)
                .allSatisfies(entry -> EntryChecks.hasName(messageIdOfHomer + ".eml"));
        }
    }

    @Test
    public void vaultDeleteShouldNotAppendMessageToTheUserMailbox() {
        bartSendMessageToHomer();
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        String messageIdOfHomer = listMessageIdsForAccount(homerAccessToken).get(0);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        deleteFromVault(webAdminApi, HOMER, messageIdOfHomer);

        assertThat(listMessageIdsForAccount(homerAccessToken))
            .hasSize(0);
    }

    private String exportAndGetFileLocationFromLastMail(ExportRequest exportRequest, AccessToken shareeAccessToken) {
        int currentNumberOfMessages = listMessageIdsForAccount(shareeAccessToken).size();
        exportVaultContent(webAdminApi, exportRequest);

        WAIT_TWO_MINUTES.until(() -> listMessageIdsForAccount(shareeAccessToken).size() == currentNumberOfMessages + 1);
        String exportingMessageId = getLastMessageId(shareeAccessToken);

        return exportedFileLocationFromMailHeader(exportingMessageId, shareeAccessToken);
    }

    private String exportedFileLocationFromMailHeader(String messageId, AccessToken accessToken) {
        return with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
                .post("/jmap")
            .jsonPath()
                .getList(ARGUMENTS + ".list.headers.corresponding-file", String.class)
                .get(0);
    }

    private void homerSharesHisMailboxWithBart() {
        with()
            .header("Authorization", homerAccessToken.serialize())
            .body("[" +
                "  [ \"setMailboxes\"," +
                "    {" +
                "      \"update\": {" +
                "        \"" + otherMailboxId.serialize() + "\" : {" +
                "          \"sharedWith\" : {\"" + BART + "\": [\"a\", \"w\", \"r\"]}" +
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
            .header("Authorization", bartAccessToken.serialize())
            .body(updateRequestBody)
            .when()
            .post("/jmap");
    }
}
