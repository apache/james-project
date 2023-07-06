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
import static org.apache.james.blob.objectstorage.aws.DockerAwsS3Container.ACCESS_KEY_ID;
import static org.apache.james.blob.objectstorage.aws.DockerAwsS3Container.SECRET_ACCESS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.TestBlobId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Testcontainers
public class S3MinioTest implements BlobStoreDAOContract {

    private static final int MINIO_PORT = 9000;
    private static S3BlobStoreDAO testee;

    @Container
    private static final GenericContainer<?> minioContainer = new GenericContainer<>("quay.io/minio/minio")
        .withExposedPorts(MINIO_PORT)
        .withEnv("MINIO_ROOT_USER", ACCESS_KEY_ID)
        .withEnv("MINIO_ROOT_PASSWORD", SECRET_ACCESS_KEY)
        .withCommand("server", "/data", "--console-address", ":9090")
        .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-minio-s3-test"));


    @BeforeAll
    static void setUp() {
        AwsS3AuthConfiguration authConfiguration = AwsS3AuthConfiguration.builder()
            .endpoint(URI.create(String.format("http://%s:%s/", minioContainer.getHost(), minioContainer.getMappedPort(MINIO_PORT))))
            .accessKeyId(ACCESS_KEY_ID)
            .secretKey(SECRET_ACCESS_KEY)
            .build();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(authConfiguration)
            .region(DockerAwsS3Container.REGION)
            .build();

        testee = new S3BlobStoreDAO(s3Configuration, new TestBlobId.Factory());
    }

    @AfterAll
    static void tearDownClass() {
        testee.close();
    }

    @AfterEach
    void tearDown() {
        testee.deleteAllBuckets().block();
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
    void saveShouldWorkForAllPayloadsWithHash() {
        Flux.range(0, 1000)
            .concatMap(i -> {
                byte[] payload = RandomStringUtils.random(128).getBytes(StandardCharsets.UTF_8);
                BlobId blobId = new HashBlobId.Factory().forPayload(payload);
                return testee.save(TEST_BUCKET_NAME, blobId, payload);
            })
            .then()
            .block();
    }

}
