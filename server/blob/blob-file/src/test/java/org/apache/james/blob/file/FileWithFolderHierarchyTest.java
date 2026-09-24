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

package org.apache.james.blob.file;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class FileWithFolderHierarchyTest implements BlobStoreContract {
    static UpdatableTickingClock clock = new UpdatableTickingClock(Instant.parse("2021-08-19T10:15:30.00Z"));

    private FileSystemImpl fileSystem;
    private FileBlobStoreDAO fileBlobStoreDAO;
    private BlobStore testee;
    private BlobId.Factory blobIdFactory;

    @BeforeEach
    void beforeEach() throws Exception {
        fileSystem = FileSystemImpl.forTesting();
        blobIdFactory = new MinIOGenerationAwareBlobId.Factory(clock, GenerationAwareBlobId.Configuration.DEFAULT, new PlainBlobId.Factory());
        fileBlobStoreDAO = new FileBlobStoreDAO(fileSystem, blobIdFactory);
        testee = createBlobStore(blobIdFactory);
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteQuietly(fileSystem.getFile("file://var/blob"));
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return blobIdFactory;
    }

    public BlobStore createBlobStore(BlobId.Factory blobIdFactory) {
        return new FileBlobStoreFactory(fileSystem).builder()
            .blobIdFactory(blobIdFactory)
            .defaultBucketName()
            .deduplication();
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    void saveShouldReturnBlobIdOfString(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, "toto", storagePolicy)).block();
        String blobIdString = blobId.asString();

        assertThat(blobIdString).isEqualTo("1/628/M/f/emXjFVhqwZi9eYtmKc5A");
        assertThat(blobId).isEqualTo(blobIdFactory().parse(blobIdString));
    }

    @Test
    void deleteShouldPruneEmptyParentDirectories() throws Exception {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, "toto", LOW_COST)).block();
        File blobFile = new File(fileSystem.getFile("file://var/blob/" + defaultBucketName.asString()), blobId.asString());
        assertThat(blobFile).exists();
        File parentDir = blobFile.getParentFile();
        assertThat(parentDir).isDirectory();

        Mono.from(fileBlobStoreDAO.delete(defaultBucketName, blobId)).block();

        assertThat(blobFile).doesNotExist();
        assertThat(parentDir).doesNotExist();
    }

    @Nested
    class Compatible {

        private BlobStore withGenerationAwareBlobId;
        private BlobStore withMinIOGenerationAwareBlobId;
        private BucketName defaultBucketName;

        @BeforeEach
        void setup() {
            BlobId.Factory plainBlobIdFactory = new PlainBlobId.Factory();
            withGenerationAwareBlobId = createBlobStore(new GenerationAwareBlobId.Factory(clock, plainBlobIdFactory, GenerationAwareBlobId.Configuration.DEFAULT));
            withMinIOGenerationAwareBlobId = createBlobStore(new MinIOGenerationAwareBlobId.Factory(clock, GenerationAwareBlobId.Configuration.DEFAULT, plainBlobIdFactory));
            defaultBucketName = withGenerationAwareBlobId.getDefaultBucketName();
        }

        @Test
        void readWithMinIOGenerationAwareShouldSuccessWhenBlobWasStoredByGenerationAware() {
            String originalData = "toto" + UUID.randomUUID();
            BlobId blobId = Mono.from(withGenerationAwareBlobId.save(defaultBucketName, originalData, LOW_COST)).block();

            assertThat(blobId).isInstanceOf(GenerationAwareBlobId.class);

            byte[] readAsByte = Mono.from(withMinIOGenerationAwareBlobId.readBytes(defaultBucketName, blobId)).block();

            assertThat(new String(readAsByte, StandardCharsets.UTF_8)).isEqualTo(originalData);
        }

        @Test
        void listBlobsShouldReturnCorrectBlobIdWhenBlobWasStoredByGenerationAware() {
            String originalData = "toto" + UUID.randomUUID();
            BlobId blobId = Mono.from(withGenerationAwareBlobId.save(defaultBucketName, originalData, LOW_COST)).block();
            assertThat(blobId).isInstanceOf(GenerationAwareBlobId.class);

            List<BlobId> blobIdList = Flux.from(withMinIOGenerationAwareBlobId.listBlobs(defaultBucketName)).collectList().block();
            assertThat(blobIdList).hasSize(1);
            assertThat(blobIdList.getFirst()).isInstanceOf(GenerationAwareBlobId.class);

            byte[] readAsByte = Mono.from(withMinIOGenerationAwareBlobId.readBytes(defaultBucketName, blobIdList.getFirst())).block();

            assertThat(new String(readAsByte, StandardCharsets.UTF_8)).isEqualTo(originalData);
        }

        @Test
        void readWithGenerationAwareShouldSuccessWhenBlobWasStoredByMinIOGenerationAware() {
            String originalData = "toto" + UUID.randomUUID();
            BlobId blobId = Mono.from(withMinIOGenerationAwareBlobId.save(defaultBucketName, originalData, LOW_COST)).block();
            assertThat(blobId).isInstanceOf(MinIOGenerationAwareBlobId.class);

            byte[] readAsByte = Mono.from(withGenerationAwareBlobId.readBytes(defaultBucketName, blobId)).block();

            assertThat(new String(readAsByte, StandardCharsets.UTF_8)).isEqualTo(originalData);
        }

        @Test
        void listBlobsShouldReturnCorrectBlobIdWhenBlobWasStoredByMinIOGenerationAware() {
            String originalData = "toto" + UUID.randomUUID();
            BlobId blobId = Mono.from(withMinIOGenerationAwareBlobId.save(defaultBucketName, originalData, LOW_COST)).block();
            assertThat(blobId).isInstanceOf(MinIOGenerationAwareBlobId.class);

            List<BlobId> blobIdList = Flux.from(withGenerationAwareBlobId.listBlobs(defaultBucketName)).collectList().block();
            assertThat(blobIdList).hasSize(1);
            assertThat(blobIdList.getFirst()).isInstanceOf(GenerationAwareBlobId.class);

            byte[] readAsByte = Mono.from(withGenerationAwareBlobId.readBytes(defaultBucketName, blobIdList.getFirst())).block();

            assertThat(new String(readAsByte, StandardCharsets.UTF_8)).isEqualTo(originalData);
        }
    }
}
