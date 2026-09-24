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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.compaction.BlobCompactionAlgorithm;
import org.apache.james.blob.compaction.BlobCompactionTask;
import org.apache.james.blob.compaction.ChunkId;
import org.apache.james.blob.compaction.ChunkedBlobStoreDAO;
import org.apache.james.blob.compaction.CompactionConfiguration;
import org.apache.james.blob.compaction.CompactionRequest;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BloomFilterGCAlgorithm;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.task.Task;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

class S3MinioBlobStoreCompactionTest {

    private static final BucketName BUCKET = BucketName.of("compaction-test-bucket");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2020-01-01T00:00:00Z");

    @RegisterExtension
    static S3MinioExtension minioExtension = new S3MinioExtension();

    private S3BlobStoreDAO rawStore;
    private ChunkedBlobStoreDAO chunkedBlobStoreDAO;
    private UpdatableTickingClock clock;
    private GenerationAwareBlobId.Factory generationAwareBlobIdFactory;
    private GenerationAwareBlobId.Configuration generationConfiguration;

    @BeforeEach
    void setUp() {
        AwsS3AuthConfiguration awsS3AuthConfiguration = minioExtension.minioDocker().getAwsS3AuthConfiguration();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(awsS3AuthConfiguration)
            .region(DockerAwsS3Container.REGION)
            .uploadRetrySpec(Optional.of(Retry.backoff(3, Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .defaultBucketName(BUCKET)
            .build();

        S3ClientFactory s3ClientFactory = new S3ClientFactory(s3Configuration, new RecordingMetricFactory(), new NoopGaugeRegistry());
        PlainBlobId.Factory plainBlobIdFactory = new PlainBlobId.Factory();

        clock = new UpdatableTickingClock(NOW.toInstant());
        generationConfiguration = GenerationAwareBlobId.Configuration.DEFAULT;
        generationAwareBlobIdFactory = new GenerationAwareBlobId.Factory(clock, plainBlobIdFactory, generationConfiguration);

        rawStore = new S3BlobStoreDAO(s3ClientFactory, s3Configuration, generationAwareBlobIdFactory, S3RequestOption.DEFAULT);
        chunkedBlobStoreDAO = new ChunkedBlobStoreDAO(rawStore);
    }

    @Test
    void compactionShouldPackBlobsAndAllowReadsWhileGcPreservesChunks() {
        BlobStore blobStore = new DeDuplicationBlobStore(rawStore, BUCKET, generationAwareBlobIdFactory);

        Map<BlobId, byte[]> savedBlobs = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            byte[] content = ("blob-content-payload-" + i).getBytes(StandardCharsets.UTF_8);
            BlobId blobId = Mono.from(blobStore.save(BUCKET, content, BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block();
            savedBlobs.put(blobId, content);
        }

        List<BlobId> initialListed = Flux.from(rawStore.listBlobs(BUCKET)).collectList().block();
        assertThat(initialListed).hasSize(20);

        long targetGeneration = NOW.toInstant().getEpochSecond() / generationConfiguration.getDuration().toSeconds();
        int targetFamily = generationConfiguration.getFamily();

        // Advance clock into the next generation so that targetGeneration is now an old generation
        clock.setInstant(NOW.plusMonths(2).toInstant());

        Map<BlobId, BlobId> updatedIds = new ConcurrentHashMap<>();
        BlobIdUpdater.Factory updaterFactory = (predicate, observer) -> {
            savedBlobs.keySet().stream()
                .filter(predicate)
                .forEach(observer);
            return Mono.just((oldId, newId) -> {
                updatedIds.put(oldId, newId);
                return Mono.empty();
            });
        };

        BlobCompactionAlgorithm algorithm = new BlobCompactionAlgorithm(
            chunkedBlobStoreDAO,
            rawStore,
            updaterFactory);

        CompactionRequest request = CompactionRequest.builder()
            .generation(targetGeneration)
            .family(targetFamily)
            .bucketName(BUCKET)
            .configuration(CompactionConfiguration.DEFAULT)
            .build();

        BlobCompactionTask task = new BlobCompactionTask(algorithm, request, clock);
        Task.Result taskResult = task.run();
        assertThat(taskResult).isEqualTo(Task.Result.COMPLETED);

        // 1. Assert: original objects are gone from rawStore
        for (BlobId oldBlobId : savedBlobs.keySet()) {
            assertThatThrownBy(() -> Mono.from(rawStore.readBytes(BUCKET, oldBlobId)).block())
                .isInstanceOf(ObjectNotFoundException.class);
        }

        // 2. Assert: chunk object exists in rawStore
        List<BlobId> chunkIds = Flux.from(rawStore.listBlobs(BUCKET))
            .filter(ChunkId::isChunkRef)
            .collectList()
            .block();
        assertThat(chunkIds).isNotEmpty();

        // 3. Assert: every blob is readable byte-identical through ChunkedBlobStoreDAO using new slot-ref id
        for (Map.Entry<BlobId, byte[]> entry : savedBlobs.entrySet()) {
            BlobId newSlotId = updatedIds.get(entry.getKey());
            assertThat(newSlotId).isNotNull();
            BlobStoreDAO.BytesBlob readBlob = Mono.from(chunkedBlobStoreDAO.readBytes(BUCKET, newSlotId)).block();
            assertThat(readBlob.payload()).isEqualTo(entry.getValue());
        }

        // 4. Assert: BloomFilterGCAlgorithm after compaction does NOT delete the chunk object
        BlobReferenceSource gcRefSource = () -> Flux.fromIterable(updatedIds.values());
        BloomFilterGCAlgorithm gcAlgorithm = new BloomFilterGCAlgorithm(
            gcRefSource,
            rawStore,
            generationAwareBlobIdFactory,
            generationConfiguration,
            clock);

        Task.Result gcResult = Mono.from(gcAlgorithm.gc(100, 10, 0.01, BUCKET, new BloomFilterGCAlgorithm.Context(100, 0.01))).block();
        assertThat(gcResult).isEqualTo(Task.Result.COMPLETED);

        for (BlobId chunkId : chunkIds) {
            assertThatCode(() -> Mono.from(rawStore.readBytes(BUCKET, chunkId)).block())
                .doesNotThrowAnyException();
        }
    }
}
