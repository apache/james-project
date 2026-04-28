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

import static org.apache.james.blob.api.BlobStoreDAOFixture.ELEVEN_KILOBYTES;
import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.apache.james.blob.objectstorage.aws.JamesS3MetricPublisher.DEFAULT_S3_METRICS_PREFIX;
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.MetadataAwareBlobStoreDAOContract;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Extension;
import org.apache.james.blob.objectstorage.aws.JamesS3MetricPublisher;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.blob.objectstorage.aws.S3ClientFactory;
import org.apache.james.blob.objectstorage.aws.S3RequestOption;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@ExtendWith(DockerAwsS3Extension.class)
class ZstdBlobStoreDAOTest implements BlobStoreDAOContract, MetadataAwareBlobStoreDAOContract {
    private static final CompressionConfiguration DEFAULT_COMPRESSION_CONFIGURATION = CompressionConfiguration.builder()
        .enabled(true)
        .build();
    private static final BucketName FALLBACK_BUCKET = BucketName.of("fallback");

    private static S3BlobStoreDAO underlying;
    private static S3ClientFactory s3ClientFactory;

    private RecordingMetricFactory metricFactory;
    private ZstdBlobStoreDAO testee;

    @BeforeAll
    static void setUp(DockerAwsS3Container dockerAwsS3) {
        AwsS3AuthConfiguration authConfiguration = AwsS3AuthConfiguration.builder()
            .endpoint(dockerAwsS3.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(authConfiguration)
            .region(dockerAwsS3.dockerAwsS3().region())
            .uploadRetrySpec(Optional.of(Retry.backoff(3, Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .defaultBucketName(BucketName.DEFAULT)
            .fallbackBucketName(Optional.of(FALLBACK_BUCKET))
            .build();

        s3ClientFactory = new S3ClientFactory(s3Configuration, () -> new JamesS3MetricPublisher(new RecordingMetricFactory(),
            new NoopGaugeRegistry(), DEFAULT_S3_METRICS_PREFIX));
        underlying = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, new TestBlobId.Factory(), S3RequestOption.DEFAULT);
    }

    @AfterAll
    static void tearDownClass() {
        if (s3ClientFactory != null) {
            s3ClientFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        metricFactory = new RecordingMetricFactory();
        testee = new ZstdBlobStoreDAO(underlying, DEFAULT_COMPRESSION_CONFIGURATION, metricFactory);
    }

    @AfterEach
    void tearDown() {
        if (underlying != null) {
            underlying.deleteAllBuckets().block();
        }
    }

    @Override
    public BlobStoreDAO testee() {
        return testee;
    }

    @Override
    @Test
    public void retrieveContentTransferEncodingShouldSucceed() {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build(),
            metricFactory);

        // should compress and append content-transfer-encoding metadata, when threshold is met.
        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        assertThat(Mono.from(localTestee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block().metadata().contentEncoding())
            .contains(BlobStoreDAO.ContentEncoding.ZSTD);
    }

    @Test
    void shouldPreserveCallerMetadataWhenCompressionHappens() {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build(),
            metricFactory);
        BlobStoreDAO.BlobMetadata metadata = BlobStoreDAO.BlobMetadata.empty()
            .withMetadata(new BlobStoreDAO.BlobMetadataName("name"), new BlobStoreDAO.BlobMetadataValue("value"))
            .withMetadata(new BlobStoreDAO.BlobMetadataName("type"), new BlobStoreDAO.BlobMetadataValue("attachment"));
        BlobStoreDAO.BytesBlob blob = BlobStoreDAO.BytesBlob.of(ELEVEN_KILOBYTES.payload(), metadata);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, blob)).block();

        BlobStoreDAO.BytesBlob readBlob = Mono.from(localTestee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertSoftly(softly -> {
            softly.assertThat(readBlob.metadata().underlyingMap())
                .containsEntry(new BlobStoreDAO.BlobMetadataName("name"), new BlobStoreDAO.BlobMetadataValue("value"))
                .containsEntry(new BlobStoreDAO.BlobMetadataName("type"), new BlobStoreDAO.BlobMetadataValue("attachment"))
                .containsEntry(BlobStoreDAO.ContentEncoding.NAME, BlobStoreDAO.ContentEncoding.ZSTD.asValue())
                .containsEntry(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE,
                    new BlobStoreDAO.BlobMetadataValue(String.valueOf(ELEVEN_KILOBYTES.payload().length)));
        });
    }

    @Test
    void readExistingNonCompressedBlobShouldSucceed() {
        Mono.from(underlying.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    void shouldNotCompressBlobIfThresholdIsNotMet() {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(16 * 1024)
                .build(),
            metricFactory);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        BlobStoreDAO.BytesBlob storedBlob = Mono.from(underlying.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertSoftly(softly -> {
            softly.assertThat(storedBlob.payload()).isEqualTo(SHORT_BYTEARRAY.payload());
            softly.assertThat(storedBlob.metadata().contentEncoding()).isEmpty();
            softly.assertThat(storedBlob.metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE)).isEmpty();
        });
    }

    @Test
    void readShouldDecompressCompressedBlob() throws IOException {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build(),
            metricFactory);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        BlobStoreDAO.InputStreamBlob readBlob = localTestee.read(TEST_BUCKET_NAME, TEST_BLOB_ID);
        byte[] payload = readBlob.payload().readAllBytes();

        assertSoftly(softly -> {
            softly.assertThat(payload).isEqualTo(ELEVEN_KILOBYTES.payload());
            softly.assertThat(readBlob.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
            softly.assertThat(readBlob.metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE))
                .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(ELEVEN_KILOBYTES.payload().length)));
        });
    }

    @Test
    void readReactiveShouldDecompressCompressedBlob() throws IOException {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build(),
            metricFactory);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        BlobStoreDAO.InputStreamBlob readBlob = Mono.from(localTestee.readReactive(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();
        byte[] payload = readBlob.payload().readAllBytes();

        assertSoftly(softly -> {
            softly.assertThat(payload).isEqualTo(ELEVEN_KILOBYTES.payload());
            softly.assertThat(readBlob.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
            softly.assertThat(readBlob.metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE))
                .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(ELEVEN_KILOBYTES.payload().length)));
        });
    }

    @Test
    void shouldNotCompressBlobWhenMinRatioIsZero() {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .minRatio(0F)
                .build(),
            metricFactory);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        BlobStoreDAO.BytesBlob storedBlob = Mono.from(underlying.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertSoftly(softly -> {
            softly.assertThat(storedBlob.payload()).isEqualTo(ELEVEN_KILOBYTES.payload());
            softly.assertThat(storedBlob.metadata().contentEncoding()).isEmpty();
            softly.assertThat(storedBlob.metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE)).isEmpty();
        });
    }

    @Test
    void shouldStillDecompressBlobWhenMinRatioIsZero() {
        ZstdBlobStoreDAO compressingTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build(),
            metricFactory);
        ZstdBlobStoreDAO uncompressingOnlyTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .minRatio(0F)
                .build(),
            metricFactory);

        Mono.from(compressingTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        BlobStoreDAO.BytesBlob readBlob = Mono.from(uncompressingOnlyTestee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertSoftly(softly -> {
            softly.assertThat(readBlob.payload()).isEqualTo(ELEVEN_KILOBYTES.payload());
            softly.assertThat(readBlob.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
            softly.assertThat(readBlob.metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE))
                .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(ELEVEN_KILOBYTES.payload().length)));
        });
    }

    @Test
    void shouldNotCompressBlobWhenMinRatioIsNotMet() {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .minRatio(0.5F)
                .build(),
            metricFactory);
        byte[] randomPayload = new byte[4096];
        new Random(1).nextBytes(randomPayload);
        BlobStoreDAO.BytesBlob randomBlob = BlobStoreDAO.BytesBlob.of(randomPayload);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, randomBlob)).block();

        BlobStoreDAO.BytesBlob storedBlob = Mono.from(underlying.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertSoftly(softly -> {
            softly.assertThat(storedBlob.payload()).isEqualTo(randomPayload);
            softly.assertThat(storedBlob.metadata().contentEncoding()).isEmpty();
            softly.assertThat(storedBlob.metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE)).isEmpty();
        });
    }

    @Test
    void shouldRecordMetrics() {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build(),
            metricFactory);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();
        Mono.from(localTestee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();
        BlobStoreDAO.BytesBlob storedBlob = Mono.from(underlying.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertSoftly(softly -> {
            softly.assertThat(metricFactory.countFor(ZstdBlobStoreDAO.MetricRecorder.BLOB_ZSTD_COMPRESS_SAVE_COUNT_METRIC_NAME)).isEqualTo(1);
            softly.assertThat(metricFactory.countFor(ZstdBlobStoreDAO.MetricRecorder.BLOB_ZSTD_DECOMPRESS_COUNT_METRIC_NAME)).isEqualTo(1);
            softly.assertThat(metricFactory.countFor(ZstdBlobStoreDAO.MetricRecorder.BLOB_ZSTD_SAVED_BYTES_METRIC_NAME))
                .isEqualTo(ELEVEN_KILOBYTES.payload().length - storedBlob.payload().length);
            softly.assertThat(metricFactory.executionTimesFor(ZstdBlobStoreDAO.MetricRecorder.BLOB_ZSTD_COMPRESS_LATENCY_METRIC_NAME)).hasSize(1);
            softly.assertThat(metricFactory.executionTimesFor(ZstdBlobStoreDAO.MetricRecorder.BLOB_ZSTD_DECOMPRESS_LATENCY_METRIC_NAME)).hasSize(1);
        });
    }

    @Test
    void shouldRecordThresholdSkipMetricIfThresholdNotMatch() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(metricFactory.countFor(ZstdBlobStoreDAO.MetricRecorder.BLOB_ZSTD_THRESHOLD_SKIP_COUNT_METRIC_NAME))
            .isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("blobsWithReservedCompressionMetadata")
    void saveShouldRejectReservedCompressionMetadata(BlobStoreDAO.Blob blob) {
        assertThatThrownBy(() -> Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, blob)).block())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Reserved zstd metadata are not allowed");
    }

    @ParameterizedTest
    @MethodSource("compressionSamples")
    void readBytesShouldRoundTripCompressedResourcesWithoutCorruption(String resourcePath) throws IOException {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build(),
            metricFactory);
        byte[] resourceBytes = readResource(resourcePath);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, BlobStoreDAO.BytesBlob.of(resourceBytes))).block();

        BlobStoreDAO.BytesBlob readBlob = Mono.from(localTestee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertSoftly(softly -> {
            softly.assertThat(readBlob.payload()).isEqualTo(resourceBytes);
            softly.assertThat(readBlob.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
            softly.assertThat(readBlob.metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE))
                .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(resourceBytes.length)));
        });
    }

    @ParameterizedTest
    @MethodSource("compressionSamples")
    void readShouldRoundTripCompressedResourcesWithoutCorruption(String resourcePath) throws IOException {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build(),
            metricFactory);
        byte[] resourceBytes = readResource(resourcePath);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, BlobStoreDAO.BytesBlob.of(resourceBytes))).block();

        BlobStoreDAO.InputStreamBlob readBlob = localTestee.read(TEST_BUCKET_NAME, TEST_BLOB_ID);
        byte[] payload = readBlob.payload().readAllBytes();

        assertSoftly(softly -> {
            softly.assertThat(payload).isEqualTo(resourceBytes);
            softly.assertThat(readBlob.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
            softly.assertThat(readBlob.metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE))
                .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(resourceBytes.length)));
        });
    }

    @ParameterizedTest
    @MethodSource("compressionSamples")
    void readReactiveShouldRoundTripCompressedResourcesWithoutCorruption(String resourcePath) throws IOException {
        ZstdBlobStoreDAO localTestee = new ZstdBlobStoreDAO(underlying,
            CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build(),
            metricFactory);
        byte[] resourceBytes = readResource(resourcePath);

        Mono.from(localTestee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, BlobStoreDAO.BytesBlob.of(resourceBytes))).block();

        BlobStoreDAO.InputStreamBlob readBlob = Mono.from(localTestee.readReactive(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();
        byte[] payload = readBlob.payload().readAllBytes();

        assertSoftly(softly -> {
            softly.assertThat(payload).isEqualTo(resourceBytes);
            softly.assertThat(readBlob.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
            softly.assertThat(readBlob.metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE))
                .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(resourceBytes.length)));
        });
    }

    private static Stream<Arguments> compressionSamples() {
        return Stream.of(
            Arguments.of("zstd/text.txt"),
            Arguments.of("zstd/james-logo.jpg"),
            Arguments.of("zstd/mail1.eml"),
            Arguments.of("zstd/document.pdf"));
    }

    private static Stream<Arguments> blobsWithReservedCompressionMetadata() {
        BlobStoreDAO.BlobMetadata reservedMetadata = BlobStoreDAO.BlobMetadata.empty()
            .withMetadata(BlobStoreDAO.ContentEncoding.NAME, BlobStoreDAO.ContentEncoding.ZSTD.asValue());

        return Stream.of(
            Arguments.of(BlobStoreDAO.BytesBlob.of(ELEVEN_KILOBYTES.payload(), reservedMetadata)),
            Arguments.of(BlobStoreDAO.InputStreamBlob.of(new ByteArrayInputStream(ELEVEN_KILOBYTES.payload()), reservedMetadata)),
            Arguments.of(BlobStoreDAO.ByteSourceBlob.of(ByteSource.wrap(ELEVEN_KILOBYTES.payload()), reservedMetadata)));
    }

    private byte[] readResource(String resourcePath) throws IOException {
        try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).describedAs("resource %s should exist", resourcePath).isNotNull();
            return inputStream.readAllBytes();
        }
    }
}
