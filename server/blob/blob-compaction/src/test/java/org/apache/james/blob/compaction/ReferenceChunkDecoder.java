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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;

import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;
import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;

import com.google.common.collect.ImmutableMap;

/**
 * Second, deliberately naive reading of the format specification in {@link ChunkFormat}, sharing no code with
 * {@link ChunkParser}. Any exception means the chunk is rejected.
 */
final class ReferenceChunkDecoder {
    private static final String MAGIC = "JCHK";
    private static final int VERSION = 1;
    private static final int HEADER = 13;
    private static final int TRAILER = 21;
    private static final int MINIMAL_SLOT = 10;

    private ReferenceChunkDecoder() {
    }

    static List<BytesBlob> decode(byte[] chunk) throws IOException {
        DataInputStream header = stream(chunk, 0, HEADER);
        expectMagicAndVersion(header);
        long identity = header.readLong();

        DataInputStream trailer = stream(chunk, chunk.length - TRAILER, TRAILER);
        long footerOffset = trailer.readLong();
        int footerLength = trailer.readInt();
        int footerCrc = Integer.reverseBytes(trailer.readInt());
        expectMagicAndVersion(trailer);
        check(footerOffset >= HEADER && footerLength >= 12 && footerOffset + footerLength == chunk.length - TRAILER);
        check(crc32c(chunk, (int) footerOffset, footerLength) == footerCrc);

        DataInputStream footer = stream(chunk, (int) footerOffset, footerLength);
        check(footer.readLong() == identity);
        int count = footer.readInt();
        check(count >= 0 && 12L + 16L * count == footerLength);
        List<BytesBlob> blobs = new ArrayList<>();
        long expectedOffset = HEADER;
        for (int i = 0; i < count; i++) {
            long offset = footer.readLong();
            long length = footer.readLong();
            check(offset == expectedOffset && length >= MINIMAL_SLOT && length <= footerOffset - offset);
            blobs.add(decodeSlot(Arrays.copyOfRange(chunk, (int) offset, (int) (offset + length))));
            expectedOffset = offset + length;
        }
        check(expectedOffset == footerOffset);
        return blobs;
    }

    static BytesBlob decodeSlot(byte[] slot) throws IOException {
        int contentLength = slot.length - 4;
        check(Integer.reverseBytes(stream(slot, contentLength, 4).readInt()) == crc32c(slot, 0, contentLength));
        DataInputStream content = stream(slot, 0, contentLength);
        int metadataLength = content.readInt();
        check(metadataLength >= 2 && metadataLength <= contentLength - 4);

        DataInputStream metadata = stream(slot, 4, metadataLength);
        int entryCount = metadata.readUnsignedShort();
        Map<BlobMetadataName, BlobMetadataValue> entries = new LinkedHashMap<>();
        for (int i = 0; i < entryCount; i++) {
            String name = new String(readField(metadata), StandardCharsets.US_ASCII);
            check(name.matches("[a-z0-9-]+") && !name.equals("total-object-size"));
            String value = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(readField(metadata)))
                .toString();
            check(entries.put(new BlobMetadataName(name), new BlobMetadataValue(value)) == null);
        }
        check(metadata.available() == 0);
        return BytesBlob.of(Arrays.copyOfRange(slot, 4 + metadataLength, contentLength), new BlobMetadata(ImmutableMap.copyOf(entries)));
    }

    private static byte[] readField(DataInputStream input) throws IOException {
        byte[] field = new byte[input.readUnsignedShort()];
        input.readFully(field);
        return field;
    }

    private static void expectMagicAndVersion(DataInputStream input) throws IOException {
        byte[] magic = new byte[MAGIC.length()];
        input.readFully(magic);
        check(new String(magic, StandardCharsets.US_ASCII).equals(MAGIC) && input.readUnsignedByte() == VERSION);
    }

    private static DataInputStream stream(byte[] bytes, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new EOFException();
        }
        return new DataInputStream(new ByteArrayInputStream(bytes, offset, length));
    }

    private static int crc32c(byte[] bytes, int offset, int length) {
        CRC32C crc = new CRC32C();
        crc.update(bytes, offset, length);
        return (int) crc.getValue();
    }

    private static void check(boolean condition) throws IOException {
        if (!condition) {
            throw new IOException("Not a valid chunk");
        }
    }
}
