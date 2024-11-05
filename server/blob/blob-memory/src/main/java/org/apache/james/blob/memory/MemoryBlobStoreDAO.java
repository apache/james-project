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
import java.util.Collection;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.Bucket;
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

    private final Table<BucketName, BlobId, byte[]> blobs;

    public MemoryBlobStoreDAO() {
        blobs = HashBasedTable.create();
    }

    @Override
    public InputStream read(Bucket bucket, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return readBytes(bucket, blobId)
            .map(ByteArrayInputStream::new)
            .block();
    }

    @Override
    public Publisher<InputStream> readReactive(Bucket bucket, BlobId blobId) {
        return readBytes(bucket, blobId)
            .map(ByteArrayInputStream::new);
    }

    @Override
    public Mono<byte[]> readBytes(Bucket bucket, BlobId blobId) {
        return Mono.fromCallable(() -> blobs.get(bucket.bucketName(), blobId))
            .switchIfEmpty(Mono.error(() -> new ObjectNotFoundException(String.format("blob '%s' not found in bucket '%s'", blobId.asString(), bucket.bucketName().asString()))));
    }

    @Override
    public Mono<Void> save(Bucket bucket, BlobId blobId, byte[] data) {
        return Mono.fromRunnable(() -> {
            synchronized (blobs) {
                blobs.put(bucket.bucketName(), blobId, data);
            }
        });
    }

    @Override
    public Mono<Void> save(Bucket bucket, BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(inputStream);
        return Mono.fromCallable(() -> {
                try {
                    return IOUtils.toByteArray(inputStream);
                } catch (IOException e) {
                    throw new ObjectStoreIOException("IOException occured", e);
                }
            })
            .flatMap(bytes -> save(bucket, blobId, bytes));
    }

    @Override
    public Mono<Void> save(Bucket bucket, BlobId blobId, ByteSource content) {
        return Mono.fromCallable(() -> {
                try {
                    return content.read();
                } catch (IOException e) {
                    throw new ObjectStoreIOException("IOException occured", e);
                }
            })
            .flatMap(bytes -> save(bucket, blobId, bytes));
    }

    @Override
    public Mono<Void> delete(Bucket bucket, BlobId blobId) {
        Preconditions.checkNotNull(bucket.bucketName());
        return Mono.fromRunnable(() -> {
            synchronized (blobs) {
                blobs.remove(bucket.bucketName(), blobId);
            }
        });
    }

    @Override
    public Publisher<Void> delete(Bucket bucket, Collection<BlobId> blobIds) {
        return Flux.fromIterable(blobIds)
            .flatMap(id -> delete(bucket, id))
            .then();
    }

    @Override
    public Mono<Void> deleteBucket(Bucket bucket) {
        return Mono.fromRunnable(() -> {
            synchronized (blobs) {
                blobs.row(bucket.bucketName()).clear();
            }
        });
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return Flux.fromIterable(ImmutableSet.copyOf(blobs.rowKeySet()));
    }

    @Override
    public Publisher<BlobId> listBlobs(Bucket bucket) {
        return Flux.fromIterable(ImmutableSet.copyOf(blobs.row(bucket.bucketName()).keySet()));
    }
}
