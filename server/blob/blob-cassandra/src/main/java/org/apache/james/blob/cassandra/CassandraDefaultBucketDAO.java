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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobTable.ID;
import static org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobTable.NUMBER_OF_CHUNK;

import java.nio.ByteBuffer;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobParts;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class CassandraDefaultBucketDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement insertPart;
    private final PreparedStatement select;
    private final PreparedStatement selectPart;
    private final PreparedStatement delete;
    private final PreparedStatement deleteParts;

    @Inject
    @VisibleForTesting
    public CassandraDefaultBucketDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);
        this.insertPart = prepareInsertPart(session);
        this.selectPart = prepareSelectPart(session);
        this.delete = prepareDelete(session);
        this.deleteParts = prepareDeleteParts(session);
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
            .from(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
            .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareSelectPart(Session session) {
        return session.prepare(select()
            .from(DefaultBucketBlobParts.TABLE_NAME)
            .where(eq(DefaultBucketBlobParts.ID, bindMarker(DefaultBucketBlobParts.ID)))
            .and(eq(DefaultBucketBlobParts.CHUNK_NUMBER, bindMarker(DefaultBucketBlobParts.CHUNK_NUMBER))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
            .value(ID, bindMarker(ID))
            .value(NUMBER_OF_CHUNK, bindMarker(NUMBER_OF_CHUNK)));
    }

    private PreparedStatement prepareInsertPart(Session session) {
        return session.prepare(insertInto(DefaultBucketBlobParts.TABLE_NAME)
            .value(DefaultBucketBlobParts.ID, bindMarker(DefaultBucketBlobParts.ID))
            .value(DefaultBucketBlobParts.CHUNK_NUMBER, bindMarker(DefaultBucketBlobParts.CHUNK_NUMBER))
            .value(DefaultBucketBlobParts.DATA, bindMarker(DefaultBucketBlobParts.DATA)));
    }

    private PreparedStatement prepareDeleteParts(Session session) {
        return session.prepare(
            delete().from(DefaultBucketBlobParts.TABLE_NAME)
                .where(eq(DefaultBucketBlobParts.ID, bindMarker(DefaultBucketBlobParts.ID))));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(
            delete().from(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
                .where(eq(BlobTables.DefaultBucketBlobTable.ID, bindMarker(BlobTables.DefaultBucketBlobTable.ID))));
    }

    Mono<Void> writePart(ByteBuffer data, BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeVoid(
            insertPart.bind()
                .setString(ID, blobId.asString())
                .setInt(DefaultBucketBlobParts.CHUNK_NUMBER, position)
                .setBytes(DefaultBucketBlobParts.DATA, data));
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

    Mono<ByteBuffer> readPart(BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectPart.bind()
                .setString(DefaultBucketBlobParts.ID, blobId.asString())
                .setInt(DefaultBucketBlobParts.CHUNK_NUMBER, position))
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

    private ByteBuffer rowToData(Row row) {
        return row.getBytes(DefaultBucketBlobParts.DATA);
    }
}
