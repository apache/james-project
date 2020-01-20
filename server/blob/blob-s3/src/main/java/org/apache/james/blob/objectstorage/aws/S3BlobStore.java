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

package org.apache.james.blob.objectstorage.aws;

import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.FileBackedOutputStream;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

public class S3BlobStore implements BlobStore {

    private static final boolean LAZY_RESOURCE_CLEANUP = false;
    private static final int FILE_THRESHOLD = 1024 * 100;
    private final S3DumbBlobStore dumbBlobStore;
    private final BlobId.Factory blobIdFactory;
    private final BucketName defaultBucket;

    @Inject
    @VisibleForTesting
    S3BlobStore(S3DumbBlobStore dumbBlobStore, BlobId.Factory blobIdFactory, BucketName defaultBucket) {
        this.dumbBlobStore = dumbBlobStore;
        this.blobIdFactory = blobIdFactory;
        this.defaultBucket = defaultBucket;
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] data, StoragePolicy storagePolicy) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(data);

        BlobId blobId = blobIdFactory.forPayload(data);

        return dumbBlobStore.save(bucketName, blobId, data)
            .then(Mono.just(blobId));
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, InputStream data, StoragePolicy storagePolicy) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(data);
        HashingInputStream hashingInputStream = new HashingInputStream(Hashing.sha256(), data);
        return Mono.using(
            () -> new FileBackedOutputStream(FILE_THRESHOLD),
            fileBackedOutputStream -> saveAndGenerateBlobId(bucketName, hashingInputStream, fileBackedOutputStream),
            fileBackedOutputStream -> {
                try {
                    fileBackedOutputStream.reset();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            LAZY_RESOURCE_CLEANUP);
    }

    private Mono<BlobId> saveAndGenerateBlobId(BucketName bucketName, HashingInputStream hashingInputStream, FileBackedOutputStream fileBackedOutputStream) {
        return Mono
            .fromCallable(() -> {
                IOUtils.copy(hashingInputStream, fileBackedOutputStream);
                return Tuples.of(blobIdFactory.from(hashingInputStream.hash().toString()), fileBackedOutputStream.asByteSource());
            })
            .flatMap(tuple -> dumbBlobStore.save(bucketName, tuple.getT1(), tuple.getT2()).thenReturn(tuple.getT1()));
    }


    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        Preconditions.checkNotNull(bucketName);

        return dumbBlobStore.readBytes(bucketName, blobId);
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) {
        Preconditions.checkNotNull(bucketName);

        return dumbBlobStore.read(bucketName, blobId);
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        Preconditions.checkNotNull(bucketName);

        return dumbBlobStore.deleteBucket(bucketName);
    }

    @Override
    public BucketName getDefaultBucketName() {
        return defaultBucket;
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(blobId);

        return dumbBlobStore.delete(bucketName, blobId);
    }

}
