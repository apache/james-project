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
import static org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobTable.ID;
import static org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobTable.NUMBER_OF_CHUNK;

import java.nio.ByteBuffer;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobParts;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraDefaultBucketDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement insertPart;
    private final PreparedStatement select;
    private final PreparedStatement selectPart;
    private final PreparedStatement delete;
    private final PreparedStatement deleteParts;
    private final PreparedStatement listBlobs;
    private final BlobId.Factory blobIdFactory;
    private final DriverExecutionProfile optimisticConsistencyLevelProfile;

    @Inject
    @VisibleForTesting
    public CassandraDefaultBucketDAO(CqlSession session, BlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.blobIdFactory = blobIdFactory;

        this.insert = session.prepare(insertInto(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
            .value(ID, bindMarker(ID))
            .value(NUMBER_OF_CHUNK, bindMarker(NUMBER_OF_CHUNK))
            .build());

        this.select = session.prepare(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
            .all()
            .whereColumn(ID).isEqualTo(bindMarker(ID))
            .build());

        this.insertPart = session.prepare(insertInto(DefaultBucketBlobParts.TABLE_NAME)
            .value(DefaultBucketBlobParts.ID, bindMarker(DefaultBucketBlobParts.ID))
            .value(DefaultBucketBlobParts.CHUNK_NUMBER, bindMarker(DefaultBucketBlobParts.CHUNK_NUMBER))
            .value(DefaultBucketBlobParts.DATA, bindMarker(DefaultBucketBlobParts.DATA))
            .build());

        this.selectPart = session.prepare(selectFrom(DefaultBucketBlobParts.TABLE_NAME)
            .all()
            .whereColumn(DefaultBucketBlobParts.ID).isEqualTo(bindMarker(DefaultBucketBlobParts.ID))
            .whereColumn(DefaultBucketBlobParts.CHUNK_NUMBER).isEqualTo(bindMarker(DefaultBucketBlobParts.CHUNK_NUMBER))
            .build());

        this.delete = session.prepare(deleteFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
            .whereColumn(BlobTables.DefaultBucketBlobTable.ID).isEqualTo(bindMarker(BlobTables.DefaultBucketBlobTable.ID))
            .build());

        this.deleteParts = session.prepare(deleteFrom(DefaultBucketBlobParts.TABLE_NAME)
            .whereColumn(DefaultBucketBlobParts.ID).isEqualTo(bindMarker(DefaultBucketBlobParts.ID))
            .build());

        this.listBlobs = session.prepare(selectFrom(DefaultBucketBlobParts.TABLE_NAME)
            .column(DefaultBucketBlobParts.ID)
            .build());

        optimisticConsistencyLevelProfile = JamesExecutionProfiles.getOptimisticConsistencyLevelProfile(session);
    }

    Mono<Void> writePart(ByteBuffer data, BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeVoid(
            insertPart.bind()
                .setString(ID, blobId.asString())
                .setInt(DefaultBucketBlobParts.CHUNK_NUMBER, position)
                .setByteBuffer(DefaultBucketBlobParts.DATA, data));
    }

    Mono<Void> saveBlobPartsReferences(BlobId blobId, int numberOfChunk) {
        return cassandraAsyncExecutor.executeVoid(
            insert.bind()
                .setString(ID, blobId.asString())
                .setInt(NUMBER_OF_CHUNK, numberOfChunk));
    }

    Mono<Integer> selectRowCount(BlobId blobId) {
        return cassandraAsyncExecutor.executeSingleRow(
                select.bind()
                    .setString(ID, blobId.asString()))
            .map(row -> row.getInt(NUMBER_OF_CHUNK));
    }

    Mono<Integer> selectRowCountClOne(BlobId blobId) {
        return cassandraAsyncExecutor.executeSingleRow(
                select.bind()
                    .setString(ID, blobId.asString())
                    .setExecutionProfile(optimisticConsistencyLevelProfile))
            .map(row -> row.getInt(NUMBER_OF_CHUNK));
    }

    Mono<ByteBuffer> readPart(BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeSingleRow(
                selectPart.bind()
                    .setString(DefaultBucketBlobParts.ID, blobId.asString())
                    .setInt(DefaultBucketBlobParts.CHUNK_NUMBER, position))
            .map(this::rowToData);
    }

    Mono<ByteBuffer> readPartClOne(BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeSingleRow(
                selectPart.bind()
                    .setString(DefaultBucketBlobParts.ID, blobId.asString())
                    .setInt(DefaultBucketBlobParts.CHUNK_NUMBER, position)
                    .setExecutionProfile(optimisticConsistencyLevelProfile))
            .map(this::rowToData);
    }

    Mono<Void> deletePosition(BlobId blobId) {
        return cassandraAsyncExecutor.executeVoid(
            delete.bind()
                .setString(ID, blobId.asString()));
    }

    Mono<Void> deleteParts(BlobId blobId) {
        return cassandraAsyncExecutor.executeVoid(
            deleteParts.bind()
                .setString(DefaultBucketBlobParts.ID, blobId.asString()));
    }

    Flux<BlobId> listBlobs() {
        return cassandraAsyncExecutor.executeRows(listBlobs.bind())
            .map(row -> blobIdFactory.from(row.getString(DefaultBucketBlobParts.ID)));
    }

    private ByteBuffer rowToData(Row row) {
        return row.getByteBuffer(DefaultBucketBlobParts.DATA);
    }
}
