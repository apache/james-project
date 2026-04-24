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

import java.io.IOException;
import java.util.Collection;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryBlobStoreDAO implements BlobStoreDAO {

    private final Table<BucketName, BlobId, BytesBlob> blobs;

    public MemoryBlobStoreDAO() {
        blobs = HashBasedTable.create();
    }

    @Override
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return Mono.from(readBytes(bucketName, blobId))
            .map(BytesBlob::asInputStream)
            .block();
    }

    @Override
    public Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.from(readBytes(bucketName, blobId))
            .map(BytesBlob::asInputStream);
    }

    @Override
    public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> blobs.get(bucketName, blobId))
            .switchIfEmpty(Mono.error(() -> new ObjectNotFoundException(String.format("blob '%s' not found in bucket '%s'", blobId.asString(), bucketName.asString()))));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        Preconditions.checkNotNull(blob);
        return switch (blob) {
            case BytesBlob bytesBlob -> save(bucketName, blobId, bytesBlob);
            case InputStreamBlob inputStreamBlob -> Mono.fromCallable(() -> {
                    try {
                        return inputStreamBlob.asBytes();
                    } catch (IOException e) {
                        throw new ObjectStoreIOException("IOException occurred", e);
                    }
                })
                .flatMap(bytes -> save(bucketName, blobId, bytes));
            case ByteSourceBlob byteSourceBlob -> Mono.fromCallable(() -> {
                    try {
                        return byteSourceBlob.asBytes();
                    } catch (IOException e) {
                        throw new ObjectStoreIOException("IOException occurred", e);
                    }
                })
            .map(bytes -> checkContentSize(byteSourceBlob.payload(), bytes))
                .flatMap(bytes -> save(bucketName, blobId, bytes));
        };
    }

    public Mono<Void> save(BucketName bucketName, BlobId blobId, BytesBlob data) {
        return Mono.fromRunnable(() -> {
            synchronized (blobs) {
                blobs.put(bucketName, blobId, data);
            }
        });
    }

    private static BytesBlob checkContentSize(ByteSource content, BytesBlob bytes) {
        try {
            long preComputedSize = content.size();
            long realSize = bytes.payload().length;
            Preconditions.checkArgument(content.size() == realSize,
                "Difference in size between the pre-computed content can cause other blob stores to fail thus we need to test for alignment. Expecting " + realSize + " but pre-computed size was " + preComputedSize);
            return bytes;
        } catch (IOException e) {
            throw new ObjectStoreIOException("IOException occurred", e);
        }
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        Preconditions.checkNotNull(bucketName);
        return Mono.fromRunnable(() -> {
            synchronized (blobs) {
                blobs.remove(bucketName, blobId);
            }
        });
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return Flux.fromIterable(blobIds)
            .flatMap(id -> delete(bucketName, id))
            .then();
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Mono.fromRunnable(() -> {
            synchronized (blobs) {
                blobs.row(bucketName).clear();
            }
        });
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return Flux.fromIterable(ImmutableSet.copyOf(blobs.rowKeySet()));
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return Flux.fromIterable(ImmutableSet.copyOf(blobs.row(bucketName).keySet()));
    }
}
