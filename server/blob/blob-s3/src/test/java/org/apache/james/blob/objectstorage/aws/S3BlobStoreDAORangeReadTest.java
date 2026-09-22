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

import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.apache.james.blob.objectstorage.aws.JamesS3MetricPublisher.DEFAULT_S3_METRICS_PREFIX;
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@ExtendWith(DockerAwsS3Extension.class)
class S3BlobStoreDAORangeReadTest {
    private static S3BlobStoreDAO testee;
    private static S3ClientFactory s3ClientFactory;

    private BlobId blobId;
    private byte[] content;

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
            .build();

        s3ClientFactory = new S3ClientFactory(s3Configuration, () -> new JamesS3MetricPublisher(new RecordingMetricFactory(), new NoopGaugeRegistry(),
            DEFAULT_S3_METRICS_PREFIX));

        testee = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, new TestBlobId.Factory(), S3RequestOption.DEFAULT);
    }

    @BeforeEach
    void init() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append(String.format("%010d", i)); // 100 * 10 = 1000 bytes
        }
        content = builder.toString().getBytes(StandardCharsets.UTF_8);
        blobId = new TestBlobId.Factory().of(java.util.UUID.randomUUID().toString());
        Mono.from(testee.save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.BytesBlob.of(content))).block();
    }

    @Test
    void readRangeFirstBytesShouldReturnFirstBytes() throws IOException {
        BlobStoreDAO.Blob slice = testee.readRange(TEST_BUCKET_NAME, blobId, 0, 99).block();

        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(content, 0, 100));
    }

    @Test
    void readRangeMidObjectShouldReturnMidBytes() throws IOException {
        BlobStoreDAO.Blob slice = testee.readRange(TEST_BUCKET_NAME, blobId, 200, 399).block();

        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(content, 200, 400));
    }

    @Test
    void readRangeLastBytesNegativeShouldReturnSuffix() throws IOException {
        BlobStoreDAO.Blob slice = testee.readRange(TEST_BUCKET_NAME, blobId, -100, -1).block();

        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(content, 900, 1000));
    }

    @Test
    void readRangeFullObjectShouldReturnAllBytes() throws IOException {
        BlobStoreDAO.Blob slice = testee.readRange(TEST_BUCKET_NAME, blobId, 0, 999).block();

        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.asBytes().payload()).isEqualTo(content);
    }

    @Test
    void crossCheckAgainstFullReadShouldMatch() throws IOException {
        BlobStoreDAO.Blob slice = testee.readRange(TEST_BUCKET_NAME, blobId, 150, 450).block();
        byte[] fullRead = Mono.from(testee.readBytes(TEST_BUCKET_NAME, blobId)).block().payload();

        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(fullRead, 150, 451));
    }
}
