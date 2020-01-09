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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.HIGH_PERFORMANCE;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.blob.memory.MemoryDumbBlobStore;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.MoreObjects;

import nl.jqno.equalsverifier.EqualsVerifier;
import reactor.core.publisher.Mono;

class HybridBlobStoreTest implements BlobStoreContract {

    private static class FailingBlobStore implements BlobStore {
        @Override
        public Mono<BlobId> save(BucketName bucketName, InputStream data, StoragePolicy storagePolicy) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public Mono<BlobId> save(BucketName bucketName, byte[] data, StoragePolicy storagePolicy) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public Mono<BlobId> save(BucketName bucketName, String data, StoragePolicy storagePolicy) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public BucketName getDefaultBucketName() {
            return BucketName.DEFAULT;
        }

        @Override
        public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public InputStream read(BucketName bucketName, BlobId blobId) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public Mono<Void> deleteBucket(BucketName bucketName) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .toString();
        }
    }

    private static class ThrowingBlobStore implements BlobStore {

        @Override
        public Mono<BlobId> save(BucketName bucketName, byte[] data, StoragePolicy storagePolicy) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public Mono<BlobId> save(BucketName bucketName, String data, StoragePolicy storagePolicy) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public BucketName getDefaultBucketName() {
            return BucketName.DEFAULT;
        }

        @Override
        public Mono<BlobId> save(BucketName bucketName, InputStream data, StoragePolicy storagePolicy) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public InputStream read(BucketName bucketName, BlobId blobId) {
            throw new RuntimeException("broken everywhere");
        }

        @Override
        public Mono<Void> deleteBucket(BucketName bucketName) {
            return Mono.error(new RuntimeException("broken everywhere"));
        }

        @Override
        public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
            return Mono.error(new RuntimeException("broken everywhere"));
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

    private MemoryBlobStore lowCostBlobStore;
    private MemoryBlobStore highPerformanceBlobStore;
    private HybridBlobStore hybridBlobStore;

    @BeforeEach
    void setup() {
        lowCostBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());
        highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());
        hybridBlobStore = HybridBlobStore.builder()
            .lowCost(lowCostBlobStore)
            .highPerformance(highPerformanceBlobStore)
            .configuration(HybridBlobStore.Configuration.DEFAULT)
            .build();
    }

    @Override
    public BlobStore testee() {
        return hybridBlobStore;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return BLOB_ID_FACTORY;
    }

    @Nested
    class StoragePolicyTests {
        @Test
        void saveShouldRelyOnLowCostWhenLowCost() {
            BlobId blobId = hybridBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, LOW_COST).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(lowCostBlobStore.read(BucketName.DEFAULT, blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThatThrownBy(() -> highPerformanceBlobStore.read(BucketName.DEFAULT, blobId))
                    .isInstanceOf(ObjectNotFoundException.class);
            });
        }

        @Test
        void saveShouldRelyOnPerformingWhenPerforming() {
            BlobId blobId = hybridBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, HIGH_PERFORMANCE).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(highPerformanceBlobStore.read(BucketName.DEFAULT, blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThatThrownBy(() -> lowCostBlobStore.read(BucketName.DEFAULT, blobId))
                    .isInstanceOf(ObjectNotFoundException.class);
            });
        }

        @Test
        void saveShouldRelyOnPerformingWhenSizeBasedAndSmall() {
            BlobId blobId = hybridBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, SIZE_BASED).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(highPerformanceBlobStore.read(BucketName.DEFAULT, blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThatThrownBy(() -> lowCostBlobStore.read(BucketName.DEFAULT, blobId))
                    .isInstanceOf(ObjectNotFoundException.class);
            });
        }

        @Test
        void saveShouldRelyOnLowCostWhenSizeBasedAndBig() {
            BlobId blobId = hybridBlobStore.save(BucketName.DEFAULT, TWELVE_MEGABYTES, SIZE_BASED).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(lowCostBlobStore.read(BucketName.DEFAULT, blobId))
                    .satisfies(Throwing.consumer(inputStream -> assertThat(inputStream.read()).isGreaterThan(0)));
                softly.assertThatThrownBy(() -> highPerformanceBlobStore.read(BucketName.DEFAULT, blobId))
                    .isInstanceOf(ObjectNotFoundException.class);
            });
        }

        @Test
        void saveInputStreamShouldRelyOnLowCostWhenLowCost() {
            BlobId blobId = hybridBlobStore.save(BucketName.DEFAULT, new ByteArrayInputStream(BLOB_CONTENT), LOW_COST).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(lowCostBlobStore.read(BucketName.DEFAULT, blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThatThrownBy(() -> highPerformanceBlobStore.read(BucketName.DEFAULT, blobId))
                    .isInstanceOf(ObjectNotFoundException.class);
            });
        }

        @Test
        void saveInputStreamShouldRelyOnPerformingWhenPerforming() {
            BlobId blobId = hybridBlobStore.save(BucketName.DEFAULT, new ByteArrayInputStream(BLOB_CONTENT), HIGH_PERFORMANCE).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(highPerformanceBlobStore.read(BucketName.DEFAULT, blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThatThrownBy(() -> lowCostBlobStore.read(BucketName.DEFAULT, blobId))
                    .isInstanceOf(ObjectNotFoundException.class);
            });
        }

        @Test
        void saveInputStreamShouldRelyOnPerformingWhenSizeBasedAndSmall() {
            BlobId blobId = hybridBlobStore.save(BucketName.DEFAULT, new ByteArrayInputStream(BLOB_CONTENT), SIZE_BASED).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(highPerformanceBlobStore.read(BucketName.DEFAULT, blobId))
                    .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
                softly.assertThatThrownBy(() -> lowCostBlobStore.read(BucketName.DEFAULT, blobId))
                    .isInstanceOf(ObjectNotFoundException.class);
            });
        }

        @Test
        void saveInputStreamShouldRelyOnLowCostWhenSizeBasedAndBig() {
            BlobId blobId = hybridBlobStore.save(BucketName.DEFAULT, new ByteArrayInputStream(TWELVE_MEGABYTES), SIZE_BASED).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(lowCostBlobStore.read(BucketName.DEFAULT, blobId))
                    .satisfies(Throwing.consumer(inputStream -> assertThat(inputStream.read()).isGreaterThan(0)));
                softly.assertThatThrownBy(() -> highPerformanceBlobStore.read(BucketName.DEFAULT, blobId))
                    .isInstanceOf(ObjectNotFoundException.class);
            });
        }
    }

    @Nested
    class LowCostSaveThrowsExceptionDirectly {
        @Test
        void saveShouldFailWhenException() {
            MemoryBlobStore highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());
            HybridBlobStore hybridBlobStore = HybridBlobStore.builder()
                .lowCost(new ThrowingBlobStore())
                .highPerformance(highPerformanceBlobStore)
                .configuration(HybridBlobStore.Configuration.DEFAULT)
                .build();

            assertThatThrownBy(() -> hybridBlobStore.save(hybridBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void saveInputStreamShouldFailWhenException() {
            MemoryBlobStore highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());
            HybridBlobStore hybridBlobStore = HybridBlobStore.builder()
                .lowCost(new ThrowingBlobStore())
                .highPerformance(highPerformanceBlobStore)
                .configuration(HybridBlobStore.Configuration.DEFAULT)
                .build();

            assertThatThrownBy(() -> hybridBlobStore.save(hybridBlobStore.getDefaultBucketName(), new ByteArrayInputStream(BLOB_CONTENT), LOW_COST).block())
                .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    class LowCostSaveCompletesExceptionally {

        @Test
        void saveShouldFailWhenLowCostCompletedExceptionally() {
            MemoryBlobStore highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());
            HybridBlobStore hybridBlobStore = HybridBlobStore.builder()
                .lowCost(new FailingBlobStore())
                .highPerformance(highPerformanceBlobStore)
                .configuration(HybridBlobStore.Configuration.DEFAULT)
                .build();

            assertThatThrownBy(() -> hybridBlobStore.save(hybridBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block())
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void saveInputStreamShouldFallBackToPerformingWhenLowCostCompletedExceptionally() {
            MemoryBlobStore highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());
            HybridBlobStore hybridBlobStore = HybridBlobStore.builder()
                .lowCost(new FailingBlobStore())
                .highPerformance(highPerformanceBlobStore)
                .configuration(HybridBlobStore.Configuration.DEFAULT)
                .build();

            assertThatThrownBy(() -> hybridBlobStore.save(hybridBlobStore.getDefaultBucketName(), new ByteArrayInputStream(BLOB_CONTENT), LOW_COST).block())
                .isInstanceOf(RuntimeException.class);
        }

    }

    @Nested
    class LowCostReadThrowsExceptionDirectly {

        @Test
        void readShouldReturnFallbackToPerformingWhenLowCostGotException() {
            MemoryBlobStore highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());
            HybridBlobStore hybridBlobStore = HybridBlobStore.builder()
                .lowCost(new ThrowingBlobStore())
                .highPerformance(highPerformanceBlobStore)
                .configuration(HybridBlobStore.Configuration.DEFAULT)
                .build();
            BlobId blobId = highPerformanceBlobStore.save(hybridBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block();

            assertThat(hybridBlobStore.read(hybridBlobStore.getDefaultBucketName(), blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToPerformingWhenLowCostGotException() {
            MemoryBlobStore highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());

            HybridBlobStore hybridBlobStore = HybridBlobStore.builder()
                .lowCost(new ThrowingBlobStore())
                .highPerformance(highPerformanceBlobStore)
                .configuration(HybridBlobStore.Configuration.DEFAULT)
                .build();
            BlobId blobId = highPerformanceBlobStore.save(hybridBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block();

            assertThat(hybridBlobStore.readBytes(hybridBlobStore.getDefaultBucketName(), blobId).block())
                .isEqualTo(BLOB_CONTENT);
        }

    }

    @Nested
    class LowCostReadCompletesExceptionally {

        @Test
        void readShouldReturnFallbackToPerformingWhenLowCostCompletedExceptionally() {
            MemoryBlobStore highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());
            HybridBlobStore hybridBlobStore = HybridBlobStore.builder()
                .lowCost(new FailingBlobStore())
                .highPerformance(highPerformanceBlobStore)
                .configuration(HybridBlobStore.Configuration.DEFAULT)
                .build();
            BlobId blobId = highPerformanceBlobStore.save(hybridBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block();

            assertThat(hybridBlobStore.read(hybridBlobStore.getDefaultBucketName(), blobId))
                .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
        }

        @Test
        void readBytesShouldReturnFallbackToPerformingWhenLowCostCompletedExceptionally() {
            MemoryBlobStore highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, new MemoryDumbBlobStore());
            HybridBlobStore hybridBlobStore = HybridBlobStore.builder()
                .lowCost(new FailingBlobStore())
                .highPerformance(highPerformanceBlobStore)
                .configuration(HybridBlobStore.Configuration.DEFAULT)
                .build();
            BlobId blobId = highPerformanceBlobStore.save(hybridBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block();

            assertThat(hybridBlobStore.readBytes(hybridBlobStore.getDefaultBucketName(), blobId).block())
                .isEqualTo(BLOB_CONTENT);
        }
    }

    @Test
    void readShouldReturnFromLowCostWhenAvailable() {
        BlobId blobId = lowCostBlobStore.save(lowCostBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block();

        assertThat(hybridBlobStore.read(hybridBlobStore.getDefaultBucketName(), blobId))
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
    }

    @Test
    void readShouldReturnFromPerformingWhenLowCostNotAvailable() {
        BlobId blobId = highPerformanceBlobStore.save(hybridBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block();

        assertThat(hybridBlobStore.read(hybridBlobStore.getDefaultBucketName(), blobId))
            .hasSameContentAs(new ByteArrayInputStream(BLOB_CONTENT));
    }

    @Test
    void readBytesShouldReturnFromLowCostWhenAvailable() {
        BlobId blobId = lowCostBlobStore.save(lowCostBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block();

        assertThat(hybridBlobStore.readBytes(lowCostBlobStore.getDefaultBucketName(), blobId).block())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void readBytesShouldReturnFromPerformingWhenLowCostNotAvailable() {
        BlobId blobId = highPerformanceBlobStore.save(hybridBlobStore.getDefaultBucketName(), BLOB_CONTENT, LOW_COST).block();

        assertThat(hybridBlobStore.readBytes(hybridBlobStore.getDefaultBucketName(), blobId).block())
            .isEqualTo(BLOB_CONTENT);
    }

    @Test
    void deleteBucketShouldDeleteBothLowCostAndPerformingBuckets() {
        BlobId blobId1 = highPerformanceBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, LOW_COST).block();
        BlobId blobId2 = lowCostBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, LOW_COST).block();

        hybridBlobStore.deleteBucket(BucketName.DEFAULT).block();

        assertThatThrownBy(() -> highPerformanceBlobStore.readBytes(BucketName.DEFAULT, blobId1).block())
            .isInstanceOf(ObjectStoreException.class);
        assertThatThrownBy(() -> lowCostBlobStore.readBytes(BucketName.DEFAULT, blobId2).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    void deleteBucketShouldDeleteLowCostBucketEvenWhenPerformingDoesNotExist() {
        BlobId blobId = lowCostBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, LOW_COST).block();

        hybridBlobStore.deleteBucket(BucketName.DEFAULT).block();

        assertThatThrownBy(() -> lowCostBlobStore.readBytes(BucketName.DEFAULT, blobId).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    void deleteBucketShouldDeletePerformingBucketEvenWhenLowCostDoesNotExist() {
        BlobId blobId = highPerformanceBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, LOW_COST).block();

        hybridBlobStore.deleteBucket(BucketName.DEFAULT).block();

        assertThatThrownBy(() -> highPerformanceBlobStore.readBytes(BucketName.DEFAULT, blobId).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    void deleteBucketShouldNotThrowWhenLowCostAndPerformingBucketsDoNotExist() {
        assertThatCode(() -> hybridBlobStore.deleteBucket(BucketName.DEFAULT).block())
            .doesNotThrowAnyException();
    }

    @Test
    void getDefaultBucketNameShouldThrowWhenBlobStoreDontShareTheSameDefaultBucketName() {
        lowCostBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, BucketName.of("lowCost"), new MemoryDumbBlobStore());
        highPerformanceBlobStore = new MemoryBlobStore(BLOB_ID_FACTORY, BucketName.of("highPerformance"), new MemoryDumbBlobStore());
        hybridBlobStore = HybridBlobStore.builder()
            .lowCost(lowCostBlobStore)
            .highPerformance(highPerformanceBlobStore)
            .configuration(HybridBlobStore.Configuration.DEFAULT)
            .build();

        assertThatThrownBy(() -> hybridBlobStore.getDefaultBucketName())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteShouldDeleteBothLowCostAndPerformingBlob() {
        BlobId blobId1 = hybridBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, LOW_COST).block();
        BlobId blobId2 = hybridBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, HIGH_PERFORMANCE).block();

        hybridBlobStore.delete(BucketName.DEFAULT, blobId1).block();

        assertThatThrownBy(() -> highPerformanceBlobStore.readBytes(BucketName.DEFAULT, blobId1).block())
            .isInstanceOf(ObjectStoreException.class);
        assertThatThrownBy(() -> lowCostBlobStore.readBytes(BucketName.DEFAULT, blobId2).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    void deleteShouldDeleteLowCostBlobEvenWhenPerformingDoesNotExist() {
        BlobId blobId = lowCostBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, LOW_COST).block();

        hybridBlobStore.delete(BucketName.DEFAULT, blobId).block();

        assertThatThrownBy(() -> lowCostBlobStore.readBytes(BucketName.DEFAULT, blobId).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    void deleteShouldDeletePerformingBlobEvenWhenLowCostDoesNotExist() {
        BlobId blobId = highPerformanceBlobStore.save(BucketName.DEFAULT, BLOB_CONTENT, LOW_COST).block();

        hybridBlobStore.delete(BucketName.DEFAULT, blobId).block();

        assertThatThrownBy(() -> highPerformanceBlobStore.readBytes(BucketName.DEFAULT, blobId).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    void deleteShouldNotThrowWhenLowCostAndPerformingBlobsDoNotExist() {
        assertThatCode(() -> hybridBlobStore.delete(BucketName.DEFAULT, blobIdFactory().randomId()).block())
            .doesNotThrowAnyException();
    }

    @Nested
    class ConfigurationTest {
        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(HybridBlobStore.Configuration.class)
                .verify();
        }
    }
}
