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

package org.apache.james.blob.joining;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.util.CompletableFutureUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JoiningBlobStoreTest implements BlobStoreContract {

    private static class FutureThrowingBlobStore implements BlobStore {

        @Override
        public CompletableFuture<BlobId> save(byte[] data) {
            return CompletableFutureUtil.exceptionallyFuture(new RuntimeException("not supported"));
        }

        @Override
        public CompletableFuture<BlobId> save(InputStream data) {
            return CompletableFutureUtil.exceptionallyFuture(new RuntimeException("not supported"));
        }

        @Override
        public CompletableFuture<byte[]> readBytes(BlobId blobId) {
            return CompletableFutureUtil.exceptionallyFuture(new RuntimeException("not supported"));
        }

        @Override
        public InputStream read(BlobId blobId) {
            throw new RuntimeException("not supported");
        }
    }

    private static class ThrowingBlobStore implements BlobStore {

        @Override
        public CompletableFuture<BlobId> save(byte[] data) {
            throw new RuntimeException("not supported");
        }

        @Override
        public CompletableFuture<BlobId> save(InputStream data) {
            throw new RuntimeException("not supported");
        }

        @Override
        public CompletableFuture<byte[]> readBytes(BlobId blobId) {
            throw new RuntimeException("not supported");
        }

        @Override
        public InputStream read(BlobId blobId) {
            throw new RuntimeException("not supported");
        }
    }

    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final byte [] BLOB_CONTENT = "blob content".getBytes();

    private MemoryBlobStore primaryBlobStore;
    private MemoryBlobStore secondaryBlobStore;
    private JoiningBlobStore joiningBlobStore;

    @BeforeEach
    void setup() {
        primaryBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
        secondaryBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
        joiningBlobStore = new JoiningBlobStore(primaryBlobStore, secondaryBlobStore);
    }

    @Override
    public BlobStore testee() {
        return joiningBlobStore;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return BLOB_ID_FACTORY;
    }

    @Nested
    class PrimaryReadThrowsExceptionDirectly {

        @Test
        void readShouldReturnFallbackToSecondaryWhenPrimaryGotException() throws Exception {
            MemoryBlobStore secondaryBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            JoiningBlobStore joiningBlobStore = new JoiningBlobStore(new ThrowingBlobStore(), secondaryBlobStore);
            BlobId blobId = secondaryBlobStore.save(BLOB_CONTENT).get();

            assertThat(joiningBlobStore.read(blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToSecondaryWhenPrimaryGotException() throws Exception {
            MemoryBlobStore secondaryBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            JoiningBlobStore joiningBlobStore = new JoiningBlobStore(new ThrowingBlobStore(), secondaryBlobStore);
            BlobId blobId = secondaryBlobStore.save(BLOB_CONTENT).get();

            assertThat(joiningBlobStore.readBytes(blobId).get())
                .isEqualTo(BLOB_CONTENT);
        }

    }

    @Nested
    class PrimaryReadCompletesExceptionally {

        @Test
        void readShouldReturnFallbackToSecondaryWhenPrimaryCompletedExceptionally() throws Exception {
            MemoryBlobStore secondaryBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            JoiningBlobStore joiningBlobStore = new JoiningBlobStore(new FutureThrowingBlobStore(), secondaryBlobStore);
            BlobId blobId = secondaryBlobStore.save(BLOB_CONTENT).get();

            assertThat(joiningBlobStore.read(blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToSecondaryWhenPrimaryCompletedExceptionally() throws Exception {
            MemoryBlobStore secondaryBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            JoiningBlobStore joiningBlobStore = new JoiningBlobStore(new FutureThrowingBlobStore(), secondaryBlobStore);
            BlobId blobId = secondaryBlobStore.save(BLOB_CONTENT).get();

            assertThat(joiningBlobStore.readBytes(blobId).get())
                .isEqualTo(BLOB_CONTENT);
        }
    }


    @Test
    void readShouldReturnFromPrimaryWhenAvailable() throws Exception {
        BlobId blobId = primaryBlobStore.save(BLOB_CONTENT).get();

        assertThat(joiningBlobStore.read(blobId))
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
    }

    @Test
    void readShouldReturnFromSecondaryWhenPrimaryNotAvailable() throws Exception {
        BlobId blobId = secondaryBlobStore.save(BLOB_CONTENT).get();

        assertThat(joiningBlobStore.read(blobId))
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
    }

    @Test
    void readBytesShouldReturnFromPrimaryWhenAvailable() throws Exception {
        BlobId blobId = primaryBlobStore.save(BLOB_CONTENT).get();

        assertThat(joiningBlobStore.readBytes(blobId).get())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void readBytesShouldReturnFromSecondaryWhenPrimaryNotAvailable() throws Exception {
        BlobId blobId = secondaryBlobStore.save(BLOB_CONTENT).get();

        assertThat(joiningBlobStore.readBytes(blobId).get())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveShouldWriteToPrimary() throws Exception {
        BlobId blobId = joiningBlobStore.save(BLOB_CONTENT).get();

        assertThat(primaryBlobStore.readBytes(blobId).get())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveShouldNotWriteToSecondary() throws Exception {
        BlobId blobId = joiningBlobStore.save(BLOB_CONTENT).get();

        assertThat(secondaryBlobStore.readBytes(blobId).get())
            .isEmpty();
    }

    @Test
    void saveInputStreamShouldWriteToPrimary() throws Exception {
        BlobId blobId = joiningBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).get();

        assertThat(primaryBlobStore.readBytes(blobId).get())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveInputStreamShouldNotWriteToSecondary() throws Exception {
        BlobId blobId = joiningBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).get();

        assertThat(secondaryBlobStore.readBytes(blobId).get())
            .isEmpty();
    }
}