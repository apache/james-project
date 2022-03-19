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

import static org.apache.james.mailbox.DefaultMailboxes.INBOX;
import static org.apache.james.webadmin.service.ExportServiceTestSystem.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.mail.MessagingException;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.blob.export.file.FileSystemExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import com.google.common.base.Strings;
import com.google.common.io.Files;

import reactor.core.publisher.Mono;

@ExtendWith(FileSystemExtension.class)
class ExportServiceTest {

    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username UNKNOWN_USER = Username.fromLocalPartWithDomain("unknown", DOMAIN);
    private static final String CORRESPONDING_FILE_HEADER = "corresponding-file";
    private static final String MESSAGE_CONTENT = "MIME-Version: 1.0\r\n" +
        "Subject: test\r\n" +
        "Content-Type: text/plain; charset=UTF-8\r\n" +
        "\r\n" +
        "testmail";
    private static final String TWELVE_MEGABYTES_STRING = Strings.repeat("0123456789\r\n", 1024 * 1024);
    private static final String FILE_PREFIX = "mailbox-backup-";
    private static final BlobId.Factory FACTORY = new HashBlobId.Factory();

    private ExportService testee;
    private ExportServiceTestSystem testSystem;
    private ExportService.Progress progress;

    @BeforeEach
    void setUp(FileSystem fileSystem) throws Exception {
        testSystem = new ExportServiceTestSystem(fileSystem);
        testee = Mockito.spy(new ExportService(testSystem.backup, testSystem.blobStore, testSystem.blobExport, testSystem.usersRepository));
        progress = new ExportService.Progress();
    }

    private String getFileUrl() throws MessagingException {
        return testSystem.mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
    }

    @Test
    void exportUserMailboxesDataShouldReturnCompletedWhenUserDoesNotExist() {
        assertThat(testee.export(progress, UNKNOWN_USER).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void exportUserMailboxesDataShouldReturnCompletedWhenExistingUserWithoutMailboxes() {
        assertThat(testee.export(progress, BOB).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void exportUserMailboxesDataShouldReturnCompletedWhenExistingUser() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        assertThat(testee.export(progress, BOB).block())
            .isEqualTo(Task.Result.COMPLETED);
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

    @Test
    void exportUserMailboxesDataShouldProduceAnEmptyZipWhenUserDoesNotExist() throws Exception {
        testee.export(progress, UNKNOWN_USER).block();

        ZipAssert.assertThatZip(new FileInputStream(getFileUrl()))
            .hasNoEntry();
    }

    @Test
    void exportUserMailboxesDataShouldProduceAnEmptyZipWhenExistingUserWithoutAnyMailboxes() throws Exception {
        testee.export(progress, BOB).block();

        ZipAssert.assertThatZip(new FileInputStream(getFileUrl()))
            .hasNoEntry();
    }

    @Test
    void exportUserMailboxesDataShouldProduceAZipWithEntry() throws Exception {
        ComposedMessageId id = createAMailboxWithAMail(MESSAGE_CONTENT);

        testee.export(progress, BOB).block();

        ZipAssert.assertThatZip(new FileInputStream(getFileUrl()))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory(),
                ZipAssert.EntryChecks.hasName(id.getMessageId().serialize()).hasStringContent(MESSAGE_CONTENT));
    }

    @Test
    void exportUserMailboxesDataShouldProduceAFileWithExpectedExtension() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        testee.export(progress, BOB).block();

        assertThat(Files.getFileExtension(getFileUrl())).isEqualTo(FileExtension.ZIP.getExtension());
    }

    @Test
    void exportUserMailboxesDataShouldProduceAFileWithExpectedName() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        testee.export(progress, BOB).block();

        File file = new File(getFileUrl());

        assertThat(file.getName()).startsWith(FILE_PREFIX + BOB.asString());
    }

    @Test
    void exportUserMailboxesWithSizableDataShouldProduceAFile() throws Exception {
        ComposedMessageId id = createAMailboxWithAMail(TWELVE_MEGABYTES_STRING);

        testee.export(progress, BOB).block();

        ZipAssert.assertThatZip(new FileInputStream(getFileUrl()))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory(),
                ZipAssert.EntryChecks.hasName(id.getMessageId().serialize()).hasStringContent(TWELVE_MEGABYTES_STRING));
    }

    @Test
    void exportUserMailboxesDataShouldDeleteBlobAfterCompletion() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        testee.export(progress, BOB).block();

        String fileName = Files.getNameWithoutExtension(getFileUrl());
        String blobId = fileName.substring(fileName.lastIndexOf("-") + 1);

        SoftAssertions.assertSoftly(softly -> {
            assertThatThrownBy(() -> testSystem.blobStore.read(testSystem.blobStore.getDefaultBucketName(), FACTORY.from(blobId)))
                .isInstanceOf(ObjectNotFoundException.class);
            assertThatThrownBy(() -> testSystem.blobStore.read(testSystem.blobStore.getDefaultBucketName(), FACTORY.from(blobId)))
                .hasMessage(String.format("blob '%s' not found in bucket '%s'", blobId, testSystem.blobStore.getDefaultBucketName().asString()));
        });
    }

    @Test
    void exportUserMailboxesDataShouldReturnSuccessWhenBlobDeletingFails() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        doReturn(Mono.error(new RuntimeException()))
            .when(testSystem.blobStore)
            .delete(any(), any());

        Task.Result result = testee.export(progress, BOB).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void exportUserMailboxesDataShouldUpdateProgressWhenZipping() throws IOException {
        testee.zipMailboxesContent(progress, BOB);

        assertThat(progress.getStage()).isEqualTo(ExportService.Stage.ZIPPING);
    }

    @Test
    void exportUserMailboxesDataShouldUpdateProgressWhenExporting() {
        doReturn(Mono.error(new RuntimeException()))
            .when(testSystem.blobStore)
            .save(any(), any(InputStream.class), any());

        testee.export(progress, BOB, new ByteArrayInputStream(MESSAGE_CONTENT.getBytes())).block();

        assertThat(progress.getStage()).isEqualTo(ExportService.Stage.EXPORTING);
    }

    @Test
    void exportUserMailboxesDataShouldUpdateProgressWhenComplete() {
        testee.export(progress, BOB).block();

        assertThat(progress.getStage()).isEqualTo(ExportService.Stage.COMPLETED);
    }
}