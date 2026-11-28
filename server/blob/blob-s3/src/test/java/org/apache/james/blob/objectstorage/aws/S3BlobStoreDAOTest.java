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
package org.apache.james.blob.objectstorage.aws;

import static org.apache.james.blob.api.BlobStoreDAOFixture.ELEVEN_KILOBYTES;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.apache.james.blob.objectstorage.aws.JamesS3MetricPublisher.DEFAULT_S3_METRICS_PREFIX;
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.MetadataAwareBlobStoreDAOContract;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@ExtendWith(DockerAwsS3Extension.class)
public class S3BlobStoreDAOTest implements BlobStoreDAOContract, MetadataAwareBlobStoreDAOContract {
    private static final BucketName fallbackBucket = BucketName.of("fallback");

    private static final long MULTIPART_THRESHOLD = 6L * 1024 * 1024;
    private static final long MULTIPART_PART_SIZE = 5L * 1024 * 1024;
    // 3 full parts and a smaller last one
    private static final byte[] MULTIPART_CONTENT = randomBytes(3 * (int) MULTIPART_PART_SIZE + 1234);

    private static S3BlobStoreDAO testee;
    private static S3BlobStoreDAO multipartTestee;
    private static S3ClientFactory s3ClientFactory;

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

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
            .uploadRetrySpec(Optional.of(Retry.backoff(3, java.time.Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .defaultBucketName(BucketName.DEFAULT)
            .fallbackBucketName(Optional.of(fallbackBucket))
            .build();

        s3ClientFactory = new S3ClientFactory(s3Configuration, () -> new JamesS3MetricPublisher(new RecordingMetricFactory(), new NoopGaugeRegistry(),
            DEFAULT_S3_METRICS_PREFIX));

        testee = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, new TestBlobId.Factory(), S3RequestOption.DEFAULT);

        S3BlobStoreConfiguration multipartConfiguration = S3BlobStoreConfiguration.builder()
            .authConfiguration(authConfiguration)
            .region(dockerAwsS3.dockerAwsS3().region())
            .uploadRetrySpec(Optional.of(Retry.backoff(3, java.time.Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .defaultBucketName(BucketName.DEFAULT)
            .multipartUploadThreshold(Optional.of(MULTIPART_THRESHOLD))
            .multipartUploadPartSize(Optional.of(MULTIPART_PART_SIZE))
            .build();
        multipartTestee = new S3BlobStoreDAO(s3ClientFactory, multipartConfiguration, new TestBlobId.Factory(), S3RequestOption.DEFAULT);
    }

    @AfterEach
    void tearDown() {
        testee.deleteAllBuckets().block();
    }

    @AfterAll
    static void tearDownClass() {
        s3ClientFactory.close();
    }

    @Override
    public BlobStoreDAO testee() {
        return testee;
    }

    @Test
    void chunkSizeShouldBePositiveForContentsLargerThanTwoGigabytes() {
        long sixGigabytes = 6L * 1024 * 1024 * 1024;

        assertThat(S3BlobStoreDAO.chunkSize(sixGigabytes)).isEqualTo(1024 * 100);
    }

    @Test
    void chunkSizeShouldBeContentLengthForSmallContents() {
        assertThat(S3BlobStoreDAO.chunkSize(12)).isEqualTo(12);
    }

    @Test
    void chunkSizeShouldBeZeroForEmptyContents() {
        assertThat(S3BlobStoreDAO.chunkSize(0)).isZero();
    }

    @Test
    void multipartUploadShouldStoreByteSourceContent() {
        TestBlobId blobId = new TestBlobId("multipart");

        Mono.from(multipartTestee.save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.ByteSourceBlob.of(ByteSource.wrap(MULTIPART_CONTENT)))).block();

        assertThat(Mono.from(multipartTestee.readBytes(TEST_BUCKET_NAME, blobId)).block().payload())
            .isEqualTo(MULTIPART_CONTENT);
    }

    @Test
    void multipartUploadShouldStoreInputStreamContent() {
        TestBlobId blobId = new TestBlobId("multipart");

        Mono.from(multipartTestee.save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.InputStreamBlob.of(new ByteArrayInputStream(MULTIPART_CONTENT)))).block();

        assertThat(multipartTestee.read(TEST_BUCKET_NAME, blobId).payload())
            .hasBinaryContent(MULTIPART_CONTENT);
    }

    @Test
    void multipartUploadShouldStoreBytesContent() {
        TestBlobId blobId = new TestBlobId("multipart");

        Mono.from(multipartTestee.save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.BytesBlob.of(MULTIPART_CONTENT))).block();

        assertThat(Mono.from(multipartTestee.readBytes(TEST_BUCKET_NAME, blobId)).block().payload())
            .isEqualTo(MULTIPART_CONTENT);
    }

    @Test
    void multipartUploadShouldStoreContentOfExactlyOnePart() {
        TestBlobId blobId = new TestBlobId("multipart");
        byte[] content = randomBytes((int) MULTIPART_THRESHOLD + 1);

        Mono.from(multipartTestee.save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.ByteSourceBlob.of(ByteSource.wrap(content)))).block();

        assertThat(Mono.from(multipartTestee.readBytes(TEST_BUCKET_NAME, blobId)).block().payload())
            .isEqualTo(content);
    }

    @Test
    void multipartUploadShouldPreserveMetadata() {
        TestBlobId blobId = new TestBlobId("multipart");
        BlobStoreDAO.BlobMetadata metadata = BlobStoreDAO.BlobMetadata.empty()
            .withMetadata(new BlobStoreDAO.BlobMetadataName("name"), new BlobStoreDAO.BlobMetadataValue("value"));

        Mono.from(multipartTestee.save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.ByteSourceBlob.of(ByteSource.wrap(MULTIPART_CONTENT), metadata))).block();

        assertThat(Mono.from(multipartTestee.readBytes(TEST_BUCKET_NAME, blobId)).block().metadata().underlyingMap())
            .containsAllEntriesOf(metadata.underlyingMap());
    }

    @Test
    void multipartUploadShouldOverwriteExistingBlob() {
        TestBlobId blobId = new TestBlobId("multipart");
        Mono.from(multipartTestee.save(TEST_BUCKET_NAME, blobId, ELEVEN_KILOBYTES)).block();

        Mono.from(multipartTestee.save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.ByteSourceBlob.of(ByteSource.wrap(MULTIPART_CONTENT)))).block();

        assertThat(Mono.from(multipartTestee.readBytes(TEST_BUCKET_NAME, blobId)).block().payload())
            .isEqualTo(MULTIPART_CONTENT);
    }

    @Test
    void failingMultipartUploadShouldBeAborted() {
        TestBlobId blobId = new TestBlobId("multipart");
        ByteSource failingContent = new ByteSource() {
            @Override
            public InputStream openStream() {
                return new InputStream() {
                    private long position = 0;

                    @Override
                    public int read() throws IOException {
                        if (position++ > MULTIPART_PART_SIZE + 1) {
                            throw new IOException("Simulated failure");
                        }
                        return 0;
                    }
                };
            }

            @Override
            public long size() {
                return MULTIPART_CONTENT.length;
            }
        };

        assertThatThrownBy(() -> Mono.from(multipartTestee.save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.ByteSourceBlob.of(failingContent))).block())
            .isInstanceOf(Exception.class);

        assertThat(s3ClientFactory.get().listMultipartUploads(builder -> builder.bucket(TEST_BUCKET_NAME.asString())).join().uploads())
            .isEmpty();
    }

    @Test
    void listingManyBlobsShouldSucceedWhenExceedingPageSize() {
        BlobStoreDAO store = testee();

        final int count = 1500;
        Flux.range(0, count)
            .concatMap(i -> store.save(TEST_BUCKET_NAME, new TestBlobId("test-blob-id-" + i), ELEVEN_KILOBYTES))
            .blockLast();

        assertThat(Flux.from(testee().listBlobs(TEST_BUCKET_NAME)).count().block())
            .isEqualTo(count);
    }

    @Test
    void readShouldNotLeakHttpConnexionsForUnclosedStreams() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(TEST_BUCKET_NAME, blobId, ELEVEN_KILOBYTES)).block();

        assertThatCode(() -> IntStream.range(0, 256)
            .forEach(i -> {
                InputStream inputStream = store.read(TEST_BUCKET_NAME, blobId).payload();
                // Close the stream without reading it
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })).doesNotThrowAnyException();
    }

    @Test
    void readShouldFallbackToDefinedBucketWhenFailingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(fallbackBucket, blobId, ELEVEN_KILOBYTES)).block();

        InputStream read = store.read(BucketName.DEFAULT, blobId).payload();

        assertThat(read).hasSameContentAs(ELEVEN_KILOBYTES.asInputStream().payload());
    }

    @Test
    void readReactiveShouldFallbackToDefinedBucketWhenFailingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(fallbackBucket, blobId, ELEVEN_KILOBYTES)).block();

        InputStream read = Mono.from(store.readReactive(BucketName.DEFAULT, blobId)).block().payload();

        assertThat(read).hasSameContentAs(ELEVEN_KILOBYTES.asInputStream().payload());
    }

    @Test
    void readBytesShouldFallbackToDefinedBucketWhenFailingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(fallbackBucket, blobId, ELEVEN_KILOBYTES)).block();

        BlobStoreDAO.BytesBlob bytes = Mono.from(store.readBytes(BucketName.DEFAULT, blobId)).block();

        assertThat(bytes).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    void fallbackBucketNameShouldNotBeAffectedByBucketPrefix(DockerAwsS3Container dockerAwsS3) {
        S3BlobStoreConfiguration prefixedConfiguration = S3BlobStoreConfiguration.builder()
            .authConfiguration(AwsS3AuthConfiguration.builder()
                .endpoint(dockerAwsS3.getEndpoint())
                .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
                .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
                .build())
            .region(dockerAwsS3.dockerAwsS3().region())
            .defaultBucketName(BucketName.DEFAULT)
            .bucketPrefix("prefix-")
            .fallbackBucketName(Optional.of(fallbackBucket))
            .build();
        S3BlobStoreDAO prefixedStore = new S3BlobStoreDAO(s3ClientFactory, prefixedConfiguration, new TestBlobId.Factory(), S3RequestOption.DEFAULT);

        TestBlobId blobId = new TestBlobId("id");
        // Save in the un-prefixed fallback bucket
        Mono.from(testee.save(fallbackBucket, blobId, ELEVEN_KILOBYTES)).block();

        InputStream read = prefixedStore.read(BucketName.DEFAULT, blobId).payload();

        assertThat(read).hasSameContentAs(ELEVEN_KILOBYTES.asInputStream().payload());
    }

    @Test
    void shouldNotReadOnFallbackBucketWhenNotReadingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(TEST_BUCKET_NAME, blobId, ELEVEN_KILOBYTES)).block();

        assertThatThrownBy(() -> store.read(BucketName.DEFAULT, blobId))
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void shouldNotReadReactiveOnFallbackBucketWhenNotReadingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(TEST_BUCKET_NAME, blobId, ELEVEN_KILOBYTES)).block();

        assertThatThrownBy(() -> Mono.from(store.readReactive(BucketName.DEFAULT, blobId)).block().payload())
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void shouldNotReadBytesOnFallbackBucketWhenNotReadingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(TEST_BUCKET_NAME, blobId, ELEVEN_KILOBYTES)).block();

        assertThatThrownBy(() -> Mono.from(store.readBytes(BucketName.DEFAULT, blobId)).block().payload())
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }
}
