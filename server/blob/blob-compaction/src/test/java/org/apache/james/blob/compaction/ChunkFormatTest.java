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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;
import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;
import org.apache.james.blob.api.BlobStoreDAO.ContentEncoding;
import org.junit.jupiter.api.Test;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

class ChunkFormatTest {
    private static final BlobMetadata METADATA = BlobMetadata.empty()
        .withContentEncoding(ContentEncoding.ZSTD)
        .withMetadata(new BlobMetadataName("content-original-size"), new BlobMetadataValue("12"));
    private static final BytesBlob FIRST = BytesBlob.of("first payload".getBytes(StandardCharsets.UTF_8), METADATA);
    private static final char UNPAIRED_HIGH_SURROGATE = (char) 0xD800;
    private static final BytesBlob EMPTY = BytesBlob.of(new byte[0]);
    private static final BytesBlob THIRD = BytesBlob.of("third".getBytes(StandardCharsets.UTF_8),
        BlobMetadata.empty().withMetadata(new BlobMetadataName("note"), new BlobMetadataValue("é ü")));

    @Test
    void slotsShouldRoundTripWithTheirMetadata() {
        ChunkWriter writer = new ChunkWriter();
        List<SlotLocation> locations = List.of(writer.addSlot(FIRST), writer.addSlot(EMPTY), writer.addSlot(THIRD));
        byte[] chunk = writer.finish();

        assertThat(locations.stream().map(location -> ChunkParser.parseSlot(ChunkParser.sliceSlot(chunk, location), location)))
            .containsExactly(FIRST, EMPTY, THIRD);
    }

    @Test
    void encodedSlotSizeShouldMatchTheWrittenSlot() {
        ChunkWriter writer = new ChunkWriter();

        assertThat(writer.addSlot(FIRST).length()).isEqualTo(ChunkWriter.encodedSlotSize(FIRST));
        assertThat(writer.addSlot(EMPTY).length()).isEqualTo(ChunkWriter.encodedSlotSize(EMPTY));
    }

    @Test
    void footerShouldListContiguousSlots() {
        ChunkWriter writer = new ChunkWriter();
        List<SlotLocation> locations = List.of(writer.addSlot(FIRST), writer.addSlot(EMPTY), writer.addSlot(THIRD));
        byte[] chunk = writer.finish();

        assertThat(readFooter(chunk).slots()).isEqualTo(locations);
    }

    @Test
    void emptyChunkShouldHaveAnEmptyFooter() {
        byte[] chunk = new ChunkWriter().finish();

        assertThat(readFooter(chunk).slots()).isEmpty();
    }

    @Test
    void totalObjectSizeShouldNotBeStored() {
        BytesBlob rangeResult = BytesBlob.of("abc".getBytes(StandardCharsets.UTF_8), METADATA.withTotalObjectSize(3));
        ChunkWriter writer = new ChunkWriter();
        SlotLocation location = writer.addSlot(rangeResult);
        byte[] chunk = writer.finish();

        assertThat(ChunkParser.parseSlot(ChunkParser.sliceSlot(chunk, location), location).metadata()).isEqualTo(METADATA);
    }

    @Test
    void chunkLengthShouldFollowTheFormat() {
        ChunkWriter writer = new ChunkWriter();
        long slotsLength = writer.addSlot(FIRST).length() + writer.addSlot(THIRD).length();

        assertThat((long) writer.finish().length).isEqualTo(ChunkFormat.HEADER_SIZE + slotsLength
            + ChunkFormat.FOOTER_PREFIX_SIZE + 2 * ChunkFormat.FOOTER_ENTRY_SIZE + ChunkFormat.TRAILER_SIZE);
    }

    @Test
    void slotBytesShouldNotDependOnTheirPosition() {
        assertThat(Slot.of(THIRD).bytes).isEqualTo(Slot.at(THIRD, FIRST, EMPTY).bytes);
    }

    @Test
    void parsedSlotsShouldEncodeBackToTheSameBytes() {
        Slot slot = Slot.of(THIRD);

        assertThat(Slot.of(slot.parse()).bytes).isEqualTo(slot.bytes);
    }

    @Test
    void writerShouldRejectUnpairedSurrogates() {
        ChunkWriter writer = new ChunkWriter();
        BytesBlob unpairedSurrogate = BytesBlob.of(new byte[0],
            BlobMetadata.empty().withMetadata(new BlobMetadataName("note"), new BlobMetadataValue(String.valueOf(UNPAIRED_HIGH_SURROGATE))));

        assertThatThrownBy(() -> writer.addSlot(unpairedSurrogate)).isInstanceOf(IllegalArgumentException.class);
        assertThat(writer.addSlot(FIRST).offset()).isEqualTo(ChunkFormat.HEADER_SIZE);
    }

    @Test
    void writerShouldRejectSlotsAfterFinish() {
        ChunkWriter writer = new ChunkWriter();
        writer.finish();

        assertThatThrownBy(() -> writer.addSlot(FIRST)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(writer::finish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void flippedPayloadByteShouldBeReportedAsCorruption() {
        Slot slot = Slot.of(FIRST);
        slot.bytes[slot.bytes.length - ChunkFormat.CRC_SIZE - 1] ^= 1;

        assertThatThrownBy(slot::parse).isInstanceOf(CorruptedSlotException.class);
    }

    @Test
    void truncatedSlotShouldBeRejected() {
        Slot slot = Slot.of(FIRST);

        assertThatThrownBy(() -> ChunkParser.parseSlot(Arrays.copyOf(slot.bytes, slot.bytes.length - 1), slot.location))
            .isInstanceOf(ChunkFormatException.class)
            .isNotInstanceOf(CorruptedSlotException.class);
    }

    @Test
    void metadataLengthBeyondTheSlotShouldBeRejected() {
        byte[] slot = withCrc(ByteBuffer.allocate(Integer.BYTES + Short.BYTES).putInt(1000).putShort((short) 0).array());

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class).hasMessageContaining("metadata length");
    }

    @Test
    void negativeMetadataLengthShouldBeRejected() {
        byte[] slot = withCrc(ByteBuffer.allocate(Integer.BYTES + Short.BYTES).putInt(-1).putShort((short) 0).array());

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class);
    }

    @Test
    void metadataFieldBeyondItsSectionShouldBeRejected() {
        byte[] slot = slotWithMetadata(metadata -> {
            metadata.writeShort(1);
            metadata.writeShort(500);
            metadata.write("name".getBytes(StandardCharsets.US_ASCII));
        });

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class).hasMessageContaining("exceeds");
    }

    @Test
    void missingMetadataValueShouldBeRejected() {
        byte[] slot = slotWithMetadata(metadata -> {
            metadata.writeShort(1);
            writeField(metadata, "name".getBytes(StandardCharsets.US_ASCII));
        });

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class);
    }

    @Test
    void invalidMetadataNameShouldBeRejected() {
        byte[] slot = slotWithMetadata(metadata -> {
            metadata.writeShort(1);
            writeField(metadata, "Not_valid".getBytes(StandardCharsets.US_ASCII));
            writeField(metadata, "value".getBytes(StandardCharsets.UTF_8));
        });

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class).hasMessageContaining("Invalid slot metadata");
    }

    @Test
    void malformedUtf8MetadataValueShouldBeRejected() {
        byte[] slot = slotWithMetadata(metadata -> {
            metadata.writeShort(1);
            writeField(metadata, "name".getBytes(StandardCharsets.US_ASCII));
            writeField(metadata, new byte[] {(byte) 0xC3});
        });

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class).hasMessageContaining("UTF-8");
    }

    @Test
    void uppercaseMetadataNameShouldBeRejected() {
        byte[] slot = slotWithMetadata(metadata -> {
            metadata.writeShort(1);
            writeField(metadata, "Content-Encoding".getBytes(StandardCharsets.US_ASCII));
            writeField(metadata, "zstd".getBytes(StandardCharsets.UTF_8));
        });

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class).hasMessageContaining("lowercase");
    }

    @Test
    void storedTotalObjectSizeShouldBeRejected() {
        byte[] slot = slotWithMetadata(metadata -> {
            metadata.writeShort(1);
            writeField(metadata, "total-object-size".getBytes(StandardCharsets.US_ASCII));
            writeField(metadata, "3".getBytes(StandardCharsets.UTF_8));
        });

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class).hasMessageContaining("cannot be stored");
    }

    @Test
    void duplicatedMetadataShouldBeRejected() {
        byte[] slot = slotWithMetadata(metadata -> {
            metadata.writeShort(2);
            writeField(metadata, "name".getBytes(StandardCharsets.US_ASCII));
            writeField(metadata, "a".getBytes(StandardCharsets.UTF_8));
            writeField(metadata, "name".getBytes(StandardCharsets.US_ASCII));
            writeField(metadata, "b".getBytes(StandardCharsets.UTF_8));
        });

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class).hasMessageContaining("Duplicated");
    }

    @Test
    void trailingMetadataBytesShouldBeRejected() {
        byte[] slot = slotWithMetadata(metadata -> {
            metadata.writeShort(0);
            metadata.writeByte(7);
        });

        assertThatThrownBy(() -> parse(slot)).isInstanceOf(ChunkFormatException.class).hasMessageContaining("unexpected bytes");
    }

    @Test
    void slotBeyondTheChunkShouldBeRejected() {
        byte[] chunk = new ChunkWriter().finish();

        assertThatThrownBy(() -> ChunkParser.sliceSlot(chunk, new SlotLocation(ChunkFormat.HEADER_SIZE, 1000)))
            .isInstanceOf(ChunkFormatException.class);
    }

    @Test
    void invalidHeaderShouldBeRejected() {
        byte[] header = Arrays.copyOf(new ChunkWriter().finish(), ChunkFormat.HEADER_SIZE);
        header[ChunkFormat.MAGIC.length] = 2;

        assertThatThrownBy(() -> ChunkParser.parseHeader(header)).isInstanceOf(ChunkFormatException.class);
        assertThatThrownBy(() -> ChunkParser.parseHeader(new byte[2])).isInstanceOf(ChunkFormatException.class);
    }

    @Test
    void trailerShouldRejectAnInvalidMagic() {
        byte[] chunk = chunkWithSlots();
        byte[] trailer = trailer(chunk);
        trailer[trailer.length - 2] = 'X';

        assertThatThrownBy(() -> ChunkParser.parseTrailer(trailer, chunk.length)).isInstanceOf(ChunkFormatException.class);
    }

    @Test
    void trailerShouldRejectAFooterNotEndingAtTheTrailer() {
        byte[] chunk = chunkWithSlots();

        assertThatThrownBy(() -> ChunkParser.parseTrailer(trailer(chunk), chunk.length + 1)).isInstanceOf(ChunkFormatException.class);
    }

    @Test
    void trailerShouldRejectAFooterOffsetOverlappingTheHeader() {
        byte[] trailer = ByteBuffer.allocate(ChunkFormat.TRAILER_SIZE)
            .putLong(0).putInt(ChunkFormat.FOOTER_PREFIX_SIZE).putInt(0).put(ChunkFormat.MAGIC).put(ChunkFormat.VERSION)
            .array();

        assertThatThrownBy(() -> ChunkParser.parseTrailer(trailer, ChunkFormat.FOOTER_PREFIX_SIZE + ChunkFormat.TRAILER_SIZE))
            .isInstanceOf(ChunkFormatException.class);
    }

    @Test
    void footerShouldRejectAChecksumMismatch() {
        byte[] chunk = chunkWithSlots();
        ChunkTrailer trailer = ChunkParser.parseTrailer(trailer(chunk), chunk.length);
        byte[] footer = footer(chunk, trailer);
        footer[footer.length - 1] ^= 1;

        assertThatThrownBy(() -> ChunkParser.parseFooter(footer, trailer, header(chunk))).isInstanceOf(ChunkFormatException.class);
    }

    @Test
    void footerOfAnotherChunkShouldBeRejected() {
        byte[] chunk = chunkWithSlots();
        byte[] otherChunk = chunkWithSlots();
        ChunkTrailer trailer = ChunkParser.parseTrailer(trailer(otherChunk), otherChunk.length);

        assertThatThrownBy(() -> ChunkParser.parseFooter(footer(otherChunk, trailer), trailer, header(chunk)))
            .isInstanceOf(ChunkFormatException.class)
            .hasMessageContaining("another chunk");
    }

    @Test
    void footerShouldRejectNonContiguousSlots() {
        byte[] footer = ByteBuffer.allocate(ChunkFormat.FOOTER_PREFIX_SIZE + 2 * ChunkFormat.FOOTER_ENTRY_SIZE)
            .putLong(IDENTITY.identity())
            .putInt(2)
            .putLong(ChunkFormat.HEADER_SIZE).putLong(20)
            .putLong(ChunkFormat.HEADER_SIZE + 21).putLong(20)
            .array();

        assertThatThrownBy(() -> ChunkParser.parseFooter(footer, trailerFor(footer, ChunkFormat.HEADER_SIZE + 41), IDENTITY))
            .isInstanceOf(ChunkFormatException.class)
            .hasMessageContaining("does not follow");
    }

    @Test
    void footerShouldRejectSlotsNotEndingAtTheFooter() {
        byte[] footer = ByteBuffer.allocate(ChunkFormat.FOOTER_PREFIX_SIZE + ChunkFormat.FOOTER_ENTRY_SIZE)
            .putLong(IDENTITY.identity())
            .putInt(1)
            .putLong(ChunkFormat.HEADER_SIZE).putLong(20)
            .array();

        assertThatThrownBy(() -> ChunkParser.parseFooter(footer, trailerFor(footer, ChunkFormat.HEADER_SIZE + 30), IDENTITY))
            .isInstanceOf(ChunkFormatException.class);
    }

    @Test
    void footerShouldRejectAnOverflowingSlotCount() {
        byte[] footer = ByteBuffer.allocate(ChunkFormat.FOOTER_PREFIX_SIZE).putLong(IDENTITY.identity()).putInt(Integer.MAX_VALUE).array();

        assertThatThrownBy(() -> ChunkParser.parseFooter(footer, trailerFor(footer, ChunkFormat.HEADER_SIZE), IDENTITY))
            .isInstanceOf(ChunkFormatException.class);
    }

    /** Reads the footer as chunk maintenance does: header, trailer, then footer. */
    static ChunkFooter readFooter(byte[] chunk) {
        if (chunk.length < ChunkFormat.HEADER_SIZE + ChunkFormat.TRAILER_SIZE) {
            throw new ChunkFormatException("Chunk too short: " + chunk.length);
        }
        ChunkHeader header = header(chunk);
        ChunkTrailer trailer = ChunkParser.parseTrailer(trailer(chunk), chunk.length);
        return ChunkParser.parseFooter(footer(chunk, trailer), trailer, header);
    }

    /** Encodes a blob as a slot, for canonicity checks. */
    static byte[] encodeSlot(BytesBlob blob) {
        return Slot.of(blob).bytes;
    }

    private static ChunkHeader header(byte[] chunk) {
        return ChunkParser.parseHeader(Arrays.copyOf(chunk, ChunkFormat.HEADER_SIZE));
    }

    private static byte[] trailer(byte[] chunk) {
        return Arrays.copyOfRange(chunk, chunk.length - ChunkFormat.TRAILER_SIZE, chunk.length);
    }

    private static byte[] footer(byte[] chunk, ChunkTrailer trailer) {
        int offset = Math.toIntExact(trailer.footerOffset());
        return Arrays.copyOfRange(chunk, offset, offset + trailer.footerLength());
    }

    private static final ChunkHeader IDENTITY = new ChunkHeader(42);

    private static ChunkTrailer trailerFor(byte[] footer, long footerOffset) {
        return new ChunkTrailer(footerOffset, footer.length, ChunkFormat.crc32c(footer, 0, footer.length));
    }

    private static byte[] chunkWithSlots() {
        ChunkWriter writer = new ChunkWriter();
        writer.addSlot(FIRST);
        writer.addSlot(THIRD);
        return writer.finish();
    }

    private static BytesBlob parse(byte[] slot) {
        return ChunkParser.parseSlot(slot, new SlotLocation(ChunkFormat.HEADER_SIZE, slot.length));
    }

    private static byte[] slotWithMetadata(Consumer<ByteArrayDataOutput> metadataWriter) {
        ByteArrayDataOutput metadata = ByteStreams.newDataOutput();
        metadataWriter.accept(metadata);
        byte[] metadataBytes = metadata.toByteArray();
        ByteArrayDataOutput slot = ByteStreams.newDataOutput();
        slot.writeInt(metadataBytes.length);
        slot.write(metadataBytes);
        return withCrc(slot.toByteArray());
    }

    private static void writeField(ByteArrayDataOutput output, byte[] field) {
        output.writeShort(field.length);
        output.write(field);
    }

    static byte[] withCrc(byte[] content) {
        return ByteBuffer.allocate(content.length + ChunkFormat.CRC_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(content)
            .putInt(ChunkFormat.crc32c(content, 0, content.length))
            .array();
    }

    private record Slot(byte[] bytes, SlotLocation location) {
        static Slot of(BytesBlob blob) {
            return at(blob);
        }

        /** The slot of {@code blob}, written after {@code preceding} slots. */
        static Slot at(BytesBlob blob, BytesBlob... preceding) {
            ChunkWriter writer = new ChunkWriter();
            List.of(preceding).forEach(writer::addSlot);
            SlotLocation location = writer.addSlot(blob);
            return new Slot(ChunkParser.sliceSlot(writer.finish(), location), location);
        }

        BytesBlob parse() {
            return ChunkParser.parseSlot(bytes, location);
        }
    }
}
