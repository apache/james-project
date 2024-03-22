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

package org.apache.james.blob.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.blob.cassandra.BlobTables.BucketBlobTable.BUCKET;
import static org.apache.james.blob.cassandra.BlobTables.BucketBlobTable.ID;
import static org.apache.james.blob.cassandra.BlobTables.BucketBlobTable.NUMBER_OF_CHUNK;

import java.nio.ByteBuffer;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.cassandra.BlobTables.BucketBlobParts;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraBucketDAO {
    private final BlobId.Factory blobIdFactory;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement insertPart;
    private final PreparedStatement select;
    private final PreparedStatement selectPart;
    private final PreparedStatement delete;
    private final PreparedStatement deleteParts;
    private final PreparedStatement listAll;
    private final PreparedStatement listBucketContent;
    private final DriverExecutionProfile optimisticConsistencyLevelProfile;

    @Inject
    @VisibleForTesting
    public CassandraBucketDAO(BlobId.Factory blobIdFactory, CqlSession session) {
        this.blobIdFactory = blobIdFactory;
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.optimisticConsistencyLevelProfile = JamesExecutionProfiles.getOptimisticConsistencyLevelProfile(session);

        this.insert = session.prepare(insertInto(BlobTables.BucketBlobTable.TABLE_NAME)
            .value(BUCKET, bindMarker(BUCKET))
            .value(ID, bindMarker(ID))
            .value(NUMBER_OF_CHUNK, bindMarker(NUMBER_OF_CHUNK))
            .build());

        this.insertPart = session.prepare(insertInto(BucketBlobParts.TABLE_NAME)
            .value(BucketBlobParts.BUCKET, bindMarker(BucketBlobParts.BUCKET))
            .value(BucketBlobParts.ID, bindMarker(BucketBlobParts.ID))
            .value(BucketBlobParts.CHUNK_NUMBER, bindMarker(BucketBlobParts.CHUNK_NUMBER))
            .value(BucketBlobParts.DATA, bindMarker(BucketBlobParts.DATA))
            .build());

        this.select = session.prepare(selectFrom(BlobTables.BucketBlobTable.TABLE_NAME)
            .all()
            .whereColumn(BUCKET).isEqualTo(bindMarker(BUCKET))
            .whereColumn(ID).isEqualTo(bindMarker(ID))
            .build());

        this.selectPart = session.prepare(selectFrom(BucketBlobParts.TABLE_NAME)
            .all()
            .whereColumn(BUCKET).isEqualTo(bindMarker(BUCKET))
            .whereColumn(ID).isEqualTo(bindMarker(ID))
            .whereColumn(BucketBlobParts.CHUNK_NUMBER).isEqualTo(bindMarker(BucketBlobParts.CHUNK_NUMBER))
            .build());

        this.listAll = session.prepare(selectFrom(BlobTables.BucketBlobTable.TABLE_NAME)
            .all()
            .build());

        this.listBucketContent = session.prepare(selectFrom(BlobTables.BucketBlobTable.TABLE_NAME)
            .all()
            .whereColumn(BUCKET).isEqualTo(bindMarker(BUCKET))
            .allowFiltering()
            .build());

        this.deleteParts = session.prepare(deleteFrom(BucketBlobParts.TABLE_NAME)
            .whereColumn(BucketBlobParts.BUCKET).isEqualTo(bindMarker(BucketBlobParts.BUCKET))
            .whereColumn(BucketBlobParts.ID).isEqualTo(bindMarker(BucketBlobParts.ID))
            .build());

        this.delete = session.prepare(deleteFrom(BlobTables.BucketBlobTable.TABLE_NAME)
            .whereColumn(BUCKET).isEqualTo(bindMarker(BUCKET))
            .whereColumn(ID).isEqualTo(bindMarker(ID))
            .build());
    }

    Mono<Void> writePart(ByteBuffer data, BucketName bucketName, BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeVoid(
            insertPart.bind()
                .setString(BucketBlobParts.BUCKET, bucketName.asString())
                .setString(BucketBlobParts.ID, blobId.asString())
                .setInt(BucketBlobParts.CHUNK_NUMBER, position)
                .setByteBuffer(BucketBlobParts.DATA, data));
    }

    Mono<Void> saveBlobPartsReferences(BucketName bucketName, BlobId blobId, int numberOfChunk) {
        return cassandraAsyncExecutor.executeVoid(
            insert.bind()
                .setString(BUCKET, bucketName.asString())
                .setString(ID, blobId.asString())
                .setInt(NUMBER_OF_CHUNK, numberOfChunk));
    }

    Mono<Integer> selectRowCount(BucketName bucketName, BlobId blobId) {
        return cassandraAsyncExecutor.executeSingleRow(
                select.bind()
                    .setString(BUCKET, bucketName.asString())
                    .setString(ID, blobId.asString()))
            .map(row -> row.getInt(NUMBER_OF_CHUNK));
    }

    Mono<Integer> selectRowCountClOne(BucketName bucketName, BlobId blobId) {
        return cassandraAsyncExecutor.executeSingleRow(
                select.bind()
                    .setString(BUCKET, bucketName.asString())
                    .setString(ID, blobId.asString())
                    .setExecutionProfile(optimisticConsistencyLevelProfile))
            .map(row -> row.getInt(NUMBER_OF_CHUNK));
    }

    Mono<ByteBuffer> readPart(BucketName bucketName, BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeSingleRow(
                selectPart.bind()
                    .setString(BucketBlobParts.BUCKET, bucketName.asString())
                    .setString(BucketBlobParts.ID, blobId.asString())
                    .setInt(BucketBlobParts.CHUNK_NUMBER, position))
            .map(this::rowToData);
    }

    Mono<ByteBuffer> readPartClOne(BucketName bucketName, BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeSingleRow(
                selectPart.bind()
                    .setString(BucketBlobParts.BUCKET, bucketName.asString())
                    .setString(BucketBlobParts.ID, blobId.asString())
                    .setInt(BucketBlobParts.CHUNK_NUMBER, position)
                    .setExecutionProfile(optimisticConsistencyLevelProfile))
            .map(this::rowToData);
    }

    Mono<Void> deletePosition(BucketName bucketName, BlobId blobId) {
        return cassandraAsyncExecutor.executeVoid(
            delete.bind()
                .setString(BUCKET, bucketName.asString())
                .setString(ID, blobId.asString()));
    }

    Mono<Void> deleteParts(BucketName bucketName, BlobId blobId) {
        return cassandraAsyncExecutor.executeVoid(
            deleteParts.bind()
                .setString(BucketBlobParts.BUCKET, bucketName.asString())
                .setString(BucketBlobParts.ID, blobId.asString()));
    }

    public Flux<Pair<BucketName, BlobId>> listAll() {
        return cassandraAsyncExecutor.executeRows(listAll.bind())
            .map(row -> Pair.of(BucketName.of(row.getString(BUCKET)), blobIdFactory.from(row.getString(ID))));
    }

    public Flux<BlobId> listAll(BucketName bucketName) {
        return cassandraAsyncExecutor.executeRows(listBucketContent.bind()
                .setString(BUCKET, bucketName.asString()))
            .map(row -> blobIdFactory.from(row.getString(ID)));
    }

    private ByteBuffer rowToData(Row row) {
        return row.getByteBuffer(BucketBlobParts.DATA);
    }
}
