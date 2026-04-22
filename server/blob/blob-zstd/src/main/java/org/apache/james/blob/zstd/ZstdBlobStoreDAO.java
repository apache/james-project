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
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
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
        boolean satisfyCompressionMinRatio(float minCompressionRatio) {
            // minCompressionRatio == 0 means decompress-only mode: never compress on save
            if (minCompressionRatio == 0) {
                return false;
            }

            return ((float) compressedSize / originalSize) <= minCompressionRatio;
        }
    }

    static final class MetricRecorder {
        static final String BLOB_ZSTD_COMPRESS_SAVE_COUNT_METRIC_NAME = "blobZstdCompressSaveCount";
        static final String BLOB_ZSTD_THRESHOLD_SKIP_COUNT_METRIC_NAME = "blobZstdThresholdSkipCount";
        static final String BLOB_ZSTD_RATIO_SKIP_COUNT_METRIC_NAME = "blobZstdRatioSkipCount";
        static final String BLOB_ZSTD_DECOMPRESS_COUNT_METRIC_NAME = "blobZstdDecompressCount";
        static final String BLOB_ZSTD_SAVED_BYTES_METRIC_NAME = "blobZstdSavedBytes";
        static final String BLOB_ZSTD_COMPRESS_LATENCY_METRIC_NAME = "blobZstdCompressLatency";
        static final String BLOB_ZSTD_DECOMPRESS_LATENCY_METRIC_NAME = "blobZstdDecompressLatency";

        private final MetricFactory metricFactory;
        private final Metric compressSaveCount;
        private final Metric thresholdSkipCount;
        private final Metric ratioSkipCount;
        private final Metric decompressCount;
        private final Metric savedBytes;

        private MetricRecorder(MetricFactory metricFactory) {
            this.metricFactory = metricFactory;
            this.compressSaveCount = metricFactory.generate(BLOB_ZSTD_COMPRESS_SAVE_COUNT_METRIC_NAME);
            this.thresholdSkipCount = metricFactory.generate(BLOB_ZSTD_THRESHOLD_SKIP_COUNT_METRIC_NAME);
            this.ratioSkipCount = metricFactory.generate(BLOB_ZSTD_RATIO_SKIP_COUNT_METRIC_NAME);
            this.decompressCount = metricFactory.generate(BLOB_ZSTD_DECOMPRESS_COUNT_METRIC_NAME);
            this.savedBytes = metricFactory.generate(BLOB_ZSTD_SAVED_BYTES_METRIC_NAME);
        }

        TimeMetric startCompressionLatencyTimer() {
            return metricFactory.timer(BLOB_ZSTD_COMPRESS_LATENCY_METRIC_NAME);
        }

        TimeMetric startDecompressionLatencyTimer() {
            return metricFactory.timer(BLOB_ZSTD_DECOMPRESS_LATENCY_METRIC_NAME);
        }

        void recordCompressedSave(long originalSize, long compressedSize) {
            compressSaveCount.increment();
            savedBytes.add(Math.toIntExact(originalSize - compressedSize));
        }

        void recordThresholdSkip() {
            thresholdSkipCount.increment();
        }

        void recordRatioSkip() {
            ratioSkipCount.increment();
        }

        void recordDecompression() {
            decompressCount.increment();
        }
    }

    public static final BlobMetadataName CONTENT_ORIGINAL_SIZE = new BlobMetadataName("content-original-size");
    private static final int FILE_THRESHOLD = 100 * 1024;
    private static final Set<BlobMetadataName> RESERVED_METADATA_NAMES = Set.of(ContentTransferEncoding.NAME, CONTENT_ORIGINAL_SIZE);

    private final BlobStoreDAO underlying;
    private final CompressionConfiguration compressionConfiguration;
    private final MetricRecorder metricRecorder;

    public ZstdBlobStoreDAO(BlobStoreDAO underlying, CompressionConfiguration compressionConfiguration, MetricFactory metricFactory) {
        this.underlying = underlying;
        this.compressionConfiguration = compressionConfiguration;
        this.metricRecorder = new MetricRecorder(metricFactory);
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
            case BytesBlob bytesBlob -> save(bucketName, blobId, bytesBlob);
            case InputStreamBlob inputStreamBlob -> save(bucketName, blobId, inputStreamBlob);
            case ByteSourceBlob byteSourceBlob -> save(bucketName, blobId, byteSourceBlob);
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
            metricRecorder.recordDecompression();
            return InputStreamBlob.of(new ZstdInputStream(blob.payload()), blob.metadata());
        } catch (IOException e) {
            throw new ObjectStoreIOException("Failed to initialize zstd decompression", e);
        }
    }

    private Mono<InputStreamBlob> decompressReactive(InputStreamBlob blob) {
        return Mono.fromCallable(() -> InputStreamBlob.of(new ZstdInputStream(blob.payload()), blob.metadata()))
            .doOnNext(ignored -> metricRecorder.recordDecompression())
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Failed to initialize zstd decompression", e));
    }

    private Mono<BytesBlob> decompressBytesReactive(BytesBlob blob) {
        return Mono.fromCallable(() -> decompress(blob))
            .subscribeOn(Schedulers.parallel())
            .doOnNext(ignored -> metricRecorder.recordDecompression())
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Failed to decompress blob", e));
    }

    private BytesBlob decompress(BytesBlob blob) throws IOException {
        TimeMetric timeMetric = metricRecorder.startDecompressionLatencyTimer();
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

    private Publisher<Void> save(BucketName bucketName, BlobId blobId, BytesBlob bytesBlob) {
        validateMetadata(bytesBlob.metadata());

        if (shouldAttemptCompression(bytesBlob.payload().length)) {
            return Mono.fromCallable(() -> compress(bytesBlob.payload()))
                .subscribeOn(Schedulers.parallel())
                .flatMap(compressed -> saveCompressedIfWorthKeeping(bucketName, blobId,
                    BytesBlob.of(compressed, withCompressionMetadata(bytesBlob.metadata(), bytesBlob.payload().length)),
                    bytesBlob))
                .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob " + blobId.asString(), e));
        }

        return Mono.from(underlying.save(bucketName, blobId, bytesBlob))
            .doOnSuccess(ignored -> {
                if (compressionEnabled() && bytesBlob.payload().length < compressionConfiguration.threshold()) {
                    metricRecorder.recordThresholdSkip();
                }
            });
    }

    private Mono<Void> saveCompressedIfWorthKeeping(BucketName bucketName, BlobId blobId, BytesBlob compressedBlob,
                                                    BytesBlob uncompressedBlob) {
        CompressionDecision compressionDecision = compressionDecision(uncompressedBlob.payload().length, compressedBlob.payload().length);
        if (compressionDecision.satisfyCompressionMinRatio(compressionConfiguration.minRatio())) {
            return Mono.from(underlying.save(bucketName, blobId, compressedBlob))
                .doOnSuccess(ignored -> metricRecorder.recordCompressedSave(uncompressedBlob.payload().length, compressedBlob.payload().length));
        }
        return Mono.from(underlying.save(bucketName, blobId, uncompressedBlob))
            .doOnSuccess(ignored -> metricRecorder.recordRatioSkip());
    }

    private Publisher<Void> save(BucketName bucketName, BlobId blobId, InputStreamBlob inputStreamBlob) {
        validateMetadata(inputStreamBlob.metadata());

        if (!compressionEnabled()) {
            return Mono.from(underlying.save(bucketName, blobId, inputStreamBlob));
        }

        return Mono.usingWhen(Mono.fromCallable(() -> new FileBackedOutputStream(FILE_THRESHOLD)),
                originalContent -> Mono.fromCallable(() -> inputStreamBlob.payload().transferTo(originalContent))
                    .flatMap(originalSize -> {
                        if (originalSize >= compressionConfiguration.threshold()) {
                            return compressAndSave(bucketName, blobId, originalContent, originalSize, inputStreamBlob.metadata());
                        }

                        return Mono.from(underlying.save(bucketName, blobId, byteSourceBlobWithSize(originalContent.asByteSource(), originalSize, inputStreamBlob.metadata())))
                            .doOnSuccess(ignored -> metricRecorder.recordThresholdSkip());
                    }),
                originalContent -> Mono.fromRunnable(Throwing.runnable(originalContent::reset)).subscribeOn(Schedulers.boundedElastic()))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob " + blobId.asString(), e));
    }

    private Publisher<Void> save(BucketName bucketName, BlobId blobId, ByteSourceBlob byteSourceBlob) {
        validateMetadata(byteSourceBlob.metadata());

        if (!compressionEnabled()) {
            return Mono.from(underlying.save(bucketName, blobId, byteSourceBlob));
        }

        return Mono.fromCallable(() -> resolveSize(byteSourceBlob.payload()))
            .flatMap(originalSize -> {
                if (originalSize < compressionConfiguration.threshold()) {
                    return Mono.from(underlying.save(bucketName, blobId, byteSourceBlobWithSize(byteSourceBlob.payload(), originalSize, byteSourceBlob.metadata())))
                        .doOnSuccess(ignored -> metricRecorder.recordThresholdSkip());
                }

                return compressAndSave(bucketName, blobId, byteSourceBlob, originalSize);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob " + blobId.asString(), e));
    }

    private Mono<Void> compressAndSave(BucketName bucketName, BlobId blobId, FileBackedOutputStream originalContent,
                                       long originalSize, BlobMetadata metadata) {
        return compressAndSave(bucketName, blobId, originalSize, metadata,
            compressContent -> prepareCompressedContent(originalContent, originalSize, compressContent),
            () -> Mono.from(underlying.save(bucketName, blobId, byteSourceBlobWithSize(originalContent.asByteSource(), originalSize, metadata)))
                .doOnSuccess(ignored -> metricRecorder.recordRatioSkip()));
    }

    private Mono<Void> compressAndSave(BucketName bucketName, BlobId blobId, ByteSourceBlob byteSourceBlob, long originalSize) {
        return compressAndSave(bucketName, blobId, originalSize, byteSourceBlob.metadata(),
            compressContent -> prepareCompressedContent(byteSourceBlob.payload(), originalSize, compressContent),
            () -> Mono.from(underlying.save(bucketName, blobId, byteSourceBlobWithSize(byteSourceBlob.payload(), originalSize, byteSourceBlob.metadata())))
                .doOnSuccess(ignored -> metricRecorder.recordRatioSkip()));
    }

    private Mono<Void> compressAndSave(BucketName bucketName, BlobId blobId, long originalSize, BlobMetadata metadata,
                                       Function<FileBackedOutputStream, Mono<CompressionDecision>> prepareCompressedContent,
                                       Supplier<Mono<Void>> saveOriginal) {
        return Mono.usingWhen(Mono.fromCallable(() -> new FileBackedOutputStream(FILE_THRESHOLD)),
                compressContent -> prepareCompressedContent.apply(compressContent)
                    .flatMap(compressionDecision -> saveCompressedIfWorthKeeping(bucketName, blobId, originalSize, metadata,
                        compressContent, compressionDecision, saveOriginal)),
                compressContent -> Mono.fromRunnable(Throwing.runnable(compressContent::reset)).subscribeOn(Schedulers.boundedElastic()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> saveCompressedIfWorthKeeping(BucketName bucketName, BlobId blobId, long originalSize,
                                                    BlobMetadata metadata, FileBackedOutputStream compressedContent,
                                                    CompressionDecision compressionDecision, Supplier<Mono<Void>> saveOriginal) {
        if (compressionDecision.satisfyCompressionMinRatio(compressionConfiguration.minRatio())) {
            return Mono.from(underlying.save(bucketName, blobId,
                    byteSourceBlobWithSize(compressedContent.asByteSource(), compressionDecision.compressedSize(), withCompressionMetadata(metadata, originalSize))))
                .doOnSuccess(ignored -> metricRecorder.recordCompressedSave(originalSize, compressionDecision.compressedSize()));
        }

        return saveOriginal.get();
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

    private Mono<CompressionDecision> prepareCompressedContent(ByteSource byteSource, long originalSize,
                                                               FileBackedOutputStream compressContent) {
        return Mono.fromCallable(() -> {
            long compressedSize = compress(byteSource, compressContent);
            return compressionDecision(originalSize, compressedSize);
        });
    }

    private byte[] compress(byte[] data) {
        TimeMetric timeMetric = metricRecorder.startCompressionLatencyTimer();
        try {
            return Zstd.compress(data);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private long compress(InputStream inputStream, FileBackedOutputStream compressedContent) throws IOException {
        TimeMetric timeMetric = metricRecorder.startCompressionLatencyTimer();

        try (CountingOutputStream countingOutputStream = new CountingOutputStream(compressedContent);
             ZstdOutputStream zstdOutputStream = new ZstdOutputStream(countingOutputStream)) {
            inputStream.transferTo(zstdOutputStream);
            zstdOutputStream.close();
            return countingOutputStream.getCount();
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private long compress(ByteSource byteSource, FileBackedOutputStream compressedContent) throws IOException {
        TimeMetric timeMetric = metricRecorder.startCompressionLatencyTimer();

        try (CountingOutputStream countingOutputStream = new CountingOutputStream(compressedContent);
             ZstdOutputStream zstdOutputStream = new ZstdOutputStream(countingOutputStream)) {
            byteSource.copyTo(zstdOutputStream);
            zstdOutputStream.close();
            return countingOutputStream.getCount();
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private boolean shouldAttemptCompression(long originalSize) {
        return compressionEnabled()
            && originalSize >= compressionConfiguration.threshold();
    }

    private boolean compressionEnabled() {
        return compressionConfiguration.enabled()
            && compressionConfiguration.minRatio() > 0; // minRatio == 0 means decompress-only mode: never compress on save
    }

    private CompressionDecision compressionDecision(long originalSize, long compressedSize) {
        return new CompressionDecision(originalSize, compressedSize);
    }

    private boolean isCompressed(BlobMetadata metadata) {
        return metadata.contentTransferEncoding()
            .filter(ContentTransferEncoding.ZSTD::equals)
            .isPresent();
    }

    private long resolveSize(ByteSource byteSource) throws IOException {
        if (byteSource.sizeIfKnown().isPresent()) {
            return byteSource.sizeIfKnown().get();
        }

        return byteSource.size();
    }

    private void validateMetadata(BlobMetadata metadata) {
        Set<BlobMetadataName> reservedNames = metadata.underlyingMap()
            .keySet()
            .stream()
            .filter(RESERVED_METADATA_NAMES::contains)
            .collect(Collectors.toSet());

        if (!reservedNames.isEmpty()) {
            throw new IllegalArgumentException("Reserved zstd metadata are not allowed: " + reservedNames);
        }
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
