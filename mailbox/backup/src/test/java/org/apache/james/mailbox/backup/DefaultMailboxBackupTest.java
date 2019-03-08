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


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.ZipAssert.EntryChecks;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.inmemory.MemoryMailboxManagerProvider;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

class DefaultMailboxBackupTest implements MailboxMessageFixture {

    private static final String USER = "user";
    private static final String OTHER_USER = "otherUser";

    private static final User USER1 = User.fromUsername(USER);
    private static final MailboxPath MAILBOX_PATH_USER1_MAILBOX1 = MailboxPath.forUser(USER, MAILBOX_1_NAME);
    private static final MailboxPath MAILBOX_PATH_USER1_MAILBOX2 = MailboxPath.forUser(USER, MAILBOX_2_NAME);
    private static final MailboxPath MAILBOX_PATH_OTHER_USER_MAILBOX1 = MailboxPath.forUser(OTHER_USER, MAILBOX_OTHER_USER_NAME);
    private static final HashSet<PreDeletionHook> PRE_DELETION_HOOKS = new HashSet<>();

    private static final int BUFFER_SIZE = 4096;

    private final ArchiveService archiveService = new Zipper();

    private MailboxManager mailboxManager;
    private DefaultMailboxBackup backup;

    @BeforeEach
    void beforeEach() {
        mailboxManager = MemoryMailboxManagerProvider.provideMailboxManager(PRE_DELETION_HOOKS);
        backup = new DefaultMailboxBackup(mailboxManager, archiveService);
    }

    private void createMailBoxWithMessage(MailboxSession session, MailboxPath mailboxPath, MessageManager.AppendCommand... messages) throws Exception {
        MailboxId mailboxId = mailboxManager.createMailbox(mailboxPath, session).get();
        Arrays.stream(messages).forEach(Throwing.consumer(message ->
                mailboxManager.getMailbox(mailboxId, session).appendMessage(message, session)
            )
        );
    }

    @Test
    void doBackupWithoutMailboxShouldStoreEmptyBackup() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USER1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching();
        }
    }

    @Test
    void doBackupWithoutMessageShouldStoreAnArchiveWithOnlyOneEntry() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        MailboxSession session = mailboxManager.createSystemSession(USER);
        createMailBoxWithMessage(session, MAILBOX_PATH_USER1_MAILBOX1);

        backup.backupAccount(USER1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory());
        }
    }

    @Test
    void doBackupWithOneMessageShouldStoreAnArchiveWithTwoEntries() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        MailboxSession session = mailboxManager.createSystemSession(USER);
        createMailBoxWithMessage(session, MAILBOX_PATH_USER1_MAILBOX1, getMessage1AppendCommand());

        backup.backupAccount(USER1, destination);

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
        MailboxSession session = mailboxManager.createSystemSession(USER);
        createMailBoxWithMessage(session, MAILBOX_PATH_USER1_MAILBOX1, getMessage1AppendCommand());
        createMailBoxWithMessage(session, MAILBOX_PATH_USER1_MAILBOX2);

        backup.backupAccount(USER1, destination);

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
        MailboxSession session = mailboxManager.createSystemSession(USER);
        MailboxSession otherSession = mailboxManager.createSystemSession(OTHER_USER);

        createMailBoxWithMessage(session, MAILBOX_PATH_USER1_MAILBOX1, getMessage1AppendCommand());
        createMailBoxWithMessage(otherSession, MAILBOX_PATH_OTHER_USER_MAILBOX1, getMessage1OtherUserAppendCommand());

        backup.backupAccount(USER1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(
                EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
                EntryChecks.hasName(MESSAGE_ID_1.serialize()).hasStringContent(MESSAGE_CONTENT_1)
            );
        }
    }

    private MessageManager.AppendCommand getMessage1AppendCommand() throws IOException {
        return MessageManager.AppendCommand.builder().withFlags(flags1).build(MESSAGE_1.getFullContent());
    }

    private MessageManager.AppendCommand getMessage1OtherUserAppendCommand() throws IOException {
        return MessageManager.AppendCommand.builder().withFlags(flags1).build(MESSAGE_1_OTHER_USER.getFullContent());
    }

}
