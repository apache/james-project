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

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class CacheBlobStore implements BlobStore {

    private static final String DEFAULT_BUCKET = "cassandraDefault";

    private final BlobStoreCache cache;
    private final BlobStore backend;
    private final Integer sizeThresholdInBytes;
    private final BucketName defaultBucket;

    @Inject
    public CacheBlobStore(BlobStoreCache cache, BlobStore backend,
                          CassandraCacheConfiguration cacheConfiguration,
                          @Named(DEFAULT_BUCKET) BucketName defaultBucket) {
        this.cache = cache;
        this.backend = backend;
        this.sizeThresholdInBytes = cacheConfiguration.getSizeThresholdInBytes();
        this.defaultBucket = defaultBucket;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        Preconditions.checkNotNull(bucketName, "bucketName should not be null");

        return Mono.just(bucketName)
            .filter(defaultBucket::equals)
            .flatMap(ignored ->
                Mono.from(cache.read(blobId))
                    .<InputStream>flatMap(bytes -> Mono.fromCallable(() -> new ByteArrayInputStream(bytes))))
            .switchIfEmpty(Mono.fromCallable(() -> backend.read(bucketName, blobId)))
            .blockOptional()
            .orElseThrow(() -> new ObjectNotFoundException(String.format("Could not retrieve blob metadata for %s", blobId)));
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.just(bucketName)
            .filter(defaultBucket::equals)
            .flatMap(ignored -> Mono.from(cache.read(blobId)))
            .switchIfEmpty(Mono.from(backend.readBytes(bucketName, blobId)));
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] bytes, StoragePolicy storagePolicy) {
        return Mono.from(backend.save(bucketName, bytes, storagePolicy))
            .flatMap(blobId -> {
                if (isAbleToCache(bucketName, bytes, storagePolicy)) {
                    return Mono.from(cache.cache(blobId, bytes)).thenReturn(blobId);
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

    private Publisher<BlobId> saveInCache(BucketName bucketName, InputStream inputStream, StoragePolicy storagePolicy) {
        PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, sizeThresholdInBytes + 1);

        return Mono.fromCallable(() -> fullyReadSmallStream(pushbackInputStream))
            .flatMap(Mono::justOrEmpty)
            .filter(bytes -> isAbleToCache(bucketName, bytes, storagePolicy))
            .flatMap(bytes -> Mono.from(backend.save(bucketName, pushbackInputStream, storagePolicy))
                .flatMap(blobId -> Mono.from(cache.cache(blobId, bytes))
                    .thenReturn(blobId)))
            .switchIfEmpty(Mono.from(backend.save(bucketName, pushbackInputStream, storagePolicy)));
    }

    @Override
    public BucketName getDefaultBucketName() {
        return defaultBucket;
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.from(backend.delete(bucketName, blobId))
            .then(Mono.just(bucketName)
                .filter(defaultBucket::equals)
                .flatMap(ignored -> Mono.from(cache.remove(blobId)))
                .then());
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return Mono.from(backend.deleteBucket(bucketName));
    }

    private Optional<byte[]> fullyReadSmallStream(PushbackInputStream pushbackInputStream) throws IOException {
        int sizeToRead = sizeThresholdInBytes + 1;
        byte[] bytes = new byte[sizeToRead];
        int readByteCount = pushbackInputStream.read(bytes, 0, sizeToRead);
        try {
            if (readByteCount > sizeThresholdInBytes) {
                return Optional.empty();
            }
            if (readByteCount < 0) {
                return Optional.of(new byte[] {});
            }
            return Optional.of(Arrays.copyOf(bytes, readByteCount));
        } finally {
            if (readByteCount > 0) {
                pushbackInputStream.unread(bytes, 0, readByteCount);
            }
        }
    }

    /**
     * bytes: byte[] from PushbackInputStream.If PushbackInputStream is empty bytes.length == 1
     */
    private boolean isAbleToCache(BucketName bucketName, byte[] bytes, StoragePolicy storagePolicy) {
        return isAbleToCache(bucketName, storagePolicy) && bytes.length <= sizeThresholdInBytes;
    }

    private boolean isAbleToCache(BucketName bucketName, StoragePolicy storagePolicy) {
        return defaultBucket.equals(bucketName) && !storagePolicy.equals(LOW_COST);
    }
}
