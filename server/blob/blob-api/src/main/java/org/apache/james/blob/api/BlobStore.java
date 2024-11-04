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
package org.apache.james.blob.api;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.reactivestreams.Publisher;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

public interface BlobStore {
    String DEFAULT_BUCKET_NAME_QUALIFIER = "defaultBucket";

    enum StoragePolicy {
        SIZE_BASED,
        LOW_COST,
        HIGH_PERFORMANCE
    }

    @FunctionalInterface
    interface BlobIdProvider {
        Publisher<Tuple2<BlobId, InputStream>> apply(InputStream stream);
    }

    Publisher<BlobId> save(BucketName bucketName, byte[] data, StoragePolicy storagePolicy);

    default Publisher<BlobId> save(Bucket bucket, byte[] data, StoragePolicy storagePolicy) {
        return save(bucket.bucketName(), data, storagePolicy);
    }

    Publisher<BlobId> save(BucketName bucketName, InputStream data, StoragePolicy storagePolicy);

    default Publisher<BlobId> save(Bucket bucket, InputStream data, StoragePolicy storagePolicy) {
        return save(bucket.bucketName(), data, storagePolicy);
    }

    Publisher<BlobId> save(BucketName bucketName, ByteSource data, StoragePolicy storagePolicy);

    default Publisher<BlobId> save(Bucket bucket, ByteSource data, StoragePolicy storagePolicy) {
        return save(bucket.bucketName(), data, storagePolicy);
    }

    Publisher<BlobId> save(BucketName bucketName, byte[] data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy);

    default Publisher<BlobId> save(Bucket bucket, byte[] data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy) {
        return save(bucket.bucketName(), data, blobIdProvider, storagePolicy);
    }

    Publisher<BlobId> save(BucketName bucketName, InputStream data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy);

    default Publisher<BlobId> save(Bucket bucket, InputStream data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy) {
        return save(bucket.bucketName(), data, blobIdProvider, storagePolicy);
    }

    Publisher<BlobId> save(BucketName bucketName, ByteSource data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy);

    default Publisher<BlobId> save(Bucket bucket, ByteSource data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy) {
        return save(bucket.bucketName(), data, blobIdProvider, storagePolicy);
    }

    default Publisher<BlobId> save(BucketName bucketName, String data, StoragePolicy storagePolicy) {
        return save(bucketName, data.getBytes(StandardCharsets.UTF_8), storagePolicy);
    }

    default Publisher<BlobId> save(Bucket bucket, String data, StoragePolicy storagePolicy) {
        return save(bucket.bucketName(), data.getBytes(StandardCharsets.UTF_8), storagePolicy);
    }

    Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId);

    default Publisher<byte[]> readBytes(Bucket bucket, BlobId blobId) {
        return readBytes(bucket.bucketName(), blobId);
    }

    InputStream read(BucketName bucketName, BlobId blobId);

    default InputStream read(Bucket bucket, BlobId blobId) {
        return read(bucket.bucketName(), blobId);
    }

    Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId);

    default Publisher<InputStream> readReactive(Bucket bucket, BlobId blobId) {
        return readReactive(bucket.bucketName(), blobId);
    }

    default Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
       return readBytes(bucketName, blobId);
    }

    default Publisher<byte[]> readBytes(Bucket bucket, BlobId blobId, StoragePolicy storagePolicy) {
        return readBytes(bucket.bucketName(), blobId);
    }

    default InputStream read(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
        return read(bucketName, blobId);
    }

    default InputStream read(Bucket bucket, BlobId blobId, StoragePolicy storagePolicy) {
        return read(bucket.bucketName(), blobId);
    }

    default Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
        return readReactive(bucketName, blobId);
    }

    default Publisher<InputStream> readReactive(Bucket bucket, BlobId blobId, StoragePolicy storagePolicy) {
        return readReactive(bucket.bucketName(), blobId);
    }

    BucketName getDefaultBucketName();

    default Bucket getDefaultBucket(Optional<Tenant> tenant) {
        return new Bucket(getDefaultBucketName(), tenant);
    }

    Publisher<BucketName> listBuckets();

    default Publisher<Bucket> listBuckets(Optional<Tenant> tenant) {
        return Flux.from(listBuckets())
            .map(bucketName -> new Bucket(bucketName, tenant));
    }

    Publisher<Void> deleteBucket(BucketName bucketName);

    default Publisher<Void> deleteBucket(Bucket bucket) {
        return deleteBucket(bucket.bucketName());
    }

    Publisher<Boolean> delete(BucketName bucketName, BlobId blobId);

    default Publisher<Boolean> delete(Bucket bucket, BlobId blobId) {
        return delete(bucket.bucketName(), blobId);
    }

    Publisher<BlobId> listBlobs(BucketName bucketName);

    default Publisher<BlobId> listBlobs(Bucket bucket) {
        return listBlobs(bucket.bucketName());
    }
}
