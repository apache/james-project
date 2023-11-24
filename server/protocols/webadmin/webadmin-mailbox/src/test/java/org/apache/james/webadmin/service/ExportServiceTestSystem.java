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

import static org.mockito.Mockito.mock;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.file.LocalFileBlobExportMechanism;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.backup.ArchiveService;
import org.apache.james.mailbox.backup.DefaultMailboxBackup;
import org.apache.james.mailbox.backup.MailArchivesLoader;
import org.apache.james.mailbox.backup.MailboxBackup;
import org.apache.james.mailbox.backup.ZipMailArchiveRestorer;
import org.apache.james.mailbox.backup.zip.ZipArchivesLoader;
import org.apache.james.mailbox.backup.zip.Zipper;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMailContext;
import org.mockito.Mockito;

public class ExportServiceTestSystem {

    public static final Domain DOMAIN = Domain.of("domain.tld");
    public static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    public static final Username CEDRIC = Username.fromLocalPartWithDomain("cedric", DOMAIN);
    public static final String PASSWORD = "password";
    private static final String JAMES_HOST = "james-host";
    private static final BlobId.Factory FACTORY = new HashBlobId.Factory();

    final FakeMailContext mailetContext;
    final InMemoryMailboxManager mailboxManager;
    final MemoryUsersRepository usersRepository;
    final MailboxSession bobSession;
    final BlobStore blobStore;
    final MailboxBackup backup;
    final BlobExportMechanism blobExport;

    ExportServiceTestSystem(FileSystem fileSystem) throws Exception {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        backup = createMailboxBackup();
        DNSService dnsService = createDnsService();

        usersRepository = createUsersRepository();

        bobSession = mailboxManager.createSystemSession(BOB);

        blobStore = Mockito.spy(MemoryBlobStoreFactory.builder()
            .blobIdFactory(FACTORY)
            .defaultBucketName()
            .passthrough());
        mailetContext = FakeMailContext.builder().postmaster(MailAddressFixture.POSTMASTER_AT_JAMES).build();
        blobExport = new LocalFileBlobExportMechanism(mailetContext, blobStore, fileSystem, dnsService,
            LocalFileBlobExportMechanism.Configuration.DEFAULT_CONFIGURATION);
    }

    private MemoryUsersRepository createUsersRepository() throws Exception {
        MemoryDomainList domainList = new MemoryDomainList();
        domainList.configure(DomainListConfiguration.DEFAULT);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

        domainList.addDomain(DOMAIN);
        usersRepository.addUser(BOB, PASSWORD);
        return usersRepository;
    }

    private DefaultMailboxBackup createMailboxBackup() {
        ArchiveService archiveService = new Zipper();
        MailArchivesLoader archiveLoader = new ZipArchivesLoader();
        ZipMailArchiveRestorer archiveRestorer = new ZipMailArchiveRestorer(mailboxManager, archiveLoader);
        return new DefaultMailboxBackup(mailboxManager, archiveService, archiveRestorer);
    }

    private DNSService createDnsService() throws UnknownHostException {
        InetAddress localHost = mock(InetAddress.class);
        Mockito.when(localHost.getHostName()).thenReturn(JAMES_HOST);
        DNSService dnsService = mock(DNSService.class);
        Mockito.when(dnsService.getLocalHost()).thenReturn(localHost);
        return dnsService;
    }
}
