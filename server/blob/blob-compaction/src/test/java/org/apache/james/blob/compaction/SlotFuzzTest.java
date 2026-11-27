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

import java.util.Arrays;

import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.DictionaryFile;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Slot encoding: safety, canonicity, sizes and error classes. Normal builds replay the committed inputs; fuzz one
 * target with e.g. {@code JAZZER_FUZZ=1 mvn -pl server/blob/blob-compaction test -Dtest=SlotFuzzTest#parsedSlotsShouldBeCanonical}.
 */
class SlotFuzzTest {
    /** A parsed slot encodes back to its exact bytes, so that two encodings never denote the same blob. */
    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void parsedSlotsShouldBeCanonical(FuzzedDataProvider data) {
        boolean validChecksum = data.consumeBoolean();
        byte[] bytes = data.consumeRemainingAsBytes();
        byte[] slot = validChecksum ? ChunkFormatTest.withCrc(bytes) : bytes;
        if (slot.length < ChunkFormat.MIN_SLOT_SIZE) {
            return;
        }
        BytesBlob blob;
        try {
            blob = ChunkParser.parseSlot(slot, new SlotLocation(ChunkFormat.HEADER_SIZE, slot.length));
        } catch (ChunkFormatException e) {
            return;
        }

        assertThat(ChunkFormatTest.encodeSlot(blob)).isEqualTo(slot);
    }

    /**
     * Either the writer refuses a blob, or it reads back normalized. Sizes are exact and slot bytes do not depend on their
     * position, so that compaction can size chunks and copy slots verbatim.
     */
    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void writtenSlotsShouldReadBackNormalized(FuzzedDataProvider data) {
        ChunkWriter writer = new ChunkWriter();
        long expectedOffset = ChunkFormat.HEADER_SIZE;
        int slotCount = data.consumeInt(0, 6);
        BytesBlob[] blobs = new BytesBlob[slotCount];
        SlotLocation[] locations = new SlotLocation[slotCount];
        int written = 0;
        for (int i = 0; i < slotCount; i++) {
            BytesBlob blob = FuzzedBlobs.blob(data);
            if (!FuzzedBlobs.encodable(blob)) {
                assertThatThrownBy(() -> writer.addSlot(blob)).isInstanceOf(IllegalArgumentException.class);
                continue;
            }
            SlotLocation location = writer.addSlot(blob);
            assertThat(location.offset()).isEqualTo(expectedOffset);
            assertThat(location.length()).isEqualTo(ChunkWriter.encodedSlotSize(blob));
            expectedOffset = location.end();
            blobs[written] = blob;
            locations[written] = location;
            written++;
        }
        byte[] chunk = writer.finish();

        assertThat((long) chunk.length).isEqualTo(expectedOffset + ChunkFormat.FOOTER_PREFIX_SIZE
            + (long) ChunkFormat.FOOTER_ENTRY_SIZE * written + ChunkFormat.TRAILER_SIZE);
        assertThat(ChunkFormatTest.readFooter(chunk).slots()).containsExactly(Arrays.copyOf(locations, written));
        for (int i = 0; i < written; i++) {
            byte[] slot = ChunkParser.sliceSlot(chunk, locations[i]);
            assertThat(ChunkParser.parseSlot(slot, locations[i])).isEqualTo(FuzzedBlobs.normalized(blobs[i]));
            assertThat(slot).isEqualTo(ChunkFormatTest.encodeSlot(blobs[i]));
        }
    }

    /**
     * Only a checksum mismatch triggers the whole-chunk read. Slots being CRC-32C codewords, any change confined to four
     * consecutive bytes is a checksum mismatch, while a slot of the wrong length is a plain format error.
     */
    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void damagedSlotsShouldBeClassified(FuzzedDataProvider data) {
        BytesBlob blob = FuzzedBlobs.blob(data);
        if (!FuzzedBlobs.encodable(blob)) {
            return;
        }
        byte[] slot = ChunkFormatTest.encodeSlot(blob);
        SlotLocation location = new SlotLocation(ChunkFormat.HEADER_SIZE, slot.length);

        switch (data.consumeInt(0, 2)) {
            case 0 -> assertThatThrownBy(() -> ChunkParser.parseSlot(burst(slot, data), location))
                .isInstanceOf(CorruptedSlotException.class);
            case 1 -> assertThatThrownBy(() -> ChunkParser.parseSlot(Arrays.copyOf(slot, data.consumeInt(0, slot.length - 1)), location))
                .isInstanceOf(ChunkFormatException.class)
                .isNotInstanceOf(CorruptedSlotException.class);
            default -> assertThatThrownBy(() -> ChunkParser.parseSlot(extended(slot, data), location))
                .isInstanceOf(ChunkFormatException.class)
                .isNotInstanceOf(CorruptedSlotException.class);
        }
    }

    /** Changes at most four consecutive bytes, at least one of them. */
    private static byte[] burst(byte[] slot, FuzzedDataProvider data) {
        byte[] damaged = slot.clone();
        int start = data.consumeInt(0, slot.length - 1);
        int width = data.consumeInt(1, Math.min(4, slot.length - start));
        for (int i = 0; i < width; i++) {
            damaged[start + i] ^= data.consumeByte();
        }
        if (Arrays.equals(damaged, slot)) {
            damaged[start] ^= 1;
        }
        return damaged;
    }

    private static byte[] extended(byte[] slot, FuzzedDataProvider data) {
        byte[] extra = data.consumeBytes(16);
        byte[] extended = Arrays.copyOf(slot, slot.length + Math.max(1, extra.length));
        System.arraycopy(extra, 0, extended, slot.length, extra.length);
        return extended;
    }
}
