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

import static org.apache.james.blob.objectstorage.aws.JamesS3MetricPublisher.DEFAULT_S3_METRICS_PREFIX;
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;

import java.time.Duration;
import java.util.Optional;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
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
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.util.retry.Retry;

@ExtendWith(DockerAwsS3Extension.class)
class ZstdBlobStoreDAOTest implements ZstdBlobStoreDAOContract {
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
        testee = new ZstdBlobStoreDAO(underlying, defaultCompressionConfiguration(), metricFactory);
    }

    @AfterEach
    void tearDown() {
        if (underlying != null) {
            underlying.deleteAllBuckets().block();
        }
    }

    @Override
    public BlobStoreDAO underlying() {
        return underlying;
    }

    @Override
    public RecordingMetricFactory metricFactory() {
        return metricFactory;
    }

    @Override
    public ZstdBlobStoreDAO testee() {
        return testee;
    }
}
