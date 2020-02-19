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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.blob.export.file.FileSystemExtension;
import org.apache.james.blob.export.file.LocalFileBlobExportMechanism;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.blob.memory.MemoryDumbBlobStore;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.ArchiveService;
import org.apache.james.mailbox.backup.DefaultMailboxBackup;
import org.apache.james.mailbox.backup.MailArchivesLoader;
import org.apache.james.mailbox.backup.MailboxBackup;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.backup.ZipMailArchiveRestorer;
import org.apache.james.mailbox.backup.zip.ZipArchivesLoader;
import org.apache.james.mailbox.backup.zip.Zipper;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMailContext;
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

    private static final String JAMES_HOST = "james-host";
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final Username UNKNOWN_USER = Username.fromLocalPartWithDomain("unknown", DOMAIN);
    private static final String PASSWORD = "password";
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
    private InMemoryMailboxManager mailboxManager;
    private MailboxSession bobSession;
    private BlobStore blobStore;
    private FakeMailContext mailetContext;

    @BeforeEach
    void setUp(FileSystem fileSystem) throws Exception {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        bobSession = mailboxManager.createSystemSession(BOB);

        MailboxBackup backup = createMailboxBackup();
        DNSService dnsService = createDnsService();
        blobStore = Mockito.spy(new MemoryBlobStore(FACTORY, new MemoryDumbBlobStore()));
        mailetContext = FakeMailContext.builder().postmaster(MailAddressFixture.POSTMASTER_AT_JAMES).build();
        BlobExportMechanism blobExport = new LocalFileBlobExportMechanism(mailetContext, blobStore, fileSystem, dnsService,
            LocalFileBlobExportMechanism.Configuration.DEFAULT_CONFIGURATION);
        MemoryUsersRepository usersRepository = createUsersRepository(dnsService);

        testee = new ExportService(backup, blobStore, blobExport, usersRepository);
    }

    private MemoryUsersRepository createUsersRepository(DNSService dnsService) throws Exception {
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

        domainList.addDomain(DOMAIN);
        usersRepository.addUser(BOB, PASSWORD);
        return usersRepository;
    }

    private DNSService createDnsService() throws UnknownHostException {
        InetAddress localHost = mock(InetAddress.class);
        when(localHost.getHostName()).thenReturn(JAMES_HOST);
        DNSService dnsService = mock(DNSService.class);
        when(dnsService.getLocalHost()).thenReturn(localHost);
        return dnsService;
    }

    private DefaultMailboxBackup createMailboxBackup() {
        ArchiveService archiveService = new Zipper();
        MailArchivesLoader archiveLoader = new ZipArchivesLoader();
        ZipMailArchiveRestorer archiveRestorer = new ZipMailArchiveRestorer(mailboxManager, archiveLoader);
        return new DefaultMailboxBackup(mailboxManager, archiveService, archiveRestorer);
    }

    @Test
    void exportUserMailboxesDataShouldReturnCompletedWhenUserDoesNotExist() {
        assertThat(testee.export(UNKNOWN_USER).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void exportUserMailboxesDataShouldReturnCompletedWhenExistingUserWithoutMailboxes() {
        assertThat(testee.export(BOB).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void exportUserMailboxesDataShouldReturnCompletedWhenExistingUser() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        assertThat(testee.export(BOB).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    private ComposedMessageId createAMailboxWithAMail(String message) throws MailboxException {
        MailboxPath bobInboxPath = MailboxPath.inbox(BOB);
        mailboxManager.createMailbox(bobInboxPath, bobSession);
        return mailboxManager.getMailbox(bobInboxPath, bobSession)
            .appendMessage(MessageManager.AppendCommand.builder()
                    .build(message),
                bobSession);
    }

    @Test
    void exportUserMailboxesDataShouldProduceAnEmptyZipWhenUserDoesNotExist() throws Exception {
        testee.export(UNKNOWN_USER).block();
            String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
            ZipAssert.assertThatZip(new FileInputStream(fileUrl))
                .hasNoEntry();
    }

    @Test
    void exportUserMailboxesDataShouldProduceAnEmptyZipWhenExistingUserWithoutAnyMailboxes() throws Exception {
        testee.export(BOB).block();

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];

        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .hasNoEntry();
    }

    @Test
    void exportUserMailboxesDataShouldProduceAZipWithEntry() throws Exception {
        ComposedMessageId id = createAMailboxWithAMail(MESSAGE_CONTENT);

        testee.export(BOB).block();

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];

        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory(),
                ZipAssert.EntryChecks.hasName(id.getMessageId().serialize()).hasStringContent(MESSAGE_CONTENT));
    }

    @Test
    void exportUserMailboxesDataShouldProduceAFileWithExpectedExtension() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        testee.export(BOB).block();

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];

        assertThat(Files.getFileExtension(fileUrl)).isEqualTo(FileExtension.ZIP.getExtension());
    }

    @Test
    void exportUserMailboxesDataShouldProduceAFileWithExpectedName() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        testee.export(BOB).block();

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        File file = new File(fileUrl);

        assertThat(file.getName()).startsWith(FILE_PREFIX + BOB.asString());
    }

    @Test
    void exportUserMailboxesWithSizableDataShouldProduceAFile() throws Exception {
        ComposedMessageId id = createAMailboxWithAMail(TWELVE_MEGABYTES_STRING);

        testee.export(BOB).block();
        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];

        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory(),
                ZipAssert.EntryChecks.hasName(id.getMessageId().serialize()).hasStringContent(TWELVE_MEGABYTES_STRING));
    }

    @Test
    void exportUserMailboxesDataShouldDeleteBlobAfterCompletion() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        testee.export(BOB).block();

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        String fileName = Files.getNameWithoutExtension(fileUrl);
        String blobId = fileName.substring(fileName.lastIndexOf("-") + 1);

        SoftAssertions.assertSoftly(softly -> {
            assertThatThrownBy(() -> blobStore.read(blobStore.getDefaultBucketName(), FACTORY.from(blobId)))
                .isInstanceOf(ObjectNotFoundException.class);
            assertThatThrownBy(() -> blobStore.read(blobStore.getDefaultBucketName(), FACTORY.from(blobId)))
                .hasMessage(String.format("blob '%s' not found in bucket '%s'", blobId, blobStore.getDefaultBucketName().asString()));
        });
    }

    @Test
    void exportUserMailboxesDataShouldReturnSuccessWhenBlobDeletingFails() throws Exception {
        createAMailboxWithAMail(MESSAGE_CONTENT);

        doReturn(Mono.error(new RuntimeException()))
            .when(blobStore)
            .delete(any(), any());

        Task.Result result = testee.export(BOB).block();

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        String fileName = Files.getNameWithoutExtension(fileUrl);
        String blobId = fileName.substring(fileName.lastIndexOf("-") + 1);

        blobStore.read(blobStore.getDefaultBucketName(), FACTORY.from(blobId));

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
    }
}