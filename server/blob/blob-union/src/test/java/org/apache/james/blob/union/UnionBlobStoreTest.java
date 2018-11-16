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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.util.CompletableFutureUtil;
import org.apache.james.util.StreamUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.shaded.com.google.common.base.MoreObjects;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

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

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .toString();
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

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .toString();
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
        unionBlobStore = UnionBlobStore.builder()
            .current(currentBlobStore)
            .legacy(legacyBlobStore)
            .build();
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
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new ThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
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
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new ThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
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
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new FutureThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
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
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new FutureThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
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
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new ThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).get();

            assertThat(unionBlobStore.read(blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToLegacyWhenCurrentGotException() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);

            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new ThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
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
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new FutureThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).get();

            assertThat(unionBlobStore.read(blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToLegacyWhenCurrentCompletedExceptionally() throws Exception {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new FutureThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).get();

            assertThat(unionBlobStore.readBytes(blobId).get())
                .isEqualTo(BLOB_CONTENT);
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class CurrentAndLegacyCouldNotComplete {


        Stream<Function<UnionBlobStore, CompletableFuture<?>>> blobStoreOperationsReturnFutures() {
            return Stream.of(
                blobStore -> blobStore.save(BLOB_CONTENT),
                blobStore -> blobStore.save(new ByteArrayInputStream(BLOB_CONTENT)),
                blobStore -> blobStore.readBytes(BLOB_ID_FACTORY.randomId()));
        }

        Stream<Function<UnionBlobStore, InputStream>> blobStoreOperationsNotReturnFutures() {
            return Stream.of(
                blobStore -> blobStore.read(BLOB_ID_FACTORY.randomId()));
        }

        Stream<Arguments> blobStoresCauseReturnExceptionallyFutures() {
            List<UnionBlobStore> futureThrowingUnionBlobStores = ImmutableList.of(
                UnionBlobStore.builder()
                    .current(new ThrowingBlobStore())
                    .legacy(new FutureThrowingBlobStore())
                    .build(),
                UnionBlobStore.builder()
                    .current(new FutureThrowingBlobStore())
                    .legacy(new ThrowingBlobStore())
                    .build(),
                UnionBlobStore.builder()
                    .current(new FutureThrowingBlobStore())
                    .legacy(new FutureThrowingBlobStore())
                    .build());

            return blobStoreOperationsReturnFutures()
                .flatMap(blobStoreFunction -> futureThrowingUnionBlobStores
                    .stream()
                    .map(blobStore -> Arguments.of(blobStore, blobStoreFunction)));
        }

        Stream<Arguments> blobStoresCauseThrowExceptions() {
            UnionBlobStore throwingUnionBlobStore = UnionBlobStore.builder()
                .current(new ThrowingBlobStore())
                .legacy(new ThrowingBlobStore())
                .build();

            return StreamUtils.flatten(
                blobStoreOperationsReturnFutures()
                    .map(blobStoreFunction -> Arguments.of(throwingUnionBlobStore, blobStoreFunction)),
                blobStoreOperationsNotReturnFutures()
                    .map(blobStoreFunction -> Arguments.of(throwingUnionBlobStore, blobStoreFunction)));
        }

        @ParameterizedTest
        @MethodSource("blobStoresCauseThrowExceptions")
        void operationShouldThrow(UnionBlobStore blobStoreThrowsException,
                                  Function<UnionBlobStore, CompletableFuture<?>> blobStoreOperation) {
            assertThatThrownBy(() -> blobStoreOperation.apply(blobStoreThrowsException))
                .isInstanceOf(RuntimeException.class);
        }

        @ParameterizedTest
        @MethodSource("blobStoresCauseReturnExceptionallyFutures")
        void operationShouldReturnExceptionallyFuture(UnionBlobStore blobStoreReturnsExceptionallyFuture,
                                                      Function<UnionBlobStore, CompletableFuture<?>> blobStoreOperation) {
            assertThat(blobStoreOperation.apply(blobStoreReturnsExceptionallyFuture))
                .isCompletedExceptionally();
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