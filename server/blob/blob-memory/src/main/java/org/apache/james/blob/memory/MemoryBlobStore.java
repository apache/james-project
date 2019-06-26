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

package org.apache.james.blob.memory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectStoreException;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class MemoryBlobStore implements BlobStore {
    private final ConcurrentHashMap<BlobId, byte[]> blobs;
    private final BlobId.Factory factory;

    @Inject
    public MemoryBlobStore(BlobId.Factory factory) {
        this.factory = factory;
        blobs = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] data) {
        Preconditions.checkNotNull(data);
        BlobId blobId = factory.forPayload(data);

        blobs.put(blobId, data);

        return Mono.just(blobId);
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, InputStream data) {
        Preconditions.checkNotNull(data);
        try {
            byte[] bytes = IOUtils.toByteArray(data);
            return save(bucketName, bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> retrieveStoredValue(blobId));
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) {
        return new ByteArrayInputStream(retrieveStoredValue(blobId));
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        throw new NotImplementedException("not implemented");
    }

    private byte[] retrieveStoredValue(BlobId blobId) {
        return Optional.ofNullable(blobs.get(blobId))
            .orElseThrow(() -> new ObjectStoreException("unable to find blob with id " + blobId));
    }
}
