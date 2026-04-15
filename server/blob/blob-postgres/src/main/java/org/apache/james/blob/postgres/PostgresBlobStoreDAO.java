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

import static org.apache.james.blob.postgres.PostgresBlobStorageDataDefinition.PostgresBlobStorageTable.BLOB_ID;
import static org.apache.james.blob.postgres.PostgresBlobStorageDataDefinition.PostgresBlobStorageTable.BUCKET_NAME;
import static org.apache.james.blob.postgres.PostgresBlobStorageDataDefinition.PostgresBlobStorageTable.DATA;
import static org.apache.james.blob.postgres.PostgresBlobStorageDataDefinition.PostgresBlobStorageTable.METADATA;
import static org.apache.james.blob.postgres.PostgresBlobStorageDataDefinition.PostgresBlobStorageTable.SIZE;
import static org.apache.james.blob.postgres.PostgresBlobStorageDataDefinition.PostgresBlobStorageTable.TABLE_NAME;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.postgres.utils.PostgresUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.jooq.impl.DSL;
import org.jooq.postgres.extensions.types.Hstore;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return Mono.from(readReactive(bucketName, blobId))
            .block();
    }

    @Override
    public Mono<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.from(readBytes(bucketName, blobId))
            .map(BytesBlob::asInputStream);
    }

    @Override
    public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.select(DATA, METADATA)
                .from(TABLE_NAME)
                .where(BUCKET_NAME.eq(bucketName.asString()))
                .and(BLOB_ID.eq(blobId.asString()))))
            .switchIfEmpty(Mono.error(() -> new ObjectNotFoundException("Blob " + blobId + " does not exist in bucket " + bucketName)))
            .map(record -> BytesBlob.of(record.get(DATA), asBlobMetadata(record.get(METADATA))));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        return switch (blob) {
            case BytesBlob bytesBlob -> save(bucketName, blobId, bytesBlob.payload(), bytesBlob.metadata());
            case InputStreamBlob inputStreamBlob -> save(bucketName, blobId, inputStreamBlob.payload(), inputStreamBlob.metadata());
            case ByteSourceBlob byteSourceBlob -> save(bucketName, blobId, byteSourceBlob.payload(), byteSourceBlob.metadata());
        };
    }

    private Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data, BlobMetadata metadata) {
        Preconditions.checkNotNull(data);

        return postgresExecutor.executeVoid(dslContext ->
            Mono.from(dslContext.insertInto(TABLE_NAME, BUCKET_NAME, BLOB_ID, DATA, SIZE, METADATA)
                .values(bucketName.asString(),
                    blobId.asString(),
                    data,
                    data.length,
                    asHstore(metadata))
                .onConflict(BUCKET_NAME, BLOB_ID)
                .doUpdate()
                .set(DATA, data)
                .set(SIZE, data.length)
                .set(METADATA, asHstore(metadata))));
    }

    private Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream, BlobMetadata metadata) {
        Preconditions.checkNotNull(inputStream);

        return Mono.fromCallable(() -> {
            try {
                return IOUtils.toByteArray(inputStream);
            } catch (IOException e) {
                throw new ObjectStoreIOException("IOException occurred", e);
            }
        }).flatMap(bytes -> save(bucketName, blobId, bytes, metadata));
    }

    private Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content, BlobMetadata metadata) {
        return Mono.fromCallable(() -> {
            try {
                return content.read();
            } catch (IOException e) {
                throw new ObjectStoreIOException("IOException occurred", e);
            }
        }).flatMap(bytes -> save(bucketName, blobId, bytes, metadata));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()))
            .and(BLOB_ID.eq(blobId.asString()))));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        if (blobIds.isEmpty()) {
            return Mono.empty();
        }
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
        return Flux.defer(() -> listBlobsBatch(bucketName, Optional.empty(), PostgresUtils.QUERY_BATCH_SIZE))
            .expand(blobIds -> {
                if (blobIds.isEmpty() || blobIds.size() < PostgresUtils.QUERY_BATCH_SIZE) {
                    return Mono.empty();
                }
                return listBlobsBatch(bucketName, Optional.of(blobIds.getLast()), PostgresUtils.QUERY_BATCH_SIZE);
            })
            .flatMapIterable(Function.identity());
    }

    private Mono<List<BlobId>> listBlobsBatch(BucketName bucketName, Optional<BlobId> blobIdFrom, int batchSize) {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.select(BLOB_ID)
                .from(TABLE_NAME)
                .where(BUCKET_NAME.eq(bucketName.asString()))
                .and(blobIdFrom.map(blobId -> BLOB_ID.greaterThan(blobId.asString())).orElseGet(DSL::noCondition))
                .orderBy(BLOB_ID.asc())
                .limit(batchSize)))
            .map(record -> blobIdFactory.parse(record.get(BLOB_ID)))
            .collectList()
            .switchIfEmpty(Mono.just(ImmutableList.of()));
    }

    private Hstore asHstore(BlobMetadata metadata) {
        return Hstore.hstore(metadata.underlyingMap().entrySet().stream()
            .collect(ImmutableMap.toImmutableMap(
                entry -> entry.getKey().name(),
                entry -> entry.getValue().value())));
    }

    private BlobMetadata asBlobMetadata(Hstore hstore) {
        return new BlobMetadata(Optional.ofNullable(hstore)
            .map(Hstore::data)
            .orElseGet(Map::of)
            .entrySet().stream()
            .collect(ImmutableMap.toImmutableMap(
                entry -> new BlobMetadataName(entry.getKey()),
                entry -> new BlobMetadataValue(entry.getValue()))));
    }
}
