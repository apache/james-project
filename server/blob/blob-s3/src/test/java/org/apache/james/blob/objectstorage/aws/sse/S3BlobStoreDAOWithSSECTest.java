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

package org.apache.james.blob.objectstorage.aws.sse;

import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.apache.james.blob.objectstorage.aws.JamesS3MetricPublisher.DEFAULT_S3_METRICS_PREFIX;
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.blob.objectstorage.aws.JamesS3MetricPublisher;
import org.apache.james.blob.objectstorage.aws.Region;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.blob.objectstorage.aws.S3ClientFactory;
import org.apache.james.blob.objectstorage.aws.S3MinioExtension;
import org.apache.james.blob.objectstorage.aws.S3RequestOption;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.s3.S3AsyncClient;

public class S3BlobStoreDAOWithSSECTest implements BlobStoreDAOContract, S3SSECContract {

    @RegisterExtension
    static S3MinioExtension minoExtension = new S3MinioExtension();

    private static S3BlobStoreDAO testee;
    private static S3ClientFactory s3ClientFactory;

    @BeforeAll
    static void setUp() throws Exception {
        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(minoExtension.minioDocker().getAwsS3AuthConfiguration())
            .region(Region.of(software.amazon.awssdk.regions.Region.EU_WEST_1.id()))
            .uploadRetrySpec(Optional.of(Retry.backoff(3, java.time.Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .build();

        s3ClientFactory = new S3ClientFactory(s3Configuration, () -> new JamesS3MetricPublisher(new RecordingMetricFactory(), new NoopGaugeRegistry(),
            DEFAULT_S3_METRICS_PREFIX));

        S3SSECustomerKeyFactory sseCustomerKeyFactory = new S3SSECustomerKeyFactory.SingleCustomerKeyFactory(new S3SSECConfiguration.Basic("AES256", "masterPassword", "salt"));

        S3RequestOption s3RequestOption = new S3RequestOption(new S3RequestOption.SSEC(true, Optional.of(sseCustomerKeyFactory)));
        testee = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, new TestBlobId.Factory(), s3RequestOption);
    }

//    @BeforeEach
//    void beforeEach() throws Exception {
//        // Why? https://github.com/apache/james-project/pull/1981#issuecomment-2380396460
//        s3ClientFactory.get().createBucket(builder -> builder.bucket(TEST_BUCKET_NAME.asString()))
//            .get();
//    }

    @Override
    public BlobStoreDAO testee() {
        return testee;
    }

    @Override
    public S3AsyncClient s3Client() {
        return s3ClientFactory.get();
    }

    private void deleteBucket(String bucketName) {
        try {
            s3ClientFactory.get().deleteBucket(builder -> builder.bucket(bucketName))
                .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error while deleting bucket", e);
        }
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
