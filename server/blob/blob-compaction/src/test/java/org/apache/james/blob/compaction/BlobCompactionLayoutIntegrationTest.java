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

package org.apache.james.blob.compaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class BlobCompactionLayoutIntegrationTest {
    private static final BucketName TEST_BUCKET = BucketName.of("compaction-layout-test-bucket");
    private static final long TARGET_GENERATION = 2L;
    private static final int FAMILY = 1;
    private static final int POPULATION_SIZE = 200;

    private MemoryBlobStoreDAO rawStore;
    private ChunkedBlobStoreDAO chunkedStore;
    private BlobCompactionAlgorithmTest.TestBlobIdUpdaterFactory updaterFactory;
    private Map<BlobId, BlobId> updatedReferences;
    private BlobCompactionAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        rawStore = new MemoryBlobStoreDAO();
        chunkedStore = new ChunkedBlobStoreDAO(rawStore);
        updaterFactory = new BlobCompactionAlgorithmTest.TestBlobIdUpdaterFactory();
        updatedReferences = updaterFactory.getUpdatedReferences();
        algorithm = new BlobCompactionAlgorithm(rawStore, rawStore, updaterFactory);
    }

    private byte[] readDecompressed(BlobId blobId) {
        return Mono.from(chunkedStore.readBytes(TEST_BUCKET, blobId)).block().payload();
    }

    @Test
    void layout1_uniformSmallPopulationAllActive_shouldYieldSingleChunkAndMaintainIntegrity() {
        Map<BlobId, byte[]> payloads = new HashMap<>();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            BlobId blobId = new PlainBlobId("1_2_email_" + i);
            byte[] payload = ("Subject: Email " + i + "\r\n\r\nBody of message number " + i).getBytes(StandardCharsets.UTF_8);
            payloads.put(blobId, payload);
            Mono.from(rawStore.save(TEST_BUCKET, blobId, BlobStoreDAO.BytesBlob.of(payload))).block();
            updaterFactory.add(blobId, "msg-" + i);
        }

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .build();

        CompactionResult result = algorithm.initialCompact(request).block();

        // 1. All 200 packed into chunks
        assertThat(result.packedBlobs()).isEqualTo(POPULATION_SIZE);

        // 2. Object layout verification: 0 standalone blobs remain, 1 chunk object created
        List<BlobId> storedObjects = Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block();
        assertThat(storedObjects).hasSize(1);
        assertThat(storedObjects.get(0).asString()).contains("_chunk");

        // 3. Integrity verification: all 200 emails readable via new chunk slot references
        for (Map.Entry<BlobId, byte[]> entry : payloads.entrySet()) {
            BlobId newSlotRef = updatedReferences.get(entry.getKey());
            assertThat(newSlotRef).isNotNull();
            byte[] retrieved = readDecompressed(newSlotRef);
            assertThat(retrieved).isEqualTo(entry.getValue());

            // Standalone original blob no longer accessible -> 404
            assertThatThrownBy(() -> Mono.from(rawStore.readBytes(TEST_BUCKET, entry.getKey())).block())
                .isInstanceOf(ObjectNotFoundException.class);
        }

        // 4. Non-existent blob yields 404
        assertThatThrownBy(() -> Mono.from(chunkedStore.readBytes(TEST_BUCKET, new PlainBlobId("1_2_nonexistent"))).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void layout2_partiallyUnreferencedPopulation_shouldCompactOnlyActiveAndPreserveUncompacted() {
        Map<BlobId, byte[]> activePayloads = new HashMap<>();
        List<BlobId> unreferencedBlobs = new ArrayList<>();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            BlobId blobId = new PlainBlobId("1_2_partial_" + i);
            byte[] payload = ("Email payload data " + i).getBytes(StandardCharsets.UTF_8);
            Mono.from(rawStore.save(TEST_BUCKET, blobId, BlobStoreDAO.BytesBlob.of(payload))).block();

            if (i < 100) {
                // First 100 have active metadata references
                activePayloads.put(blobId, payload);
                updaterFactory.add(blobId, "msg-active-" + i);
            } else {
                // Next 100 are orphaned/unreferenced (no metadata)
                unreferencedBlobs.add(blobId);
            }
        }

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .build();

        CompactionResult result = algorithm.initialCompact(request).block();

        // 1. Only 100 active blobs compacted
        assertThat(result.packedBlobs()).isEqualTo(100);

        // 2. Object layout verification: 1 chunk object + 100 uncompacted standalone blobs = 101 objects
        List<BlobId> storedObjects = Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block();
        assertThat(storedObjects).hasSize(101);

        // 3. Active emails readable via chunk slots
        for (Map.Entry<BlobId, byte[]> entry : activePayloads.entrySet()) {
            BlobId newSlotRef = updatedReferences.get(entry.getKey());
            assertThat(newSlotRef).isNotNull();
            assertThat(readDecompressed(newSlotRef)).isEqualTo(entry.getValue());
        }

        // 4. Unreferenced blobs still remain intact as standalone blobs
        for (BlobId unrefId : unreferencedBlobs) {
            byte[] unrefData = Mono.from(rawStore.readBytes(TEST_BUCKET, unrefId)).block().payload();
            assertThat(unrefData).isNotEmpty();
        }

        // 5. Querying non-existent / deleted metadata -> 404
        assertThatThrownBy(() -> Mono.from(chunkedStore.readBytes(TEST_BUCKET, new PlainBlobId("1_2_ghost"))).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void layout3_mixedSizesWithOversizedBlobs_shouldBypassOversizedAndCompactRemaining() {
        Map<BlobId, byte[]> smallPayloads = new HashMap<>();
        Map<BlobId, byte[]> oversizedPayloads = new HashMap<>();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            BlobId blobId = new PlainBlobId("1_2_mixed_" + i);
            byte[] payload;
            if (i % 40 == 0) {
                // 5 oversized blobs (exceeding maxBlobSizeInChunk = 64KB)
                payload = new byte[70 * 1024];
                payload[0] = (byte) i;
                oversizedPayloads.put(blobId, payload);
            } else {
                // 195 small blobs
                payload = ("Small email payload " + i).getBytes(StandardCharsets.UTF_8);
                smallPayloads.put(blobId, payload);
            }
            Mono.from(rawStore.save(TEST_BUCKET, blobId, BlobStoreDAO.BytesBlob.of(payload))).block();
            updaterFactory.add(blobId, "msg-mixed-" + i);
        }

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder()
                .maxPackableSize(64 * 1024L)
                .build())
            .build();

        CompactionResult result = algorithm.initialCompact(request).block();

        // 1. Exactly 195 small blobs packed, 5 oversized preserved
        assertThat(result.packedBlobs()).isEqualTo(195);

        // 2. Object layout verification: 1 chunk + 5 oversized standalone blobs = 6 objects
        List<BlobId> storedObjects = Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block();
        assertThat(storedObjects).hasSize(6);

        // 3. Small blobs readable through chunk slots
        for (Map.Entry<BlobId, byte[]> entry : smallPayloads.entrySet()) {
            BlobId newSlotRef = updatedReferences.get(entry.getKey());
            assertThat(newSlotRef).isNotNull();
            assertThat(readDecompressed(newSlotRef)).isEqualTo(entry.getValue());
        }

        // 4. Oversized blobs readable through their original standalone IDs
        for (Map.Entry<BlobId, byte[]> entry : oversizedPayloads.entrySet()) {
            assertThat(updatedReferences.containsKey(entry.getKey())).isFalse();
            byte[] content = Mono.from(chunkedStore.readBytes(TEST_BUCKET, entry.getKey())).block().payload();
            assertThat(content).isEqualTo(entry.getValue());
        }

        // 5. 404 on unreferenced/unknown blob
        assertThatThrownBy(() -> Mono.from(chunkedStore.readBytes(TEST_BUCKET, new PlainBlobId("1_2_missing"))).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void layout4_multiGenerationInterleavedPopulation_shouldOnlyCompactTargetGeneration() {
        Map<BlobId, byte[]> gen2Payloads = new HashMap<>();
        Map<BlobId, byte[]> gen3Payloads = new HashMap<>();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            if (i % 2 == 0) {
                BlobId blobGen2 = new PlainBlobId("1_2_email_" + i);
                byte[] payload = ("Gen2 content " + i).getBytes(StandardCharsets.UTF_8);
                gen2Payloads.put(blobGen2, payload);
                Mono.from(rawStore.save(TEST_BUCKET, blobGen2, BlobStoreDAO.BytesBlob.of(payload))).block();
                updaterFactory.add(blobGen2, "msg-gen2-" + i);
            } else {
                BlobId blobGen3 = new PlainBlobId("1_3_email_" + i);
                byte[] payload = ("Gen3 content " + i).getBytes(StandardCharsets.UTF_8);
                gen3Payloads.put(blobGen3, payload);
                Mono.from(rawStore.save(TEST_BUCKET, blobGen3, BlobStoreDAO.BytesBlob.of(payload))).block();
                updaterFactory.add(blobGen3, "msg-gen3-" + i);
            }
        }

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION) // Only generation 2
            .family(FAMILY)
            .build();

        CompactionResult result = algorithm.initialCompact(request).block();

        // 1. Exactly 100 gen2 blobs packed; gen3 untouched
        assertThat(result.packedBlobs()).isEqualTo(100);

        // 2. Object layout verification: 1 chunk for gen2 + 100 gen3 standalone blobs = 101 objects
        List<BlobId> storedObjects = Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block();
        assertThat(storedObjects).hasSize(101);

        // 3. Gen2 emails readable via chunk slots
        for (Map.Entry<BlobId, byte[]> entry : gen2Payloads.entrySet()) {
            BlobId newSlotRef = updatedReferences.get(entry.getKey());
            assertThat(newSlotRef).isNotNull();
            assertThat(readDecompressed(newSlotRef)).isEqualTo(entry.getValue());
        }

        // 4. Gen3 emails readable via their untouched standalone IDs
        for (Map.Entry<BlobId, byte[]> entry : gen3Payloads.entrySet()) {
            assertThat(updatedReferences.containsKey(entry.getKey())).isFalse();
            byte[] content = Mono.from(chunkedStore.readBytes(TEST_BUCKET, entry.getKey())).block().payload();
            assertThat(content).isEqualTo(entry.getValue());
        }

        // 5. 404 integrity check
        assertThatThrownBy(() -> Mono.from(chunkedStore.readBytes(TEST_BUCKET, new PlainBlobId("1_2_missing"))).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void layout5_twoStageCompactionWithSubsequentDeletionAndGC_shouldPurgeDeadSlotsAndPreserveLive() {
        Map<BlobId, byte[]> allPayloads = new HashMap<>();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            BlobId blobId = new PlainBlobId("1_2_two_stage_" + i);
            byte[] payload = ("Email payload number " + i).getBytes(StandardCharsets.UTF_8);
            allPayloads.put(blobId, payload);
            Mono.from(rawStore.save(TEST_BUCKET, blobId, BlobStoreDAO.BytesBlob.of(payload))).block();
            updaterFactory.add(blobId, "msg-ts-" + i);
        }

        CompactionRequest initialRequest = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .build();

        // Stage 1: Initial compaction packs all 200 into 1 chunk
        CompactionResult initialResult = algorithm.initialCompact(initialRequest).block();
        assertThat(initialResult.packedBlobs()).isEqualTo(POPULATION_SIZE);

        List<BlobId> stage1Objects = Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block();
        assertThat(stage1Objects).hasSize(1);
        BlobId originalChunkBlobId = stage1Objects.get(0);

        // Stage 2: Simulate deleting 140 messages (leaving 60 live messages)
        // 140 / 200 = 70% dead slots > 40% purge threshold
        Map<BlobId, byte[]> livePayloads = new HashMap<>();
        for (int i = 0; i < POPULATION_SIZE; i++) {
            BlobId oldBlobId = new PlainBlobId("1_2_two_stage_" + i);
            BlobId slotRef = updatedReferences.get(oldBlobId);
            if (i >= 60) {
                updaterFactory.remove(slotRef);
            } else {
                livePayloads.put(slotRef, allPayloads.get(oldBlobId));
            }
        }

        // Stage 3: Run GC compaction
        CompactionRequest gcRequest = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder()
                .purgeDeadRatio(0.4)
                .build())
            .build();

        CompactionResult gcResult = algorithm.gcCompact(gcRequest).block();

        // 140 dead slots purged
        assertThat(gcResult.deadPurged()).isEqualTo(140);

        // Object layout verification: original chunk deleted, 1 new purged chunk created
        List<BlobId> stage2Objects = Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block();
        assertThat(stage2Objects).hasSize(1);
        assertThat(stage2Objects.get(0)).isNotEqualTo(originalChunkBlobId);

        // Live messages remain readable through their re-compacted slot IDs
        for (Map.Entry<BlobId, byte[]> entry : livePayloads.entrySet()) {
            BlobId recompactedSlotRef = updatedReferences.get(entry.getKey());
            assertThat(recompactedSlotRef).isNotNull();
            assertThat(readDecompressed(recompactedSlotRef)).isEqualTo(entry.getValue());
        }

        // Deleted messages no longer referenced -> accessing invalid slot yields 404
        assertThatThrownBy(() -> Mono.from(chunkedStore.readBytes(TEST_BUCKET, new PlainBlobId("1_2_nonexistent"))).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }
}
