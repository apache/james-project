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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.DataChunker;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * WARNING: JAMES-3591 Cassandra is not made to store large binary content, its use will be suboptimal compared to
 * alternatives (namely S3 compatible BlobStores backed by for instance S3, MinIO or Ozone)
 */
public class CassandraBlobStoreDAO implements BlobStoreDAO {
    public static final boolean LAZY = false;

    public static final String CASSANDRA_BLOBSTORE_CL_ONE_MISS_COUNT_METRIC_NAME = "cassandraBlobStoreClOneMisses";
    public static final String CASSANDRA_BLOBSTORE_CL_ONE_HIT_COUNT_METRIC_NAME = "cassandraBlobStoreClOneHits";
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraBlobStoreDAO.class);

    private final CassandraDefaultBucketDAO defaultBucketDAO;
    private final CassandraBucketDAO bucketDAO;
    private final CassandraConfiguration configuration;
    private final BucketName defaultBucket;

    private final Metric metricClOneHitCount;
    private final Metric metricClOneMissCount;

    @Inject
    @VisibleForTesting
    public CassandraBlobStoreDAO(CassandraDefaultBucketDAO defaultBucketDAO,
                                 CassandraBucketDAO bucketDAO,
                                 CassandraConfiguration cassandraConfiguration,
                                 @Named(BlobStore.DEFAULT_BUCKET_NAME_QUALIFIER) BucketName defaultBucket,
                                 MetricFactory metricFactory) {
        this.defaultBucketDAO = defaultBucketDAO;
        this.bucketDAO = bucketDAO;
        this.configuration = cassandraConfiguration;
        this.defaultBucket = defaultBucket;

        this.metricClOneMissCount = metricFactory.generate(CASSANDRA_BLOBSTORE_CL_ONE_MISS_COUNT_METRIC_NAME);
        this.metricClOneHitCount = metricFactory.generate(CASSANDRA_BLOBSTORE_CL_ONE_HIT_COUNT_METRIC_NAME);

        if (Objects.equals(System.getenv("cassandra.blob.store.disable.startup.warning"), "false")) {
            LOGGER.warn("WARNING: JAMES-3591 Cassandra is not made to store large binary content, its use will be suboptimal compared to " +
                " alternatives (namely S3 compatible BlobStores backed by for instance S3, MinIO or Ozone)");
        }
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return ReactorUtils.toInputStream(readBlobParts(bucketName, blobId));
    }

    @Override
    public Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.just(read(bucketName, blobId));
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

        return Mono.fromCallable(() -> DataChunker.chunk(data, configuration.getBlobPartSize()))
            .flatMap(chunks -> save(bucketName, blobId, chunks));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(inputStream);

        return Mono.fromCallable(() -> ReactorUtils.toChunks(inputStream, configuration.getBlobPartSize())
                .subscribeOn(Schedulers.boundedElastic()))
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
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return Flux.fromIterable(blobIds)
            .flatMap(id -> delete(bucketName, id), DEFAULT_CONCURRENCY)
            .then();
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkArgument(!isDefaultBucket(bucketName), "Deleting the default bucket is forbidden");

        return bucketDAO.listAll()
            .filter(bucketNameBlobIdPair -> bucketNameBlobIdPair.getKey().equals(bucketName))
            .map(Pair::getValue)
            .flatMap(blobId -> delete(bucketName, blobId), DEFAULT_CONCURRENCY)
            .then();
    }

    private Mono<ByteBuffer> readPart(BucketName bucketName, BlobId blobId, Integer partIndex) {
        if (configuration.isOptimisticConsistencyLevel()) {
            return readPartClOne(bucketName, blobId, partIndex)
                .doOnNext(any -> metricClOneHitCount.increment())
                .switchIfEmpty(Mono.fromRunnable(metricClOneMissCount::increment)
                    .then(readPartClDefault(bucketName, blobId, partIndex)));
        } else {
            return readPartClDefault(bucketName, blobId, partIndex);
        }
    }

    private Mono<ByteBuffer> readPartClOne(BucketName bucketName, BlobId blobId, Integer partIndex) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.readPartClOne(blobId, partIndex);
        } else {
            return bucketDAO.readPartClOne(bucketName, blobId, partIndex);
        }
    }

    private Mono<ByteBuffer> readPartClDefault(BucketName bucketName, BlobId blobId, Integer partIndex) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.readPart(blobId, partIndex);
        } else {
            return bucketDAO.readPart(bucketName, blobId, partIndex);
        }
    }

    private Mono<Integer> selectRowCount(BucketName bucketName, BlobId blobId) {
        if (configuration.isOptimisticConsistencyLevel()) {
            return selectRowCountClOne(bucketName, blobId)
                .doOnNext(any -> metricClOneHitCount.increment())
                .switchIfEmpty(Mono.fromRunnable(metricClOneMissCount::increment)
                    .then(selectRowCountClDefault(bucketName, blobId)));
        } else {
            return selectRowCountClDefault(bucketName, blobId);
        }
    }

    private Mono<Integer> selectRowCountClOne(BucketName bucketName, BlobId blobId) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.selectRowCountClOne(blobId);
        } else {
            return bucketDAO.selectRowCountClOne(bucketName, blobId);
        }
    }

    private Mono<Integer> selectRowCountClDefault(BucketName bucketName, BlobId blobId) {
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

    @Override
    public Publisher<BucketName> listBuckets() {
        return bucketDAO.listAll()
            .map(Pair::getLeft)
            .distinct();
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.listBlobs();
        } else {
            return bucketDAO.listAll(bucketName);
        }
    }
}
