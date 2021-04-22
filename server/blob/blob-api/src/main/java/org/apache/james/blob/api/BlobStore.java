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

public interface BlobStore {
    String DEFAULT_BUCKET_NAME_QUALIFIER = "defaultBucket";

    enum StoragePolicy {
        SIZE_BASED,
        LOW_COST,
        HIGH_PERFORMANCE
    }

    Publisher<BlobId> save(BucketName bucketName, byte[] data, StoragePolicy storagePolicy);

    Publisher<BlobId> save(BucketName bucketName, InputStream data, StoragePolicy storagePolicy);

    Publisher<BlobId> save(BucketName bucketName, ByteSource data, StoragePolicy storagePolicy);

    default Publisher<BlobId> save(BucketName bucketName, String data, StoragePolicy storagePolicy) {
        return save(bucketName, data.getBytes(StandardCharsets.UTF_8), storagePolicy);
    }

    Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId);

    InputStream read(BucketName bucketName, BlobId blobId);

    default Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
       return readBytes(bucketName, blobId);
    }

    default InputStream read(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
        return read(bucketName, blobId);
    }

    BucketName getDefaultBucketName();

    Publisher<Void> deleteBucket(BucketName bucketName);

    Publisher<Boolean> delete(BucketName bucketName, BlobId blobId);
}
