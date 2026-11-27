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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;
import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.google.common.collect.ImmutableMap;

/** Blobs drawn from the whole input domain of the writer, including values it must refuse. */
final class FuzzedBlobs {
    private static final String NAME_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-";
    private static final int MAX_PAYLOAD = 256;

    private FuzzedBlobs() {
    }

    static BytesBlob blob(FuzzedDataProvider data) {
        Map<BlobMetadataName, BlobMetadataValue> entries = new LinkedHashMap<>();
        int entryCount = data.consumeInt(0, 4);
        for (int i = 0; i < entryCount; i++) {
            BlobMetadataName name = data.consumeBoolean() ? BlobStoreDAO.TOTAL_OBJECT_SIZE : new BlobMetadataName(name(data));
            entries.put(name, new BlobMetadataValue(value(data)));
        }
        return BytesBlob.of(data.consumeBytes(data.consumeInt(0, MAX_PAYLOAD)), new BlobMetadata(ImmutableMap.copyOf(entries)));
    }

    /**
     * Whether the writer can store this blob: unpaired surrogates cannot be encoded as UTF-8. The total object size is
     * dropped before encoding, so its value does not matter.
     */
    static boolean encodable(BytesBlob blob) {
        return normalized(blob).metadata().underlyingMap().values().stream()
            .allMatch(value -> StandardCharsets.UTF_8.newEncoder().canEncode(value.value()));
    }

    /** What reading a written blob gives back. */
    static BytesBlob normalized(BytesBlob blob) {
        Map<BlobMetadataName, BlobMetadataValue> entries = new LinkedHashMap<>(blob.metadata().underlyingMap());
        entries.remove(BlobStoreDAO.TOTAL_OBJECT_SIZE);
        return BytesBlob.of(blob.payload(), new BlobMetadata(ImmutableMap.copyOf(entries)));
    }

    /** A valid chunk of encodable blobs. */
    static WrittenChunk chunk(FuzzedDataProvider data) {
        ChunkWriter writer = new ChunkWriter();
        int slotCount = data.consumeInt(0, 6);
        for (int i = 0; i < slotCount; i++) {
            BytesBlob blob = blob(data);
            if (encodable(blob)) {
                writer.addSlot(blob);
            }
        }
        byte[] bytes = writer.finish();
        return new WrittenChunk(bytes, ChunkFormatTest.readFooter(bytes).slots());
    }

    record WrittenChunk(byte[] bytes, List<SlotLocation> slots) {
    }

    private static String name(FuzzedDataProvider data) {
        StringBuilder name = new StringBuilder();
        int length = data.consumeInt(1, 20);
        for (int i = 0; i < length; i++) {
            name.append(NAME_ALPHABET.charAt(data.consumeInt(0, NAME_ALPHABET.length() - 1)));
        }
        return name.toString();
    }

    /** Arbitrary chars, unpaired surrogates included. */
    private static String value(FuzzedDataProvider data) {
        StringBuilder value = new StringBuilder();
        int length = data.consumeInt(0, 40);
        for (int i = 0; i < length; i++) {
            value.append(data.consumeChar());
        }
        return value.toString();
    }
}
