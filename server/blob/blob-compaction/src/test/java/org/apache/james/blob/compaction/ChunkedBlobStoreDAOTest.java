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

import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.blob.aes.AESBlobStoreDAO;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAO.Blob;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;
import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;
import org.apache.james.blob.api.BlobStoreDAO.ContentEncoding;
import org.apache.james.blob.api.BlobStoreDAO.InputStreamBlob;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.BlobStoreDAORangeReadContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.MetadataAwareBlobStoreDAOContract;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.blob.zstd.CompressionConfiguration;
import org.apache.james.blob.zstd.ZstdBlobStoreDAO;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

class ChunkedBlobStoreDAOTest implements BlobStoreDAOContract, MetadataAwareBlobStoreDAOContract, BlobStoreDAORangeReadContract {
    private static final GenerationAwareBlobId.Factory BLOB_ID_FACTORY = new GenerationAwareBlobId.Factory(
        Clock.systemUTC(), new PlainBlobId.Factory(), GenerationAwareBlobId.Configuration.DEFAULT);
    private static final BlobId SAME_GENERATION = BLOB_ID_FACTORY.parse("1_689_sample");
    private static final BytesBlob FIRST = BytesBlob.of("first".getBytes(StandardCharsets.UTF_8),
        BlobMetadata.empty().withMetadata(new BlobMetadataName("origin"), new BlobMetadataValue("first")));
    private static final BytesBlob SECOND = BytesBlob.of("second".getBytes(StandardCharsets.UTF_8));
    private static final byte[] COMPRESSIBLE = "compressible content ".repeat(500).getBytes(StandardCharsets.UTF_8);

    private CorruptibleMemoryBlobStoreDAO storage;
    private ChunkedBlobStoreDAO testee;

    @BeforeEach
    void setUp() {
        storage = new CorruptibleMemoryBlobStoreDAO();
        testee = new ChunkedBlobStoreDAO(storage, BLOB_ID_FACTORY);
    }

    @Override
    public BlobStoreDAO testee() {
        return testee;
    }

    @Override
    @Disabled("Not supported by the Memory blob store")
    public void listBucketsShouldReturnBucketsWithNoBlob() {
    }

    @Nested
    class Slots {
        @Test
        void readBytesShouldReturnEachSlotWithItsMetadata() {
            List<BlobId> slots = storeChunk(FIRST, SECOND);

            assertThat(slots).map(slot -> Mono.from(testee.readBytes(TEST_BUCKET_NAME, slot)).block())
                .containsExactly(FIRST, SECOND);
        }

        @Test
        void readShouldReturnTheSlot() throws Exception {
            BlobId slot = storeChunk(FIRST, SECOND).get(1);

            InputStreamBlob blob = testee.read(TEST_BUCKET_NAME, slot);

            assertThat(blob.payload().readAllBytes()).isEqualTo(SECOND.payload());
            assertThat(blob.metadata()).isEqualTo(SECOND.metadata());
        }

        @Test
        void readReactiveShouldReturnTheSlot() throws Exception {
            BlobId slot = storeChunk(FIRST, SECOND).get(0);

            InputStreamBlob blob = Mono.from(testee.readReactive(TEST_BUCKET_NAME, slot)).block();

            assertThat(blob.payload().readAllBytes()).isEqualTo(FIRST.payload());
            assertThat(blob.metadata()).isEqualTo(FIRST.metadata());
        }

        @Test
        void slotShouldBeReadWithASingleRangedRead() {
            BlobId slot = storeChunk(FIRST, SECOND).get(1);

            Mono.from(testee.readBytes(TEST_BUCKET_NAME, slot)).block();

            assertThat(storage.rangeReads).isEqualTo(1);
            assertThat(storage.wholeObjectReads()).isZero();
        }

        @Test
        void zstdAboveShouldDecompressSlots() {
            CompressionConfiguration alwaysCompress = CompressionConfiguration.builder().enabled(true).threshold(1).build();
            BlobId slot = compactThrough(new ZstdBlobStoreDAO(storage, alwaysCompress, new RecordingMetricFactory()));

            assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, slot)).block().metadata().contentEncoding())
                .contains(ContentEncoding.ZSTD);
            assertThat(Mono.from(new ZstdBlobStoreDAO(testee, alwaysCompress, new RecordingMetricFactory()).readBytes(TEST_BUCKET_NAME, slot)).block().payload())
                .isEqualTo(COMPRESSIBLE);
        }

        @Test
        void aesAboveShouldDecryptSlots() {
            CryptoConfig cryptoConfig = CryptoConfig.builder()
                .salt("c603a7327ee3dcbc031d8d34b1096c605feca5e1")
                .password("testing".toCharArray())
                .build();
            BlobId slot = compactThrough(new AESBlobStoreDAO(storage, cryptoConfig));

            assertThat(Mono.from(new AESBlobStoreDAO(testee, cryptoConfig).readBytes(TEST_BUCKET_NAME, slot)).block().payload())
                .isEqualTo(COMPRESSIBLE);
        }

        @Test
        void missingChunkShouldFailAsNotFound() {
            BlobId slot = new SlotAddress(ChunkIds.newChunk(SAME_GENERATION, BLOB_ID_FACTORY), new SlotLocation(ChunkFormat.HEADER_SIZE, 20))
                .asBlobId(BLOB_ID_FACTORY);

            assertThatThrownBy(() -> Mono.from(testee.readBytes(TEST_BUCKET_NAME, slot)).block())
                .isInstanceOf(ObjectNotFoundException.class);
        }

        @Test
        void slotOutsideTheChunkShouldFailAsAFormatError() {
            BlobId chunk = SlotAddress.parse(storeChunk(FIRST).get(0), BLOB_ID_FACTORY).orElseThrow().chunk();
            BlobId slot = new SlotAddress(chunk, new SlotLocation(100_000, 20)).asBlobId(BLOB_ID_FACTORY);

            assertThatThrownBy(() -> Mono.from(testee.readBytes(TEST_BUCKET_NAME, slot)).block())
                .isInstanceOf(ChunkFormatException.class);
        }

        @Test
        void slotNotStartingAtASlotBoundaryShouldFailAsAFormatError() {
            SlotAddress first = SlotAddress.parse(storeChunk(FIRST, SECOND).get(0), BLOB_ID_FACTORY).orElseThrow();
            BlobId shifted = new SlotAddress(first.chunk(), new SlotLocation(first.location().offset() + 1, first.location().length()))
                .asBlobId(BLOB_ID_FACTORY);

            assertThatThrownBy(() -> Mono.from(testee.readBytes(TEST_BUCKET_NAME, shifted)).block())
                .isInstanceOf(ChunkFormatException.class);
        }

        @Test
        void corruptedRangeShouldBeRecoveredFromTheWholeChunk() {
            BlobId slot = storeChunk(FIRST, SECOND).get(1);
            storage.corruptNextRangeRead.set(true);

            assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, slot)).block()).isEqualTo(SECOND);
            assertThat(storage.wholeObjectReads()).isEqualTo(1);
        }

        @Test
        void corruptedChunkShouldFail() {
            BlobId slot = storeChunk(FIRST, SECOND).get(1);
            SlotAddress address = SlotAddress.parse(slot, BLOB_ID_FACTORY).orElseThrow();
            byte[] chunk = Mono.from(storage.readBytes(TEST_BUCKET_NAME, address.chunk())).block().payload();
            chunk[Math.toIntExact(address.location().offset()) + ChunkFormat.METADATA_LENGTH_SIZE + 3] ^= 1;
            Mono.from(storage.save(TEST_BUCKET_NAME, address.chunk(), BytesBlob.of(chunk))).block();

            assertThatThrownBy(() -> Mono.from(testee.readBytes(TEST_BUCKET_NAME, slot)).block())
                .isInstanceOf(CorruptedSlotException.class);
        }

        @Test
        void readRangeShouldRejectSlotAddresses() {
            BlobId slot = storeChunk(FIRST).get(0);

            assertThatThrownBy(() -> testee.readRange(TEST_BUCKET_NAME, slot, 0, Optional.of(1L)).block())
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void saveShouldRejectChunkIds() {
            BlobId chunk = ChunkIds.newChunk(SAME_GENERATION, BLOB_ID_FACTORY);

            assertThatThrownBy(() -> Mono.from(testee.save(TEST_BUCKET_NAME, chunk, FIRST)).block())
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deleteShouldKeepTheChunk() {
            BlobId slot = storeChunk(FIRST, SECOND).get(0);
            BlobId chunk = SlotAddress.parse(slot, BLOB_ID_FACTORY).orElseThrow().chunk();

            Mono.from(testee.delete(TEST_BUCKET_NAME, slot)).block();
            Mono.from(testee.delete(TEST_BUCKET_NAME, chunk)).block();

            assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, slot)).block()).isEqualTo(FIRST);
        }

        @Test
        void bulkDeleteShouldOnlyDeleteStandaloneBlobs() {
            BlobId slot = storeChunk(FIRST).get(0);
            BlobId standalone = BLOB_ID_FACTORY.parse("1_689_standalone");
            Mono.from(testee.save(TEST_BUCKET_NAME, standalone, SECOND)).block();

            Mono.from(testee.delete(TEST_BUCKET_NAME, List.of(slot, standalone))).block();

            assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, slot)).block()).isEqualTo(FIRST);
            assertThatThrownBy(() -> Mono.from(testee.readBytes(TEST_BUCKET_NAME, standalone)).block())
                .isInstanceOf(ObjectNotFoundException.class);
        }
    }

    /** Stores a chunk directly in the storage DAO, as compaction does, and returns the slot addresses. */
    private List<BlobId> storeChunk(BytesBlob... blobs) {
        ChunkWriter writer = new ChunkWriter();
        List<SlotLocation> locations = List.of(blobs).stream().map(writer::addSlot).toList();
        BlobId chunk = ChunkIds.newChunk(SAME_GENERATION, BLOB_ID_FACTORY);
        Mono.from(storage.save(TEST_BUCKET_NAME, chunk, BytesBlob.of(writer.finish()))).block();
        storage.resetCounters();
        return locations.stream()
            .map(location -> new SlotAddress(chunk, location).asBlobId(BLOB_ID_FACTORY))
            .toList();
    }

    /** Saves through the decorator, then packs the stored form of the blob into a chunk. */
    private BlobId compactThrough(BlobStoreDAO decorator) {
        BlobId standalone = BLOB_ID_FACTORY.parse("1_689_standalone");
        Mono.from(decorator.save(TEST_BUCKET_NAME, standalone, BytesBlob.of(COMPRESSIBLE))).block();
        BytesBlob stored = Mono.from(storage.readBytes(TEST_BUCKET_NAME, standalone)).block();
        return storeChunk(SECOND, stored).get(1);
    }

    private static class CorruptibleMemoryBlobStoreDAO extends MemoryBlobStoreDAO {
        private final AtomicBoolean corruptNextRangeRead = new AtomicBoolean();
        private int rangeReads = 0;
        private int readBytesCalls = 0;

        @Override
        public Mono<Blob> readRange(BucketName bucketName, BlobId blobId, long start, Optional<Long> end) {
            rangeReads++;
            return super.readRange(bucketName, blobId, start, end)
                .map(blob -> corruptNextRangeRead.getAndSet(false) ? flipFirstByte(blob) : blob);
        }

        @Override
        public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
            readBytesCalls++;
            return super.readBytes(bucketName, blobId);
        }

        /** The memory ranged read reads the whole object, so it is subtracted. */
        int wholeObjectReads() {
            return readBytesCalls - rangeReads;
        }

        void resetCounters() {
            rangeReads = 0;
            readBytesCalls = 0;
        }

        private static Blob flipFirstByte(Blob blob) {
            BytesBlob bytes = (BytesBlob) blob;
            byte[] payload = bytes.payload().clone();
            payload[0] ^= 1;
            return BytesBlob.of(payload, bytes.metadata());
        }
    }
}
