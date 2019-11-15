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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.NoSuchElementException;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.zip.ZipArchivesLoader;
import org.apache.james.mailbox.backup.zip.Zipper;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

class ZipArchivesLoaderTest implements MailboxMessageFixture {
    private static final int BUFFER_SIZE = 4096;

    private final ArchiveService archiveService = new Zipper();
    private final MailArchivesLoader archiveLoader = new ZipArchivesLoader();

    private MailArchiveRestorer archiveRestorer;
    private MailboxManager mailboxManager;
    private DefaultMailboxBackup backup;

    @BeforeEach
    void beforeEach() {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        archiveRestorer = new ZipMailArchiveRestorer(mailboxManager, archiveLoader);
        backup = new DefaultMailboxBackup(mailboxManager, archiveService, archiveRestorer);
    }

    private void createMailBoxWithMessage(MailboxPath mailboxPath, MailboxMessage... messages) throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());
        MailboxId mailboxId = mailboxManager.createMailbox(mailboxPath, session).get();
        Arrays.stream(messages).forEach(Throwing.consumer(message ->
            {
                MessageManager.AppendCommand appendCommand = MessageManager.AppendCommand.builder()
                    .withFlags(message.createFlags())
                    .build(message.getFullContent());
                mailboxManager.getMailbox(mailboxId, session).appendMessage(appendCommand, session);
            }
            )
        );
    }

    @Test
    void mailAccountIteratorFromEmptyArchiveShouldThrowNoSuchElementException() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USERNAME_1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        MailArchiveIterator mailArchiveIterator = archiveLoader.load(source);

        assertThat(mailArchiveIterator.hasNext()).isEqualTo(false);
        assertThatThrownBy(() -> mailArchiveIterator.next()).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void callingNextSeveralTimeOnAnEmptyIteratorShouldThrowNoSuchElementException()  throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USERNAME_1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        MailArchiveIterator mailArchiveIterator = archiveLoader.load(source);

        assertThat(mailArchiveIterator.hasNext()).isEqualTo(false);
        assertThatThrownBy(() -> mailArchiveIterator.next()).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> mailArchiveIterator.next()).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> mailArchiveIterator.next()).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void mailAccountIteratorFromArchiveWithOneMailboxShouldContainOneMailbox() throws Exception {
        createMailBoxWithMessage(MAILBOX_PATH_USER1_MAILBOX1);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USERNAME_1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        MailArchiveIterator mailArchiveIterator = archiveLoader.load(source);
        assertThat(mailArchiveIterator.hasNext()).isEqualTo(true);

        MailboxWithAnnotationsArchiveEntry expectedMailbox = new MailboxWithAnnotationsArchiveEntry(MAILBOX_1_NAME, SERIALIZED_MAILBOX_ID_1, NO_ANNOTATION);
        MailboxWithAnnotationsArchiveEntry resultMailbox = (MailboxWithAnnotationsArchiveEntry) mailArchiveIterator.next();
        verifyMailboxArchiveEntry(mailArchiveIterator, expectedMailbox, resultMailbox, false);
    }

    @Test
    void mailAccountIteratorFromArchiveWithTwoMailboxesShouldContainTwoMailboxes() throws Exception {
        createMailBoxWithMessage(MAILBOX_PATH_USER1_MAILBOX1);
        createMailBoxWithMessage(MAILBOX_PATH_USER1_MAILBOX2);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USERNAME_1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        MailArchiveIterator mailArchiveIterator = archiveLoader.load(source);
        assertThat(mailArchiveIterator.hasNext()).isEqualTo(true);

        MailboxWithAnnotationsArchiveEntry expectedMailbox = new MailboxWithAnnotationsArchiveEntry(MAILBOX_1_NAME, SERIALIZED_MAILBOX_ID_1, NO_ANNOTATION);
        MailboxWithAnnotationsArchiveEntry resultMailbox = (MailboxWithAnnotationsArchiveEntry) mailArchiveIterator.next();
        verifyMailboxArchiveEntry(mailArchiveIterator, expectedMailbox, resultMailbox, true);

        MailboxWithAnnotationsArchiveEntry expectedSecondMailbox = new MailboxWithAnnotationsArchiveEntry(MAILBOX_2_NAME, SERIALIZED_MAILBOX_ID_2, NO_ANNOTATION);
        MailboxWithAnnotationsArchiveEntry resultSecondMailbox = (MailboxWithAnnotationsArchiveEntry) mailArchiveIterator.next();
        verifyMailboxArchiveEntry(mailArchiveIterator, expectedSecondMailbox, resultSecondMailbox, false);
    }

    private void verifyMailboxArchiveEntry(MailArchiveIterator mailArchiveIterator, MailboxWithAnnotationsArchiveEntry expectedMailbox,
                                           MailboxWithAnnotationsArchiveEntry resultMailbox, boolean iteratorHasNextElement) {
        assertThat(resultMailbox.getMailboxId()).isEqualTo(expectedMailbox.getMailboxId());
        assertThat(resultMailbox.getMailboxName()).isEqualTo(expectedMailbox.getMailboxName());
        assertThat(resultMailbox.getAnnotations()).isEqualTo(expectedMailbox.getAnnotations());
        assertThat(resultMailbox).isEqualTo(expectedMailbox);
        assertThat(mailArchiveIterator.hasNext()).isEqualTo(iteratorHasNextElement);
    }

}
