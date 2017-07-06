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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BlobParts;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.Blobs;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.ids.BlobId;
import org.apache.james.mailbox.cassandra.ids.PartId;
import org.apache.james.mailbox.cassandra.mail.utils.DataChunker;
import org.apache.james.util.CompletableFutureUtil;
import org.apache.james.util.FluentFutureStream;
import org.apache.james.util.OptionalConverter;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Bytes;


public class CassandraBlobsDAO {

    public static final int CHUNK_SIZE = 1024 * 100;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement insertPart;
    private final PreparedStatement select;
    private final PreparedStatement selectPart;
    private final DataChunker dataChunker;

    @Inject
    public CassandraBlobsDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.dataChunker = new DataChunker();
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);

        this.insertPart = prepareInsertPart(session);
        this.selectPart = prepareSelectPart(session);
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
            .from(Blobs.TABLE_NAME)
            .where(eq(Blobs.ID, bindMarker(Blobs.ID))));
    }

    private PreparedStatement prepareSelectPart(Session session) {
        return session.prepare(select()
            .from(BlobParts.TABLE_NAME)
            .where(eq(BlobParts.ID, bindMarker(BlobParts.ID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(Blobs.TABLE_NAME)
            .value(Blobs.ID, bindMarker(Blobs.ID))
            .value(Blobs.POSITION, bindMarker(Blobs.POSITION))
            .value(Blobs.PART, bindMarker(Blobs.PART)));
    }

    private PreparedStatement prepareInsertPart(Session session) {
        return session.prepare(insertInto(BlobParts.TABLE_NAME)
            .value(BlobParts.ID, bindMarker(BlobParts.ID))
            .value(BlobParts.DATA, bindMarker(BlobParts.DATA)));
    }

    public CompletableFuture<Optional<BlobId>> save(byte[] data) {
        if (data == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        BlobId blobId = BlobId.forPayload(data);
        return saveBlobParts(data, blobId)
            .thenCompose(partIds -> saveBlobPartsReferences(blobId, partIds))
            .thenApply(any -> Optional.of(blobId));
    }

    private CompletableFuture<Stream<Pair<Integer, PartId>>> saveBlobParts(byte[] data, BlobId blobId) {
        return FluentFutureStream.of(
            dataChunker.chunk(data, CHUNK_SIZE)
                .map(pair -> writePart(pair.getRight(), blobId, pair.getKey())
                    .thenApply(partId -> Pair.of(pair.getKey(), partId))))
            .completableFuture();
    }

    private CompletableFuture<PartId> writePart(ByteBuffer data, BlobId blobId, int position) {
        PartId partId = PartId.create(blobId, position);
        return cassandraAsyncExecutor.executeVoid(
            insertPart.bind()
                .setString(BlobParts.ID, partId.getId())
                .setBytes(BlobParts.DATA, data))
            .thenApply(any -> partId);
    }

    private CompletableFuture<Stream<Void>> saveBlobPartsReferences(BlobId blobId, Stream<Pair<Integer, PartId>> stream) {
        return FluentFutureStream.of(stream.map(pair ->
            cassandraAsyncExecutor.executeVoid(insert.bind()
                .setString(Blobs.ID, blobId.getId())
                .setLong(Blobs.POSITION, pair.getKey())
                .setString(Blobs.PART, pair.getValue().getId()))))
            .completableFuture();
    }

    public CompletableFuture<byte[]> read(BlobId blobId) {
        return cassandraAsyncExecutor.execute(
            select.bind()
                .setString(Blobs.ID, blobId.getId()))
            .thenApply(this::toPartIds)
            .thenCompose(this::toDataParts)
            .thenApply(this::concatenateDataParts);
    }

    private ImmutableMap<Long, PartId> toPartIds(ResultSet resultSet) {
        return CassandraUtils.convertToStream(resultSet)
            .map(row -> Pair.of(row.getLong(Blobs.POSITION), PartId.from(row.getString(Blobs.PART))))
            .collect(Guavate.toImmutableMap(Pair::getKey, Pair::getValue));
    }

    private CompletableFuture<Stream<Optional<Row>>> toDataParts(ImmutableMap<Long, PartId> positionToIds) {
        return CompletableFutureUtil.chainAll(
            positionToIds.values().stream(),
            this::readPart);
    }

    private byte[] concatenateDataParts(Stream<Optional<Row>> rows) {
        ImmutableList<byte[]> parts = rows.flatMap(OptionalConverter::toStream)
            .map(this::rowToData)
            .collect(Guavate.toImmutableList());

        return Bytes.concat(parts.toArray(new byte[parts.size()][]));
    }

    private byte[] rowToData(Row row) {
        byte[] data = new byte[row.getBytes(BlobParts.DATA).remaining()];
        row.getBytes(BlobParts.DATA).get(data);
        return data;
    }

    private CompletableFuture<Optional<Row>> readPart(PartId partId) {
        return cassandraAsyncExecutor.executeSingleRow(selectPart.bind()
            .setString(BlobParts.ID, partId.getId()));
    }
}
