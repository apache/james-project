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

package org.apache.james.blob.objectstorage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.PreDestroy;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.domain.StorageType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ObjectStorageBlobStore implements BlobStore {
    private static final int BUFFERED_SIZE = 256 * 1024;

    private final BlobId.Factory blobIdFactory;

    private final BucketName defaultBucketName;
    private final org.jclouds.blobstore.BlobStore blobStore;
    private final BlobPutter blobPutter;
    private final PayloadCodec payloadCodec;
    private final ObjectStorageBucketNameResolver bucketNameResolver;

    ObjectStorageBlobStore(BucketName defaultBucketName, BlobId.Factory blobIdFactory,
                           org.jclouds.blobstore.BlobStore blobStore,
                           BlobPutter blobPutter,
                           PayloadCodec payloadCodec, ObjectStorageBucketNameResolver bucketNameResolver) {
        this.blobIdFactory = blobIdFactory;
        this.defaultBucketName = defaultBucketName;
        this.blobStore = blobStore;
        this.blobPutter = blobPutter;
        this.payloadCodec = payloadCodec;
        this.bucketNameResolver = bucketNameResolver;
    }

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory builder(SwiftTempAuthObjectStorage.Configuration testConfig) {
        return SwiftTempAuthObjectStorage.blobStoreBuilder(testConfig);
    }

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory builder(SwiftKeystone2ObjectStorage.Configuration testConfig) {
        return SwiftKeystone2ObjectStorage.blobStoreBuilder(testConfig);
    }

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory builder(SwiftKeystone3ObjectStorage.Configuration testConfig) {
        return SwiftKeystone3ObjectStorage.blobStoreBuilder(testConfig);
    }

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory builder(AwsS3AuthConfiguration testConfig) {
        return AwsS3ObjectStorage.blobStoreBuilder(testConfig);
    }

    @PreDestroy
    public void close() {
        blobStore.getContext().close();
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] data) {
        Preconditions.checkNotNull(data);
        ObjectStorageBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        BlobId blobId = blobIdFactory.forPayload(data);
        Payload payload = payloadCodec.write(data);

        Blob blob = blobStore.blobBuilder(blobId.asString())
            .payload(payload.getPayload())
            .contentLength(payload.getLength().orElse(Long.valueOf(data.length)))
            .build();

        return Mono.fromRunnable(() -> blobPutter.putDirectly(resolvedBucketName, blob))
            .thenReturn(blobId);
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, InputStream data) {
        Preconditions.checkNotNull(data);

        return Mono.defer(() -> savingStrategySelection(bucketName, data));
    }

    private Mono<BlobId> savingStrategySelection(BucketName bucketName, InputStream data) {
        InputStream bufferedData = new BufferedInputStream(data, BUFFERED_SIZE + 1);
        try {
            if (isItABigStream(bufferedData)) {
                return saveBigStream(bucketName, bufferedData);
            } else {
                return save(bucketName, IOUtils.toByteArray(bufferedData));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isItABigStream(InputStream bufferedData) throws IOException {
        bufferedData.mark(0);
        bufferedData.skip(BUFFERED_SIZE);
        boolean isItABigStream = bufferedData.read() != -1;
        bufferedData.reset();
        return isItABigStream;
    }

    private Mono<BlobId> saveBigStream(BucketName bucketName, InputStream data) {
        ObjectStorageBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        BlobId tmpId = blobIdFactory.randomId();
        HashingInputStream hashingInputStream = new HashingInputStream(Hashing.sha256(), data);
        Payload payload = payloadCodec.write(hashingInputStream);
        Blob blob = blobStore.blobBuilder(tmpId.asString())
                            .payload(payload.getPayload())
                            .build();

        Supplier<BlobId> blobIdSupplier = () -> blobIdFactory.from(hashingInputStream.hash().toString());
        return Mono.fromRunnable(() -> blobPutter.putAndComputeId(resolvedBucketName, blob, blobIdSupplier));
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> IOUtils.toByteArray(read(bucketName, blobId)));
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreException {
        ObjectStorageBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);
        Blob blob = blobStore.getBlob(resolvedBucketName.asString(), blobId.asString());

        try {
            if (blob != null) {
                return payloadCodec.read(new Payload(blob.getPayload(), Optional.empty()));
            } else {
                throw new ObjectNotFoundException("fail to load blob with id " + blobId);
            }
        } catch (IOException cause) {
            throw new ObjectStoreException(
                "Failed to readBytes blob " + blobId.asString(),
                cause);
        }
    }

    @Override
    public BucketName getDefaultBucketName() {
        return defaultBucketName;
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        ObjectStorageBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);
        return Mono.<Void>fromRunnable(() -> blobStore.deleteContainer(resolvedBucketName.asString()))
            .subscribeOn(Schedulers.elastic());
    }

    public PayloadCodec getPayloadCodec() {
        return payloadCodec;
    }

    @VisibleForTesting
    Mono<Void> deleteAllBuckets() {
        return Flux.fromIterable(blobStore.list())
            .publishOn(Schedulers.elastic())
            .filter(storageMetadata -> storageMetadata.getType().equals(StorageType.CONTAINER))
            .map(StorageMetadata::getName)
            .doOnNext(blobStore::deleteContainer)
            .then();
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        ObjectStorageBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);
        return Mono.<Void>fromRunnable(() -> blobStore.removeBlob(resolvedBucketName.asString(), blobId.asString()))
            .subscribeOn(Schedulers.elastic());
    }
}
