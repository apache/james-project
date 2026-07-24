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

import org.reactivestreams.Publisher;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

/**
 * High-level James blob storage abstraction.
 *
 * <p>A {@link BlobStore} stores binary payloads in a James logical {@link BucketName}
 * and returns {@link BlobId} references. A configured {@link BlobStore} exposes a default
 * logical bucket through {@link #getDefaultBucketName()}.</p>
 *
 * <p>A {@link BucketName} is a James-specific logical bucket. Each storage connector decides how this logical
 * bucket is represented in its backend. It should not be conflated with an S3 bucket name and does not have to map one-to-one
 * to a physical bucket.</p>
 *
 * <p>See {@code docs/modules/servers/partials/architecture/blobstore.adoc} for more details.</p>
 */
public interface BlobStore {
    String DEFAULT_BUCKET_NAME_QUALIFIER = "defaultBucket";

    enum StoragePolicy {
        SIZE_BASED,
        LOW_COST,
        HIGH_PERFORMANCE
    }

    @FunctionalInterface
    interface BlobIdProvider<T> {
        Publisher<Tuple2<BlobId, T>> apply(T stream);
    }

    Publisher<BlobId> save(BucketName bucketName, byte[] data, StoragePolicy storagePolicy);

    Publisher<BlobId> save(BucketName bucketName, InputStream data, StoragePolicy storagePolicy);

    Publisher<BlobId> save(BucketName bucketName, ByteSource data, StoragePolicy storagePolicy);

    Publisher<BlobId> save(BucketName bucketName, byte[] data, BlobIdProvider<byte[]> blobIdProvider, StoragePolicy storagePolicy);

    Publisher<BlobId> save(BucketName bucketName, InputStream data, BlobIdProvider<InputStream> blobIdProvider, StoragePolicy storagePolicy);

    Publisher<BlobId> save(BucketName bucketName, ByteSource data, BlobIdProvider<ByteSource> blobIdProvider, StoragePolicy storagePolicy);

    default Publisher<BlobId> save(BucketName bucketName, String data, StoragePolicy storagePolicy) {
        return save(bucketName, data.getBytes(StandardCharsets.UTF_8), storagePolicy);
    }

    Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId);

    InputStream read(BucketName bucketName, BlobId blobId);

    Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId);

    default Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
       return readBytes(bucketName, blobId);
    }

    default InputStream read(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
        return read(bucketName, blobId);
    }

    default Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
        return readReactive(bucketName, blobId);
    }

    BucketName getDefaultBucketName();

    Publisher<BucketName> listBuckets();

    Publisher<Void> deleteBucket(BucketName bucketName);

    Publisher<Boolean> delete(BucketName bucketName, BlobId blobId);

    Publisher<BlobId> listBlobs(BucketName bucketName);

    /**
     * Lists the blobs of a bucket whose id starts with the given prefix.
     *
     * <p>The default implementation filters the full listing. Implementations backed by a store able to
     * push the prefix down (eg. S3 {@code ListObjectsV2}) should override this for efficiency.</p>
     */
    default Publisher<BlobId> listBlobs(BucketName bucketName, String prefix) {
        return Flux.from(listBlobs(bucketName))
            .filter(blobId -> blobId.asString().startsWith(prefix));
    }
}
