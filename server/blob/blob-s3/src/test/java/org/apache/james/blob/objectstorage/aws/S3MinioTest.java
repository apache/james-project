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
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3MinioTest implements BlobStoreDAOContract {

    @RegisterExtension
    static S3MinioExtension minoExtension = new S3MinioExtension();

    private static S3BlobStoreDAO testee;
    private static S3ClientFactory s3ClientFactory;

    @BeforeAll
    static void setUp() {
        AwsS3AuthConfiguration awsS3AuthConfiguration = minoExtension.minioDocker().getAwsS3AuthConfiguration();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(awsS3AuthConfiguration)
            .region(DockerAwsS3Container.REGION)
            .uploadRetrySpec(Optional.of(Retry.backoff(3, java.time.Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .build();

        s3ClientFactory = new S3ClientFactory(s3Configuration, new RecordingMetricFactory(), new NoopGaugeRegistry());
        testee = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, new TestBlobId.Factory(), S3RequestOption.DEFAULT);
    }

    @AfterAll
    static void tearDownClass() {
        s3ClientFactory.close();
    }

    @BeforeEach
    void beforeEach() throws Exception {
        // Why? https://github.com/apache/james-project/pull/1981#issuecomment-2380396460
        createBucket(TEST_BUCKET_NAME.asString());
    }

    private void createBucket(String bucketName) throws Exception {
        s3ClientFactory.get().createBucket(builder -> builder.bucket(bucketName))
            .get();
    }

    private void deleteBucket(String bucketName) {
        try {
            s3ClientFactory.get().deleteBucket(builder -> builder.bucket(bucketName))
                .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error while deleting bucket", e);
        }
    }

    @Override
    public BlobStoreDAO testee() {
        return testee;
    }

    @Test
    void saveWillThrowWhenBlobIdHasSlashCharacters() {
        BlobId invalidBlobId = new TestBlobId("test-blob//id");
        assertThatThrownBy(() -> Mono.from(testee.save(TEST_BUCKET_NAME, invalidBlobId, SHORT_BYTEARRAY)).block())
            .isInstanceOf(S3Exception.class)
            .hasMessageContaining("Object name contains unsupported characters");
    }

    @Test
    void saveShouldWorkWhenValidBlobId() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block()).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    @Override
    public void listBucketsShouldReturnEmptyWhenNone() {
        deleteBucket(TEST_BUCKET_NAME.asString());

        BlobStoreDAO store = testee();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .isEmpty();
    }

    @Test
    @Override
    @Disabled("S3minio return `Connection: close` in header response, https://github.com/apache/james-project/pull/1981#issuecomment-2380396460")
    public void deleteBucketConcurrentlyShouldNotFail() {
    }
}
