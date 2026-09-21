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

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.james.blob.api.ObjectStoreIOException;
import org.junit.jupiter.api.Test;

class ChunkFormatTest {

    @Test
    void writeShouldStartWithFormatByte() throws Exception {
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of());
        assertThat(chunkBytes[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void singleSlotRoundTrip() throws Exception {
        byte[] raw = "Hello Apache James S3 Compaction!".getBytes(StandardCharsets.UTF_8);
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(raw)));

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        assertThat(footer.slotCount()).isEqualTo(1);
        assertThat(footer.slotStarts()).containsExactly(1L);

        long start = footer.slotStarts().get(0);
        long end = footer.footerPosition();

        byte[] slotData = Arrays.copyOfRange(chunkBytes, (int) start, (int) end);
        byte[] decompressed = ChunkFormat.readSlot(new ByteArrayInputStream(slotData), start, end);

        assertThat(decompressed).isEqualTo(raw);
    }

    @Test
    void multipleSlotsRoundTrip() throws Exception {
        byte[] raw1 = "Slot 1: Small text payload".getBytes(StandardCharsets.UTF_8);
        byte[] raw2 = "Slot 2: Another chunked email part with somewhat larger content".getBytes(StandardCharsets.UTF_8);
        byte[] raw3 = "Slot 3: Final message content in chunk".getBytes(StandardCharsets.UTF_8);

        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(
            ChunkFormat.BlobSlotContent.of(raw1),
            ChunkFormat.BlobSlotContent.of(raw2),
            ChunkFormat.BlobSlotContent.of(raw3)
        ));

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        assertThat(footer.slotCount()).isEqualTo(3);
        assertThat(footer.slotStarts().get(0)).isEqualTo(1L);

        for (int i = 0; i < 3; i++) {
            long start = footer.slotStarts().get(i);
            long end = (i == 2) ? footer.footerPosition() : footer.slotStarts().get(i + 1);

            byte[] slotSlice = Arrays.copyOfRange(chunkBytes, (int) start, (int) end);
            byte[] decompressed = ChunkFormat.readSlot(new ByteArrayInputStream(slotSlice), start, end);

            byte[] expected = switch (i) {
                case 0 -> raw1;
                case 1 -> raw2;
                case 2 -> raw3;
                default -> throw new IllegalStateException();
            };
            assertThat(decompressed).isEqualTo(expected);
        }
    }

    @Test
    void emptyContentSlotRoundTrip() throws Exception {
        byte[] raw = new byte[0];
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(raw)));

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        assertThat(footer.slotCount()).isEqualTo(1);

        long start = footer.slotStarts().get(0);
        long end = footer.footerPosition();

        byte[] slotData = Arrays.copyOfRange(chunkBytes, (int) start, (int) end);
        byte[] decompressed = ChunkFormat.readSlot(new ByteArrayInputStream(slotData), start, end);

        assertThat(decompressed).isEmpty();
    }

    @Test
    void crcCorruptionShouldThrowObjectStoreIOException() throws Exception {
        byte[] raw = "Data that will be corrupted".getBytes(StandardCharsets.UTF_8);
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(raw)));

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        long start = footer.slotStarts().get(0);
        long end = footer.footerPosition();

        byte[] slotData = Arrays.copyOfRange(chunkBytes, (int) start, (int) end);

        // Corrupt CRC32C field (bytes 8-11 in slot)
        slotData[8] ^= 0x55;

        assertThatThrownBy(() -> ChunkFormat.readSlot(new ByteArrayInputStream(slotData), start, end))
            .isInstanceOf(ObjectStoreIOException.class)
            .hasMessageContaining("CRC mismatch");
    }

    @Test
    void corruptContentByteShouldFailDecompressionOrCRC() throws Exception {
        byte[] raw = "Data to corrupt in compressed payload".getBytes(StandardCharsets.UTF_8);
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(raw)));

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        long start = footer.slotStarts().get(0);
        long end = footer.footerPosition();

        byte[] slotData = Arrays.copyOfRange(chunkBytes, (int) start, (int) end);

        // Corrupt the last byte of the slot (compressed content)
        slotData[slotData.length - 1] ^= 0xFF;

        assertThatThrownBy(() -> ChunkFormat.readSlot(new ByteArrayInputStream(slotData), start, end))
            .isInstanceOf(ObjectStoreIOException.class);
    }

    @Test
    void truncatedFooterShouldThrowObjectStoreIOException() {
        byte[] truncated = new byte[6]; // Less than 12 bytes
        assertThatThrownBy(() -> ChunkFormat.readFooter(truncated))
            .isInstanceOf(ObjectStoreIOException.class)
            .hasMessageContaining("Tail buffer is too short");
    }

    @Test
    void footerReadableFromTailBuffer() throws Exception {
        byte[] raw = "Some content for tail buffer test".getBytes(StandardCharsets.UTF_8);
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(raw)));

        // Pretend chunk is part of a larger file, or tail buffer is exactly 65536 bytes
        int tailSize = Math.min(chunkBytes.length, 65536);
        byte[] tailBuffer = Arrays.copyOfRange(chunkBytes, chunkBytes.length - tailSize, chunkBytes.length);

        ChunkFooter footer = ChunkFormat.readFooter(tailBuffer, chunkBytes.length);
        assertThat(footer.slotCount()).isEqualTo(1);
        assertThat(footer.slotStarts()).containsExactly(1L);
    }

    @Test
    void bigPayloadRoundTrip() throws Exception {
        byte[] raw = new byte[512 * 1024]; // 512 KB
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i % 127);
        }

        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(raw)));
        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);

        long start = footer.slotStarts().get(0);
        long end = footer.footerPosition();

        byte[] slotData = Arrays.copyOfRange(chunkBytes, (int) start, (int) end);
        byte[] decompressed = ChunkFormat.readSlot(new ByteArrayInputStream(slotData), start, end);

        assertThat(decompressed).isEqualTo(raw);
    }

    @Test
    void verifyBinaryLayoutExactStructure() throws Exception {
        byte[] raw = "Test".getBytes(StandardCharsets.UTF_8);
        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(raw)));

        // Byte 0: format byte 0x01
        assertThat(chunkBytes[0]).isEqualTo((byte) 0x01);

        // Bytes 1..8: contentStart = 1L
        ByteBuffer bb = ByteBuffer.wrap(chunkBytes);
        assertThat(bb.getLong(1)).isEqualTo(1L);

        // Bytes 9..12: CRC32C of "Test"
        int crc = bb.getInt(9);
        java.util.zip.CRC32C crcCalculator = new java.util.zip.CRC32C();
        crcCalculator.update(raw);
        assertThat(crc).isEqualTo((int) crcCalculator.getValue());

        // Followed by metadata
        String metadataExpected = "content-encoding=zstd\ncontent-original-size=4\n";
        byte[] metadataBytes = metadataExpected.getBytes(StandardCharsets.US_ASCII);
        byte[] actualMetadata = Arrays.copyOfRange(chunkBytes, 13, 13 + metadataBytes.length);
        assertThat(actualMetadata).isEqualTo(metadataBytes);

        // Last 12 bytes
        int totalLen = chunkBytes.length;
        int footerLength = bb.getInt(totalLen - 12);
        long footerPosition = bb.getLong(totalLen - 8);

        assertThat(footerPosition + footerLength).isEqualTo(totalLen - 12);
        // footer content should be "1"
        byte[] footerBytes = Arrays.copyOfRange(chunkBytes, (int) footerPosition, (int) footerPosition + footerLength);
        assertThat(new String(footerBytes, StandardCharsets.US_ASCII)).isEqualTo("1");
    }
}
