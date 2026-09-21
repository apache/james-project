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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32C;

import org.apache.james.blob.api.ObjectStoreIOException;

import com.github.luben.zstd.Zstd;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.ByteStreams;

public class ChunkFormat {
    public static final byte FORMAT_BYTE = 0x01;
    public static final int FOOTER_METADATA_LENGTH = 12; // 4 bytes footerLength + 8 bytes footerPosition
    public static final String METADATA_ENCODING = "content-encoding=zstd\n";
    public static final String METADATA_SIZE_PREFIX = "content-original-size=";

    public record BlobSlotContent(byte[] rawContent, long originalSize) {
        public static BlobSlotContent of(byte[] rawContent) {
            Preconditions.checkNotNull(rawContent, "'rawContent' must not be null");
            return new BlobSlotContent(rawContent, rawContent.length);
        }
    }

    public static void write(List<BlobSlotContent> slots, OutputStream outputStream) throws IOException {
        Preconditions.checkNotNull(slots, "'slots' must not be null");
        Preconditions.checkNotNull(outputStream, "'outputStream' must not be null");

        DataOutputStream dataOut = new DataOutputStream(outputStream);
        dataOut.writeByte(FORMAT_BYTE);
        long currentOffset = 1L;

        List<Long> slotStarts = new ArrayList<>(slots.size());

        for (BlobSlotContent slot : slots) {
            byte[] raw = slot.rawContent();
            long originalSize = slot.originalSize();

            CRC32C crc = new CRC32C();
            crc.update(raw);
            int crc32c = (int) crc.getValue();

            byte[] compressed;
            if (raw.length == 0) {
                compressed = new byte[0];
            } else {
                compressed = Zstd.compress(raw);
            }

            String metadata = METADATA_ENCODING + METADATA_SIZE_PREFIX + originalSize + "\n";
            byte[] metadataBytes = metadata.getBytes(StandardCharsets.US_ASCII);

            slotStarts.add(currentOffset);

            dataOut.writeLong(currentOffset);
            dataOut.writeInt(crc32c);
            dataOut.write(metadataBytes);
            dataOut.write(compressed);

            currentOffset += 8L + 4L + metadataBytes.length + compressed.length;
        }

        long footerPosition = currentOffset;
        String footerString = slotStarts.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        byte[] footerBytes = footerString.getBytes(StandardCharsets.US_ASCII);

        dataOut.write(footerBytes);
        dataOut.writeInt(footerBytes.length);
        dataOut.writeLong(footerPosition);
        dataOut.flush();
    }

    public static byte[] writeToBytes(List<BlobSlotContent> slots) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        write(slots, baos);
        return baos.toByteArray();
    }

    public record SlotRange(long offset, long limit) {}

    public record ChunkWriteResult(byte[] chunkBytes, List<SlotRange> slotRanges) {}

    public static ChunkWriteResult writeChunk(List<BlobSlotContent> slots) throws IOException {
        Preconditions.checkNotNull(slots, "'slots' must not be null");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(baos);
        dataOut.writeByte(FORMAT_BYTE);
        long currentOffset = 1L;

        List<Long> slotStarts = new ArrayList<>(slots.size());
        List<Long> slotLengths = new ArrayList<>(slots.size());

        for (BlobSlotContent slot : slots) {
            byte[] raw = slot.rawContent();
            long originalSize = slot.originalSize();

            CRC32C crc = new CRC32C();
            crc.update(raw);
            int crc32c = (int) crc.getValue();

            byte[] compressed;
            if (raw.length == 0) {
                compressed = new byte[0];
            } else {
                compressed = Zstd.compress(raw);
            }

            String metadata = METADATA_ENCODING + METADATA_SIZE_PREFIX + originalSize + "\n";
            byte[] metadataBytes = metadata.getBytes(StandardCharsets.US_ASCII);

            slotStarts.add(currentOffset);
            long slotLength = 8L + 4L + metadataBytes.length + compressed.length;
            slotLengths.add(slotLength);

            dataOut.writeLong(currentOffset);
            dataOut.writeInt(crc32c);
            dataOut.write(metadataBytes);
            dataOut.write(compressed);

            currentOffset += slotLength;
        }

        long footerPosition = currentOffset;
        String footerString = slotStarts.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        byte[] footerBytes = footerString.getBytes(StandardCharsets.US_ASCII);

        dataOut.write(footerBytes);
        dataOut.writeInt(footerBytes.length);
        dataOut.writeLong(footerPosition);
        dataOut.flush();

        List<SlotRange> ranges = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            ranges.add(new SlotRange(slotStarts.get(i), slotLengths.get(i)));
        }

        return new ChunkWriteResult(baos.toByteArray(), ranges);
    }

    public record ParsedSlot(long offset, long limit, byte[] decompressedContent) {}

    public static List<ParsedSlot> parseAllSlots(byte[] chunkBytes) throws ObjectStoreIOException {
        Preconditions.checkNotNull(chunkBytes, "'chunkBytes' must not be null");
        ChunkFooter footer = readFooter(chunkBytes);
        List<Long> starts = footer.slotStarts();
        long footerPosition = footer.footerPosition();
        List<ParsedSlot> result = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            long offset = starts.get(i);
            long end = (i + 1 < starts.size()) ? starts.get(i + 1) : footerPosition;
            long limit = end - offset;
            byte[] slotSlice = Arrays.copyOfRange(chunkBytes, (int) offset, (int) end);
            byte[] decompressed = parseSlotBytes(slotSlice, offset);
            result.add(new ParsedSlot(offset, limit, decompressed));
        }
        return result;
    }

    public static ChunkFooter readFooter(byte[] tailBuffer) throws ObjectStoreIOException {
        Preconditions.checkNotNull(tailBuffer, "'tailBuffer' must not be null");
        return readFooter(tailBuffer, tailBuffer.length);
    }

    public static ChunkFooter readFooter(byte[] tailBuffer, long totalObjectSize) throws ObjectStoreIOException {
        Preconditions.checkNotNull(tailBuffer, "'tailBuffer' must not be null");
        if (tailBuffer.length < FOOTER_METADATA_LENGTH) {
            throw new ObjectStoreIOException("Tail buffer is too short to contain chunk footer metadata: " + tailBuffer.length);
        }
        if (totalObjectSize < FOOTER_METADATA_LENGTH + 1) { // 1 byte format + 12 bytes footer
            throw new ObjectStoreIOException("Total object size is too small to be a valid chunk: " + totalObjectSize);
        }

        int bufferLen = tailBuffer.length;
        ByteBuffer buffer = ByteBuffer.wrap(tailBuffer);
        int footerLength = buffer.getInt(bufferLen - FOOTER_METADATA_LENGTH);
        long footerPosition = buffer.getLong(bufferLen - 8);

        if (footerLength < 0) {
            throw new ObjectStoreIOException("Corrupt footer length: " + footerLength);
        }
        if (footerPosition < 1 || footerPosition > totalObjectSize - FOOTER_METADATA_LENGTH) {
            throw new ObjectStoreIOException("Corrupt footer position: " + footerPosition + " for total size " + totalObjectSize);
        }
        if (footerPosition + footerLength != totalObjectSize - FOOTER_METADATA_LENGTH) {
            throw new ObjectStoreIOException(String.format("Footer integrity mismatch: footerPosition(%d) + footerLength(%d) != expected(%d)",
                footerPosition, footerLength, totalObjectSize - FOOTER_METADATA_LENGTH));
        }

        long tailBufferStartOffset = totalObjectSize - tailBuffer.length;
        if (footerPosition < tailBufferStartOffset) {
            throw new ObjectStoreIOException("Footer start offset " + footerPosition + " is before tail buffer start " + tailBufferStartOffset);
        }

        int footerOffsetInTail = (int) (footerPosition - tailBufferStartOffset);
        if (footerOffsetInTail + footerLength > bufferLen - FOOTER_METADATA_LENGTH) {
            throw new ObjectStoreIOException("Footer exceeds tail buffer bounds");
        }

        if (footerLength == 0) {
            return new ChunkFooter(List.of(), footerPosition);
        }

        String footerString = new String(tailBuffer, footerOffsetInTail, footerLength, StandardCharsets.US_ASCII);
        List<Long> slotStarts = Splitter.on(',')
            .trimResults()
            .omitEmptyStrings()
            .splitToStream(footerString)
            .map(s -> {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Corrupt contentStart value in footer: " + s, e);
                }
            })
            .collect(Collectors.toList());

        return new ChunkFooter(slotStarts, footerPosition);
    }

    public static byte[] readSlot(InputStream chunkRangeStream, long contentStart, long nextContentStart) throws ObjectStoreIOException {
        Preconditions.checkNotNull(chunkRangeStream, "'chunkRangeStream' must not be null");
        Preconditions.checkArgument(nextContentStart > contentStart,
            "nextContentStart (%s) must be strictly greater than contentStart (%s)", nextContentStart, contentStart);

        long slotLength = nextContentStart - contentStart;
        if (slotLength > Integer.MAX_VALUE) {
            throw new ObjectStoreIOException("Slot length exceeds maximum supported size: " + slotLength);
        }

        byte[] slotBytes;
        try {
            slotBytes = ByteStreams.toByteArray(ByteStreams.limit(chunkRangeStream, slotLength));
        } catch (IOException e) {
            throw new ObjectStoreIOException("Failed reading slot bytes from stream at " + contentStart, e);
        }

        if (slotBytes.length != slotLength) {
            throw new ObjectStoreIOException(String.format("Truncated slot bytes at %d: expected %d bytes, got %d",
                contentStart, slotLength, slotBytes.length));
        }

        return parseSlotBytes(slotBytes, contentStart);
    }

    public static byte[] parseSlotBytes(byte[] slotBytes, long expectedContentStart) throws ObjectStoreIOException {
        if (slotBytes.length < 8 + 4 + 2) { // 8B contentStart, 4B crc, at least 2 bytes metadata
            throw new ObjectStoreIOException("Slot byte array too short: " + slotBytes.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(slotBytes);
        long actualContentStart = buffer.getLong(0);
        if (actualContentStart != expectedContentStart) {
            throw new ObjectStoreIOException(String.format("Slot contentStart mismatch: expected %d, got %d",
                expectedContentStart, actualContentStart));
        }

        int crc32c = buffer.getInt(8);

        // Find metadata lines (up to second '\n')
        int firstNewline = -1;
        int secondNewline = -1;
        for (int i = 12; i < slotBytes.length; i++) {
            if (slotBytes[i] == '\n') {
                if (firstNewline == -1) {
                    firstNewline = i;
                } else {
                    secondNewline = i;
                    break;
                }
            }
        }

        if (firstNewline == -1 || secondNewline == -1) {
            throw new ObjectStoreIOException("Corrupt slot metadata: missing newlines in header at " + expectedContentStart);
        }

        String encodingLine = new String(slotBytes, 12, firstNewline - 12, StandardCharsets.US_ASCII);
        if (!"content-encoding=zstd".equals(encodingLine.trim())) {
            throw new ObjectStoreIOException("Unsupported slot encoding: " + encodingLine);
        }

        String sizeLine = new String(slotBytes, firstNewline + 1, secondNewline - (firstNewline + 1), StandardCharsets.US_ASCII).trim();
        if (!sizeLine.startsWith(METADATA_SIZE_PREFIX)) {
            throw new ObjectStoreIOException("Missing original size metadata line: " + sizeLine);
        }

        long originalSize;
        try {
            originalSize = Long.parseLong(sizeLine.substring(METADATA_SIZE_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            throw new ObjectStoreIOException("Corrupt original size metadata: " + sizeLine, e);
        }

        int contentOffset = secondNewline + 1;
        int compressedLength = slotBytes.length - contentOffset;
        byte[] compressedContent = new byte[compressedLength];
        System.arraycopy(slotBytes, contentOffset, compressedContent, 0, compressedLength);

        BlobSlot blobSlot = new BlobSlot(expectedContentStart, crc32c, originalSize, compressedContent);
        return blobSlot.decompress();
    }
}
