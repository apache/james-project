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

package org.apache.james.blob.postgres;

import static org.apache.james.blob.postgres.PostgresBlobStorageModule.PostgresBlobStorageTable.BLOB_ID;
import static org.apache.james.blob.postgres.PostgresBlobStorageModule.PostgresBlobStorageTable.BUCKET_NAME;
import static org.apache.james.blob.postgres.PostgresBlobStorageModule.PostgresBlobStorageTable.DATA;
import static org.apache.james.blob.postgres.PostgresBlobStorageModule.PostgresBlobStorageTable.SIZE;
import static org.apache.james.blob.postgres.PostgresBlobStorageModule.PostgresBlobStorageTable.TABLE_NAME;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresBlobStoreDAO implements BlobStoreDAO {
    private final PostgresExecutor postgresExecutor;
    private final BlobId.Factory blobIdFactory;

    @Inject
    public PostgresBlobStoreDAO(PostgresExecutor postgresExecutor, BlobId.Factory blobIdFactory) {
        this.postgresExecutor = postgresExecutor;
        this.blobIdFactory = blobIdFactory;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return Mono.from(readReactive(bucketName, blobId))
            .block();
    }

    @Override
    public Mono<InputStream> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.from(readBytes(bucketName, blobId))
            .map(ByteArrayInputStream::new);
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.select(DATA)
                .from(TABLE_NAME)
                .where(BUCKET_NAME.eq(bucketName.asString()))
                .and(BLOB_ID.eq(blobId.asString()))))
            .map(record -> record.get(DATA))
            .switchIfEmpty(Mono.error(() -> new ObjectNotFoundException("Blob " + blobId + " does not exist in bucket " + bucketName)));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        Preconditions.checkNotNull(data);

        return postgresExecutor.executeVoid(dslContext ->
            Mono.from(dslContext.insertInto(TABLE_NAME, BUCKET_NAME, BLOB_ID, DATA, SIZE)
                .values(bucketName.asString(),
                    blobId.asString(),
                    data,
                    data.length)
                .onConflict(BUCKET_NAME, BLOB_ID)
                .doUpdate()
                .set(DATA, data)
                .set(SIZE, data.length)));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(inputStream);

        return Mono.fromCallable(() -> {
            try {
                return IOUtils.toByteArray(inputStream);
            } catch (IOException e) {
                throw new ObjectStoreIOException("IOException occurred", e);
            }
        }).flatMap(bytes -> save(bucketName, blobId, bytes));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Mono.fromCallable(() -> {
            try {
                return content.read();
            } catch (IOException e) {
                throw new ObjectStoreIOException("IOException occurred", e);
            }
        }).flatMap(bytes -> save(bucketName, blobId, bytes));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()))
            .and(BLOB_ID.eq(blobId.asString()))));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()))
            .and(BLOB_ID.in(blobIds.stream().map(BlobId::asString).collect(ImmutableList.toImmutableList())))));
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()))));
    }

    @Override
    public Flux<BucketName> listBuckets() {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectDistinct(BUCKET_NAME)
                .from(TABLE_NAME)))
            .map(record -> BucketName.of(record.get(BUCKET_NAME)));
    }

    @Override
    public Flux<BlobId> listBlobs(BucketName bucketName) {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.select(BLOB_ID)
                .from(TABLE_NAME)
                .where(BUCKET_NAME.eq(bucketName.asString()))))
            .map(record -> blobIdFactory.from(record.get(BLOB_ID)));
    }
}
