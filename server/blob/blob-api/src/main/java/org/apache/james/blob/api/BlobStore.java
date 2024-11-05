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

    Publisher<BlobId> save(Bucket bucket, byte[] data, StoragePolicy storagePolicy);

    Publisher<BlobId> save(Bucket bucket, InputStream data, StoragePolicy storagePolicy);

    Publisher<BlobId> save(Bucket bucket, ByteSource data, StoragePolicy storagePolicy);

    Publisher<BlobId> save(Bucket bucket, byte[] data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy);

    Publisher<BlobId> save(Bucket bucket, InputStream data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy);

    Publisher<BlobId> save(Bucket bucket, ByteSource data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy);

    default Publisher<BlobId> save(Bucket bucket, String data, StoragePolicy storagePolicy) {
        return save(bucket, data.getBytes(StandardCharsets.UTF_8), storagePolicy);
    }

    Publisher<byte[]> readBytes(Bucket bucket, BlobId blobId);

    InputStream read(Bucket bucket, BlobId blobId);

    Publisher<InputStream> readReactive(Bucket bucket, BlobId blobId);

    default Publisher<byte[]> readBytes(Bucket bucket, BlobId blobId, StoragePolicy storagePolicy) {
       return readBytes(bucket, blobId);
    }

    default InputStream read(Bucket bucket, BlobId blobId, StoragePolicy storagePolicy) {
        return read(bucket, blobId);
    }

    default Publisher<InputStream> readReactive(Bucket bucket, BlobId blobId, StoragePolicy storagePolicy) {
        return readReactive(bucket, blobId);
    }

    BucketName getDefaultBucketName();

    default Bucket getDefaultBucket(Optional<Tenant> tenant) {
        return Bucket.of(getDefaultBucketName());
    }

    Publisher<BucketName> listBuckets();

    default Publisher<Bucket> listBuckets(Optional<Tenant> tenant) {
        return Flux.from(listBuckets())
            .map(bucketName -> new Bucket(bucketName, tenant));
    }

    Publisher<Void> deleteBucket(Bucket bucket);

    Publisher<Boolean> delete(Bucket bucket, BlobId blobId);

    Publisher<BlobId> listBlobs(Bucket bucket);
}
