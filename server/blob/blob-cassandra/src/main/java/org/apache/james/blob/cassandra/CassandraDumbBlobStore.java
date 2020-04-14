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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.NoSuchElementException;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.blob.cassandra.utils.DataChunker;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraDumbBlobStore implements DumbBlobStore {

    public static final String DEFAULT_BUCKET = "cassandraDefault";
    public static final boolean LAZY = false;

    private final CassandraDefaultBucketDAO defaultBucketDAO;
    private final CassandraBucketDAO bucketDAO;
    private final DataChunker dataChunker;
    private final CassandraConfiguration configuration;
    private final BucketName defaultBucket;

    @Inject
    public CassandraDumbBlobStore(CassandraDefaultBucketDAO defaultBucketDAO,
                           CassandraBucketDAO bucketDAO,
                           CassandraConfiguration cassandraConfiguration,
                           @Named(DEFAULT_BUCKET) BucketName defaultBucket) {
        this.defaultBucketDAO = defaultBucketDAO;
        this.bucketDAO = bucketDAO;
        this.configuration = cassandraConfiguration;
        this.defaultBucket = defaultBucket;
        this.dataChunker = new DataChunker();
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return ReactorUtils.toInputStream(readBlobParts(bucketName, blobId));
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return readBlobParts(bucketName, blobId)
            .collectList()
            .map(this::byteBuffersToBytesArray);
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        Preconditions.checkNotNull(data);

        return Mono.fromCallable(() -> dataChunker.chunk(data, configuration.getBlobPartSize()))
            .flatMap(chunks -> save(bucketName, blobId, chunks));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(inputStream);

        return Mono.fromCallable(() -> dataChunker.chunkStream(inputStream, configuration.getBlobPartSize()))
            .flatMap(chunks -> save(bucketName, blobId, chunks))
            .onErrorMap(e -> new ObjectStoreIOException("Exception occurred while saving input stream", e));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Mono.using(content::openBufferedStream,
            stream -> save(bucketName, blobId, stream),
            Throwing.consumer(InputStream::close).sneakyThrow(),
            LAZY);
    }

    private Mono<Void> save(BucketName bucketName, BlobId blobId, Flux<ByteBuffer> chunksAsFlux) {
        return saveBlobParts(bucketName, blobId, chunksAsFlux)
            .flatMap(numberOfChunk -> saveBlobPartReference(bucketName, blobId, numberOfChunk));
    }

    private Mono<Integer> saveBlobParts(BucketName bucketName, BlobId blobId, Flux<ByteBuffer> chunksAsFlux) {
        return chunksAsFlux
            .index()
            .concatMap(pair -> writePart(bucketName, blobId, pair.getT1().intValue(), pair.getT2()))
            .count()
            .map(Long::intValue);
    }

    private Mono<?> writePart(BucketName bucketName, BlobId blobId, int position, ByteBuffer data) {
        Mono<?> write;
        if (isDefaultBucket(bucketName)) {
            write = defaultBucketDAO.writePart(data, blobId, position);
        } else {
            write = bucketDAO.writePart(data, bucketName, blobId, position);
        }
        int anyNonEmptyValue = 1;
        return write.thenReturn(anyNonEmptyValue);
    }

    private Mono<Void> saveBlobPartReference(BucketName bucketName, BlobId blobId, Integer numberOfChunk) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.saveBlobPartsReferences(blobId, numberOfChunk);
        } else {
            return bucketDAO.saveBlobPartsReferences(bucketName, blobId, numberOfChunk);
        }
    }

    private boolean isDefaultBucket(BucketName bucketName) {
        return bucketName.equals(defaultBucket);
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.deletePosition(blobId)
                .then(defaultBucketDAO.deleteParts(blobId));
        } else {
            return bucketDAO.deletePosition(bucketName, blobId)
                .then(bucketDAO.deleteParts(bucketName, blobId));
        }
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkArgument(!isDefaultBucket(bucketName), "Deleting the default bucket is forbidden");

        return bucketDAO.listAll()
            .filter(bucketNameBlobIdPair -> bucketNameBlobIdPair.getKey().equals(bucketName))
            .map(Pair::getValue)
            .flatMap(blobId -> delete(bucketName, blobId))
            .then();
    }

    private Mono<ByteBuffer> readPart(BucketName bucketName, BlobId blobId, Integer partIndex) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.readPart(blobId, partIndex);
        } else {
            return bucketDAO.readPart(bucketName, blobId, partIndex);
        }
    }

    private Mono<Integer> selectRowCount(BucketName bucketName, BlobId blobId) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.selectRowCount(blobId);
        } else {
            return bucketDAO.selectRowCount(bucketName, blobId);
        }
    }

    private Flux<ByteBuffer> readBlobParts(BucketName bucketName, BlobId blobId) {
        return selectRowCount(bucketName, blobId)
            .single()
            .onErrorMap(NoSuchElementException.class, e ->
                new ObjectNotFoundException(String.format("Could not retrieve blob metadata for %s", blobId)))
            .flatMapMany(rowCount -> Flux.range(0, rowCount)
                .concatMap(partIndex -> readPart(bucketName, blobId, partIndex)
                    .single()
                    .onErrorMap(NoSuchElementException.class, e ->
                        new ObjectNotFoundException(String.format("Missing blob part for blobId %s and position %d", blobId.asString(), partIndex)))));
    }

    private byte[] byteBuffersToBytesArray(List<ByteBuffer> byteBuffers) {
        int targetSize = byteBuffers
            .stream()
            .mapToInt(ByteBuffer::remaining)
            .sum();

        return byteBuffers
            .stream()
            .reduce(ByteBuffer.allocate(targetSize), ByteBuffer::put)
            .array();
    }
}
