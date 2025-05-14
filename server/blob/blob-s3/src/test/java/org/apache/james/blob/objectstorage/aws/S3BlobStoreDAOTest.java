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
import java.util.stream.IntStream;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.BucketName;
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
public class S3BlobStoreDAOTest implements BlobStoreDAOContract {
    private static final BucketName fallbackBucket = BucketName.of("fallback");

    private static S3BlobStoreDAO testee;
    private static S3ClientFactory s3ClientFactory;

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
    void listingManyBlobsShouldSucceedWhenExceedingPageSize() {
        BlobStoreDAO store = testee();

        final int count = 1500;
        Flux.range(0, count)
            .concatMap(i -> store.save(TEST_BUCKET_NAME, new TestBlobId("test-blob-id-" + i),
                ByteSource.wrap(ELEVEN_KILOBYTES)))
            .blockLast();

        assertThat(Flux.from(testee().listBlobs(TEST_BUCKET_NAME)).count().block())
            .isEqualTo(count);
    }

    @Test
    void readShouldNotLeakHttpConnexionsForUnclosedStreams() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(ELEVEN_KILOBYTES))).block();

        assertThatCode(() -> IntStream.range(0, 256)
            .forEach(i -> {
                InputStream inputStream = store.read(TEST_BUCKET_NAME, blobId);
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
        Mono.from(store.save(fallbackBucket, blobId, ByteSource.wrap(ELEVEN_KILOBYTES))).block();

        InputStream read = store.read(BucketName.DEFAULT, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    void readReactiveShouldFallbackToDefinedBucketWhenFailingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(fallbackBucket, blobId, ByteSource.wrap(ELEVEN_KILOBYTES))).block();

        InputStream read = Mono.from(store.readReactive(BucketName.DEFAULT, blobId)).block();

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    void readBytesShouldFallbackToDefinedBucketWhenFailingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(fallbackBucket, blobId, ByteSource.wrap(ELEVEN_KILOBYTES))).block();

        byte[] bytes = Mono.from(store.readBytes(BucketName.DEFAULT, blobId)).block();

        assertThat(bytes).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    void shouldNotReadOnFallbackBucketWhenNotReadingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(ELEVEN_KILOBYTES))).block();

        assertThatThrownBy(() -> store.read(BucketName.DEFAULT, blobId))
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void shouldNotReadReactiveOnFallbackBucketWhenNotReadingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(ELEVEN_KILOBYTES))).block();

        assertThatThrownBy(() -> Mono.from(store.readReactive(BucketName.DEFAULT, blobId)).block())
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void shouldNotReadBytesOnFallbackBucketWhenNotReadingOnDefaultOne() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(ELEVEN_KILOBYTES))).block();

        assertThatThrownBy(() -> Mono.from(store.readBytes(BucketName.DEFAULT, blobId)).block())
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }
}
