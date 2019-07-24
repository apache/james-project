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

import java.io.InputStream;
import java.io.PipedInputStream;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.cassandra.BlobTable.BlobParts;
import org.apache.james.blob.cassandra.utils.DataChunker;
import org.apache.james.blob.cassandra.utils.PipedStreamSubscriber;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Bytes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CassandraBlobStore implements BlobStore {

    private static final int PREFETCH = 16;
    private static final int MAX_CONCURRENCY = 2;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement insertPart;
    private final PreparedStatement select;
    private final PreparedStatement selectPart;
    private final DataChunker dataChunker;
    private final CassandraConfiguration configuration;
    private final HashBlobId.Factory blobIdFactory;

    @Inject
    public CassandraBlobStore(Session session, CassandraConfiguration cassandraConfiguration, HashBlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.configuration = cassandraConfiguration;
        this.blobIdFactory = blobIdFactory;
        this.dataChunker = new DataChunker();
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);

        this.insertPart = prepareInsertPart(session);
        this.selectPart = prepareSelectPart(session);
    }

    @VisibleForTesting
    public CassandraBlobStore(Session session) {
        this(session, CassandraConfiguration.DEFAULT_CONFIGURATION, new HashBlobId.Factory());
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
    public Mono<BlobId> save(BucketName bucketName, byte[] data) {
        Preconditions.checkNotNull(data);

        return saveAsMono(data);
    }

    private Mono<BlobId> saveAsMono(byte[] data) {
        BlobId blobId = blobIdFactory.forPayload(data);
        return saveBlobParts(data, blobId)
            .flatMap(numberOfChunk -> saveBlobPartsReferences(blobId, numberOfChunk));
    }

    private Mono<Integer> saveBlobParts(byte[] data, BlobId blobId) {
        Stream<Pair<Integer, ByteBuffer>> chunks = dataChunker.chunk(data, configuration.getBlobPartSize());
        return Flux.fromStream(chunks)
            .publishOn(Schedulers.elastic(), PREFETCH)
            .flatMap(pair -> writePart(pair.getValue(), blobId, getChunkNum(pair)))
            .collect(Collectors.maxBy(Comparator.comparingInt(x -> x)))
            .flatMap(Mono::justOrEmpty)
            .map(this::numToCount)
            .defaultIfEmpty(0);
    }

    private int numToCount(int number) {
        return number + 1;
    }

    private Integer getChunkNum(Pair<Integer, ByteBuffer> pair) {
        return pair.getKey();
    }

    private Mono<Integer> writePart(ByteBuffer data, BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeVoid(
            insertPart.bind()
                .setString(BlobTable.ID, blobId.asString())
                .setInt(BlobParts.CHUNK_NUMBER, position)
                .setBytes(BlobParts.DATA, data))
            .then(Mono.just(position));
    }

    private Mono<BlobId> saveBlobPartsReferences(BlobId blobId, int numberOfChunk) {
        return cassandraAsyncExecutor.executeVoid(
            insert.bind()
                .setString(BlobTable.ID, blobId.asString())
                .setInt(BlobTable.NUMBER_OF_CHUNK, numberOfChunk))
            .then(Mono.just(blobId));
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return readBlobParts(blobId)
            .collectList()
            .map(parts -> Bytes.concat(parts.toArray(new byte[0][])));
    }

    private Mono<Integer> selectRowCount(BlobId blobId) {
        return cassandraAsyncExecutor.executeSingleRow(
                select.bind()
                    .setString(BlobTable.ID, blobId.asString()))
            .map(row -> row.getInt(BlobTable.NUMBER_OF_CHUNK));
    }

    private byte[] rowToData(Row row) {
        byte[] data = new byte[row.getBytes(BlobParts.DATA).remaining()];
        row.getBytes(BlobParts.DATA).get(data);
        return data;
    }

    private Mono<byte[]> readPart(BlobId blobId, int position) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectPart.bind()
                .setString(BlobTable.ID, blobId.asString())
                .setInt(BlobParts.CHUNK_NUMBER, position))
            .map(this::rowToData)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                String.format("Missing blob part for blobId %s and position %d", blobId, position))));
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) {
        PipedInputStream pipedInputStream = new PipedInputStream();
        readBlobParts(blobId)
            .subscribe(new PipedStreamSubscriber(pipedInputStream));
        return pipedInputStream;
    }

    @Override
    public BucketName getDefaultBucketName() {
        return BucketName.DEFAULT;
    }

    private Flux<byte[]> readBlobParts(BlobId blobId) {
        Integer rowCount = selectRowCount(blobId)
            .publishOn(Schedulers.elastic())
            .switchIfEmpty(Mono.error(
                new ObjectNotFoundException(String.format("Could not retrieve blob metadata for %s", blobId))))
            .block();
        return Flux.range(0, rowCount)
            .publishOn(Schedulers.elastic(), PREFETCH)
            .flatMapSequential(partIndex -> readPart(blobId, partIndex), MAX_CONCURRENCY, PREFETCH);
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, InputStream data) {
        Preconditions.checkNotNull(data);
        return Mono.fromCallable(() -> IOUtils.toByteArray(data))
            .flatMap(this::saveAsMono);
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        throw new NotImplementedException("not implemented");
    }
}
