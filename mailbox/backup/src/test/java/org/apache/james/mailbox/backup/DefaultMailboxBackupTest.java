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
package org.apache.james.mailbox.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.ZipAssert.EntryChecks;
import org.apache.james.mailbox.backup.zip.ZipArchivesLoader;
import org.apache.james.mailbox.backup.zip.Zipper;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

class DefaultMailboxBackupTest implements MailboxMessageFixture {
    private static final int BUFFER_SIZE = 4096;
    private static final String EXPECTED_ANNOTATIONS_DIR = "annotations";

    private final ArchiveService archiveService = new Zipper();
    private final MailArchivesLoader archiveLoader = new ZipArchivesLoader();

    private MailArchiveRestorer archiveRestorer;
    private MailboxManager mailboxManager;
    private DefaultMailboxBackup backup;

    private MailboxSession sessionUser;
    private MailboxSession sessionOtherUser;

    @BeforeEach
    void beforeEach() throws Exception {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        archiveRestorer = new ZipMailArchiveRestorer(mailboxManager, archiveLoader);
        backup = new DefaultMailboxBackup(mailboxManager, archiveService, archiveRestorer);
        sessionUser = mailboxManager.createSystemSession(USER);
        sessionOtherUser = mailboxManager.createSystemSession(OTHER_USER);
    }

    private void createMailboxWithMessages(MailboxSession session, MailboxPath mailboxPath, MessageManager.AppendCommand... messages) throws Exception {
        MailboxId mailboxId = mailboxManager.createMailbox(mailboxPath, session).get();
        Arrays.stream(messages).forEach(Throwing.consumer(message ->
                mailboxManager.getMailbox(mailboxId, session).appendMessage(message, session)
            )
        );
    }

    private void createMailbox(MailboxSession session, MailboxPath mailboxPath) throws Exception {
        createMailboxWithMessages(session, mailboxPath);
    }

    @Test
    void doBackupWithoutMailboxShouldStoreEmptyBackup() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USERNAME_1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching();
        }
    }

    @Test
    void doBackupWithoutMessageShouldStoreAnArchiveWithOnlyOneEntry() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        createMailbox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);

        backup.backupAccount(USERNAME_1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory());
        }
    }

    @Test
    void doBackupMailboxWithAnnotationShouldStoreAnArchiveWithMailboxAndAnnotation() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        createMailbox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);
        mailboxManager.updateAnnotations(MAILBOX_PATH_USER1_MAILBOX1, sessionUser, WITH_ANNOTATION_1);

        backup.backupAccount(USERNAME_1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
                EntryChecks.hasName(MAILBOX_1_NAME + "/" + EXPECTED_ANNOTATIONS_DIR + "/").isDirectory(),
                EntryChecks.hasName(MAILBOX_1_NAME + "/" + EXPECTED_ANNOTATIONS_DIR + "/" + ANNOTATION_1_KEY.asString()).hasStringContent(ANNOTATION_1_CONTENT)
            );
        }
    }

    @Test
    void doBackupWithOneMessageShouldStoreAnArchiveWithTwoEntries() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        createMailboxWithMessages(sessionUser, MAILBOX_PATH_USER1_MAILBOX1, getMessage1AppendCommand());

        backup.backupAccount(USERNAME_1, destination);

        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(
                EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
                EntryChecks.hasName(MESSAGE_ID_1.serialize()).hasStringContent(MESSAGE_CONTENT_1)
            );
        }
    }

    @Test
    void doBackupWithTwoMailboxesAndOneMessageShouldStoreAnArchiveWithThreeEntries() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        createMailboxWithMessages(sessionUser, MAILBOX_PATH_USER1_MAILBOX1, getMessage1AppendCommand());
        createMailbox(sessionUser, MAILBOX_PATH_USER1_MAILBOX2);

        backup.backupAccount(USERNAME_1, destination);

        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(
                EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
                EntryChecks.hasName(MAILBOX_2_NAME + "/").isDirectory(),
                EntryChecks.hasName(MESSAGE_ID_1.serialize()).hasStringContent(MESSAGE_CONTENT_1)
            );
        }
    }

    @Test
    void doBackupShouldOnlyArchiveTheMailboxOfTheUser() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);

        createMailboxWithMessages(sessionUser, MAILBOX_PATH_USER1_MAILBOX1, getMessage1AppendCommand());
        createMailboxWithMessages(sessionOtherUser, MAILBOX_PATH_OTHER_USER_MAILBOX1, getMessage1OtherUserAppendCommand());

        backup.backupAccount(USERNAME_1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(
                EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
                EntryChecks.hasName(MESSAGE_ID_1.serialize()).hasStringContent(MESSAGE_CONTENT_1)
            );
        }
    }

    @Test
    void backupEmptyAccountThenRestoringItInUser2AccountShouldCreateNoElements() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USERNAME_1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        MailboxBackup.BackupStatus backupStatus = Mono.from(backup.restore(USERNAME_2, source)).block();
        assertThat(backupStatus).isEqualTo(MailboxBackup.BackupStatus.DONE);
        List<DefaultMailboxBackup.MailAccountContent> content = backup.getAccountContentForUser(sessionOtherUser);

        assertThat(content).isEmpty();
    }

    @Test
    void backupAccountWithOneMailboxThenRestoringItInUser2AccountShouldCreateOneMailbox() throws Exception {
        createMailbox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USERNAME_1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        MailboxBackup.BackupStatus backupStatus = Mono.from(backup.restore(USERNAME_2, source)).block();
        assertThat(backupStatus).isEqualTo(MailboxBackup.BackupStatus.DONE);

        List<DefaultMailboxBackup.MailAccountContent> content = backup.getAccountContentForUser(sessionOtherUser);

        assertThat(content).hasSize(1);
        DefaultMailboxBackup.MailAccountContent mailAccountContent = content.get(0);
        Mailbox mailbox = mailAccountContent.getMailboxWithAnnotations().mailbox;
        assertThat(mailbox.getName()).isEqualTo(MAILBOX_1_NAME);
        assertThat(mailAccountContent.getMessages().count()).isEqualTo(0);
    }

    @Test
    void restoringAccountInNonEmptyAccountShouldNotBeDone() throws Exception {
        createMailbox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);
        createMailbox(sessionOtherUser, MAILBOX_PATH_OTHER_USER_MAILBOX1);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USERNAME_1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        MailboxBackup.BackupStatus backupStatus = Mono.from(backup.restore(USERNAME_2, source)).block();

        assertThat(backupStatus).isEqualTo(MailboxBackup.BackupStatus.NON_EMPTY_RECEIVER_ACCOUNT);
    }

    @Test
    void backupAccountWithTwoMailboxesThenRestoringItInUser2AccountShouldCreateTwoMailboxes() throws Exception {
        createMailbox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);
        createMailbox(sessionUser, MAILBOX_PATH_USER1_MAILBOX2);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USERNAME_1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        MailboxBackup.BackupStatus backupStatus = Mono.from(backup.restore(USERNAME_2, source)).block();

        assertThat(backupStatus).isEqualTo(MailboxBackup.BackupStatus.DONE);

        List<DefaultMailboxBackup.MailAccountContent> content = backup.getAccountContentForUser(sessionOtherUser);

        assertThat(content).hasSize(2);
        DefaultMailboxBackup.MailAccountContent contentMailbox1 = content.get(0);
        Mailbox mailbox1 = contentMailbox1.getMailboxWithAnnotations().mailbox;
        assertThat(mailbox1.getName()).isEqualTo(MAILBOX_1_NAME);
        assertThat(contentMailbox1.getMessages().count()).isEqualTo(0);

        DefaultMailboxBackup.MailAccountContent contentMailbox2 = content.get(1);
        Mailbox mailbox2 = contentMailbox2.getMailboxWithAnnotations().mailbox;
        assertThat(mailbox2.getName()).isEqualTo(MAILBOX_2_NAME);
        assertThat(contentMailbox2.getMessages().count()).isEqualTo(0);
    }

    private MessageManager.AppendCommand getMessage1AppendCommand() throws IOException {
        return MessageManager.AppendCommand.builder().withFlags(flags1).build(MESSAGE_1.getFullContent());
    }

    private MessageManager.AppendCommand getMessage1OtherUserAppendCommand() throws IOException {
        return MessageManager.AppendCommand.builder().withFlags(flags1).build(MESSAGE_1_OTHER_USER.getFullContent());
    }

}
