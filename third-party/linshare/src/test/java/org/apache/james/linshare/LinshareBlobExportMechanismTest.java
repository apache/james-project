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

package org.apache.james.linshare;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.linshare.LinshareExtension.LinshareAPIForTechnicalAccountTesting;
import static org.apache.james.linshare.LinshareExtension.LinshareAPIForUserTesting;
import static org.apache.james.linshare.LinshareFixture.USER_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.blob.memory.MemoryDumbBlobStore;
import org.apache.james.core.MailAddress;
import org.apache.james.linshare.client.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LinshareBlobExportMechanismTest {
    private static final String FILE_CONTENT = "content";
    private static final String EXPLANATION = "Explanation about the file being shared";
    private static final FileExtension FILE_TEXT_EXTENSION = FileExtension.of("txt");

    @RegisterExtension
    static LinshareExtension linshareExtension = new LinshareExtension();

    private MemoryBlobStore blobStore;
    private LinshareBlobExportMechanism testee;
    private HashBlobId.Factory blobIdFactory;
    private LinshareAPIForUserTesting user2API;

    @BeforeEach
    void setUp() throws Exception {
        blobIdFactory = new HashBlobId.Factory();
        blobStore = new MemoryBlobStore(blobIdFactory, new MemoryDumbBlobStore());

        testee = new LinshareBlobExportMechanism(
            linshareExtension.getDelegationAccountAPI(),
            blobStore);

        user2API = LinshareAPIForUserTesting.from(USER_2);
    }

    @Test
    void exportShouldUploadTheDocumentToTargetUserViaLinshare() throws Exception {
        BlobId blobId = blobStore.save(blobStore.getDefaultBucketName(), FILE_CONTENT, LOW_COST).block();
        String filePrefix = "deleted-message-of-bob@james.org-";

        testee.blobId(blobId)
            .with(new MailAddress(USER_2.getUsername()))
            .explanation(EXPLANATION)
            .filePrefix(Optional.of(filePrefix))
            .fileExtension(FILE_TEXT_EXTENSION)
            .export();

        assertThat(user2API.listAllDocuments())
            .hasSize(1)
            .allSatisfy(receivedShare -> assertThat(receivedShare.getName()).endsWith(".txt"))
            .allSatisfy(receivedShare -> assertThat(receivedShare.getName()).startsWith(filePrefix));
    }

    @Test
    void exportShouldUploadTheDocumentAndAllowDownloadViaLinshare(LinshareAPIForTechnicalAccountTesting delegationAPIForTesting) throws Exception {
        BlobId blobId = blobStore.save(blobStore.getDefaultBucketName(), FILE_CONTENT, LOW_COST).block();

        testee.blobId(blobId)
            .with(new MailAddress(USER_2.getUsername()))
            .explanation(EXPLANATION)
            .noFileCustomPrefix()
            .fileExtension(FILE_TEXT_EXTENSION)
            .export();

        Document sharedDoc = user2API.listAllDocuments().get(0);
        byte[] sharedFile = delegationAPIForTesting.downloadFileFrom(USER_2, sharedDoc.getId());
        assertThat(sharedFile).isEqualTo(FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void exportShouldFailWhenBlobDoesNotExist() {
        BlobId blobId = blobIdFactory.randomId();

        assertThatThrownBy(
            () -> testee.blobId(blobId)
                .with(new MailAddress(USER_2.getUsername()))
                .explanation(EXPLANATION)
                .noFileCustomPrefix()
                .fileExtension(FILE_TEXT_EXTENSION)
                .export())
            .isInstanceOf(BlobExportMechanism.BlobExportException.class);
    }

    @Test
    void exportWithFilePrefixShouldCreateFileWithCustomPrefix() throws Exception {
        BlobId blobId = blobStore.save(blobStore.getDefaultBucketName(), FILE_CONTENT, LOW_COST).block();
        String filePrefix = "deleted-message-of-bob@james.org";

        testee.blobId(blobId)
            .with(new MailAddress(USER_2.getUsername()))
            .explanation(EXPLANATION)
            .filePrefix(Optional.of(filePrefix))
            .fileExtension(FILE_TEXT_EXTENSION)
            .export();

        Document sharedDoc = user2API.listAllDocuments().get(0);
        assertThat(sharedDoc.getName())
            .startsWith(filePrefix);
    }
}