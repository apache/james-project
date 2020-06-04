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
package org.apache.james.blob.cassandra.cache;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class CachedBlobStore implements BlobStore {

    private static class ReadAheadInputStream {

        @FunctionalInterface
        interface RequireStream {
            RequireLength of(InputStream in);
        }

        interface RequireLength {
            ReadAheadInputStream length(int length) throws IOException;
        }


        static RequireStream eager() {
            return in -> length -> {
                //+1 is to evaluate hasMore
                var stream = new PushbackInputStream(in, length + 1);
                var bytes = new byte[length];
                int readByteCount = IOUtils.read(stream, bytes);
                Optional<byte[]> firstBytes;
                boolean hasMore;
                if (readByteCount < 0) {
                    firstBytes = Optional.empty();
                    hasMore = false;
                } else {
                    byte[] readBytes = Arrays.copyOf(bytes, readByteCount);
                    hasMore = hasMore(stream);
                    stream.unread(readBytes);
                    firstBytes = Optional.of(readBytes);
                }
                return new ReadAheadInputStream(stream, firstBytes, hasMore);
            };
        }

        private static boolean hasMore(PushbackInputStream stream) throws IOException {
            int nextByte = stream.read();
            if (nextByte >= 0) {
                stream.unread(nextByte);
                return true;
            } else {
                return false;
            }
        }

        final PushbackInputStream in;
        final Optional<byte[]> firstBytes;
        final boolean hasMore;

        private ReadAheadInputStream(PushbackInputStream in, Optional<byte[]> firstBytes, boolean hasMore) {
            this.in = in;
            this.firstBytes = firstBytes;
            this.hasMore = hasMore;
        }

    }

    public static final String BACKEND = "blobStoreBackend";

    public static final String BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME = "blobStoreCacheMisses";
    public static final String BLOBSTORE_CACHED_LATENCY_METRIC_NAME = "blobStoreCacheLatency";
    public static final String BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME = "blobStoreCacheHits";

    private final MetricFactory metricFactory;
    private final Metric metricRetrieveHitCount;
    private final Metric metricRetrieveMissCount;

    private final BlobStoreCache cache;
    private final BlobStore backend;
    private final Integer sizeThresholdInBytes;

    @Inject
    public CachedBlobStore(BlobStoreCache cache,
                           @Named(BACKEND) BlobStore backend,
                           CassandraCacheConfiguration cacheConfiguration,
                           MetricFactory metricFactory) {
        this.cache = cache;
        this.backend = backend;
        this.sizeThresholdInBytes = cacheConfiguration.getSizeThresholdInBytes();

        this.metricFactory = metricFactory;
        this.metricRetrieveMissCount = metricFactory.generate(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME);
        this.metricRetrieveHitCount = metricFactory.generate(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME);
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return Mono.just(bucketName)
            .filter(getDefaultBucketName()::equals)
            .flatMap(defaultBucket -> readInDefaultBucket(bucketName, blobId))
            .switchIfEmpty(readFromBackend(bucketName, blobId))
            .blockOptional()
            .orElseThrow(() -> new ObjectNotFoundException(String.format("Could not retrieve blob metadata for %s", blobId.asString())));
    }

    private Mono<InputStream> readInDefaultBucket(BucketName bucketName, BlobId blobId) {
        return readFromCache(blobId)
            .flatMap(this::toInputStream)
            .switchIfEmpty(readFromBackend(bucketName, blobId)
                .flatMap(inputStream ->
                    Mono.fromCallable(() -> ReadAheadInputStream.eager().of(inputStream).length(sizeThresholdInBytes))
                        .flatMap(readAheadInputStream -> putInCacheIfNeeded(bucketName, readAheadInputStream, blobId)
                            .thenReturn(readAheadInputStream.in))));
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.just(bucketName)
            .filter(getDefaultBucketName()::equals)
            .flatMap(deleteBucket -> readBytesInDefaultBucket(bucketName, blobId))
            .switchIfEmpty(readBytesFromBackend(bucketName, blobId));
    }

    private Mono<byte[]> readBytesInDefaultBucket(BucketName bucketName, BlobId blobId) {
        return readFromCache(blobId)
            .switchIfEmpty(readBytesFromBackend(bucketName, blobId)
                .filter(this::isAbleToCache)
                .doOnNext(any -> metricRetrieveMissCount.increment())
                .flatMap(bytes -> saveInCache(blobId, bytes).then(Mono.just(bytes))));
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] bytes, StoragePolicy storagePolicy) {
        return Mono.from(backend.save(bucketName, bytes, storagePolicy))
            .flatMap(blobId -> {
                if (isAbleToCache(bucketName, bytes, storagePolicy)) {
                    return saveInCache(blobId, bytes).thenReturn(blobId);
                }
                return Mono.just(blobId);
            });
    }

    @Override
    public Publisher<BlobId> save(BucketName bucketName, InputStream inputStream, StoragePolicy storagePolicy) {
        Preconditions.checkNotNull(inputStream, "InputStream must not be null");

        if (isAbleToCache(bucketName, storagePolicy)) {
            return saveInCache(bucketName, inputStream, storagePolicy);
        }

        return backend.save(bucketName, inputStream, storagePolicy);
    }

    @Override
    public BucketName getDefaultBucketName() {
        return backend.getDefaultBucketName();
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.from(backend.delete(bucketName, blobId))
            .then(Mono.just(bucketName)
                .filter(backend.getDefaultBucketName()::equals)
                .flatMap(ignored -> Mono.from(cache.remove(blobId)))
                .then());
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return Mono.from(backend.deleteBucket(bucketName));
    }

    private Mono<BlobId> saveInCache(BucketName bucketName, InputStream inputStream, StoragePolicy storagePolicy) {
        return Mono.fromCallable(() -> ReadAheadInputStream.eager().of(inputStream).length(sizeThresholdInBytes))
            .flatMap(readAhead -> saveToBackend(bucketName, storagePolicy, readAhead)
                .flatMap(blobId -> putInCacheIfNeeded(bucketName, storagePolicy, readAhead, blobId)
                    .thenReturn(blobId)));
    }

    private Mono<BlobId> saveToBackend(BucketName bucketName, StoragePolicy storagePolicy, ReadAheadInputStream readAhead) {
        return Mono.from(backend.save(bucketName, readAhead.in, storagePolicy));
    }

    private Mono<Void> putInCacheIfNeeded(BucketName bucketName, StoragePolicy storagePolicy, ReadAheadInputStream readAhead, BlobId blobId) {
        return Mono.justOrEmpty(readAhead.firstBytes)
            .filter(bytes -> isAbleToCache(bucketName, readAhead, storagePolicy))
            .flatMap(bytes -> Mono.from(cache.cache(blobId, bytes)));
    }

    private Mono<Void> putInCacheIfNeeded(BucketName bucketName, ReadAheadInputStream readAhead, BlobId blobId) {
        return Mono.justOrEmpty(readAhead.firstBytes)
            .filter(bytes -> isAbleToCache(readAhead, bucketName))
            .doOnNext(any -> metricRetrieveMissCount.increment())
            .flatMap(bytes -> Mono.from(cache.cache(blobId, bytes)));
    }

    private Mono<Void> saveInCache(BlobId blobId, byte[] bytes) {
        return Mono.from(cache.cache(blobId, bytes));
    }

    private boolean isAbleToCache(BucketName bucketName, byte[] bytes, StoragePolicy storagePolicy) {
        return isAbleToCache(bucketName, storagePolicy) && isAbleToCache(bytes);
    }

    private boolean isAbleToCache(BucketName bucketName, ReadAheadInputStream readAhead, StoragePolicy storagePolicy) {
        return isAbleToCache(bucketName, storagePolicy) && !readAhead.hasMore;
    }

    private boolean isAbleToCache(BucketName bucketName, StoragePolicy storagePolicy) {
        return backend.getDefaultBucketName().equals(bucketName) && !storagePolicy.equals(LOW_COST);
    }

    private boolean isAbleToCache(ReadAheadInputStream readAhead, BucketName bucketName) {
        return !readAhead.hasMore && backend.getDefaultBucketName().equals(bucketName);
    }

    private boolean isAbleToCache(byte[] bytes) {
        return bytes.length <= sizeThresholdInBytes;
    }

    private Mono<InputStream> toInputStream(byte[] bytes) {
        return Mono.fromCallable(() -> new ByteArrayInputStream(bytes));
    }

    private Mono<byte[]> readFromCache(BlobId blobId) {
        return Mono.from(metricFactory.runPublishingTimerMetric(BLOBSTORE_CACHED_LATENCY_METRIC_NAME, cache.read(blobId)))
            .doOnNext(any -> metricRetrieveHitCount.increment());
    }

    private Mono<InputStream> readFromBackend(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> backend.read(bucketName, blobId));
    }

    private Mono<byte[]> readBytesFromBackend(BucketName bucketName, BlobId blobId) {
        return Mono.from(backend.readBytes(bucketName, blobId));
    }
}
