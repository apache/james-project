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

package org.apache.james.server.blob.deduplication;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.BloomFilterGCAlgorithm.Context;
import org.apache.james.server.blob.deduplication.BloomFilterGCAlgorithm.Context.Snapshot;
import org.apache.james.task.Task;
import org.apache.james.utils.UpdatableTickingClock;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BloomFilterGCAlgorithmContract {

    PlainBlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();
    ZonedDateTime NOW = ZonedDateTime.parse("2015-10-30T16:12:00Z");
    BucketName DEFAULT_BUCKET = BucketName.of("default");
    GenerationAwareBlobId.Configuration GENERATION_AWARE_BLOB_ID_CONFIGURATION = GenerationAwareBlobId.Configuration.DEFAULT;
    int EXPECTED_BLOB_COUNT = 100;
    int DELETION_WINDOW_SIZE = 10;

    double ASSOCIATED_PROBABILITY = 0.01;

    ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await()
        .atMost(TEN_SECONDS);

    BlobReferenceSource BLOB_REFERENCE_SOURCE = mock(BlobReferenceSource.class);
    UpdatableTickingClock CLOCK = new UpdatableTickingClock(NOW.toInstant());
    GenerationAwareBlobId.Factory GENERATION_AWARE_BLOB_ID_FACTORY = new GenerationAwareBlobId.Factory(CLOCK, BLOB_ID_FACTORY, GENERATION_AWARE_BLOB_ID_CONFIGURATION);

    BlobStoreDAO blobStoreDAO();

    @BeforeEach
    default void setUp() {
        CLOCK.setInstant(NOW.toInstant());
    }

    default BlobStore blobStore() {
        return new DeDuplicationBlobStore(blobStoreDAO(), GENERATION_AWARE_BLOB_ID_FACTORY);
    }

    default BloomFilterGCAlgorithm bloomFilterGCAlgorithm() {
        return new BloomFilterGCAlgorithm(BLOB_REFERENCE_SOURCE,
            blobStoreDAO(),
            GENERATION_AWARE_BLOB_ID_FACTORY,
            GENERATION_AWARE_BLOB_ID_CONFIGURATION,
            CLOCK);
    }

    @RepeatedTest(10)
    default void gcShouldRemoveOrphanBlob() {
        BlobStore blobStore = blobStore();
        BlobId blobId = Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block();
        when(BLOB_REFERENCE_SOURCE.listReferencedBlobs()).thenReturn(Flux.empty());
        CLOCK.setInstant(NOW.plusMonths(2).toInstant());

        Context context = new Context(EXPECTED_BLOB_COUNT, ASSOCIATED_PROBABILITY);
        BloomFilterGCAlgorithm bloomFilterGCAlgorithm = bloomFilterGCAlgorithm();
        Task.Result result = Mono.from(bloomFilterGCAlgorithm.gc(EXPECTED_BLOB_COUNT, DELETION_WINDOW_SIZE, ASSOCIATED_PROBABILITY, DEFAULT_BUCKET, context)).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(Snapshot.builder()
                .referenceSourceCount(0)
                .blobCount(1)
                .gcedBlobCount(1)
                .errorCount(0)
                .bloomFilterExpectedBlobCount(100)
                .bloomFilterAssociatedProbability(ASSOCIATED_PROBABILITY)
                .build());
        assertThatThrownBy(() -> blobStore.read(DEFAULT_BUCKET, blobId))
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void gcShouldNotRemoveUnExpireBlob() {
        BlobStore blobStore = blobStore();
        BlobId blobId = Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block();
        when(BLOB_REFERENCE_SOURCE.listReferencedBlobs()).thenReturn(Flux.empty());

        Context context = new Context(EXPECTED_BLOB_COUNT, ASSOCIATED_PROBABILITY);
        BloomFilterGCAlgorithm bloomFilterGCAlgorithm = bloomFilterGCAlgorithm();
        Task.Result result = Mono.from(bloomFilterGCAlgorithm.gc(EXPECTED_BLOB_COUNT, DELETION_WINDOW_SIZE, ASSOCIATED_PROBABILITY, DEFAULT_BUCKET, context)).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(Snapshot.builder()
                .referenceSourceCount(0)
                .blobCount(1)
                .gcedBlobCount(0)
                .errorCount(0)
                .bloomFilterExpectedBlobCount(100)
                .bloomFilterAssociatedProbability(ASSOCIATED_PROBABILITY)
                .build());
        assertThat(blobStore.read(DEFAULT_BUCKET, blobId))
            .isNotNull();
    }

    @RepeatedTest(10)
    default void gcShouldNotRemoveReferencedBlob() {
        BlobStore blobStore = blobStore();
        BlobId blobId = Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block();

        when(BLOB_REFERENCE_SOURCE.listReferencedBlobs()).thenReturn(Flux.just(blobId));

        Context context = new Context(EXPECTED_BLOB_COUNT, ASSOCIATED_PROBABILITY);
        BloomFilterGCAlgorithm bloomFilterGCAlgorithm = bloomFilterGCAlgorithm();
        Task.Result result = Mono.from(bloomFilterGCAlgorithm.gc(EXPECTED_BLOB_COUNT, DELETION_WINDOW_SIZE, ASSOCIATED_PROBABILITY, DEFAULT_BUCKET, context)).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(Snapshot.builder()
                .referenceSourceCount(1)
                .blobCount(1)
                .gcedBlobCount(0)
                .errorCount(0)
                .bloomFilterExpectedBlobCount(100)
                .bloomFilterAssociatedProbability(ASSOCIATED_PROBABILITY)
                .build());
        assertThat(blobStore.read(DEFAULT_BUCKET, blobId))
            .isNotNull();
    }

    @Test
    default void gcShouldSuccessWhenMixCase() {
        BlobStore blobStore = blobStore();
        List<BlobId> referencedBlobIds = IntStream.range(0, 100)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .collect(Collectors.toList());
        List<BlobId> orphanBlobIds = IntStream.range(0, 50)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .collect(Collectors.toList());

        when(BLOB_REFERENCE_SOURCE.listReferencedBlobs()).thenReturn(Flux.fromIterable(referencedBlobIds));
        CLOCK.setInstant(NOW.plusMonths(2).toInstant());

        Context context = new Context(EXPECTED_BLOB_COUNT, ASSOCIATED_PROBABILITY);
        BloomFilterGCAlgorithm bloomFilterGCAlgorithm = bloomFilterGCAlgorithm();
        Task.Result result = Mono.from(bloomFilterGCAlgorithm.gc(EXPECTED_BLOB_COUNT, DELETION_WINDOW_SIZE, ASSOCIATED_PROBABILITY, DEFAULT_BUCKET, context)).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        Context.Snapshot snapshot = context.snapshot();

        assertThat(snapshot.getReferenceSourceCount())
            .isEqualTo(referencedBlobIds.size());
        assertThat(snapshot.getBlobCount())
            .isEqualTo(referencedBlobIds.size() + orphanBlobIds.size());

        assertThat(snapshot.getGcedBlobCount())
            .isLessThanOrEqualTo(orphanBlobIds.size())
            .isGreaterThan(0);

        referencedBlobIds.forEach(blobId ->
            assertThat(blobStore.read(DEFAULT_BUCKET, blobId))
                .isNotNull());
    }

    @Test
    default void allOrphanBlobIdsShouldRemovedAfterMultipleRunningTimesGC() {
        BlobStore blobStore = blobStore();
        List<BlobId> referencedBlobIds = IntStream.range(0, 100)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .collect(Collectors.toList());
        List<BlobId> orphanBlobIds = IntStream.range(0, 50)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .collect(Collectors.toList());

        when(BLOB_REFERENCE_SOURCE.listReferencedBlobs()).thenReturn(Flux.fromIterable(referencedBlobIds));
        CLOCK.setInstant(NOW.plusMonths(2).toInstant());

        CALMLY_AWAIT.untilAsserted(() -> {
            Mono.from(bloomFilterGCAlgorithm().gc(
                    EXPECTED_BLOB_COUNT,
                    DELETION_WINDOW_SIZE,
                    ASSOCIATED_PROBABILITY,
                    DEFAULT_BUCKET,
                    new Context(EXPECTED_BLOB_COUNT, ASSOCIATED_PROBABILITY)))
                .block();

            orphanBlobIds.forEach(blobId ->
                assertThatThrownBy(() -> blobStore.read(DEFAULT_BUCKET, blobId))
                    .isInstanceOf(ObjectNotFoundException.class));
        });
    }

    @Test
    default void gcShouldHandlerErrorWhenException() {
        when(BLOB_REFERENCE_SOURCE.listReferencedBlobs()).thenReturn(Flux.empty());
        BlobStoreDAO blobStoreDAO = mock(BlobStoreDAO.class);
        BlobId blobId = GENERATION_AWARE_BLOB_ID_FACTORY.of(UUID.randomUUID().toString());
        when(blobStoreDAO.listBlobs(DEFAULT_BUCKET)).thenReturn(Flux.just(blobId));
        when(blobStoreDAO.delete(ArgumentMatchers.eq(DEFAULT_BUCKET), any(Collection.class))).thenReturn(Mono.error(new RuntimeException("test")));

        CLOCK.setInstant(NOW.plusMonths(2).toInstant());

        Context context = new Context(EXPECTED_BLOB_COUNT, ASSOCIATED_PROBABILITY);
        BloomFilterGCAlgorithm bloomFilterGCAlgorithm = new BloomFilterGCAlgorithm(
            BLOB_REFERENCE_SOURCE,
            blobStoreDAO,
            GENERATION_AWARE_BLOB_ID_FACTORY,
            GENERATION_AWARE_BLOB_ID_CONFIGURATION,
            CLOCK);
        Task.Result result = Mono.from(bloomFilterGCAlgorithm.gc(EXPECTED_BLOB_COUNT, DELETION_WINDOW_SIZE, ASSOCIATED_PROBABILITY, DEFAULT_BUCKET, context)).block();

        assertThat(result).isEqualTo(Task.Result.PARTIAL);
        assertThat(context.snapshot())
            .isEqualTo(Snapshot.builder()
                .referenceSourceCount(0)
                .blobCount(1)
                .gcedBlobCount(0)
                .errorCount(1)
                .bloomFilterExpectedBlobCount(100)
                .bloomFilterAssociatedProbability(ASSOCIATED_PROBABILITY)
                .build());
    }
}
