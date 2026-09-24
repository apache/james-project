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
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAO.Blob;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

class ChunkedBlobStoreDAOTest {
    private static final BucketName TEST_BUCKET = BucketName.of("test-bucket");
    private static final GenerationAwareBlobId.Configuration CONFIG =
        new GenerationAwareBlobId.Configuration(1, Duration.ofDays(30));

    static class CountingRawStore implements BlobStoreDAO {
        private final BlobStoreDAO delegate;
        private final AtomicInteger readRangeCallCount = new AtomicInteger();

        CountingRawStore(BlobStoreDAO delegate) {
            this.delegate = delegate;
        }

        int rangeCallCount() {
            return readRangeCallCount.get();
        }

        void resetCount() {
            readRangeCallCount.set(0);
        }

        @Override
        public Mono<Blob> readRange(BucketName bucketName, BlobId blobId, long start, long end) {
            readRangeCallCount.incrementAndGet();
            return delegate.readRange(bucketName, blobId, start, end);
        }

        @Override
        public InputStreamBlob read(BucketName bucketName, BlobId blobId) {
            return delegate.read(bucketName, blobId);
        }

        @Override
        public Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
            return delegate.readReactive(bucketName, blobId);
        }

        @Override
        public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
            return delegate.readBytes(bucketName, blobId);
        }

        @Override
        public Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
            return delegate.save(bucketName, blobId, blob);
        }

        @Override
        public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
            return delegate.delete(bucketName, blobId);
        }

        @Override
        public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
            return delegate.delete(bucketName, blobIds);
        }

        @Override
        public Publisher<Void> deleteBucket(BucketName bucketName) {
            return delegate.deleteBucket(bucketName);
        }

        @Override
        public Publisher<BucketName> listBuckets() {
            return delegate.listBuckets();
        }

        @Override
        public Publisher<BlobId> listBlobs(BucketName bucketName) {
            return delegate.listBlobs(bucketName);
        }

        @Override
        public Publisher<BlobId> listBlobs(BucketName bucketName, String prefix) {
            return delegate.listBlobs(bucketName, prefix);
        }
    }

    private MemoryBlobStoreDAO rawMemoryStore;
    private CountingRawStore countingRawStore;
    private ChunkedBlobStoreDAO testee;

    @BeforeEach
    void setUp() {
        rawMemoryStore = new MemoryBlobStoreDAO();
        countingRawStore = new CountingRawStore(rawMemoryStore);
        testee = new ChunkedBlobStoreDAO(countingRawStore);
    }

    @Test
    void plainIdsShouldPassThroughDirectlyToRawStore() {
        BlobId plainId = new PlainBlobId("normal-blob-id");
        byte[] payload = "Standard uncompressed plaintext email body".getBytes(StandardCharsets.UTF_8);

        Mono.from(testee.save(TEST_BUCKET, plainId, BlobStoreDAO.BytesBlob.of(payload))).block();

        byte[] readBack = Mono.from(testee.readBytes(TEST_BUCKET, plainId)).block().payload();
        assertThat(readBack).isEqualTo(payload);

        byte[] rawBytes = Mono.from(rawMemoryStore.readBytes(TEST_BUCKET, plainId)).block().payload();
        assertThat(rawBytes).isEqualTo(payload);
    }

    @Test
    void chunkSlotRefReadReturnsOriginalContentWithExactlyOneRangedRead() throws Exception {
        byte[] content1 = "First compacted message content".getBytes(StandardCharsets.UTF_8);
        byte[] content2 = "Second compacted message content".getBytes(StandardCharsets.UTF_8);

        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(
            ChunkFormat.BlobSlotContent.of(content1),
            ChunkFormat.BlobSlotContent.of(content2)
        ));

        ChunkId baseChunk = ChunkId.ofChunk(CONFIG, 690);
        Mono.from(testee.save(TEST_BUCKET, baseChunk, BlobStoreDAO.BytesBlob.of(chunkBytes))).block();

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        long offset1 = footer.slotStarts().get(0);
        long limit1 = footer.slotLength(0);
        long offset2 = footer.slotStarts().get(1);
        long limit2 = footer.slotLength(1);

        ChunkId slot1 = ChunkId.slotRef(baseChunk, offset1, limit1);
        ChunkId slot2 = ChunkId.slotRef(baseChunk, offset2, limit2);

        countingRawStore.resetCount();

        BlobStoreDAO.BytesBlob rawSlot1 = Mono.from(testee.readBytes(TEST_BUCKET, slot1)).block();
        assertThat(rawSlot1.payload()).isEqualTo(content1);
        assertThat(countingRawStore.rangeCallCount()).isEqualTo(1); // EXACTLY ONE ranged read!

        countingRawStore.resetCount();

        BlobStoreDAO.BytesBlob rawSlot2 = Mono.from(testee.readBytes(TEST_BUCKET, slot2)).block();
        assertThat(rawSlot2.payload()).isEqualTo(content2);
        assertThat(countingRawStore.rangeCallCount()).isEqualTo(1); // EXACTLY ONE ranged read!
    }

    @Test
    void limitZeroSlotReadPerformsFooterWalk() throws Exception {
        byte[] content = "Payload for footer walk".getBytes(StandardCharsets.UTF_8);
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(content)));

        ChunkId baseChunk = ChunkId.ofChunk(CONFIG, 690);
        Mono.from(testee.save(TEST_BUCKET, baseChunk, BlobStoreDAO.BytesBlob.of(chunkBytes))).block();

        ChunkId walkRef = ChunkId.slotRef(baseChunk, 1, 0); // limit == 0 triggers footer walk

        countingRawStore.resetCount();
        BlobStoreDAO.BytesBlob readResult = Mono.from(testee.readBytes(TEST_BUCKET, walkRef)).block();

        assertThat(readResult.payload()).isEqualTo(content);
        // Footer walk does 2 range reads: 1 for tail buffer (footer), 1 for slot data
        assertThat(countingRawStore.rangeCallCount()).isEqualTo(2);
    }

    @Test
    void deleteChunkSlotRefShouldThrowUnsupportedOperationException() {
        ChunkId baseChunk = ChunkId.ofChunk(CONFIG, 690);
        ChunkId slotRef = ChunkId.slotRef(baseChunk, 123, 456);

        assertThatThrownBy(() -> Mono.from(testee.delete(TEST_BUCKET, slotRef)).block())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Individual chunk slot refs cannot be deleted directly");
    }

    @Test
    void deleteWholeChunkIdShouldSucceed() throws Exception {
        byte[] content = "Whole chunk delete test".getBytes(StandardCharsets.UTF_8);
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(content)));

        ChunkId baseChunk = ChunkId.ofChunk(CONFIG, 690);
        Mono.from(testee.save(TEST_BUCKET, baseChunk, BlobStoreDAO.BytesBlob.of(chunkBytes))).block();

        assertThat(Mono.from(rawMemoryStore.readBytes(TEST_BUCKET, baseChunk.chunkBlobId())).block()).isNotNull();

        // Delete using chunkId (limit == 0)
        Mono.from(testee.delete(TEST_BUCKET, baseChunk)).block();

        assertThatThrownBy(() -> Mono.from(rawMemoryStore.readBytes(TEST_BUCKET, baseChunk.chunkBlobId())).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void deduplicationInvariantBothMessagesReadIdenticalBytesFromSameSlot() throws Exception {
        byte[] sharedEmailBody = "Identical deduplicated message body content".getBytes(StandardCharsets.UTF_8);

        // One slot in chunk holds shared content
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(sharedEmailBody)));
        ChunkId baseChunk = ChunkId.ofChunk(CONFIG, 690);
        Mono.from(testee.save(TEST_BUCKET, baseChunk, BlobStoreDAO.BytesBlob.of(chunkBytes))).block();

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        long offset = footer.slotStarts().get(0);
        long limit = footer.slotLength(0);

        ChunkId slotRefForMessageA = ChunkId.slotRef(baseChunk, offset, limit);
        ChunkId slotRefForMessageB = ChunkId.slotRef(baseChunk, offset, limit);

        assertThat(slotRefForMessageA).isEqualTo(slotRefForMessageB);

        BlobStoreDAO.BytesBlob readBlobA = Mono.from(testee.readBytes(TEST_BUCKET, slotRefForMessageA)).block();
        BlobStoreDAO.BytesBlob readBlobB = Mono.from(testee.readBytes(TEST_BUCKET, slotRefForMessageB)).block();

        assertThat(readBlobA.payload()).isEqualTo(sharedEmailBody);
        assertThat(readBlobB.payload()).isEqualTo(sharedEmailBody);
    }

    @Test
    void readRangeShouldThrowOnChunkRef() {
        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);
        ChunkId slot = ChunkId.slotRef(chunk, 100, 200);

        assertThatThrownBy(() -> testee.readRange(TEST_BUCKET, slot, 0, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot readRange on a chunk reference");
    }

    @Test
    void saveShouldThrowWhenCalledWithSlotRef() {
        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);
        ChunkId slot = ChunkId.slotRef(chunk, 100, 200);

        assertThatThrownBy(() -> Mono.from(testee.save(TEST_BUCKET, slot, BlobStoreDAO.BytesBlob.of("data".getBytes()))).block())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Individual chunk slot refs cannot be saved directly");
    }

    @Test
    void deleteShouldThrowWhenCalledWithSlotRef() {
        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);
        ChunkId slot = ChunkId.slotRef(chunk, 100, 200);

        assertThatThrownBy(() -> Mono.from(testee.delete(TEST_BUCKET, slot)).block())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Individual chunk slot refs cannot be deleted directly");
    }

    @Test
    void batchDeleteShouldThrowWhenCollectionContainsSlotRef() {
        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);
        ChunkId slot = ChunkId.slotRef(chunk, 100, 200);
        BlobId plainId = new PlainBlobId("plain-id");

        assertThatThrownBy(() -> Mono.from(testee.delete(TEST_BUCKET, List.of(plainId, slot))).block())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Individual chunk slot refs cannot be deleted directly");
    }
}
