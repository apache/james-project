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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.BloomFilterGCAlgorithm;
import org.apache.james.server.blob.deduplication.BloomFilterGCAlgorithmContract;
import org.apache.james.task.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresBlobStoreGCAlgorithmTest implements BloomFilterGCAlgorithmContract {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresBlobStorageDataDefinition.MODULE, PostgresExtension.PoolSize.LARGE);
    private PostgresBlobStoreDAO blobStore;

    @BeforeAll
    static void setUpClass() {
        // We set the batch size to 10 to test the batching
        System.setProperty("james.postgresql.query.batch.size", "10");
    }

    @AfterAll
    static void tearDownClass() {
        System.clearProperty("james.postgresql.query.batch.size");
    }

    @BeforeEach
    void beforeEach() {
        blobStore = new PostgresBlobStoreDAO(postgresExtension.getDefaultPostgresExecutor(), new PlainBlobId.Factory());
    }

    @Override
    public BlobStoreDAO blobStoreDAO() {
        return blobStore;
    }

    @Test
    void gcShouldSuccessWhenBatchSizeIsSmallerThanAllBlobEntries() {
        BlobStore blobStore = blobStore();
        int orphanBlobCount = 200;
        List<BlobId> referencedBlobIds = IntStream.range(0, 100)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .toList();
        List<BlobId> orphanBlobIds = IntStream.range(0, orphanBlobCount)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .toList();

        when(BLOB_REFERENCE_SOURCE.listReferencedBlobs()).thenReturn(Flux.fromIterable(referencedBlobIds));
        CLOCK.setInstant(NOW.plusMonths(2).toInstant());

        BloomFilterGCAlgorithm.Context context = new BloomFilterGCAlgorithm.Context(EXPECTED_BLOB_COUNT, ASSOCIATED_PROBABILITY);
        BloomFilterGCAlgorithm bloomFilterGCAlgorithm = bloomFilterGCAlgorithm();
        Task.Result result = Mono.from(bloomFilterGCAlgorithm.gc(EXPECTED_BLOB_COUNT, DELETION_WINDOW_SIZE, ASSOCIATED_PROBABILITY, DEFAULT_BUCKET, context)).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        BloomFilterGCAlgorithm.Context.Snapshot snapshot = context.snapshot();

        assertThat(snapshot.getReferenceSourceCount())
            .isEqualTo(referencedBlobIds.size());
        assertThat(snapshot.getBlobCount())
            .isEqualTo(referencedBlobIds.size() + orphanBlobIds.size());

        assertThat(snapshot.getGcedBlobCount())
            .isLessThanOrEqualTo(orphanBlobIds.size())
            .isGreaterThan(0);
    }
}