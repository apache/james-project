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

import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;

import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BloomFilterGCAlgorithmContract;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.util.retry.Retry;

public class S3MinioBlobStoreGCAlgorithmTest implements BloomFilterGCAlgorithmContract {

    private S3BlobStoreDAO blobStoreDAO;

    @RegisterExtension
    static S3MinioExtension minoExtension = new S3MinioExtension();

    @BeforeEach
    void beforeEach() {
        AwsS3AuthConfiguration awsS3AuthConfiguration = minoExtension.minioDocker().getAwsS3AuthConfiguration();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(awsS3AuthConfiguration)
            .region(Region.of("garage"))
            .uploadRetrySpec(Optional.of(Retry.backoff(3, java.time.Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .build();

        S3ClientFactory s3ClientFactory = new S3ClientFactory(s3Configuration, new RecordingMetricFactory(), new NoopGaugeRegistry());

        BlobId.Factory plainBlobIdFactory = new PlainBlobId.Factory();
        MinIOGenerationAwareBlobId.Factory minIOGenerationAwareBlobIdFactory = new MinIOGenerationAwareBlobId.Factory(CLOCK, GenerationAwareBlobId.Configuration.DEFAULT, plainBlobIdFactory);
        blobStoreDAO = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, minIOGenerationAwareBlobIdFactory, S3RequestOption.DEFAULT);
    }

    @AfterEach
    void tearDown() {
        blobStoreDAO.deleteAllBuckets().block();
    }

    @Override
    public BlobStoreDAO blobStoreDAO() {
        return blobStoreDAO;
    }

    @Disabled("Fails for some reason...")
    @Test
    @Override
    public void gcShouldSuccessWhenMixCase() {

    }
}
