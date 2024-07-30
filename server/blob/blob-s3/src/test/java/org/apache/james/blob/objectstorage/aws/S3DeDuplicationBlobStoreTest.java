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

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.DeduplicationBlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.core.publisher.Flux;

@ExtendWith(DockerAwsS3Extension.class)
class S3DeDuplicationBlobStoreTest implements BlobStoreContract, DeduplicationBlobStoreContract {

    private BlobStore testee;
    private DockerAwsS3Container dockerAwsS3;
    private S3BlobStoreDAO s3BlobStoreDAO;
    private S3ClientFactory s3ClientFactory;

    @BeforeEach
    void setUpClass(DockerAwsS3Container dockerAwsS3) {
        this.dockerAwsS3 = dockerAwsS3;
        testee = createBlobStore();
    }

    @Override
    public BlobStore createBlobStore() {
        AwsS3AuthConfiguration authConfiguration = AwsS3AuthConfiguration.builder()
                .endpoint(dockerAwsS3.getEndpoint())
                .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
                .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
                .build();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
                .authConfiguration(authConfiguration)
                .region(dockerAwsS3.dockerAwsS3().region())
                .build();

        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();
        s3ClientFactory = new S3ClientFactory(s3Configuration, new RecordingMetricFactory(), new NoopGaugeRegistry());
        s3BlobStoreDAO = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, blobIdFactory);

        return BlobStoreFactory.builder()
                .blobStoreDAO(s3BlobStoreDAO)
                .blobIdFactory(blobIdFactory)
                .defaultBucketName()
                .deduplication();
    }

    @Test
    void saveShouldNotGenerateBlobIdValuesThatAreNotValidS3ObjectKeys() {
        Flux.range(0, 1000)
                .concatMap(i -> {
                    byte[] payload = RandomStringUtils.random(128).getBytes(StandardCharsets.UTF_8);
                    return testee.save(TEST_BUCKET_NAME, payload, BlobStore.StoragePolicy.LOW_COST);
                })
                .then()
                .block();
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
        return new HashBlobId.Factory();
    }

}