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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.task.Task;
import org.apache.james.task.Task.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BloomFilterGCAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(BloomFilterGCAlgorithm.class);
    private static final Funnel<CharSequence> BLOOM_FILTER_FUNNEL = Funnels.stringFunnel(StandardCharsets.US_ASCII);

    public static class Context {

        public static class Snapshot {

            public static Builder builder() {
                return new Builder();
            }

            static class Builder {
                private Optional<Long> referenceSourceCount;
                private Optional<Long> blobCount;
                private Optional<Long> gcedBlobCount;
                private Optional<Long> errorCount;
                private Optional<Long> bloomFilterExpectedBlobCount;
                private Optional<Double> bloomFilterAssociatedProbability;

                Builder() {
                    referenceSourceCount = Optional.empty();
                    blobCount = Optional.empty();
                    gcedBlobCount = Optional.empty();
                    errorCount = Optional.empty();
                    bloomFilterExpectedBlobCount = Optional.empty();
                    bloomFilterAssociatedProbability = Optional.empty();
                }

                public Snapshot build() {
                    return new Snapshot(
                        referenceSourceCount.orElse(0L),
                        blobCount.orElse(0L),
                        gcedBlobCount.orElse(0L),
                        errorCount.orElse(0L),
                        bloomFilterExpectedBlobCount.orElse(0L),
                        bloomFilterAssociatedProbability.orElse(0.0));
                }

                public Builder referenceSourceCount(long referenceSourceCount) {
                    this.referenceSourceCount = Optional.of(referenceSourceCount);
                    return this;
                }

                public Builder blobCount(long blobCount) {
                    this.blobCount = Optional.of(blobCount);
                    return this;
                }

                public Builder gcedBlobCount(long gcedBlobCount) {
                    this.gcedBlobCount = Optional.of(gcedBlobCount);
                    return this;
                }

                public Builder errorCount(long errorCount) {
                    this.errorCount = Optional.of(errorCount);
                    return this;
                }

                public Builder bloomFilterExpectedBlobCount(long bloomFilterExpectedBlobCount) {
                    this.bloomFilterExpectedBlobCount = Optional.of(bloomFilterExpectedBlobCount);
                    return this;
                }

                public Builder bloomFilterAssociatedProbability(double bloomFilterAssociatedProbability) {
                    this.bloomFilterAssociatedProbability = Optional.of(bloomFilterAssociatedProbability);
                    return this;
                }
            }

            private final long referenceSourceCount;
            private final long blobCount;
            private final long gcedBlobCount;
            private final long errorCount;
            private final long bloomFilterExpectedBlobCount;
            private final double bloomFilterAssociatedProbability;

            Snapshot(long referenceSourceCount,
                     long blobCount,
                     long gcedBlobCount,
                     long errorCount,
                     long bloomFilterExpectedBlobCount,
                     double bloomFilterAssociatedProbability) {
                this.referenceSourceCount = referenceSourceCount;
                this.blobCount = blobCount;
                this.gcedBlobCount = gcedBlobCount;
                this.errorCount = errorCount;
                this.bloomFilterExpectedBlobCount = bloomFilterExpectedBlobCount;
                this.bloomFilterAssociatedProbability = bloomFilterAssociatedProbability;
            }

            public long getReferenceSourceCount() {
                return referenceSourceCount;
            }

            public long getBlobCount() {
                return blobCount;
            }

            public long getGcedBlobCount() {
                return gcedBlobCount;
            }

            public long getErrorCount() {
                return errorCount;
            }

            public long getBloomFilterExpectedBlobCount() {
                return bloomFilterExpectedBlobCount;
            }

            public double getBloomFilterAssociatedProbability() {
                return bloomFilterAssociatedProbability;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot that = (Snapshot) o;

                    return Objects.equals(this.referenceSourceCount, that.referenceSourceCount)
                        && Objects.equals(this.blobCount, that.blobCount)
                        && Objects.equals(this.gcedBlobCount, that.gcedBlobCount)
                        && Objects.equals(this.errorCount, that.errorCount)
                        && Objects.equals(this.bloomFilterExpectedBlobCount, that.bloomFilterExpectedBlobCount)
                        && Objects.equals(this.bloomFilterAssociatedProbability, that.bloomFilterAssociatedProbability);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(referenceSourceCount, blobCount, gcedBlobCount, errorCount, bloomFilterExpectedBlobCount, bloomFilterAssociatedProbability);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("referenceSourceCount", referenceSourceCount)
                    .add("blobCount", blobCount)
                    .add("gcedBlobCount", gcedBlobCount)
                    .add("errorCount", errorCount)
                    .add("bloomFilterExpectedBlobCount", bloomFilterExpectedBlobCount)
                    .add("bloomFilterAssociatedProbability", bloomFilterAssociatedProbability)
                    .toString();
            }
        }

        private final AtomicLong referenceSourceCount;
        private final AtomicLong blobCount;
        private final AtomicLong gcedBlobCount;
        private final AtomicLong errorCount;
        private final Long bloomFilterExpectedBlobCount;
        private final Double bloomFilterAssociatedProbability;

        public Context(long bloomFilterExpectedBlobCount, double bloomFilterAssociatedProbability) {
            this.referenceSourceCount = new AtomicLong();
            this.blobCount = new AtomicLong();
            this.gcedBlobCount = new AtomicLong();
            this.errorCount = new AtomicLong();
            this.bloomFilterExpectedBlobCount = bloomFilterExpectedBlobCount;
            this.bloomFilterAssociatedProbability = bloomFilterAssociatedProbability;
        }

        public void incrementBlobCount() {
            blobCount.incrementAndGet();
        }

        public void incrementReferenceSourceCount() {
            referenceSourceCount.incrementAndGet();
        }

        public void incrementGCedBlobCount(int count) {
            gcedBlobCount.addAndGet(count);
        }

        public void incrementErrorCount() {
            errorCount.incrementAndGet();
        }

        public Snapshot snapshot() {
            return Snapshot.builder()
                .referenceSourceCount(referenceSourceCount.get())
                .blobCount(blobCount.get())
                .gcedBlobCount(gcedBlobCount.get())
                .errorCount(errorCount.get())
                .bloomFilterExpectedBlobCount(bloomFilterExpectedBlobCount)
                .bloomFilterAssociatedProbability(bloomFilterAssociatedProbability)
                .build();
        }
    }

    private final BlobReferenceSource referenceSource;
    private final BlobStoreDAO blobStoreDAO;
    private final BlobId.Factory blobIdFactory;
    private final GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration;
    private final Instant now;

    // Avoids two subsequent run to have the same false positives.
    private final String salt;

    public BloomFilterGCAlgorithm(BlobReferenceSource referenceSource,
                                  BlobStoreDAO blobStoreDAO,
                                  BlobId.Factory generationAwareBlobIdFactory,
                                  GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration,
                                  Clock clock) {
        this.referenceSource = referenceSource;
        this.blobStoreDAO = blobStoreDAO;
        this.blobIdFactory = generationAwareBlobIdFactory;
        this.generationAwareBlobIdConfiguration = generationAwareBlobIdConfiguration;
        this.salt = UUID.randomUUID().toString();
        this.now = clock.instant();
    }

    public Mono<Result> gc(int expectedBlobCount, int deletionWindowSize, double associatedProbability, BucketName bucketName, Context context) {
        return populatedBloomFilter(expectedBlobCount, associatedProbability, context)
            .flatMap(bloomFilter -> gc(bloomFilter, bucketName, context, deletionWindowSize))
            .onErrorResume(error -> {
                LOGGER.error("Error when running blob deduplicate garbage collection", error);
                return Mono.just(Result.PARTIAL);
            });
    }

    private Mono<Result> gc(BloomFilter<CharSequence> bloomFilter, BucketName bucketName, Context context, int deletionWindowSize) {
        return Flux.from(blobStoreDAO.listBlobs(bucketName))
            .doOnNext(blobId -> context.incrementBlobCount())
            .flatMap(blobId -> Mono.fromCallable(() -> blobIdFactory.parse(blobId.asString())))
            .filter(blobId -> {
                if (blobId instanceof GenerationAware generationAware) {
                    return !generationAware.inActiveGeneration(generationAwareBlobIdConfiguration, now);
                }
                return false;
            })
            .filter(blobId -> !bloomFilter.mightContain(salt + blobId.asString()))
            .window(deletionWindowSize)
            .flatMap(blobIdFlux -> handlePagedDeletion(bucketName, context, blobIdFlux), DEFAULT_CONCURRENCY)
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED));
    }

    private Mono<Result> handlePagedDeletion(BucketName bucketName, Context context, Flux<BlobId> blobIdFlux) {
        return blobIdFlux.collectList()
            .flatMap(orphanBlobIds -> Mono.from(blobStoreDAO.delete(bucketName, (Collection) orphanBlobIds))
                .then(Mono.fromCallable(() -> {
                    context.incrementGCedBlobCount(orphanBlobIds.size());
                    return Result.COMPLETED;
                })).onErrorResume(error -> {
                    LOGGER.error("Error when gc orphan blob", error);
                    context.incrementErrorCount();
                    return Mono.just(Result.PARTIAL);
                }));
    }

    private Mono<BloomFilter<CharSequence>> populatedBloomFilter(int expectedBlobCount, double associatedProbability, Context context) {
        return Mono.fromCallable(() -> BloomFilter.create(
                BLOOM_FILTER_FUNNEL,
                expectedBlobCount,
                associatedProbability))
            .flatMap(bloomFilter ->
                Flux.from(referenceSource.listReferencedBlobs())
                    .doOnNext(ref -> context.incrementReferenceSourceCount())
                    .map(ref -> bloomFilter.put(salt + ref.asString()))
                    .then()
                    .thenReturn(bloomFilter));
    }
}
