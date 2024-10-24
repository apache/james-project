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

package org.apache.james.blob.postgres;

import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

class PostgresBlobStoreDAOTest implements BlobStoreDAOContract {
    static Duration CONCURRENT_TEST_DURATION = Duration.ofMinutes(5);

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresBlobStorageModule.MODULE, PostgresExtension.PoolSize.LARGE);

    private PostgresBlobStoreDAO blobStore;

    @BeforeEach
    void setUp() {
        blobStore = new PostgresBlobStoreDAO(postgresExtension.getDefaultPostgresExecutor(), new PlainBlobId.Factory());
    }

    @Override
    public BlobStoreDAO testee() {
        return blobStore;
    }

    @Override
    @Disabled("Not supported")
    public void listBucketsShouldReturnBucketsWithNoBlob() {
    }

    @Override
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    public void concurrentSaveByteSourceShouldReturnConsistentValues(String description, byte[] bytes) throws ExecutionException, InterruptedException {
        Mono.from(testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytes)).block();
        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (threadNumber, step) -> testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(bytes)),
                (threadNumber, step) -> checkConcurrentSaveOperation(bytes)
            )
            .threadCount(10)
            .operationCount(20)
            .runSuccessfullyWithin(CONCURRENT_TEST_DURATION);
    }

    @Override
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blobs")
    public void concurrentSaveInputStreamShouldReturnConsistentValues(String description, byte[] bytes) throws ExecutionException, InterruptedException {
        Mono.from(testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytes)).block();
        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (threadNumber, step) -> testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(bytes)),
                (threadNumber, step) -> checkConcurrentSaveOperation(bytes)
            )
            .threadCount(10)
            .operationCount(20)
            .runSuccessfullyWithin(CONCURRENT_TEST_DURATION);
    }
}