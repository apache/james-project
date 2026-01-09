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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public class S3WithMinIOGenerationAwareBlobIdTest implements BlobStoreContract {
    static UpdatableTickingClock clock = new UpdatableTickingClock(Instant.parse("2021-08-19T10:15:30.00Z"));

    @RegisterExtension
    static S3MinioExtension minoExtension = new S3MinioExtension();

    private static BlobStore testee;
    private static S3ClientFactory s3ClientFactory;
    private S3BlobStoreDAO s3BlobStoreDAO;
    private BlobId.Factory blobIdFactory;

    @BeforeEach
    void beforeEach() throws Exception {
        blobIdFactory = new MinIOGenerationAwareBlobId.Factory(clock, GenerationAwareBlobId.Configuration.DEFAULT, new PlainBlobId.Factory());
        testee = createBlobStore(blobIdFactory);

        // Why? https://github.com/apache/james-project/pull/1981#issuecomment-2380396460
        createBucket(BucketName.DEFAULT.asString());
    }

    @AfterEach
    void tearDown() {
        s3BlobStoreDAO.deleteAllBuckets().block();
        s3ClientFactory.close();
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return blobIdFactory;
    }

    private void createBucket(String bucketName) throws Exception {
        s3ClientFactory.get().createBucket(builder -> builder.bucket(bucketName))
            .get();
    }

    public BlobStore createBlobStore(BlobId.Factory blobIdFactory) {
        AwsS3AuthConfiguration awsS3AuthConfiguration = minoExtension.minioDocker().getAwsS3AuthConfiguration();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(awsS3AuthConfiguration)
            .region(DockerAwsS3Container.REGION)
            .uploadRetrySpec(Optional.of(Retry.backoff(3, java.time.Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .build();

        s3ClientFactory = new S3ClientFactory(s3Configuration, new RecordingMetricFactory(), new NoopGaugeRegistry());
        s3BlobStoreDAO = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, blobIdFactory, S3RequestOption.DEFAULT);

        return BlobStoreFactory.builder()
            .blobStoreDAO(s3BlobStoreDAO)
            .blobIdFactory(blobIdFactory)
            .deduplication();
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    void saveShouldReturnBlobIdOfString(BlobStore.StoragePolicy storagePolicy) {
        // Given: A BlobStore and its default bucket
        BlobStore store = testee();

        // When: Saving a blob
        BlobId blobId = Mono.from(store.save(BucketName.DEFAULT, "toto", storagePolicy)).block();
        String blobIdString = blobId.asString();

        // Then: BlobId string and parsed BlobId should match expectations
        assertThat(blobIdString).isEqualTo("1/628/M/f/emXjFVhqwZi9eYtmKc5JA9CJlHbVdBqfMuLlIbamY=");
        assertThat(blobId).isEqualTo(blobIdFactory().parse(blobIdString));
    }

    @Test
    @Override
    @Disabled("S3minio return `Connection: close` in header response, https://github.com/apache/james-project/pull/1981#issuecomment-2380396460")
    public void deleteBucketConcurrentlyShouldNotFail() {
    }

    @Nested
    class Compatible {

        private BlobStore withGenerationAwareBlobId;
        private BlobStore withMinIOGenerationAwareBlobId;

        @BeforeEach
        void setup() {
            BlobId.Factory plainBlobIdFactory = new PlainBlobId.Factory();
            withGenerationAwareBlobId = createBlobStore(new GenerationAwareBlobId.Factory(clock, plainBlobIdFactory, GenerationAwareBlobId.Configuration.DEFAULT));
            withMinIOGenerationAwareBlobId = createBlobStore(new MinIOGenerationAwareBlobId.Factory(clock, GenerationAwareBlobId.Configuration.DEFAULT, plainBlobIdFactory));
        }


        @Test
        void readWithMinIOGenerationAwareShouldSuccessWhenBlobWasStoredByGenerationAware() {
            String originalData = "toto" + UUID.randomUUID();
            // Given a blob stored with GenerationAwareBlobId
            BlobId blobId = Mono.from(withGenerationAwareBlobId.save(BucketName.DEFAULT, originalData, LOW_COST)).block();

            assertThat(blobId).isInstanceOf(GenerationAwareBlobId.class);

            // When reading it with MinIOGenerationAwareBlobId
            byte[] readAsByte = Mono.from(withMinIOGenerationAwareBlobId.readBytes(BucketName.DEFAULT, blobId)).block();

            // Then the data should be the same
            assertThat(new String(readAsByte, StandardCharsets.UTF_8)).isEqualTo(originalData);
        }

        @Test
        void listBlobsShouldReturnCorrectBlobIdWhenBlobWasStoredByGenerationAware() {
            String originalData = "toto" + UUID.randomUUID();
            // Given a blob stored with GenerationAwareBlobId
            BlobId blobId = Mono.from(withGenerationAwareBlobId.save(BucketName.DEFAULT, originalData, LOW_COST)).block();
            assertThat(blobId).isInstanceOf(GenerationAwareBlobId.class);

            // When listing blobs with MinIOGenerationAwareBlobId
            List<BlobId> blobIdList = Flux.from(withMinIOGenerationAwareBlobId.listBlobs(BucketName.DEFAULT)).collectList().block();
            assertThat(blobIdList).hasSize(1);
            assertThat(blobIdList.getFirst()).isInstanceOf(GenerationAwareBlobId.class);

            // And using the returned BlobId to read the data
            byte[] readAsByte = Mono.from(withMinIOGenerationAwareBlobId.readBytes(BucketName.DEFAULT, blobIdList.getFirst())).block();

            // Then the data should be the same as the original data
            assertThat(new String(readAsByte, StandardCharsets.UTF_8)).isEqualTo(originalData);
        }

        @Test
        void readWithGenerationAwareShouldSuccessWhenBlobWasStoredByMinIOGenerationAware() {
            String originalData = "toto" + UUID.randomUUID();
            // Given a blob stored with MinIOGenerationAwareBlobId
            BlobId blobId = Mono.from(withMinIOGenerationAwareBlobId.save(BucketName.DEFAULT, originalData, LOW_COST)).block();
            assertThat(blobId).isInstanceOf(MinIOGenerationAwareBlobId.class);

            // When reading it with GenerationAwareBlobId
            byte[] readAsByte = Mono.from(withGenerationAwareBlobId.readBytes(BucketName.DEFAULT, blobId)).block();

            // Then the data should be the same
            assertThat(new String(readAsByte, StandardCharsets.UTF_8)).isEqualTo(originalData);
        }

        @Test
        void listBlobsShouldReturnCorrectBlobIdWhenBlobWasStoredByMinIOGenerationAware() {
            String originalData = "toto" + UUID.randomUUID();
            // Given a blob stored with MinIOGenerationAwareBlobId
            BlobId blobId = Mono.from(withMinIOGenerationAwareBlobId.save(BucketName.DEFAULT, originalData, LOW_COST)).block();
            assertThat(blobId).isInstanceOf(MinIOGenerationAwareBlobId.class);

            // When listing blobs with GenerationAwareBlobId
            List<BlobId> blobIdList = Flux.from(withGenerationAwareBlobId.listBlobs(BucketName.DEFAULT)).collectList().block();
            assertThat(blobIdList).hasSize(1);
            assertThat(blobIdList.getFirst()).isInstanceOf(GenerationAwareBlobId.class);

            // And using the returned BlobId to read the data
            byte[] readAsByte = Mono.from(withGenerationAwareBlobId.readBytes(BucketName.DEFAULT, blobIdList.getFirst())).block();

            // Then the data should be the same as the original data
            assertThat(new String(readAsByte, StandardCharsets.UTF_8)).isEqualTo(originalData);
        }
    }
}
