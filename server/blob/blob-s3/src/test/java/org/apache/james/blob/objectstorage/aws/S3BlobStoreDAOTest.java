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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.IntStream;

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
import reactor.core.publisher.Mono;

@ExtendWith(DockerAwsS3Extension.class)
public class S3BlobStoreDAOTest implements BlobStoreDAOContract {
    private static S3BlobStoreDAO testee;

    @BeforeAll
    static void setUp(DockerAwsS3Container dockerAwsS3) {
        S3BlobStoreConfiguration blobStoreConfiguration = S3ConfigurationHelper.baseBlobStoreConfiguration(dockerAwsS3).build();
        testee = new S3BlobStoreDAO(blobStoreConfiguration, new TestBlobId.Factory());
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
            .concatMap(i -> store.save(TEST_BUCKET_NAME, new TestBlobId("test-blob-id-" + i),
                ByteSource.wrap(ELEVEN_KILOBYTES)))
            .blockLast();

        assertThat(Flux.from(testee().listBlobs(TEST_BUCKET_NAME)).count().block())
            .isEqualTo(count);
    }

    @Test
    void readShouldNotLeakHttpConnexionsForUnclosedStreams() {
        BlobStoreDAO store = testee();

        TestBlobId blobId = new TestBlobId("id");
        Mono.from(store.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(ELEVEN_KILOBYTES))).block();

        assertThatCode(() -> IntStream.range(0, 256)
            .forEach(i -> {
                InputStream inputStream = store.read(TEST_BUCKET_NAME, blobId);
                // Close the stream without reading it
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })).doesNotThrowAnyException();
    }
}
