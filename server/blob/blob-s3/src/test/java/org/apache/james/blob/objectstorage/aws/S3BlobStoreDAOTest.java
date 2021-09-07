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
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.TestBlobId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;

@ExtendWith(DockerAwsS3Extension.class)
public class S3BlobStoreDAOTest implements BlobStoreDAOContract {
    private static S3BlobStoreDAO testee;

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
            .build();

        testee = new S3BlobStoreDAO(s3Configuration, new TestBlobId.Factory());
    }

    @AfterEach
    void tearDown() {
        testee.deleteAllBuckets().block();
    }

    @AfterAll
    static void tearDownClass() {
        testee.close();
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
            .flatMap(i -> store.save(TEST_BUCKET_NAME, new TestBlobId("test-blob-id-" + i), ByteSource.wrap(ELEVEN_KILOBYTES)))
            .blockLast();

        assertThat(Flux.from(testee().listBlobs(TEST_BUCKET_NAME)).count().block())
            .isEqualTo(count);
    }
}
