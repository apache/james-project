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

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.MetadataAwareBlobStoreDAOContract;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FileBlobStoreDAOTest implements BlobStoreDAOContract, MetadataAwareBlobStoreDAOContract {

    private FileBlobStoreDAO blobStore;

    @BeforeEach
    void setUp() throws Exception {
        blobStore = new FileBlobStoreDAO(FileSystemImpl.forTesting(), new PlainBlobId.Factory());
    }

    @Override
    public BlobStoreDAO testee() {
        return blobStore;
    }

    @Override
    @Test
    @Disabled("Not supported")
    public void mixingSaveReadAndDeleteShouldReturnConsistentState() {

    }

    @Override
    @DisabledOnOs(OS.WINDOWS)
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    public void concurrentSaveBytesShouldReturnConsistentValues(String description, BlobStoreDAO.BytesBlob bytes) throws java.util.concurrent.ExecutionException, InterruptedException {
        BlobStoreDAOContract.super.concurrentSaveBytesShouldReturnConsistentValues(description, bytes);
    }

    @Override
    @DisabledOnOs(OS.WINDOWS)
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    public void concurrentSaveInputStreamShouldReturnConsistentValues(String description, BlobStoreDAO.BytesBlob bytes) throws java.util.concurrent.ExecutionException, InterruptedException {
        BlobStoreDAOContract.super.concurrentSaveInputStreamShouldReturnConsistentValues(description, bytes);
    }

    @Override
    @DisabledOnOs(OS.WINDOWS)
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    public void concurrentSaveByteSourceShouldReturnConsistentValues(String description, BlobStoreDAO.BytesBlob bytes) throws java.util.concurrent.ExecutionException, InterruptedException {
        BlobStoreDAOContract.super.concurrentSaveByteSourceShouldReturnConsistentValues(description, bytes);
    }

    @Override
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void readBytesShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() throws Exception {
        BlobStoreDAOContract.super.readBytesShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob();
    }

    @Override
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void readShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() throws Exception {
        BlobStoreDAOContract.super.readShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob();
    }

    @Test
    void saveShouldRejectBlobIdWithReservedStagingPrefix() {
        org.apache.james.blob.api.BucketName bucketName = org.apache.james.blob.api.BucketName.of("test-bucket");
        org.apache.james.blob.api.BlobId nestedStagingId = new PlainBlobId.Factory().of("folder/.james-staging-evil");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                reactor.core.publisher.Mono.from(blobStore.save(bucketName, nestedStagingId, BlobStoreDAO.BytesBlob.of(new byte[]{1, 2, 3}))).block())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Blob name uses reserved staging prefix");
    }
}
