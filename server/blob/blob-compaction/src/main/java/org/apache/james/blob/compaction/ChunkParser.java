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

import static org.apache.james.blob.compaction.ChunkFormat.CRC_SIZE;
import static org.apache.james.blob.compaction.ChunkFormat.FOOTER_COUNT_SIZE;
import static org.apache.james.blob.compaction.ChunkFormat.FOOTER_ENTRY_SIZE;
import static org.apache.james.blob.compaction.ChunkFormat.HEADER_SIZE;
import static org.apache.james.blob.compaction.ChunkFormat.METADATA_COUNT_SIZE;
import static org.apache.james.blob.compaction.ChunkFormat.METADATA_LENGTH_SIZE;
import static org.apache.james.blob.compaction.ChunkFormat.MIN_SLOT_SIZE;
import static org.apache.james.blob.compaction.ChunkFormat.TRAILER_SIZE;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;
import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Parses untrusted chunk bytes. Every length is checked against the bytes actually available before use, so malformed
 * input only ever raises {@link ChunkFormatException}.
 */
final class ChunkParser {
    private ChunkParser() {
    }

    static BytesBlob parseSlot(byte[] slot, SlotLocation location) {
        if (slot.length != location.length()) {
            throw new ChunkFormatException(String.format("Slot at offset %d: expected %d bytes, got %d",
                location.offset(), location.length(), slot.length));
        }
        int crcOffset = slot.length - CRC_SIZE;
        checkSlotCrc(slot, crcOffset, location);

        int metadataLength = ByteBuffer.wrap(slot).getInt();
        if (metadataLength < METADATA_COUNT_SIZE || metadataLength > crcOffset - METADATA_LENGTH_SIZE) {
            throw new ChunkFormatException("Invalid metadata length " + metadataLength + " in slot at offset " + location.offset());
        }
        BlobMetadata metadata = parseMetadata(ByteBuffer.wrap(slot, METADATA_LENGTH_SIZE, metadataLength).slice());
        byte[] payload = Arrays.copyOfRange(slot, METADATA_LENGTH_SIZE + metadataLength, crcOffset);
        return BytesBlob.of(payload, metadata);
    }

    static byte[] sliceSlot(byte[] chunk, SlotLocation location) {
        if (location.end() > chunk.length) {
            throw new ChunkFormatException(String.format("Slot ending at %d exceeds chunk size %d", location.end(), chunk.length));
        }
        return Arrays.copyOfRange(chunk, Math.toIntExact(location.offset()), Math.toIntExact(location.end()));
    }

    static void checkHeader(byte[] header) {
        if (header.length != HEADER_SIZE || !hasMagicAndVersion(header, 0)) {
            throw new ChunkFormatException("Not a chunk: invalid header");
        }
    }

    static ChunkTrailer parseTrailer(byte[] trailer, long chunkSize) {
        if (trailer.length != TRAILER_SIZE || !hasMagicAndVersion(trailer, TRAILER_SIZE - HEADER_SIZE)) {
            throw new ChunkFormatException("Not a chunk: invalid trailer");
        }
        ByteBuffer buffer = ByteBuffer.wrap(trailer);
        long footerOffset = buffer.getLong();
        int footerLength = buffer.getInt();
        int footerCrc = buffer.getInt();

        boolean footerFitsBetweenHeaderAndTrailer = footerLength >= FOOTER_COUNT_SIZE
            && footerOffset >= HEADER_SIZE
            && chunkSize - TRAILER_SIZE - footerLength == footerOffset;
        if (!footerFitsBetweenHeaderAndTrailer) {
            throw new ChunkFormatException(String.format("Footer of %d bytes at offset %d does not fit a chunk of %d bytes",
                footerLength, footerOffset, chunkSize));
        }
        return new ChunkTrailer(footerOffset, footerLength, footerCrc);
    }

    static ChunkFooter parseFooter(byte[] footer, ChunkTrailer trailer) {
        if (footer.length != trailer.footerLength()) {
            throw new ChunkFormatException(String.format("Expected a footer of %d bytes, got %d", trailer.footerLength(), footer.length));
        }
        if (ChunkFormat.crc32c(footer, 0, footer.length) != trailer.footerCrc()) {
            throw new ChunkFormatException("Footer checksum mismatch");
        }
        ByteBuffer buffer = ByteBuffer.wrap(footer);
        int slotCount = buffer.getInt();
        if (slotCount < 0 || FOOTER_COUNT_SIZE + (long) slotCount * FOOTER_ENTRY_SIZE != footer.length) {
            throw new ChunkFormatException("Footer slot count " + slotCount + " does not match its length " + footer.length);
        }
        return new ChunkFooter(parseContiguousSlots(buffer, slotCount, trailer.footerOffset()));
    }

    private static ImmutableList<SlotLocation> parseContiguousSlots(ByteBuffer buffer, int slotCount, long footerOffset) {
        ImmutableList.Builder<SlotLocation> slots = ImmutableList.builderWithExpectedSize(slotCount);
        long expectedOffset = HEADER_SIZE;
        for (int i = 0; i < slotCount; i++) {
            long offset = buffer.getLong();
            long length = buffer.getLong();
            boolean followsPreviousSlot = offset == expectedOffset && length >= MIN_SLOT_SIZE && length <= footerOffset - offset;
            if (!followsPreviousSlot) {
                throw new ChunkFormatException(String.format("Slot %d (offset %d, length %d) does not follow the previous slot", i, offset, length));
            }
            slots.add(new SlotLocation(offset, length));
            expectedOffset = offset + length;
        }
        if (expectedOffset != footerOffset) {
            throw new ChunkFormatException("Slots end at " + expectedOffset + " but the footer starts at " + footerOffset);
        }
        return slots.build();
    }

    private static void checkSlotCrc(byte[] slot, int crcOffset, SlotLocation location) {
        int storedCrc = ByteBuffer.wrap(slot).getInt(crcOffset);
        if (storedCrc != ChunkFormat.crc32c(slot, 0, crcOffset)) {
            throw new CorruptedSlotException("Checksum mismatch for slot at offset " + location.offset());
        }
    }

    private static BlobMetadata parseMetadata(ByteBuffer metadata) {
        int entryCount = Short.toUnsignedInt(metadata.getShort());
        Map<BlobMetadataName, BlobMetadataValue> entries = new LinkedHashMap<>();
        for (int i = 0; i < entryCount; i++) {
            BlobMetadataName name = toMetadata(() -> new BlobMetadataName(readField(metadata, StandardCharsets.US_ASCII)));
            BlobMetadataValue value = toMetadata(() -> new BlobMetadataValue(readField(metadata, StandardCharsets.UTF_8)));
            if (entries.put(name, value) != null) {
                throw new ChunkFormatException("Duplicated metadata " + name.name());
            }
        }
        if (metadata.hasRemaining()) {
            throw new ChunkFormatException(metadata.remaining() + " unexpected bytes after slot metadata");
        }
        return new BlobMetadata(ImmutableMap.copyOf(entries));
    }

    private static String readField(ByteBuffer buffer, Charset charset) {
        if (buffer.remaining() < Short.BYTES) {
            throw new ChunkFormatException("Truncated metadata field length");
        }
        int length = Short.toUnsignedInt(buffer.getShort());
        if (buffer.remaining() < length) {
            throw new ChunkFormatException("Metadata field of " + length + " bytes exceeds the metadata section");
        }
        ByteBuffer field = buffer.slice(buffer.position(), length);
        buffer.position(buffer.position() + length);
        try {
            return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(field)
                .toString();
        } catch (CharacterCodingException e) {
            throw new ChunkFormatException("Metadata field is not valid " + charset.name(), e);
        }
    }

    private static <T> T toMetadata(Supplier<T> constructor) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException e) {
            throw new ChunkFormatException("Invalid slot metadata", e);
        }
    }

    private static boolean hasMagicAndVersion(byte[] bytes, int offset) {
        return Arrays.equals(bytes, offset, offset + ChunkFormat.MAGIC.length, ChunkFormat.MAGIC, 0, ChunkFormat.MAGIC.length)
            && bytes[offset + ChunkFormat.MAGIC.length] == ChunkFormat.VERSION;
    }
}
