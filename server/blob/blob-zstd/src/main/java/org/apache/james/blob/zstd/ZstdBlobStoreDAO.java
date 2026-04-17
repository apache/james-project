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

package org.apache.james.blob.zstd;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.common.io.ByteSource;
import com.google.common.io.CountingOutputStream;
import com.google.common.io.FileBackedOutputStream;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ZstdBlobStoreDAO implements BlobStoreDAO {
    private record CompressionDecision(long originalSize, long compressedSize) {
        boolean satisfyCompressionMinRatio(float minRatio) {
            return ((float) compressedSize / originalSize) <= minRatio;
        }
    }

    public static final BlobMetadataName CONTENT_ORIGINAL_SIZE = new BlobMetadataName("content-original-size");
    public static final String BLOB_ZSTD_COMPRESS_SAVE_COUNT_METRIC_NAME = "blobZstdCompressSaveCount";
    public static final String BLOB_ZSTD_THRESHOLD_SKIP_COUNT_METRIC_NAME = "blobZstdThresholdSkipCount";
    public static final String BLOB_ZSTD_RATIO_SKIP_COUNT_METRIC_NAME = "blobZstdRatioSkipCount";
    public static final String BLOB_ZSTD_DECOMPRESS_COUNT_METRIC_NAME = "blobZstdDecompressCount";
    public static final String BLOB_ZSTD_SAVED_BYTES_METRIC_NAME = "blobZstdSavedBytes";
    public static final String BLOB_ZSTD_COMPRESS_LATENCY_METRIC_NAME = "blobZstdCompressLatency";
    public static final String BLOB_ZSTD_DECOMPRESS_LATENCY_METRIC_NAME = "blobZstdDecompressLatency";
    private static final int FILE_THRESHOLD = 100 * 1024;
    private static final Set<BlobMetadataName> RESERVED_METADATA_NAMES = Set.of(ContentTransferEncoding.NAME, CONTENT_ORIGINAL_SIZE);

    private final BlobStoreDAO underlying;
    private final CompressionConfiguration compressionConfiguration;
    private final MetricFactory metricFactory;
    private final Metric compressSaveCount;
    private final Metric thresholdSkipCount;
    private final Metric ratioSkipCount;
    private final Metric decompressCount;
    private final Metric savedBytes;

    public ZstdBlobStoreDAO(BlobStoreDAO underlying, CompressionConfiguration compressionConfiguration, MetricFactory metricFactory) {
        this.underlying = underlying;
        this.compressionConfiguration = compressionConfiguration;
        this.metricFactory = metricFactory;
        this.compressSaveCount = metricFactory.generate(BLOB_ZSTD_COMPRESS_SAVE_COUNT_METRIC_NAME);
        this.thresholdSkipCount = metricFactory.generate(BLOB_ZSTD_THRESHOLD_SKIP_COUNT_METRIC_NAME);
        this.ratioSkipCount = metricFactory.generate(BLOB_ZSTD_RATIO_SKIP_COUNT_METRIC_NAME);
        this.decompressCount = metricFactory.generate(BLOB_ZSTD_DECOMPRESS_COUNT_METRIC_NAME);
        this.savedBytes = metricFactory.generate(BLOB_ZSTD_SAVED_BYTES_METRIC_NAME);
    }

    @Override
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        InputStreamBlob blob = underlying.read(bucketName, blobId);
        if (isCompressed(blob.metadata())) {
            return decompress(blob);
        }
        return blob;
    }

    @Override
    public Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.from(underlying.readReactive(bucketName, blobId))
            .flatMap(blob -> {
                if (isCompressed(blob.metadata())) {
                    return decompressReactive(blob);
                }
                return Mono.just(blob);
            });
    }

    @Override
    public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(underlying.readBytes(bucketName, blobId))
            .flatMap(blob -> {
                if (isCompressed(blob.metadata())) {
                    return decompressBytesReactive(blob);
                }
                return Mono.just(blob);
            });
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        return switch (blob) {
            case BytesBlob bytesBlob -> save(bucketName, blobId, bytesBlob.payload(), bytesBlob.metadata());
            case InputStreamBlob inputStreamBlob -> save(bucketName, blobId, inputStreamBlob.payload(), inputStreamBlob.metadata());
            case ByteSourceBlob byteSourceBlob -> save(bucketName, blobId, byteSourceBlob.payload(), byteSourceBlob.metadata());
        };
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        return underlying.delete(bucketName, blobId);
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return underlying.delete(bucketName, blobIds);
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return underlying.deleteBucket(bucketName);
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return underlying.listBuckets();
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return underlying.listBlobs(bucketName);
    }

    private InputStreamBlob decompress(InputStreamBlob blob) throws ObjectStoreIOException {
        try {
            decompressCount.increment();
            return InputStreamBlob.of(new ZstdInputStream(blob.payload()), blob.metadata());
        } catch (IOException e) {
            throw new ObjectStoreIOException("Failed to initialize zstd decompression", e);
        }
    }

    private Mono<InputStreamBlob> decompressReactive(InputStreamBlob blob) {
        return Mono.fromCallable(() -> InputStreamBlob.of(new ZstdInputStream(blob.payload()), blob.metadata()))
            .doOnNext(ignored -> decompressCount.increment())
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Failed to initialize zstd decompression", e));
    }

    private Mono<BytesBlob> decompressBytesReactive(BytesBlob blob) {
        return Mono.fromCallable(() -> decompress(blob))
            .subscribeOn(Schedulers.parallel())
            .doOnNext(ignored -> decompressCount.increment())
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Failed to decompress blob", e));
    }

    private BytesBlob decompress(BytesBlob blob) throws IOException {
        TimeMetric timeMetric = metricFactory.timer(BLOB_ZSTD_DECOMPRESS_LATENCY_METRIC_NAME);
        try {
            return BytesBlob.of(Zstd.decompress(blob.payload(), originalSize(blob.metadata())), blob.metadata());
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private int originalSize(BlobMetadata metadata) throws IOException {
        BlobMetadataValue sizeMetadata = metadata.get(CONTENT_ORIGINAL_SIZE)
            .orElseThrow(() -> new IOException("Missing " + CONTENT_ORIGINAL_SIZE.name() + " metadata for compressed blob"));

        try {
            long originalSize = Long.parseLong(sizeMetadata.value());
            return Math.toIntExact(originalSize);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IOException("Invalid " + CONTENT_ORIGINAL_SIZE.name() + " metadata value: " + sizeMetadata.value(), e);
        }
    }

    private Publisher<Void> save(BucketName bucketName, BlobId blobId, byte[] data, BlobMetadata metadata) {
        BlobMetadata sanitizedMetadata = sanitizeMetadata(metadata);

        if (shouldAttemptCompression(data.length)) {
            return Mono.fromCallable(() -> compress(data))
                .subscribeOn(Schedulers.parallel())
                .publishOn(Schedulers.boundedElastic())
                .flatMap(compressed -> {
                    CompressionDecision compressionDecision = compressionDecision(data.length, compressed.length);
                    if (compressionDecision.satisfyCompressionMinRatio(compressionConfiguration.minRatio())) {
                        return saveCompressed(bucketName, blobId, data, compressed, sanitizedMetadata);
                    }

                    return saveOriginal(bucketName, blobId, data, sanitizedMetadata)
                        .doOnSuccess(ignored -> ratioSkipCount.increment());
                })
                .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob " + blobId.asString(), e));
        }

        recordThresholdSkipIfNeeded(data.length);
        return Mono.from(underlying.save(bucketName, blobId, BytesBlob.of(data, sanitizedMetadata)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> saveCompressed(BucketName bucketName, BlobId blobId, byte[] data, byte[] compressed, BlobMetadata sanitizedMetadata) {
        return Mono.from(underlying.save(bucketName, blobId, BytesBlob.of(compressed, withCompressionMetadata(sanitizedMetadata, data.length))))
            .doOnSuccess(ignored -> {
                compressSaveCount.increment();
                savedBytes.add(data.length - compressed.length);
            });
    }

    private Mono<Void> saveOriginal(BucketName bucketName, BlobId blobId, byte[] data, BlobMetadata sanitizedMetadata) {
        return Mono.from(underlying.save(bucketName, blobId, BytesBlob.of(data, sanitizedMetadata)));
    }

    private Publisher<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream, BlobMetadata metadata) {
        BlobMetadata sanitizedMetadata = sanitizeMetadata(metadata);

        return Mono.usingWhen(Mono.fromCallable(() -> new FileBackedOutputStream(FILE_THRESHOLD)),
                originalContent -> Mono.fromCallable(() -> inputStream.transferTo(originalContent))
                    .flatMap(originalSize -> {
                        if (shouldAttemptCompression(originalSize)) {
                            return compressAndSave(bucketName, blobId, originalContent, originalSize, sanitizedMetadata);
                        }

                        recordThresholdSkipIfNeeded(originalSize);
                        return Mono.from(underlying.save(bucketName, blobId, byteSourceBlobWithSize(originalContent.asByteSource(), originalSize, sanitizedMetadata)));
                    }),
                originalContent -> Mono.fromRunnable(Throwing.runnable(originalContent::reset)).subscribeOn(Schedulers.boundedElastic()))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob " + blobId.asString(), e));
    }

    private Publisher<Void> save(BucketName bucketName, BlobId blobId, ByteSource byteSource, BlobMetadata metadata) {
        return Mono.using(byteSource::openStream,
                inputStream -> Mono.from(save(bucketName, blobId, inputStream, metadata)),
                Throwing.consumer(InputStream::close))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob " + blobId.asString(), e));
    }

    private Mono<Void> compressAndSave(BucketName bucketName, BlobId blobId, FileBackedOutputStream originalContent,
                                       long originalSize, BlobMetadata metadata) {
        return Mono.usingWhen(Mono.fromCallable(() -> new FileBackedOutputStream(FILE_THRESHOLD)),
                compressContent -> prepareCompressedContent(originalContent, originalSize, compressContent)
                    .flatMap(compressionDecision -> {
                        if (compressionDecision.satisfyCompressionMinRatio(compressionConfiguration.minRatio())) {
                            return saveCompressed(bucketName, blobId, originalSize, metadata, compressContent, compressionDecision.compressedSize());
                        }

                        return saveOriginal(bucketName, blobId, originalContent, originalSize, metadata);
                    }),
                compressContent -> Mono.fromRunnable(Throwing.runnable(compressContent::reset)).subscribeOn(Schedulers.boundedElastic()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<CompressionDecision> prepareCompressedContent(FileBackedOutputStream originalContent, long originalSize,
                                                               FileBackedOutputStream compressContent) {
        return Mono.fromCallable(() -> {
            try (InputStream originalStream = originalContent.asByteSource().openStream()) {
                long compressedSize = compress(originalStream, compressContent);
                return compressionDecision(originalSize, compressedSize);
            }
        });
    }

    private Mono<Void> saveCompressed(BucketName bucketName, BlobId blobId, long originalSize, BlobMetadata metadata,
                                      FileBackedOutputStream compressContent, long compressedSize) {
        return Mono.from(underlying.save(bucketName, blobId, byteSourceBlobWithSize(compressContent.asByteSource(), compressedSize, withCompressionMetadata(metadata, originalSize))))
            .doOnSuccess(ignored -> {
                compressSaveCount.increment();
                savedBytes.add(Math.toIntExact(originalSize - compressedSize));
            });
    }

    private Mono<Void> saveOriginal(BucketName bucketName, BlobId blobId, FileBackedOutputStream originalContent,
                                    long originalSize, BlobMetadata metadata) {
        return Mono.from(underlying.save(bucketName, blobId, byteSourceBlobWithSize(originalContent.asByteSource(), originalSize, metadata)))
            .doOnSuccess(ignored -> ratioSkipCount.increment());
    }

    private byte[] compress(byte[] data) {
        TimeMetric timeMetric = metricFactory.timer(BLOB_ZSTD_COMPRESS_LATENCY_METRIC_NAME);
        try {
            return Zstd.compress(data);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private long compress(InputStream inputStream, FileBackedOutputStream compressedContent) throws IOException {
        TimeMetric timeMetric = metricFactory.timer(BLOB_ZSTD_COMPRESS_LATENCY_METRIC_NAME);

        try (CountingOutputStream countingOutputStream = new CountingOutputStream(compressedContent);
             ZstdOutputStream zstdOutputStream = new ZstdOutputStream(countingOutputStream)) {
            inputStream.transferTo(zstdOutputStream);
            zstdOutputStream.close();
            return countingOutputStream.getCount();
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private boolean shouldAttemptCompression(long originalSize) {
        return compressionConfiguration.enabled()
            && compressionConfiguration.minRatio() > 0
            && originalSize >= compressionConfiguration.threshold();
    }

    private void recordThresholdSkipIfNeeded(long originalSize) {
        if (compressionConfiguration.enabled()
            && compressionConfiguration.minRatio() > 0
            && originalSize < compressionConfiguration.threshold()) {
            thresholdSkipCount.increment();
        }
    }

    private CompressionDecision compressionDecision(long originalSize, long compressedSize) {
        return new CompressionDecision(originalSize, compressedSize);
    }

    private boolean isCompressed(BlobMetadata metadata) {
        return metadata.contentTransferEncoding()
            .filter(ContentTransferEncoding.ZSTD::equals)
            .isPresent();
    }

    private BlobMetadata sanitizeMetadata(BlobMetadata metadata) {
        return new BlobMetadata(metadata.underlyingMap()
            .entrySet()
            .stream()
            .filter(entry -> !RESERVED_METADATA_NAMES.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private BlobMetadata withCompressionMetadata(BlobMetadata metadata, long originalSize) {
        return metadata
            .withContentTransferEncoding(ContentTransferEncoding.ZSTD)
            .withMetadata(CONTENT_ORIGINAL_SIZE, new BlobMetadataValue(String.valueOf(originalSize)));
    }

    private ByteSourceBlob byteSourceBlobWithSize(ByteSource byteSource, long size, BlobMetadata metadata) {
        return ByteSourceBlob.of(new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return byteSource.openStream();
            }

            @Override
            public com.google.common.base.Optional<Long> sizeIfKnown() {
                return com.google.common.base.Optional.of(size);
            }

            @Override
            public long size() {
                return size;
            }
        }, metadata);
    }

}
