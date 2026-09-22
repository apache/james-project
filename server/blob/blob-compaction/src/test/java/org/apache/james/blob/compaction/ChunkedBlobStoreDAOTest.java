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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.blob.aes.AESBlobStoreDAO;
import org.apache.james.blob.aes.CryptoConfig;
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

import com.github.luben.zstd.Zstd;

import reactor.core.publisher.Mono;

class ChunkedBlobStoreDAOTest {
    private static final BucketName TEST_BUCKET = BucketName.of("test-bucket");
    private static final GenerationAwareBlobId.Configuration CONFIG =
        new GenerationAwareBlobId.Configuration(1, Duration.ofDays(30));
    private static final String SAMPLE_SALT = "c603a7327ee3dcbc031d8d34b1096c605feca5e1";
    private static final CryptoConfig CRYPTO_CONFIG = CryptoConfig.builder()
        .salt(SAMPLE_SALT)
        .password("testing-password".toCharArray())
        .build();

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
    private AESBlobStoreDAO plainChain;
    private ChunkedBlobStoreDAO testee;

    @BeforeEach
    void setUp() {
        rawMemoryStore = new MemoryBlobStoreDAO();
        countingRawStore = new CountingRawStore(rawMemoryStore);

        // Plain chain: AES(raw)
        plainChain = new AESBlobStoreDAO(countingRawStore, CRYPTO_CONFIG);

        // Outermost: Chunked(plainChain, raw)
        testee = new ChunkedBlobStoreDAO(plainChain, countingRawStore);
    }

    @Test
    void plainIdsShouldPassThroughPlainChainByteIdentical() {
        BlobId plainId = new PlainBlobId("normal-blob-id");
        byte[] payload = "Standard uncompressed plaintext email body".getBytes(StandardCharsets.UTF_8);

        Mono.from(testee.save(TEST_BUCKET, plainId, BlobStoreDAO.BytesBlob.of(payload))).block();

        byte[] readBack = Mono.from(testee.readBytes(TEST_BUCKET, plainId)).block().payload();
        assertThat(readBack).isEqualTo(payload);

        // Confirm raw storage holds encrypted/compressed bytes, not plain text
        byte[] rawBytes = Mono.from(rawMemoryStore.readBytes(TEST_BUCKET, plainId)).block().payload();
        assertThat(rawBytes).isNotEqualTo(payload);
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

        // ChunkedBlobStoreDAO returns slot tagged with ContentEncoding.ZSTD
        BlobStoreDAO.BytesBlob rawSlot1 = Mono.from(testee.readBytes(TEST_BUCKET, slot1)).block();
        assertThat(rawSlot1.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
        assertThat(rawSlot1.metadata().get(BlobSlot.CONTENT_ORIGINAL_SIZE))
            .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(content1.length)));
        byte[] decompressed1 = Zstd.decompress(rawSlot1.payload(), content1.length);
        assertThat(decompressed1).isEqualTo(content1);
        assertThat(countingRawStore.rangeCallCount()).isEqualTo(1); // EXACTLY ONE ranged read!

        countingRawStore.resetCount();

        BlobStoreDAO.BytesBlob rawSlot2 = Mono.from(testee.readBytes(TEST_BUCKET, slot2)).block();
        assertThat(rawSlot2.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
        byte[] decompressed2 = Zstd.decompress(rawSlot2.payload(), content2.length);
        assertThat(decompressed2).isEqualTo(content2);
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

        assertThat(readResult.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
        byte[] decompressed = Zstd.decompress(readResult.payload(), content.length);
        assertThat(decompressed).isEqualTo(content);
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

        assertThat(readBlobA.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
        assertThat(readBlobB.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
        assertThat(readBlobA.payload()).isEqualTo(readBlobB.payload());

        byte[] decompressedA = Zstd.decompress(readBlobA.payload(), sharedEmailBody.length);
        byte[] decompressedB = Zstd.decompress(readBlobB.payload(), sharedEmailBody.length);

        assertThat(decompressedA).isEqualTo(sharedEmailBody);
        assertThat(decompressedB).isEqualTo(sharedEmailBody);
    }

    @Test
    void readChunkSlotShouldTriggerRepairWhenCollaboratorProvided() throws Exception {
        byte[] content = "Self-healing test data".getBytes(StandardCharsets.UTF_8);
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(content)));

        ChunkId baseChunk = ChunkId.ofChunk(CONFIG, 690);
        Mono.from(testee.save(TEST_BUCKET, baseChunk, BlobStoreDAO.BytesBlob.of(chunkBytes))).block();

        ChunkId missingChunk = ChunkId.ofChunk(CONFIG, 999);
        ChunkId staleSlot = ChunkId.slotRef(missingChunk, 0, 100);

        // Save original plain blob in plainChain
        BlobId originalPlainBlob = new PlainBlobId("original-pre-compaction-blob");
        Mono.from(plainChain.save(TEST_BUCKET, originalPlainBlob, BlobStoreDAO.BytesBlob.of(content))).block();

        // Repairer that repairs staleSlot to originalPlainBlob
        BlobIdRepairer repairer = (bucket, staleBlobId) -> {
            if (staleBlobId.equals(staleSlot)) {
                return Mono.just(originalPlainBlob);
            }
            return Mono.empty();
        };

        ChunkedBlobStoreDAO daoWithRepairer = new ChunkedBlobStoreDAO(plainChain, countingRawStore, Optional.of(repairer));

        byte[] repairedRead = Mono.from(daoWithRepairer.readBytes(TEST_BUCKET, staleSlot)).block().payload();
        assertThat(repairedRead).isEqualTo(content);
    }

    @Test
    void readChunkSlotShouldTriggerRepairWhenRepairedToAnotherSlotRef() throws Exception {
        byte[] content = "Repaired slot data".getBytes(StandardCharsets.UTF_8);
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(content)));

        ChunkId baseChunk = ChunkId.ofChunk(CONFIG, 700);
        Mono.from(testee.save(TEST_BUCKET, baseChunk, BlobStoreDAO.BytesBlob.of(chunkBytes))).block();

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        ChunkId validSlot = ChunkId.slotRef(baseChunk, footer.slotStarts().get(0), footer.slotLength(0));
        ChunkId staleSlot = ChunkId.slotRef(baseChunk, 999999L, 100L);

        BlobIdRepairer repairer = (bucket, staleBlobId) -> {
            if (staleBlobId.equals(staleSlot)) {
                return Mono.just(validSlot);
            }
            return Mono.empty();
        };

        ChunkedBlobStoreDAO daoWithRepairer = new ChunkedBlobStoreDAO(plainChain, countingRawStore, Optional.of(repairer));
        BlobStoreDAO.BytesBlob repairedSlot = Mono.from(daoWithRepairer.readBytes(TEST_BUCKET, staleSlot)).block();
        assertThat(repairedSlot.metadata().contentEncoding()).contains(BlobStoreDAO.ContentEncoding.ZSTD);
        byte[] decompressed = Zstd.decompress(repairedSlot.payload(), content.length);
        assertThat(decompressed).isEqualTo(content);
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
