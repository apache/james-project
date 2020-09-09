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

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

public class MemoryBlobStoreDAO implements BlobStoreDAO {

    private final Table<BucketName, BlobId, byte[]> blobs;

    public MemoryBlobStoreDAO() {
        blobs = HashBasedTable.create();
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return readBytes(bucketName, blobId)
            .map(ByteArrayInputStream::new)
            .block();
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> blobs.get(bucketName, blobId))
            .switchIfEmpty(Mono.error(() -> new ObjectNotFoundException(String.format("blob '%s' not found in bucket '%s'", blobId.asString(), bucketName.asString()))));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        return Mono.fromRunnable(() -> {
            synchronized (blobs) {
                blobs.put(bucketName, blobId, data);
            }
        });
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        return Mono.fromCallable(() -> {
                try {
                    return IOUtils.toByteArray(inputStream);
                } catch (IOException e) {
                    throw new ObjectStoreIOException("IOException occured", e);
                }
            })
            .flatMap(bytes -> save(bucketName, blobId, bytes));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Mono.fromCallable(() -> {
                try {
                    return content.read();
                } catch (IOException e) {
                    throw new ObjectStoreIOException("IOException occured", e);
                }
            })
            .flatMap(bytes -> save(bucketName, blobId, bytes));
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
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Mono.fromRunnable(() -> {
            synchronized (blobs) {
                blobs.row(bucketName).clear();
            }
        });
    }
}
