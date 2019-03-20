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

package org.apache.james.blob.export.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FileSystemExtension.class)
class LocalFileBlobExportMechanismTest {
    private static final byte[] BLOB_CONTENT = "blob_content".getBytes(StandardCharsets.UTF_8);
    private static final String JAMES_HOST = "james-host";

    private BlobStore blobStore;
    private FakeMailContext mailetContext;
    private LocalFileBlobExportMechanism testee;

    @BeforeEach
    void setUp(FileSystem fileSystem) throws Exception {
        mailetContext = FakeMailContext.builder().postmaster(MailAddressFixture.POSTMASTER_AT_JAMES).build();
        blobStore = new MemoryBlobStore(new HashBlobId.Factory());

        InetAddress localHost = mock(InetAddress.class);
        when(localHost.getHostName()).thenReturn(JAMES_HOST);
        DNSService dnsService = mock(DNSService.class);
        when(dnsService.getLocalHost()).thenReturn(localHost);

        LocalFileBlobExportMechanism.Configuration blobExportConfiguration = new LocalFileBlobExportMechanism.Configuration("file://var/blobExporting");

        testee = new LocalFileBlobExportMechanism(mailetContext, blobStore, fileSystem, dnsService, blobExportConfiguration);
    }

    @Test
    void exportingBlobShouldSendAMail() {
        BlobId blobId = blobStore.save(BLOB_CONTENT).block();

        String explanation = "The content of a deleted message vault had been shared with you.";
        testee.blobId(blobId)
            .with(MailAddressFixture.RECIPIENT1)
            .explanation(explanation)
            .export();

        assertThat(mailetContext.getSentMails()).hasSize(1)
            .element(0)
            .satisfies(sentMail -> {
                try {
                    assertThat(sentMail.getSender()).isEqualTo(MailAddressFixture.POSTMASTER_AT_JAMES);
                    assertThat(sentMail.getRecipients()).containsExactly(MailAddressFixture.RECIPIENT1);

                    assertThat(sentMail.getMsg().getFrom()).contains(new InternetAddress(MailAddressFixture.POSTMASTER_AT_JAMES.asString()));
                    assertThat(sentMail.getMsg().getRecipients(Message.RecipientType.TO)).contains(new InternetAddress(MailAddressFixture.RECIPIENT1.asString()));
                    assertThat(sentMail.getMsg().getSubject()).isEqualTo("Some content had had just been exported");

                    String mailContent = MimeMessageUtil.asString(sentMail.getMsg());

                    assertThat(mailContent).contains(explanation);
                    assertThat(mailContent).contains("The content of this blob can be read directly on James host filesystem (james-host) in this file: ");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    void exportingBlobShouldCreateAFileWithTheCorrespondingContent(FileSystem fileSystem) {
        BlobId blobId = blobStore.save(BLOB_CONTENT).block();

        testee.blobId(blobId)
            .with(MailAddressFixture.RECIPIENT1)
            .explanation("The content of a deleted message vault had been shared with you.")
            .export();

        assertThat(mailetContext.getSentMails())
            .element(0)
            .satisfies(sentMail -> {
                try {
                    String fileUrl = sentMail.getMsg().getHeader(LocalFileBlobExportMechanism.CORRESPONDING_FILE_HEADER)[0];

                    assertThat(fileSystem.getResource(fileUrl)).hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    void shareShouldFailWhenBlobDoesNotExist() {
        BlobId blobId = new HashBlobId.Factory().forPayload("not existing".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() ->
            testee.blobId(blobId)
                .with(MailAddressFixture.RECIPIENT1)
                .explanation("The content of a deleted message vault had been shared with you.")
                .export())
            .isInstanceOf(ObjectStoreException.class);
    }
}