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

package org.apache.james.blob.union;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.concurrent.CompletableFuture;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.util.CompletableFutureUtil;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnionBlobStoreTest implements BlobStoreContract {

    private static class FutureThrowingBlobStore implements BlobStore {

        @Override
        public CompletableFuture<BlobId> save(byte[] data) {
            return CompletableFutureUtil.exceptionallyFuture(new RuntimeException("broken everywhere"));
        }

        @Override
        public CompletableFuture<BlobId> save(InputStream data) {
            return CompletableFutureUtil.exceptionallyFuture(new RuntimeException("broken everywhere"));
        }

        @Override
        public CompletableFuture<byte[]> readBytes(BlobId blobId) {
            return CompletableFutureUtil.exceptionallyFuture(new RuntimeException("broken everywhere"));
        }

        @Override
        public InputStream read(BlobId blobId) {
            throw new RuntimeException("broken everywhere");
        }
    }

    private static class ThrowingBlobStore implements BlobStore {

        @Override
        public CompletableFuture<BlobId> save(byte[] data) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public CompletableFuture<BlobId> save(InputStream data) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public CompletableFuture<byte[]> readBytes(BlobId blobId) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public InputStream read(BlobId blobId) {
            throw new RuntimeException("broken everywhere");
        }
    }

    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final byte [] BLOB_CONTENT = "blob content".getBytes();

    private MemoryBlobStore currentBlobStore;
    private MemoryBlobStore legacyBlobStore;
    private UnionBlobStore unionBlobStore;

    @BeforeEach
    void setup() {
        currentBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
        legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
        unionBlobStore = new UnionBlobStore(currentBlobStore, legacyBlobStore);
    }

    @Override
    public BlobStore testee() {
        return unionBlobStore;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return BLOB_ID_FACTORY;
    }

    @Nested
    class CurrentSaveThrowsExceptionDirectly {

        @Test
        void saveShouldFallBackToLegacyWhenCurrentGotException() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = new UnionBlobStore(new ThrowingBlobStore(), legacyBlobStore);
            BlobId blobId = unionBlobStore.save(BLOB_CONTENT).get();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(unionBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThat(legacyBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
            });
        }

        @Test
        void saveInputStreamShouldFallBackToLegacyWhenCurrentGotException() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = new UnionBlobStore(new ThrowingBlobStore(), legacyBlobStore);
            BlobId blobId = unionBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).get();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(unionBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThat(legacyBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
            });
        }
    }

    @Nested
    class CurrentSaveCompletesExceptionally {

        @Test
        void saveShouldFallBackToLegacyWhenCurrentCompletedExceptionally() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = new UnionBlobStore(new FutureThrowingBlobStore(), legacyBlobStore);
            BlobId blobId = unionBlobStore.save(BLOB_CONTENT).get();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(unionBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThat(legacyBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
            });
        }

        @Test
        void saveInputStreamShouldFallBackToLegacyWhenCurrentCompletedExceptionally() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = new UnionBlobStore(new FutureThrowingBlobStore(), legacyBlobStore);
            BlobId blobId = unionBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).get();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(unionBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThat(legacyBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
            });
        }

    }

    @Nested
    class CurrentReadThrowsExceptionDirectly {

        @Test
        void readShouldReturnFallbackToLegacyWhenCurrentGotException() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = new UnionBlobStore(new ThrowingBlobStore(), legacyBlobStore);
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).get();

            assertThat(unionBlobStore.read(blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToLegacyWhenCurrentGotException() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = new UnionBlobStore(new ThrowingBlobStore(), legacyBlobStore);
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).get();

            assertThat(unionBlobStore.readBytes(blobId).get())
                .isEqualTo(BLOB_CONTENT);
        }

    }

    @Nested
    class CurrentReadCompletesExceptionally {

        @Test
        void readShouldReturnFallbackToLegacyWhenCurrentCompletedExceptionally() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = new UnionBlobStore(new FutureThrowingBlobStore(), legacyBlobStore);
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).get();

            assertThat(unionBlobStore.read(blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToLegacyWhenCurrentCompletedExceptionally() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = new UnionBlobStore(new FutureThrowingBlobStore(), legacyBlobStore);
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).get();

            assertThat(unionBlobStore.readBytes(blobId).get())
                .isEqualTo(BLOB_CONTENT);
        }
    }


    @Test
    void readShouldReturnFromCurrentWhenAvailable() throws Exception {
        BlobId blobId = currentBlobStore.save(BLOB_CONTENT).get();

        assertThat(unionBlobStore.read(blobId))
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
    }

    @Test
    void readShouldReturnFromLegacyWhenCurrentNotAvailable() throws Exception {
        BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).get();

        assertThat(unionBlobStore.read(blobId))
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
    }

    @Test
    void readBytesShouldReturnFromCurrentWhenAvailable() throws Exception {
        BlobId blobId = currentBlobStore.save(BLOB_CONTENT).get();

        assertThat(unionBlobStore.readBytes(blobId).get())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void readBytesShouldReturnFromLegacyWhenCurrentNotAvailable() throws Exception {
        BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).get();

        assertThat(unionBlobStore.readBytes(blobId).get())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveShouldWriteToCurrent() throws Exception {
        BlobId blobId = unionBlobStore.save(BLOB_CONTENT).get();

        assertThat(currentBlobStore.readBytes(blobId).get())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveShouldNotWriteToLegacy() throws Exception {
        BlobId blobId = unionBlobStore.save(BLOB_CONTENT).get();

        assertThat(legacyBlobStore.readBytes(blobId).get())
            .isEmpty();
    }

    @Test
    void saveInputStreamShouldWriteToCurrent() throws Exception {
        BlobId blobId = unionBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).get();

        assertThat(currentBlobStore.readBytes(blobId).get())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveInputStreamShouldNotWriteToLegacy() throws Exception {
        BlobId blobId = unionBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).get();

        assertThat(legacyBlobStore.readBytes(blobId).get())
            .isEmpty();
    }

    @Test
    void streamHasContentShouldReturnTrueWhenStreamHasContent() throws Exception {
        PushbackInputStream pushBackIS = new PushbackInputStream(new ByteArrayInputStream(BLOB_CONTENT));

        assertThat(unionBlobStore.streamHasContent(pushBackIS))
            .isTrue();
    }

    @Test
    void streamHasContentShouldReturnFalseWhenStreamHasNoContent() throws Exception {
        PushbackInputStream pushBackIS = new PushbackInputStream(new ByteArrayInputStream(new byte[0]));

        assertThat(unionBlobStore.streamHasContent(pushBackIS))
            .isFalse();
    }

    @Test
    void streamHasContentShouldNotThrowWhenStreamHasNoContent() {
        PushbackInputStream pushBackIS = new PushbackInputStream(new ByteArrayInputStream(new byte[0]));

        assertThatCode(() -> unionBlobStore.streamHasContent(pushBackIS))
            .doesNotThrowAnyException();
    }

    @Test
    void streamHasContentShouldNotDrainPushBackStreamContent() throws Exception {
        PushbackInputStream pushBackIS = new PushbackInputStream(new ByteArrayInputStream(BLOB_CONTENT));
        unionBlobStore.streamHasContent(pushBackIS);

        assertThat(pushBackIS)
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
    }

    @Test
    void streamHasContentShouldKeepStreamEmptyWhenStreamIsEmpty() throws Exception {
        PushbackInputStream pushBackIS = new PushbackInputStream(new ByteArrayInputStream(new byte[0]));
        unionBlobStore.streamHasContent(pushBackIS);

        assertThat(pushBackIS)
            .hasSameContentAs(new ByteArrayInputStream(new byte[0]));
    }
}