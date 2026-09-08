/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership. The ASF licenses this file    *
 * to you under the Apache License, Version 2.0 (the             *
 * "License"); you may not use this file except in compliance   *
 * with the License. You may obtain a copy of the License at     *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the     *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.blob.objectstorage.aws;

import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.apache.james.blob.api.TestBlobId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.core.publisher.Mono;

@ExtendWith(DockerCephS3Extension.class)
class DockerCephS3ContainerTest {
    private static S3ClientFactory s3ClientFactory;
    private static S3BlobStoreDAO testee;

    @BeforeAll
    static void setUp(DockerCephS3Container ceph) {
        S3BlobStoreConfiguration configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(ceph.getAwsS3AuthConfiguration())
            .region(DockerCephS3Container.REGION)
            .build();

        s3ClientFactory = new S3ClientFactory(configuration, new RecordingMetricFactory(), new NoopGaugeRegistry());
        testee = new S3BlobStoreDAO(s3ClientFactory, configuration, new TestBlobId.Factory(), S3RequestOption.DEFAULT);
    }

    @AfterAll
    static void tearDown() {
        s3ClientFactory.close();
    }

    @Test
    void shouldSupportS3WriteAndRead() {
        TestBlobId blobId = new TestBlobId(UUID.randomUUID().toString());

        Mono.from(testee.save(DockerCephS3Container.TEST_BUCKET, blobId, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(testee.readBytes(DockerCephS3Container.TEST_BUCKET, blobId)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }
}
