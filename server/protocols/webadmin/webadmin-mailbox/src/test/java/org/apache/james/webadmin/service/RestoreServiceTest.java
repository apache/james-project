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

package org.apache.james.webadmin.service;

import static org.apache.james.webadmin.service.ExportServiceTestSystem.BOB;
import static org.apache.james.webadmin.service.ExportServiceTestSystem.CEDRIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.export.file.FileSystemExtension;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.task.Task;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

@ExtendWith(FileSystemExtension.class)
class RestoreServiceTest {
    private static final int BUFFER_SIZE = 4096;
    private static final String MESSAGE_CONTENT = "MIME-Version: 1.0\r\n" +
        "Subject: test\r\n" +
        "Content-Type: text/plain; charset=UTF-8\r\n" +
        "\r\n" +
        "testmail";

    private RestoreService testee;
    private ExportServiceTestSystem testSystem;

    @BeforeEach
    void setUp(FileSystem fileSystem) throws Exception {
        testSystem = new ExportServiceTestSystem(fileSystem);
        testee = Mockito.spy(new RestoreService(testSystem.backup, testSystem.blobStore));
    }

    @Test
    void restoreShouldReturnCompleteWhenExistingUserWithoutDataAndEmptyZip() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        testSystem.backup.backupAccount(BOB, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        BlobId blobId = Mono.from(testSystem.blobStore.save(testSystem.blobStore.getDefaultBucketName(), source, BlobStore.StoragePolicy.LOW_COST)).block();

        assertThat(testee.restore(CEDRIC, blobId).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void restoreShouldReturnCompleteWhenExistingUserWithoutDataAndNonEmptyZip() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        testSystem.backup.backupAccount(BOB, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        BlobId blobId = Mono.from(testSystem.blobStore.save(testSystem.blobStore.getDefaultBucketName(), source, BlobStore.StoragePolicy.LOW_COST)).block();

        assertThat(testee.restore(CEDRIC, blobId).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void restoreShouldReturnPartialWhenNonEmptyAccount() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        testSystem.backup.backupAccount(BOB, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        BlobId blobId = Mono.from(testSystem.blobStore.save(testSystem.blobStore.getDefaultBucketName(), source, BlobStore.StoragePolicy.LOW_COST)).block();

        assertThat(testee.restore(BOB, blobId).block())
            .isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void restoreShouldReturnPartialWhenFailed() throws Exception {
        doThrow(new RuntimeException())
            .when(testSystem.blobStore)
            .read(any(), any());

        createAMailboxWithAMail(MESSAGE_CONTENT);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        testSystem.backup.backupAccount(BOB, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        BlobId blobId = Mono.from(testSystem.blobStore.save(testSystem.blobStore.getDefaultBucketName(), source, BlobStore.StoragePolicy.LOW_COST)).block();

        assertThat(testee.restore(CEDRIC, blobId).block())
            .isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void restoreShouldNoopWhenEmptyZip() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        testSystem.backup.backupAccount(BOB, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        BlobId blobId = Mono.from(testSystem.blobStore.save(testSystem.blobStore.getDefaultBucketName(), source, BlobStore.StoragePolicy.LOW_COST)).block();

        testee.restore(CEDRIC, blobId).block();

        MailboxSession cedricSession = testSystem.mailboxManager.createSystemSession(CEDRIC);
        assertThat(testSystem.mailboxManager.list(cedricSession))
            .isEmpty();
    }

    @Test
    void restoreShouldRestoreContentFromNonEmptyZip() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        testSystem.backup.backupAccount(BOB, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        BlobId blobId = Mono.from(testSystem.blobStore.save(testSystem.blobStore.getDefaultBucketName(), source, BlobStore.StoragePolicy.LOW_COST)).block();

        testee.restore(CEDRIC, blobId).block();

        MailboxSession cedricSession = testSystem.mailboxManager.createSystemSession(CEDRIC);
        MessageManager mailbox = testSystem.mailboxManager.getMailbox(MailboxPath.inbox(CEDRIC), cedricSession);
        MessageResultIterator resultIterator = mailbox.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, cedricSession);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(resultIterator).toIterable().hasSize(1);
            softly.assertThat(Throwing.supplier(() -> resultIterator.next().getBody().asBytesSequence())).isEqualTo(MESSAGE_CONTENT.getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    void restoreShouldDeleteBlobAfterCompletion() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        testSystem.backup.backupAccount(BOB, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        BlobId blobId = Mono.from(testSystem.blobStore.save(testSystem.blobStore.getDefaultBucketName(), source, BlobStore.StoragePolicy.LOW_COST)).block();

        testee.restore(CEDRIC, blobId).block();

        assertThatThrownBy(() -> testSystem.blobStore.read(testSystem.blobStore.getDefaultBucketName(), blobId))
            .isInstanceOf(ObjectNotFoundException.class);
    }

    private ComposedMessageId createAMailboxWithAMail(String message) throws MailboxException {
        MailboxPath bobInboxPath = MailboxPath.inbox(BOB);
        testSystem.mailboxManager.createMailbox(bobInboxPath, testSystem.bobSession);
        return testSystem.mailboxManager.getMailbox(bobInboxPath, testSystem.bobSession)
            .appendMessage(MessageManager.AppendCommand.builder()
                    .build(message),
                testSystem.bobSession)
            .getId();
    }
}
