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

import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TWELVE_MEGABYTES;
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.Optional;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

class S3MinioIfNoneMatchTest {
    private static final BlobStoreDAO.BytesBlob OTHER_CONTENT = BlobStoreDAO.BytesBlob.of("other content");
    private static final BucketName CUSTOM_BUCKET = BucketName.of("custom-if-none-match");

    @RegisterExtension
    static S3MinioExtension minioExtension = new S3MinioExtension();

    private static S3BlobStoreDAO testee;
    private static S3ClientFactory s3ClientFactory;

    @BeforeAll
    static void setUp() {
        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(minioExtension.minioDocker().getAwsS3AuthConfiguration())
            .region(DockerAwsS3Container.REGION)
            .uploadRetrySpec(Optional.of(Retry.backoff(3, Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .build();

        s3ClientFactory = new S3ClientFactory(s3Configuration, new RecordingMetricFactory(), new NoopGaugeRegistry());
        testee = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, new TestBlobId.Factory(),
            new S3RequestOption(S3RequestOption.DEFAULT.ssec(), true));
    }

    @AfterAll
    static void tearDownClass() {
        s3ClientFactory.close();
    }

    @BeforeEach
    void beforeEach() throws Exception {
        // Why? https://github.com/apache/james-project/pull/1981#issuecomment-2380396460
        s3ClientFactory.get().createBucket(builder -> builder.bucket(TEST_BUCKET_NAME.asString())).get();
    }

    @Test
    void saveShouldSucceedWhenTheBlobIsNotStoredYet() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block()).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    void saveShouldSucceedWhenTheBlobIsAlreadyStored() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThatCode(() -> Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block())
            .doesNotThrowAnyException();
    }

    @Test
    void saveShouldNotOverwriteAnAlreadyStoredBlob() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, OTHER_CONTENT)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block()).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    void saveShouldNotOverwriteAnAlreadyStoredBlobWhenInputStream() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY.asInputStream())).block();
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, OTHER_CONTENT.asInputStream())).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block()).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    void saveShouldNotOverwriteAnAlreadyStoredBlobWhenByteSource() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY.asByteSource())).block();
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, OTHER_CONTENT.asByteSource())).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block()).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    void saveShouldNotOverwriteAnAlreadyStoredBigBlob() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES)).block();
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, OTHER_CONTENT)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block()).isEqualTo(TWELVE_MEGABYTES);
    }

    @Test
    void concurrentSavesOfTheSameBlobShouldAllSucceed() {
        assertThatCode(() -> Flux.range(0, 10)
            .flatMap(i -> testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY))
            .then()
            .block())
            .doesNotThrowAnyException();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block()).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    void saveShouldCreateTheBucketWhenMissing() {
        TestBlobId blobId = new TestBlobId("id");

        Mono.from(testee.save(CUSTOM_BUCKET, blobId, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(testee.readBytes(CUSTOM_BUCKET, blobId)).block()).isEqualTo(SHORT_BYTEARRAY);
    }
}
