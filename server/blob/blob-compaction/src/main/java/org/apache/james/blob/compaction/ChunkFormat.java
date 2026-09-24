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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.CRC32C;

import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;
import org.apache.james.blob.api.ObjectStoreIOException;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.ByteStreams;

public class ChunkFormat {
    public static final byte FORMAT_BYTE = 0x01;
    public static final int FOOTER_METADATA_LENGTH = 12; // 4 bytes footerLength + 8 bytes footerPosition

    public record BlobSlotContent(byte[] payload, BlobMetadata metadata) {
        public static BlobSlotContent of(byte[] payload) {
            return of(payload, BlobMetadata.empty());
        }

        public static BlobSlotContent of(byte[] payload, BlobMetadata metadata) {
            Preconditions.checkNotNull(payload, "'payload' must not be null");
            Preconditions.checkNotNull(metadata, "'metadata' must not be null");
            return new BlobSlotContent(payload, metadata);
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
            byte[] payload = slot.payload();

            CRC32C crc = new CRC32C();
            crc.update(payload);
            int crc32c = (int) crc.getValue();

            StringBuilder metadataBuilder = new StringBuilder();
            if (slot.metadata() != null) {
                for (Map.Entry<BlobMetadataName, BlobMetadataValue> entry : slot.metadata().underlyingMap().entrySet()) {
                    metadataBuilder.append(entry.getKey().name()).append('=').append(entry.getValue().value()).append('\n');
                }
            }
            metadataBuilder.append('\n');
            byte[] metadataBytes = metadataBuilder.toString().getBytes(StandardCharsets.US_ASCII);

            slotStarts.add(currentOffset);

            dataOut.writeLong(currentOffset);
            dataOut.writeInt(crc32c);
            dataOut.write(metadataBytes);
            dataOut.write(payload);

            currentOffset += 8L + 4L + metadataBytes.length + payload.length;
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
            byte[] payload = slot.payload();

            CRC32C crc = new CRC32C();
            crc.update(payload);
            int crc32c = (int) crc.getValue();

            StringBuilder metadataBuilder = new StringBuilder();
            if (slot.metadata() != null) {
                for (Map.Entry<BlobMetadataName, BlobMetadataValue> entry : slot.metadata().underlyingMap().entrySet()) {
                    metadataBuilder.append(entry.getKey().name()).append('=').append(entry.getValue().value()).append('\n');
                }
            }
            metadataBuilder.append('\n');
            byte[] metadataBytes = metadataBuilder.toString().getBytes(StandardCharsets.US_ASCII);

            slotStarts.add(currentOffset);
            long slotLength = 8L + 4L + metadataBytes.length + payload.length;
            slotLengths.add(slotLength);

            dataOut.writeLong(currentOffset);
            dataOut.writeInt(crc32c);
            dataOut.write(metadataBytes);
            dataOut.write(payload);

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

    public record ParsedSlot(long offset, long limit, byte[] payload, BlobMetadata metadata) {}

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
            BlobSlot blobSlot = parseSlot(slotSlice, offset);
            result.add(new ParsedSlot(offset, limit, blobSlot.payload(), blobSlot.metadata()));
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

        return parseSlot(slotBytes, contentStart).payload();
    }

    public static BlobSlot parseSlot(byte[] slotBytes, long expectedContentStart) throws ObjectStoreIOException {
        if (slotBytes.length < 8 + 4 + 1) { // 8B contentStart, 4B crc, at least 1 byte newline for metadata
            throw new ObjectStoreIOException("Slot byte array too short: " + slotBytes.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(slotBytes);
        long actualContentStart = buffer.getLong(0);
        if (actualContentStart != expectedContentStart) {
            throw new ObjectStoreIOException(String.format("Slot contentStart mismatch: expected %d, got %d",
                expectedContentStart, actualContentStart));
        }

        int crc32c = buffer.getInt(8);

        int lineStart = 12;
        BlobMetadata customMetadata = BlobMetadata.empty();
        int contentOffset = -1;

        while (lineStart < slotBytes.length) {
            int newlineIndex = -1;
            for (int i = lineStart; i < slotBytes.length; i++) {
                if (slotBytes[i] == '\n') {
                    newlineIndex = i;
                    break;
                }
            }
            if (newlineIndex == -1) {
                break;
            }

            int lineLen = newlineIndex - lineStart;
            if (lineLen == 0) {
                contentOffset = newlineIndex + 1;
                break;
            }

            String line = new String(slotBytes, lineStart, lineLen, StandardCharsets.US_ASCII).trim();
            int eqIndex = line.indexOf('=');
            if (eqIndex > 0) {
                String key = line.substring(0, eqIndex).trim();
                String val = line.substring(eqIndex + 1).trim();
                try {
                    customMetadata = customMetadata.withMetadata(new BlobMetadataName(key), new BlobMetadataValue(val));
                } catch (IllegalArgumentException e) {
                    contentOffset = lineStart;
                    break;
                }
            } else {
                contentOffset = lineStart;
                break;
            }

            lineStart = newlineIndex + 1;
        }

        if (contentOffset == -1) {
            contentOffset = lineStart;
        }

        int payloadLength = slotBytes.length - contentOffset;
        byte[] payload = new byte[payloadLength];
        System.arraycopy(slotBytes, contentOffset, payload, 0, payloadLength);

        BlobSlot blobSlot = new BlobSlot(expectedContentStart, crc32c, payload, customMetadata);
        blobSlot.verifyCrc();
        return blobSlot;
    }
}
