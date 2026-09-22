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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.compaction.BlobReferenceMappingSource.BlobIdMessageIdMapping;
import org.apache.james.blob.compaction.ChunkFormat.BlobSlotContent;
import org.apache.james.blob.compaction.ChunkFormat.ChunkWriteResult;
import org.apache.james.blob.compaction.ChunkFormat.SlotRange;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import com.github.luben.zstd.Zstd;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class BlobCompactionAlgorithmTest {
    private static final BucketName TEST_BUCKET = BucketName.of("compaction-test-bucket");
    private static final long TARGET_GENERATION = 2L;
    private static final int FAMILY = 1;

    static class RecordingBlobIdUpdater implements BlobIdUpdater {
        record Replacement(BlobId oldId, BlobId newId, Collection<String> messageIds) {}

        private final List<Replacement> replacements = new ArrayList<>();

        @Override
        public synchronized Mono<Void> replaceReferences(BlobId oldId, BlobId newId, Collection<String> messageIds) {
            replacements.add(new Replacement(oldId, newId, new ArrayList<>(messageIds)));
            return Mono.empty();
        }

        public synchronized List<Replacement> getReplacements() {
            return new ArrayList<>(replacements);
        }
    }

    static class TestMappingSource implements BlobReferenceMappingSource {
        private final Map<BlobId, Set<String>> mappings = new ConcurrentHashMap<>();

        public void add(BlobId blobId, String messageId) {
            mappings.computeIfAbsent(blobId, k -> ConcurrentHashMap.newKeySet()).add(messageId);
        }

        public void remove(BlobId blobId) {
            mappings.remove(blobId);
        }

        @Override
        public Publisher<BlobIdMessageIdMapping> listBlobIdMessageIdMappings() {
            List<BlobIdMessageIdMapping> list = new ArrayList<>();
            mappings.forEach((blobId, msgIds) ->
                msgIds.forEach(msgId -> list.add(new BlobIdMessageIdMapping(blobId, msgId))));
            return Flux.fromIterable(list);
        }

        @Override
        public Publisher<BlobIdMessageIdMapping> loadReferencesFor(Collection<BlobId> blobIds) {
            List<BlobIdMessageIdMapping> list = new ArrayList<>();
            for (BlobId blobId : blobIds) {
                Set<String> msgIds = mappings.get(blobId);
                if (msgIds != null) {
                    msgIds.forEach(msgId -> list.add(new BlobIdMessageIdMapping(blobId, msgId)));
                }
            }
            return Flux.fromIterable(list);
        }
    }

    private MemoryBlobStoreDAO rawStore;
    private ChunkedBlobStoreDAO chunkedBlobStoreDAO;
    private TestMappingSource mappingSource;
    private RecordingBlobIdUpdater recordingUpdater;
    private BlobCompactionAlgorithm testee;

    @BeforeEach
    void setUp() {
        rawStore = new MemoryBlobStoreDAO();
        chunkedBlobStoreDAO = new ChunkedBlobStoreDAO(rawStore, rawStore);
        mappingSource = new TestMappingSource();
        recordingUpdater = new RecordingBlobIdUpdater();
        testee = new BlobCompactionAlgorithm(rawStore, rawStore, mappingSource, recordingUpdater);
    }

    private byte[] readDecompressed(BlobId blobId) {
        BlobStoreDAO.BytesBlob blob = Mono.from(chunkedBlobStoreDAO.readBytes(TEST_BUCKET, blobId)).block();
        if (blob.metadata().contentEncoding().filter(BlobStoreDAO.ContentEncoding.ZSTD::equals).isPresent()) {
            long origSize = blob.metadata().get(BlobSlot.CONTENT_ORIGINAL_SIZE)
                .map(v -> Long.parseLong(v.value()))
                .orElse((long) blob.payload().length);
            return Zstd.decompress(blob.payload(), (int) origSize);
        }
        return blob.payload();
    }

    @Test
    void initialCompactionShouldPackSmallBlobsAndSkipLargeBlobs() {
        BlobId small1 = new PlainBlobId("1_2_small1");
        BlobId small2 = new PlainBlobId("1_2_small2");
        BlobId large = new PlainBlobId("1_2_large");

        byte[] payload1 = "Small payload 1".getBytes(StandardCharsets.UTF_8);
        byte[] payload2 = "Small payload 2".getBytes(StandardCharsets.UTF_8);
        byte[] largePayload = new byte[2 * 1024 * 1024]; // 2MB
        Arrays.fill(largePayload, (byte) 'L');

        Mono.from(rawStore.save(TEST_BUCKET, small1, BlobStoreDAO.BytesBlob.of(payload1))).block();
        Mono.from(rawStore.save(TEST_BUCKET, small2, BlobStoreDAO.BytesBlob.of(payload2))).block();
        Mono.from(rawStore.save(TEST_BUCKET, large, BlobStoreDAO.BytesBlob.of(largePayload))).block();

        mappingSource.add(small1, "msg-1");
        mappingSource.add(small2, "msg-2");
        mappingSource.add(large, "msg-3");

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder()
                .maxPackableSize(1024 * 1024L) // 1MB
                .build())
            .build();

        CompactionResult result = testee.initialCompact(request).block();

        assertThat(result.packedBlobs()).isEqualTo(2);
        assertThat(result.chunksWritten()).isEqualTo(1);

        // Standalone small blobs should be deleted
        assertThat(Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block())
            .doesNotContain(small1, small2)
            .contains(large);

        // Replacements recorded
        assertThat(recordingUpdater.getReplacements()).hasSize(2);
        BlobId newSlotRef1 = recordingUpdater.getReplacements().get(0).newId();
        BlobId newSlotRef2 = recordingUpdater.getReplacements().get(1).newId();

        assertThat(ChunkId.isChunkRef(newSlotRef1)).isTrue();
        assertThat(ChunkId.isChunkRef(newSlotRef2)).isTrue();

        assertThat(readDecompressed(newSlotRef1)).isEqualTo(payload1);
        assertThat(readDecompressed(newSlotRef2)).isEqualTo(payload2);
    }

    @Test
    void initialCompactionPreservesDeduplication() {
        BlobId sharedBlobId = new PlainBlobId("1_2_shared");
        BlobId otherBlobId = new PlainBlobId("1_2_other");
        byte[] sharedPayload = "Shared email attachment body".getBytes(StandardCharsets.UTF_8);
        byte[] otherPayload = "Other email attachment body".getBytes(StandardCharsets.UTF_8);

        Mono.from(rawStore.save(TEST_BUCKET, sharedBlobId, BlobStoreDAO.BytesBlob.of(sharedPayload))).block();
        Mono.from(rawStore.save(TEST_BUCKET, otherBlobId, BlobStoreDAO.BytesBlob.of(otherPayload))).block();

        // Two messages reference the SAME blob
        mappingSource.add(sharedBlobId, "msg-A");
        mappingSource.add(sharedBlobId, "msg-B");
        mappingSource.add(otherBlobId, "msg-C");

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .build();

        CompactionResult result = testee.initialCompact(request).block();

        assertThat(result.packedBlobs()).isEqualTo(2);
        assertThat(result.chunksWritten()).isEqualTo(1);

        // Replacements include one covering both messages for sharedBlobId
        List<RecordingBlobIdUpdater.Replacement> reps = recordingUpdater.getReplacements();
        assertThat(reps).hasSize(2);
        RecordingBlobIdUpdater.Replacement sharedRep = reps.stream()
            .filter(r -> r.oldId().equals(sharedBlobId))
            .findFirst()
            .orElseThrow();
        assertThat(sharedRep.messageIds()).containsExactlyInAnyOrder("msg-A", "msg-B");

        assertThat(readDecompressed(sharedRep.newId())).isEqualTo(sharedPayload);
    }

    @Test
    void gcCompactShouldPurgeDeadSlotsWhenThresholdExceeded() throws Exception {
        ChunkId chunkId = ChunkId.ofChunk(FAMILY, TARGET_GENERATION);

        List<BlobSlotContent> slots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            slots.add(BlobSlotContent.of(("Slot content " + i).getBytes(StandardCharsets.UTF_8)));
        }

        ChunkWriteResult writeResult = ChunkFormat.writeChunk(slots);
        Mono.from(rawStore.save(TEST_BUCKET, chunkId.chunkBlobId(), BlobStoreDAO.BytesBlob.of(writeResult.chunkBytes()))).block();

        // Only 8 slots are live, 2 slots (slots 8 and 9) are dead (20% dead > 10% threshold)
        for (int i = 0; i < 8; i++) {
            ChunkId slotRef = ChunkId.slotRef(chunkId, writeResult.slotRanges().get(i).offset(), writeResult.slotRanges().get(i).limit());
            mappingSource.add(slotRef, "msg-" + i);
        }

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder()
                .purgeDeadRatio(0.1)
                .gainThreshold(0.1)
                .build())
            .build();

        CompactionResult result = testee.gcCompact(request).block();

        assertThat(result.deadPurged()).isEqualTo(2);

        // Old chunk deleted
        List<BlobId> remainingBlobs = Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block();
        assertThat(remainingBlobs).doesNotContain(chunkId.chunkBlobId());
        assertThat(remainingBlobs).hasSize(1); // the new purged chunk

        // 8 surviving slots updated
        assertThat(recordingUpdater.getReplacements()).hasSize(8);

        // Surviving slots readable
        for (int i = 0; i < 8; i++) {
            BlobId newSlotRef = recordingUpdater.getReplacements().get(i).newId();
            assertThat(readDecompressed(newSlotRef)).isEqualTo(("Slot content " + i).getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void gcCompactShouldRespectGainThreshold() throws Exception {
        ChunkId chunkId = ChunkId.ofChunk(FAMILY, TARGET_GENERATION);

        List<BlobSlotContent> slots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            slots.add(BlobSlotContent.of(("Slot content " + i).getBytes(StandardCharsets.UTF_8)));
        }

        ChunkWriteResult writeResult = ChunkFormat.writeChunk(slots);
        Mono.from(rawStore.save(TEST_BUCKET, chunkId.chunkBlobId(), BlobStoreDAO.BytesBlob.of(writeResult.chunkBytes()))).block();

        // 9 slots are live, 1 slot is dead (10% dead). Gain threshold is 20%.
        for (int i = 0; i < 9; i++) {
            ChunkId slotRef = ChunkId.slotRef(chunkId, writeResult.slotRanges().get(i).offset(), writeResult.slotRanges().get(i).limit());
            mappingSource.add(slotRef, "msg-" + i);
        }

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder()
                .purgeDeadRatio(0.1)
                .gainThreshold(0.20) // 20% > 10% dead
                .build())
            .build();

        CompactionResult result = testee.gcCompact(request).block();

        assertThat(result.deadPurged()).isEqualTo(0);
        // Old chunk is untouched
        assertThat(Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block())
            .contains(chunkId.chunkBlobId());
    }

    @Test
    void gcCompactShouldMergeTwoSmallChunks() throws Exception {
        ChunkId chunk1 = ChunkId.ofChunk(FAMILY, TARGET_GENERATION);
        ChunkId chunk2 = ChunkId.ofChunk(FAMILY, TARGET_GENERATION);

        byte[] payload1 = "Chunk 1 payload".getBytes(StandardCharsets.UTF_8);
        byte[] payload2 = "Chunk 2 payload".getBytes(StandardCharsets.UTF_8);

        ChunkWriteResult w1 = ChunkFormat.writeChunk(List.of(BlobSlotContent.of(payload1)));
        ChunkWriteResult w2 = ChunkFormat.writeChunk(List.of(BlobSlotContent.of(payload2)));

        Mono.from(rawStore.save(TEST_BUCKET, chunk1.chunkBlobId(), BlobStoreDAO.BytesBlob.of(w1.chunkBytes()))).block();
        Mono.from(rawStore.save(TEST_BUCKET, chunk2.chunkBlobId(), BlobStoreDAO.BytesBlob.of(w2.chunkBytes()))).block();

        ChunkId slot1 = ChunkId.slotRef(chunk1, w1.slotRanges().get(0).offset(), w1.slotRanges().get(0).limit());
        ChunkId slot2 = ChunkId.slotRef(chunk2, w2.slotRanges().get(0).offset(), w2.slotRanges().get(0).limit());

        mappingSource.add(slot1, "msg-1");
        mappingSource.add(slot2, "msg-2");

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder()
                .chunkTargetSize(100_000L)
                .mergeDeadRatio(0.5) // chunks < 50,000 bytes qualify for merge
                .build())
            .build();

        CompactionResult result = testee.gcCompact(request).block();

        assertThat(result.mergedChunks()).isEqualTo(2);

        // Old chunks deleted
        List<BlobId> blobs = Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block();
        assertThat(blobs).doesNotContain(chunk1.chunkBlobId(), chunk2.chunkBlobId());
        assertThat(blobs).hasSize(1); // merged chunk

        // Both slots updated and readable
        assertThat(recordingUpdater.getReplacements()).hasSize(2);
        BlobId newSlot1 = recordingUpdater.getReplacements().get(0).newId();
        BlobId newSlot2 = recordingUpdater.getReplacements().get(1).newId();

        assertThat(readDecompressed(newSlot1)).isEqualTo(payload1);
        assertThat(readDecompressed(newSlot2)).isEqualTo(payload2);
    }

    @Test
    void gcCompactShouldKeepSingleSmallChunkAlone() throws Exception {
        ChunkId chunk1 = ChunkId.ofChunk(FAMILY, TARGET_GENERATION);
        byte[] payload1 = "Single chunk payload".getBytes(StandardCharsets.UTF_8);

        ChunkWriteResult w1 = ChunkFormat.writeChunk(List.of(BlobSlotContent.of(payload1)));
        Mono.from(rawStore.save(TEST_BUCKET, chunk1.chunkBlobId(), BlobStoreDAO.BytesBlob.of(w1.chunkBytes()))).block();

        ChunkId slot1 = ChunkId.slotRef(chunk1, w1.slotRanges().get(0).offset(), w1.slotRanges().get(0).limit());
        mappingSource.add(slot1, "msg-1");

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder()
                .chunkTargetSize(100_000L)
                .mergeDeadRatio(0.5)
                .build())
            .build();

        CompactionResult result = testee.gcCompact(request).block();

        assertThat(result.mergedChunks()).isEqualTo(0);
        assertThat(Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block())
            .contains(chunk1.chunkBlobId());
    }

    @Test
    void gcCompactShouldDeleteOrphanChunkForCrashRecovery() throws Exception {
        ChunkId orphanChunk = ChunkId.ofChunk(FAMILY, TARGET_GENERATION);
        byte[] payload = "Orphan chunk content with no references".getBytes(StandardCharsets.UTF_8);

        ChunkWriteResult w = ChunkFormat.writeChunk(List.of(BlobSlotContent.of(payload)));
        Mono.from(rawStore.save(TEST_BUCKET, orphanChunk.chunkBlobId(), BlobStoreDAO.BytesBlob.of(w.chunkBytes()))).block();

        // Notice: NO references in mappingSource (100% orphan)

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .build();

        CompactionResult result = testee.gcCompact(request).block();

        assertThat(result.deadPurged()).isEqualTo(1);
        assertThat(Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block())
            .doesNotContain(orphanChunk.chunkBlobId());
    }

    @Test
    void gcCompactShouldBeIdempotentAndTerminateWithEmptyResult() {
        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .build();

        CompactionResult result = testee.gcCompact(request).block();
        assertThat(result).isEqualTo(CompactionResult.NONE);
    }

    @Test
    void initialCompactShouldNotDeleteOriginalBlobWhenReferenceUpdateFails() {
        BlobId failingBlob = new PlainBlobId("1_2_failing");
        BlobId succeedingBlob = new PlainBlobId("1_2_succeeding");

        byte[] payloadFailing = "Payload failing".getBytes(StandardCharsets.UTF_8);
        byte[] payloadSucceeding = "Payload succeeding".getBytes(StandardCharsets.UTF_8);

        Mono.from(rawStore.save(TEST_BUCKET, failingBlob, BlobStoreDAO.BytesBlob.of(payloadFailing))).block();
        Mono.from(rawStore.save(TEST_BUCKET, succeedingBlob, BlobStoreDAO.BytesBlob.of(payloadSucceeding))).block();

        mappingSource.add(failingBlob, "msg-fail");
        mappingSource.add(succeedingBlob, "msg-success");

        BlobIdUpdater partiallyFailingUpdater = (oldId, newId, messageIds) -> {
            if (oldId.equals(failingBlob)) {
                return Mono.error(new RuntimeException("Cassandra write timeout simulation"));
            }
            return Mono.empty();
        };

        BlobCompactionAlgorithm algorithmWithFailingUpdater = new BlobCompactionAlgorithm(
            rawStore, rawStore, mappingSource, partiallyFailingUpdater);

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .build();

        CompactionResult result = algorithmWithFailingUpdater.initialCompact(request).block();

        assertThat(result.packedBlobs()).isEqualTo(1);

        List<BlobId> remainingBlobs = Flux.from(rawStore.listBlobs(TEST_BUCKET)).collectList().block();
        assertThat(remainingBlobs).contains(failingBlob);
        assertThat(remainingBlobs).doesNotContain(succeedingBlob);
    }

    static class RangedReadTrackingMemoryBlobStoreDAO extends MemoryBlobStoreDAO {
        private final AtomicLong maxRangedReadBytes = new AtomicLong(0);
        private final List<BlobId> wholeChunkReads = new CopyOnWriteArrayList<>();
        private final Map<BlobId, byte[]> rawBlobs = new ConcurrentHashMap<>();

        @Override
        public Mono<Void> save(BucketName bucketName, BlobId blobId, BytesBlob blob) {
            rawBlobs.put(blobId, blob.payload());
            return super.save(bucketName, blobId, blob);
        }

        @Override
        public Mono<Blob> readRange(BucketName bucketName, BlobId blobId, long start, long end) {
            byte[] allBytes = rawBlobs.get(blobId);
            if (allBytes == null) {
                return Mono.error(new ObjectNotFoundException("Blob not found: " + blobId.asString()));
            }
            long totalSize = allBytes.length;
            int from;
            int to;
            if (start < 0) {
                int suffixLength = (int) Math.min(totalSize, -start);
                from = (int) Math.max(0, totalSize - suffixLength);
                to = (int) totalSize;
            } else {
                from = (int) Math.min(totalSize, start);
                to = (int) Math.min(totalSize, end + 1);
            }
            byte[] slice = Arrays.copyOfRange(allBytes, from, to);
            maxRangedReadBytes.updateAndGet(curr -> Math.max(curr, slice.length));
            BlobMetadata metadata = BlobMetadata.empty()
                .withMetadata(TOTAL_OBJECT_SIZE, new BlobMetadataValue(String.valueOf(totalSize)));
            return Mono.just(BytesBlob.of(slice, metadata));
        }

        @Override
        public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
            if (ChunkId.isChunkRef(blobId)) {
                wholeChunkReads.add(blobId);
            }
            return super.readBytes(bucketName, blobId);
        }

        public long getMaxRangedReadBytes() {
            return maxRangedReadBytes.get();
        }

        public List<BlobId> getWholeChunkReads() {
            return new ArrayList<>(wholeChunkReads);
        }
    }

    @Test
    void gcCompactShouldBoundHeapUsageByMaxSlotSizeAndNeverReadWholeChunkPayloads() throws Exception {
        RangedReadTrackingMemoryBlobStoreDAO trackingStore = new RangedReadTrackingMemoryBlobStoreDAO();
        TestMappingSource localMapping = new TestMappingSource();
        RecordingBlobIdUpdater localUpdater = new RecordingBlobIdUpdater();
        BlobCompactionAlgorithm algorithm = new BlobCompactionAlgorithm(
            trackingStore, trackingStore, localMapping, localUpdater);

        ChunkId chunkId = ChunkId.ofChunk(FAMILY, TARGET_GENERATION);

        Random random = new Random(42);
        byte[] slot1Payload = new byte[50 * 1024];
        random.nextBytes(slot1Payload);
        byte[] slot2Payload = new byte[100 * 1024];
        random.nextBytes(slot2Payload);
        byte[] slot3Payload = new byte[75 * 1024];
        random.nextBytes(slot3Payload);

        ChunkWriteResult writeResult = ChunkFormat.writeChunk(List.of(
            BlobSlotContent.of(slot1Payload),
            BlobSlotContent.of(slot2Payload),
            BlobSlotContent.of(slot3Payload)
        ));

        Mono.from(trackingStore.save(TEST_BUCKET, chunkId.chunkBlobId(), BlobStoreDAO.BytesBlob.of(writeResult.chunkBytes()))).block();

        SlotRange r1 = writeResult.slotRanges().get(0);
        SlotRange r3 = writeResult.slotRanges().get(2);
        ChunkId slot1Ref = ChunkId.slotRef(chunkId, r1.offset(), r1.limit());
        ChunkId slot3Ref = ChunkId.slotRef(chunkId, r3.offset(), r3.limit());

        localMapping.add(slot1Ref, "msg-1");
        localMapping.add(slot3Ref, "msg-3");

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder()
                .purgeDeadRatio(0.1)
                .gainThreshold(0.1)
                .build())
            .build();

        CompactionResult result = algorithm.gcCompact(request).block();

        assertThat(result.deadPurged()).isEqualTo(1);
        assertThat(result.packedBlobs()).isEqualTo(0);
        assertThat(localUpdater.getReplacements()).hasSize(2);

        // Verification of memory bounds (R2-2):
        // 1. Whole chunk readBytes must NEVER be invoked on chunk objects
        assertThat(trackingStore.getWholeChunkReads()).isEmpty();

        // 2. The largest single ranged read must be strictly bounded by max(64KB footer, maxSlotSize + slot header)
        long totalChunkSize = writeResult.chunkBytes().length;
        assertThat(trackingStore.getMaxRangedReadBytes()).isLessThan(totalChunkSize);
        assertThat(trackingStore.getMaxRangedReadBytes()).isLessThanOrEqualTo(Math.max(65536, 76 * 1024));
    }

    @Test
    void initialCompactShouldPreserveCustomBlobMetadata() {
        BlobId b1 = new PlainBlobId("1_2_blob1");
        BlobId b2 = new PlainBlobId("1_2_blob2");

        BlobStoreDAO.BlobMetadata meta1 = BlobStoreDAO.BlobMetadata.empty()
            .withMetadata(new BlobStoreDAO.BlobMetadataName("custom-header"), new BlobStoreDAO.BlobMetadataValue("meta-value-1"));
        BlobStoreDAO.BlobMetadata meta2 = BlobStoreDAO.BlobMetadata.empty()
            .withMetadata(new BlobStoreDAO.BlobMetadataName("custom-header"), new BlobStoreDAO.BlobMetadataValue("meta-value-2"));

        byte[] payload1 = "Candidate 1 with metadata".getBytes(StandardCharsets.UTF_8);
        byte[] payload2 = "Candidate 2 with metadata".getBytes(StandardCharsets.UTF_8);

        Mono.from(rawStore.save(TEST_BUCKET, b1, BlobStoreDAO.BytesBlob.of(payload1, meta1))).block();
        Mono.from(rawStore.save(TEST_BUCKET, b2, BlobStoreDAO.BytesBlob.of(payload2, meta2))).block();

        mappingSource.add(b1, "msg1");
        mappingSource.add(b2, "msg2");

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder().chunkTargetSize(100_000).build())
            .build();

        CompactionResult result = testee.initialCompact(request).block();
        assertThat(result.packedBlobs()).isEqualTo(2);

        List<RecordingBlobIdUpdater.Replacement> replacements = recordingUpdater.getReplacements();
        assertThat(replacements).hasSize(2);

        BlobId newRef1 = replacements.get(0).newId();
        BlobStoreDAO.BytesBlob readBlob1 = Mono.from(chunkedBlobStoreDAO.readBytes(TEST_BUCKET, newRef1)).block();
        assertThat(readBlob1.metadata().get(new BlobStoreDAO.BlobMetadataName("custom-header")))
            .contains(new BlobStoreDAO.BlobMetadataValue("meta-value-1"));

        BlobId newRef2 = replacements.get(1).newId();
        BlobStoreDAO.BytesBlob readBlob2 = Mono.from(chunkedBlobStoreDAO.readBytes(TEST_BUCKET, newRef2)).block();
        assertThat(readBlob2.metadata().get(new BlobStoreDAO.BlobMetadataName("custom-header")))
            .contains(new BlobStoreDAO.BlobMetadataValue("meta-value-2"));
    }

    @Test
    void initialCompactShouldUsePrefixPushdownToAvoidListingOtherFamiliesOrGenerations() {
        BlobId targetBlob1 = new PlainBlobId("1_2_target1");
        BlobId targetBlob2 = new PlainBlobId("1_2_target2");
        BlobId otherGenBlob = new PlainBlobId("1_3_otherGen");
        BlobId otherFamilyBlob = new PlainBlobId("2_2_otherFamily");

        Mono.from(rawStore.save(TEST_BUCKET, targetBlob1, BlobStoreDAO.BytesBlob.of("target1"))).block();
        Mono.from(rawStore.save(TEST_BUCKET, targetBlob2, BlobStoreDAO.BytesBlob.of("target2"))).block();
        Mono.from(rawStore.save(TEST_BUCKET, otherGenBlob, BlobStoreDAO.BytesBlob.of("otherGen"))).block();
        Mono.from(rawStore.save(TEST_BUCKET, otherFamilyBlob, BlobStoreDAO.BytesBlob.of("otherFamily"))).block();

        mappingSource.add(targetBlob1, "msg-target1");
        mappingSource.add(targetBlob2, "msg-target2");
        mappingSource.add(otherGenBlob, "msg-otherGen");
        mappingSource.add(otherFamilyBlob, "msg-otherFamily");

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder().chunkTargetSize(100_000).build())
            .build();

        CompactionResult result = testee.initialCompact(request).block();
        assertThat(result.packedBlobs()).isEqualTo(2);

        List<RecordingBlobIdUpdater.Replacement> replacements = recordingUpdater.getReplacements();
        assertThat(replacements).hasSize(2);
        assertThat(replacements).extracting(r -> r.oldId().asString())
            .containsExactlyInAnyOrder("1_2_target1", "1_2_target2");
    }

    @Test
    void initialCompactShouldSkipPackingWhenCandidateCountIsOne() {
        BlobId singleBlob = new PlainBlobId("1_2_single");
        Mono.from(rawStore.save(TEST_BUCKET, singleBlob, BlobStoreDAO.BytesBlob.of("single-blob-payload"))).block();
        mappingSource.add(singleBlob, "msg-single");

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(TEST_BUCKET)
            .generation(TARGET_GENERATION)
            .family(FAMILY)
            .configuration(CompactionConfiguration.builder().chunkTargetSize(100_000).build())
            .build();

        CompactionResult result = testee.initialCompact(request).block();

        assertThat(result.packedBlobs()).isEqualTo(0);
        assertThat(result.chunksWritten()).isEqualTo(0);
        assertThat(recordingUpdater.getReplacements()).isEmpty();

        // Standalone blob remains untouched in raw storage
        assertThat(Mono.from(rawStore.readBytes(TEST_BUCKET, singleBlob)).block().payload())
            .isEqualTo("single-blob-payload".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void windowByCumulativeSizeShouldPartitionCandidatesWhenSizeExceedsTarget() {
        BlobStoreDAO.BlobMetadata empty = BlobStoreDAO.BlobMetadata.empty();
        List<BlobCompactionAlgorithm.CandidateBlob> candidates = List.of(
            new BlobCompactionAlgorithm.CandidateBlob(new PlainBlobId("1_2_b1"), new byte[30], empty),
            new BlobCompactionAlgorithm.CandidateBlob(new PlainBlobId("1_2_b2"), new byte[40], empty),
            new BlobCompactionAlgorithm.CandidateBlob(new PlainBlobId("1_2_b3"), new byte[50], empty),
            new BlobCompactionAlgorithm.CandidateBlob(new PlainBlobId("1_2_b4"), new byte[40], empty),
            new BlobCompactionAlgorithm.CandidateBlob(new PlainBlobId("1_2_b5"), new byte[20], empty)
        );

        List<List<BlobCompactionAlgorithm.CandidateBlob>> batches = BlobCompactionAlgorithm
            .windowByCumulativeSize(Flux.fromIterable(candidates), 80L)
            .collectList()
            .block();

        assertThat(batches).hasSize(3);
        // Batch 1: 30 + 40 = 70 bytes <= 80
        assertThat(batches.get(0)).extracting(c -> c.blobId().asString())
            .containsExactly("1_2_b1", "1_2_b2");
        // Batch 2: 50 bytes <= 80
        assertThat(batches.get(1)).extracting(c -> c.blobId().asString())
            .containsExactly("1_2_b3");
        // Batch 3: 40 + 20 = 60 bytes <= 80
        assertThat(batches.get(2)).extracting(c -> c.blobId().asString())
            .containsExactly("1_2_b4", "1_2_b5");
    }
}
