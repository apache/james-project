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
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.util.StreamUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

class UnionBlobStoreTest implements BlobStoreContract {

    private static class FailingBlobStore implements BlobStore {

        @Override
        public Mono<BlobId> save(byte[] data) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public Mono<BlobId> save(String data) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public Mono<BlobId> save(InputStream data) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public Mono<byte[]> readBytes(BlobId blobId) {
            return Mono.error(new RuntimeException("broken everywhere"));
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
        public Mono<BlobId> save(byte[] data) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public Mono<BlobId> save(String data) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public Mono<BlobId> save(InputStream data) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public Mono<byte[]> readBytes(BlobId blobId) {
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
    private static final String STRING_CONTENT = "blob content";
    private static final byte [] BLOB_CONTENT = STRING_CONTENT.getBytes();

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
        void saveShouldFallBackToLegacyWhenCurrentGotException() {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new ThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = unionBlobStore.save(BLOB_CONTENT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(unionBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThat(legacyBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
            });
        }

        @Test
        void saveInputStreamShouldFallBackToLegacyWhenCurrentGotException() {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new ThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = unionBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).block();

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
        void saveShouldFallBackToLegacyWhenCurrentCompletedExceptionally() {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new FailingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = unionBlobStore.save(BLOB_CONTENT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(unionBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThat(legacyBlobStore.read(blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
            });
        }

        @Test
        void saveInputStreamShouldFallBackToLegacyWhenCurrentCompletedExceptionally() {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new FailingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = unionBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).block();

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
        void readShouldReturnFallbackToLegacyWhenCurrentGotException() {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new ThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).block();

            assertThat(unionBlobStore.read(blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToLegacyWhenCurrentGotException() {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);

            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new ThrowingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).block();

            assertThat(unionBlobStore.readBytes(blobId).block())
                .isEqualTo(BLOB_CONTENT);
        }

    }

    @Nested
    class CurrentReadCompletesExceptionally {

        @Test
        void readShouldReturnFallbackToLegacyWhenCurrentCompletedExceptionally() {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new FailingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).block();

            assertThat(unionBlobStore.read(blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToLegacyWhenCurrentCompletedExceptionally() {
            MemoryBlobStore legacyBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY);
            UnionBlobStore unionBlobStore = UnionBlobStore.builder()
                .current(new FailingBlobStore())
                .legacy(legacyBlobStore)
                .build();
            BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).block();

            assertThat(unionBlobStore.readBytes(blobId).block())
                .isEqualTo(BLOB_CONTENT);
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class CurrentAndLegacyCouldNotComplete {


        Stream<Function<UnionBlobStore, Mono<?>>> blobStoreOperationsReturnFutures() {
            return Stream.of(
                blobStore -> blobStore.save(BLOB_CONTENT),
                blobStore -> blobStore.save(STRING_CONTENT),
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
                    .legacy(new FailingBlobStore())
                    .build(),
                UnionBlobStore.builder()
                    .current(new FailingBlobStore())
                    .legacy(new ThrowingBlobStore())
                    .build(),
                UnionBlobStore.builder()
                    .current(new FailingBlobStore())
                    .legacy(new FailingBlobStore())
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
                                  Function<UnionBlobStore, Mono<?>> blobStoreOperation) {
            assertThatThrownBy(() -> blobStoreOperation.apply(blobStoreThrowsException).block())
                .isInstanceOf(RuntimeException.class);
        }

        @ParameterizedTest
        @MethodSource("blobStoresCauseReturnExceptionallyFutures")
        void operationShouldReturnExceptionallyFuture(UnionBlobStore blobStoreReturnsExceptionallyFuture,
                                                      Function<UnionBlobStore, Mono<?>> blobStoreOperation) {
            Mono<?> mono = blobStoreOperation.apply(blobStoreReturnsExceptionallyFuture);
            assertThatThrownBy(mono::block).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void readShouldReturnFromCurrentWhenAvailable() {
        BlobId blobId = currentBlobStore.save(BLOB_CONTENT).block();

        assertThat(unionBlobStore.read(blobId))
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
    }

    @Test
    void readShouldReturnFromLegacyWhenCurrentNotAvailable() {
        BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).block();

        assertThat(unionBlobStore.read(blobId))
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
    }

    @Test
    void readBytesShouldReturnFromCurrentWhenAvailable() {
        BlobId blobId = currentBlobStore.save(BLOB_CONTENT).block();

        assertThat(unionBlobStore.readBytes(blobId).block())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void readBytesShouldReturnFromLegacyWhenCurrentNotAvailable() {
        BlobId blobId = legacyBlobStore.save(BLOB_CONTENT).block();

        assertThat(unionBlobStore.readBytes(blobId).block())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveShouldWriteToCurrent() {
        BlobId blobId = unionBlobStore.save(BLOB_CONTENT).block();

        assertThat(currentBlobStore.readBytes(blobId).block())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveShouldNotWriteToLegacy() {
        BlobId blobId = unionBlobStore.save(BLOB_CONTENT).block();

        assertThatThrownBy(() -> legacyBlobStore.readBytes(blobId).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    void saveStringShouldWriteToCurrent() {
        BlobId blobId = unionBlobStore.save(STRING_CONTENT).block();

        assertThat(currentBlobStore.readBytes(blobId).block())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveStringShouldNotWriteToLegacy() {
        BlobId blobId = unionBlobStore.save(STRING_CONTENT).block();

        assertThatThrownBy(() -> legacyBlobStore.readBytes(blobId).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    void saveInputStreamShouldWriteToCurrent() {
        BlobId blobId = unionBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).block();

        assertThat(currentBlobStore.readBytes(blobId).block())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void saveInputStreamShouldNotWriteToLegacy() {
        BlobId blobId = unionBlobStore.save(new ByteArrayInputStream(BLOB_CONTENT)).block();

        assertThatThrownBy(() -> legacyBlobStore.readBytes(blobId).block())
            .isInstanceOf(ObjectStoreException.class);
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
