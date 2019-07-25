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
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.cassandra.utils.DataChunker;
import org.apache.james.blob.cassandra.utils.PipedStreamSubscriber;

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
    private final CassandraDefaultBucketDAO defaultBucketDAO;
    private final DataChunker dataChunker;
    private final CassandraConfiguration configuration;
    private final HashBlobId.Factory blobIdFactory;

    @Inject
    CassandraBlobStore(CassandraDefaultBucketDAO defaultBucketDAO, CassandraConfiguration cassandraConfiguration, HashBlobId.Factory blobIdFactory) {
        this.defaultBucketDAO = defaultBucketDAO;
        this.configuration = cassandraConfiguration;
        this.blobIdFactory = blobIdFactory;
        this.dataChunker = new DataChunker();
    }

    @VisibleForTesting
    public CassandraBlobStore(Session session) {
        this(new CassandraDefaultBucketDAO(session), CassandraConfiguration.DEFAULT_CONFIGURATION, new HashBlobId.Factory());
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] data) {
        Preconditions.checkNotNull(data);

        return saveAsMono(data);
    }

    private Mono<BlobId> saveAsMono(byte[] data) {
        BlobId blobId = blobIdFactory.forPayload(data);
        return saveBlobParts(data, blobId)
            .flatMap(numberOfChunk -> defaultBucketDAO.saveBlobPartsReferences(blobId, numberOfChunk)
                .then(Mono.just(blobId)));
    }

    private Mono<Integer> saveBlobParts(byte[] data, BlobId blobId) {
        Stream<Pair<Integer, ByteBuffer>> chunks = dataChunker.chunk(data, configuration.getBlobPartSize());
        return Flux.fromStream(chunks)
            .publishOn(Schedulers.elastic(), PREFETCH)
            .flatMap(pair -> defaultBucketDAO.writePart(pair.getValue(), blobId, getChunkNum(pair))
                .then(Mono.just(getChunkNum(pair))))
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

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return readBlobParts(blobId)
            .collectList()
            .map(parts -> Bytes.concat(parts.toArray(new byte[0][])));
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
        Integer rowCount = defaultBucketDAO.selectRowCount(blobId)
            .publishOn(Schedulers.elastic())
            .switchIfEmpty(Mono.error(
                new ObjectNotFoundException(String.format("Could not retrieve blob metadata for %s", blobId))))
            .block();
        return Flux.range(0, rowCount)
            .publishOn(Schedulers.elastic(), PREFETCH)
            .flatMapSequential(partIndex -> defaultBucketDAO.readPart(blobId, partIndex)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                    String.format("Missing blob part for blobId %s and position %d", blobId, partIndex))))
                , MAX_CONCURRENCY, PREFETCH);
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
