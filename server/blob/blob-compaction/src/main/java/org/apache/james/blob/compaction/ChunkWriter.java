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
import static org.apache.james.blob.compaction.ChunkFormat.MAX_CHUNK_SIZE;
import static org.apache.james.blob.compaction.ChunkFormat.MAX_FIELD_LENGTH;
import static org.apache.james.blob.compaction.ChunkFormat.METADATA_LENGTH_SIZE;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;
import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

/** Builds a chunk in memory. Slots keep the caller payload and metadata unchanged. */
final class ChunkWriter {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ImmutableList.Builder<SlotLocation> slots = ImmutableList.builder();
    private boolean finished = false;

    ChunkWriter() {
        output.writeBytes(ChunkFormat.MAGIC);
        output.write(ChunkFormat.VERSION);
    }

    /** Exact number of bytes {@link #addSlot(BytesBlob)} writes for this blob. */
    static long encodedSlotSize(BytesBlob blob) {
        return slotSize(encodeMetadata(blob.metadata()), blob.payload());
    }

    SlotLocation addSlot(BytesBlob blob) {
        Preconditions.checkState(!finished, "Chunk is already finished");
        byte[] metadata = encodeMetadata(blob.metadata());
        SlotLocation location = new SlotLocation(output.size(), slotSize(metadata, blob.payload()));
        Preconditions.checkArgument(location.end() <= MAX_CHUNK_SIZE, "Chunk would exceed %s bytes", MAX_CHUNK_SIZE);

        output.writeBytes(encodeSlot(metadata, blob.payload()));
        slots.add(location);
        return location;
    }

    byte[] finish() {
        Preconditions.checkState(!finished, "Chunk is already finished");
        finished = true;
        long footerOffset = output.size();
        byte[] footer = encodeFooter(slots.build());
        output.writeBytes(footer);
        output.writeBytes(encodeTrailer(new ChunkTrailer(footerOffset, footer.length, ChunkFormat.crc32c(footer, 0, footer.length))));
        Preconditions.checkState(output.size() <= MAX_CHUNK_SIZE, "Chunk exceeds %s bytes", MAX_CHUNK_SIZE);
        return output.toByteArray();
    }

    private static long slotSize(byte[] metadata, byte[] payload) {
        return (long) METADATA_LENGTH_SIZE + metadata.length + payload.length + CRC_SIZE;
    }

    private static byte[] encodeSlot(byte[] metadata, byte[] payload) {
        ByteArrayDataOutput slot = ByteStreams.newDataOutput(Math.toIntExact(slotSize(metadata, payload)));
        slot.writeInt(metadata.length);
        slot.write(metadata);
        slot.write(payload);
        byte[] content = slot.toByteArray();
        slot.writeInt(ChunkFormat.crc32c(content, 0, content.length));
        return slot.toByteArray();
    }

    private static byte[] encodeMetadata(BlobMetadata metadata) {
        // The total object size describes a ranged read response, not the stored blob.
        List<Map.Entry<BlobMetadataName, BlobMetadataValue>> entries = metadata.underlyingMap().entrySet().stream()
            .filter(entry -> !entry.getKey().equals(BlobStoreDAO.TOTAL_OBJECT_SIZE))
            .toList();
        Preconditions.checkArgument(entries.size() <= MAX_FIELD_LENGTH, "Too many metadata entries: %s", entries.size());

        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeShort(entries.size());
        entries.forEach(entry -> {
            writeField(output, entry.getKey().name().getBytes(StandardCharsets.US_ASCII));
            writeField(output, entry.getValue().value().getBytes(StandardCharsets.UTF_8));
        });
        return output.toByteArray();
    }

    private static void writeField(ByteArrayDataOutput output, byte[] field) {
        Preconditions.checkArgument(field.length <= MAX_FIELD_LENGTH, "Metadata field exceeds %s bytes", MAX_FIELD_LENGTH);
        output.writeShort(field.length);
        output.write(field);
    }

    private static byte[] encodeFooter(List<SlotLocation> locations) {
        ByteArrayDataOutput footer = ByteStreams.newDataOutput(ChunkFormat.FOOTER_COUNT_SIZE + locations.size() * ChunkFormat.FOOTER_ENTRY_SIZE);
        footer.writeInt(locations.size());
        locations.forEach(location -> {
            footer.writeLong(location.offset());
            footer.writeLong(location.length());
        });
        return footer.toByteArray();
    }

    private static byte[] encodeTrailer(ChunkTrailer trailer) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput(ChunkFormat.TRAILER_SIZE);
        output.writeLong(trailer.footerOffset());
        output.writeInt(trailer.footerLength());
        output.writeInt(trailer.footerCrc());
        output.write(ChunkFormat.MAGIC);
        output.writeByte(ChunkFormat.VERSION);
        return output.toByteArray();
    }
}
