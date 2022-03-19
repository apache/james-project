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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.blob.export.file.LocalFileBlobExportMechanism.Configuration;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import nl.jqno.equalsverifier.EqualsVerifier;
import reactor.core.publisher.Mono;

@ExtendWith(FileSystemExtension.class)
class LocalFileBlobExportMechanismTest {
    private static final String BLOB_CONTENT = "blob_content";
    private static final String JAMES_HOST = "james-host";

    private BlobStore blobStore;
    private FakeMailContext mailetContext;
    private LocalFileBlobExportMechanism testee;

    @BeforeEach
    void setUp(FileSystem fileSystem) throws Exception {
        mailetContext = FakeMailContext.builder().postmaster(MailAddressFixture.POSTMASTER_AT_JAMES).build();
        blobStore = MemoryBlobStoreFactory.builder()
            .blobIdFactory(new HashBlobId.Factory())
            .defaultBucketName()
            .passthrough();

        InetAddress localHost = mock(InetAddress.class);
        when(localHost.getHostName()).thenReturn(JAMES_HOST);
        DNSService dnsService = mock(DNSService.class);
        when(dnsService.getLocalHost()).thenReturn(localHost);

        testee = new LocalFileBlobExportMechanism(mailetContext, blobStore, fileSystem, dnsService,
            LocalFileBlobExportMechanism.Configuration.DEFAULT_CONFIGURATION);
    }

    @Test
    void exportingBlobShouldSendAMail() {
        BlobId blobId = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST)).block();

        String explanation = "The content of a deleted message vault had been shared with you.";
        testee.blobId(blobId)
            .with(MailAddressFixture.RECIPIENT1)
            .explanation(explanation)
            .noFileCustomPrefix()
            .noFileExtension()
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
        BlobId blobId = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST)).block();

        testee.blobId(blobId)
            .with(MailAddressFixture.RECIPIENT1)
            .explanation("The content of a deleted message vault had been shared with you.")
            .noFileCustomPrefix()
            .noFileExtension()
            .export();

        assertThat(mailetContext.getSentMails())
            .element(0)
            .satisfies(sentMail -> {
                try {
                    String absoluteUrl = sentMail.getMsg().getHeader(LocalFileBlobExportMechanism.CORRESPONDING_FILE_HEADER)[0];

                    assertThat(new FileInputStream(absoluteUrl)).hasSameContentAs(IOUtils.toInputStream(BLOB_CONTENT, StandardCharsets.UTF_8));
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
                .noFileCustomPrefix()
                .noFileExtension()
                .export())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    void exportingBlobShouldCreateAFileWithoutExtensionWhenNotDeclaringExtension() {
        BlobId blobId = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST)).block();

        testee.blobId(blobId)
            .with(MailAddressFixture.RECIPIENT1)
            .explanation("The content of a deleted message vault had been shared with you.")
            .noFileCustomPrefix()
            .noFileExtension()
            .export();

        assertThat(mailetContext.getSentMails())
            .element(0)
            .satisfies(sentMail -> {
                try {
                    String fileUrl = sentMail.getMsg().getHeader(LocalFileBlobExportMechanism.CORRESPONDING_FILE_HEADER)[0];
                    assertThat(FilenameUtils.getExtension(fileUrl))
                        .isEmpty();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    void exportingBlobShouldCreateAFileWithExtensionWhenDeclaringExtension() {
        BlobId blobId = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST)).block();

        testee.blobId(blobId)
            .with(MailAddressFixture.RECIPIENT1)
            .explanation("The content of a deleted message vault had been shared with you.")
            .noFileCustomPrefix()
            .fileExtension(FileExtension.ZIP)
            .export();

        assertThat(mailetContext.getSentMails())
            .element(0)
            .satisfies(sentMail -> {
                try {
                    String fileUrl = sentMail.getMsg().getHeader(LocalFileBlobExportMechanism.CORRESPONDING_FILE_HEADER)[0];
                    String fileExtensionInString = FilenameUtils.getExtension(fileUrl);
                    assertThat(FileExtension.of(fileExtensionInString))
                        .isEqualTo(FileExtension.ZIP);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    void exportingBlobShouldCreateAFileWithPrefixWhenDeclaringPrefix() {
        BlobId blobId = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST)).block();
        String filePrefix = "deleted-message-of-bob@james.org";

        testee.blobId(blobId)
            .with(MailAddressFixture.RECIPIENT1)
            .explanation("The content of a deleted message vault had been shared with you.")
            .filePrefix(filePrefix)
            .fileExtension(FileExtension.ZIP)
            .export();

        assertThat(mailetContext.getSentMails())
            .element(0)
            .satisfies(sentMail -> {
                try {
                    String fileUrl = sentMail.getMsg().getHeader(LocalFileBlobExportMechanism.CORRESPONDING_FILE_HEADER)[0];
                    assertThat(FilenameUtils.getName(fileUrl))
                        .startsWith(filePrefix);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Nested
    class ConfigurationTest {
        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(Configuration.class)
                .verify();
        }

        @Test
        void fromShouldReturnEmptyWhenDirectoryIsMissing() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();

            assertThat(Configuration.from(configuration)).isEmpty();
        }

        @Test
        void fromShouldReturnEmptyWhenDirectoryIsNull() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty("blob.export.localFile.directory", null);

            assertThat(Configuration.from(configuration)).isEmpty();
        }

        @Test
        void fromShouldReturnConfigurationWhenDirectoryIsSpecified() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            String exportDirectory = "file://var/localFileBlobExport";
            configuration.addProperty("blob.export.localFile.directory", exportDirectory);

            assertThat(Configuration.from(configuration))
                .contains(new Configuration(exportDirectory));
        }
    }
}