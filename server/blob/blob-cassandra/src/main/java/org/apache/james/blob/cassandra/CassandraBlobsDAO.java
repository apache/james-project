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
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.ObjectStore;
import org.apache.james.blob.cassandra.BlobTable.BlobParts;
import org.apache.james.blob.cassandra.utils.DataChunker;
import org.apache.james.util.FluentFutureStream;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

public class CassandraBlobsDAO implements ObjectStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraBlobsDAO.class);
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement insertPart;
    private final PreparedStatement select;
    private final PreparedStatement selectPart;
    private final DataChunker dataChunker;
    private final CassandraConfiguration configuration;

    @Inject
    public CassandraBlobsDAO(Session session, CassandraConfiguration cassandraConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.configuration = cassandraConfiguration;
        this.dataChunker = new DataChunker();
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);

        this.insertPart = prepareInsertPart(session);
        this.selectPart = prepareSelectPart(session);
    }

    @VisibleForTesting
    public CassandraBlobsDAO(Session session) {
        this(session, CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
            .from(BlobTable.TABLE_NAME)
            .where(eq(BlobTable.ID, bindMarker(BlobTable.ID))));
    }

    private PreparedStatement prepareSelectPart(Session session) {
        return session.prepare(select()
            .from(BlobParts.TABLE_NAME)
            .where(eq(BlobTable.ID, bindMarker(BlobTable.ID)))
            .and(eq(BlobParts.CHUNK_NUMBER, bindMarker(BlobParts.CHUNK_NUMBER))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(BlobTable.TABLE_NAME)
            .value(BlobTable.ID, bindMarker(BlobTable.ID))
            .value(BlobTable.NUMBER_OF_CHUNK, bindMarker(BlobTable.NUMBER_OF_CHUNK)));
    }

    private PreparedStatement prepareInsertPart(Session session) {
        return session.prepare(insertInto(BlobParts.TABLE_NAME)
            .value(BlobTable.ID, bindMarker(BlobTable.ID))
            .value(BlobParts.CHUNK_NUMBER, bindMarker(BlobParts.CHUNK_NUMBER))
            .value(BlobParts.DATA, bindMarker(BlobParts.DATA)));
    }

    @Override
    public CompletableFuture<BlobId> save(byte[] data) {
        Preconditions.checkNotNull(data);

        CassandraBlobId blobId = CassandraBlobId.forPayload(data);
        return saveBlobParts(data, blobId)
            .thenCompose(numberOfChunk -> saveBlobPartsReferences(blobId, numberOfChunk))
            .thenApply(any -> blobId);
    }

    private CompletableFuture<Integer> saveBlobParts(byte[] data, CassandraBlobId blobId) {
        return FluentFutureStream.of(
            dataChunker.chunk(data, configuration.getBlobPartSize())
                .map(pair -> writePart(pair.getRight(), blobId, pair.getKey())
                    .thenApply(partId -> Pair.of(pair.getKey(), partId))))
            .completableFuture()
            .thenApply(stream ->
                getLastOfStream(stream)
                    .map(numOfChunkAndPartId -> numOfChunkAndPartId.getLeft() + 1)
                    .orElse(0));
    }

    private static <T> Optional<T> getLastOfStream(Stream<T> stream) {
        return stream.reduce((first, second) -> second);
    }

    private CompletableFuture<Void> writePart(ByteBuffer data, CassandraBlobId blobId, int position) {
        return cassandraAsyncExecutor.executeVoid(
            insertPart.bind()
                .setString(BlobTable.ID, blobId.asString())
                .setInt(BlobParts.CHUNK_NUMBER, position)
                .setBytes(BlobParts.DATA, data));
    }

    private CompletableFuture<Void> saveBlobPartsReferences(CassandraBlobId blobId, int numberOfChunk) {
        return cassandraAsyncExecutor.executeVoid(insert.bind()
            .setString(BlobTable.ID, blobId.asString())
            .setInt(BlobTable.NUMBER_OF_CHUNK, numberOfChunk));
    }

    @Override
    public CompletableFuture<byte[]> read(BlobId blobId) {
        return cassandraAsyncExecutor.executeSingleRow(
            select.bind()
                .setString(BlobTable.ID, blobId.asString()))
            .thenCompose(row -> toDataParts(row, blobId))
            .thenApply(this::concatenateDataParts);
    }

    private CompletableFuture<Stream<BlobPart>> toDataParts(Optional<Row> blobRowOptional, BlobId blobId) {
        return blobRowOptional.map(blobRow -> {
            int numOfChunk = blobRow.getInt(BlobTable.NUMBER_OF_CHUNK);
            return FluentFutureStream.of(
                IntStream.range(0, numOfChunk)
                    .mapToObj(position -> readPart(blobId, position)))
                .completableFuture();
        }).orElseGet(() -> {
            LOGGER.warn("Could not retrieve blob metadata for {}", blobId);
            return CompletableFuture.completedFuture(Stream.empty());
        });
    }

    private byte[] concatenateDataParts(Stream<BlobPart> blobParts) {
        ImmutableList<byte[]> parts = blobParts
            .map(blobPart -> OptionalUtils.executeIfEmpty(
                blobPart.row,
                () -> LOGGER.warn("Missing blob part for blobId {} and position {}", blobPart.blobId, blobPart.position)))
            .flatMap(OptionalUtils::toStream)
            .map(this::rowToData)
            .collect(Guavate.toImmutableList());

        return Bytes.concat(parts.toArray(new byte[parts.size()][]));
    }

    private byte[] rowToData(Row row) {
        byte[] data = new byte[row.getBytes(BlobParts.DATA).remaining()];
        row.getBytes(BlobParts.DATA).get(data);
        return data;
    }

    private CompletableFuture<BlobPart> readPart(BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectPart.bind()
                .setString(BlobTable.ID, blobId.asString())
                .setInt(BlobParts.CHUNK_NUMBER, position))
            .thenApply(row -> new BlobPart(blobId, position, row));
    }

    private static class BlobPart {
        private final BlobId blobId;
        private final int position;
        private final Optional<Row> row;

        public BlobPart(BlobId blobId, int position, Optional<Row> row) {
            Preconditions.checkNotNull(blobId);
            Preconditions.checkArgument(position >= 0, "position need to be positive");
            this.blobId = blobId;
            this.position = position;
            this.row = row;
        }
    }
}
