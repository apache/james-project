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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;
import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.google.common.collect.ImmutableMap;

/**
 * Parser safety and format reversibility. Normal builds replay the committed inputs; fuzz one target with e.g.
 * {@code JAZZER_FUZZ=1 mvn -pl server/blob/blob-compaction test -Dtest=ChunkParserFuzzTest#parseSlotShouldOnlyRejectWithFormatErrors}.
 */
class ChunkParserFuzzTest {
    private static final String NAME_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789-";

    @FuzzTest(maxDuration = "60s")
    void parseSlotShouldOnlyRejectWithFormatErrors(FuzzedDataProvider data) {
        parseSlot(data.consumeRemainingAsBytes());
    }

    /** Appends a valid checksum so that fuzzing reaches the metadata parser. */
    @FuzzTest(maxDuration = "60s")
    void parseChecksummedSlotShouldOnlyRejectWithFormatErrors(FuzzedDataProvider data) {
        parseSlot(ChunkFormatTest.withCrc(data.consumeRemainingAsBytes()));
    }

    @FuzzTest(maxDuration = "60s")
    void parseTrailerAndFooterShouldOnlyRejectWithFormatErrors(FuzzedDataProvider data) {
        byte[] chunk = data.consumeRemainingAsBytes();
        if (chunk.length < ChunkFormat.TRAILER_SIZE) {
            return;
        }
        try {
            ChunkFooter footer = ChunkFormatTest.readFooter(chunk);
            assertThat(footer.slots()).allSatisfy(slot -> assertThat(slot.end()).isLessThanOrEqualTo(chunk.length));
        } catch (ChunkFormatException e) {
            // Expected for malformed chunks
        }
    }

    @FuzzTest(maxDuration = "60s")
    void writtenChunksShouldParseBackUnchanged(FuzzedDataProvider data) {
        List<BytesBlob> blobs = new ArrayList<>();
        int slotCount = data.consumeInt(0, 8);
        for (int i = 0; i < slotCount; i++) {
            blobs.add(BytesBlob.of(data.consumeBytes(data.consumeInt(0, 512)), metadata(data)));
        }
        ChunkWriter writer = new ChunkWriter();
        List<SlotLocation> locations = blobs.stream().map(writer::addSlot).toList();
        byte[] chunk = writer.finish();

        assertThat(ChunkFormatTest.readFooter(chunk).slots()).isEqualTo(locations);
        assertThat(locations).map(location -> ChunkParser.parseSlot(ChunkParser.sliceSlot(chunk, location), location))
            .isEqualTo(blobs);
    }

    private static void parseSlot(byte[] slot) {
        if (slot.length < ChunkFormat.MIN_SLOT_SIZE) {
            return;
        }
        try {
            ChunkParser.parseSlot(slot, new SlotLocation(ChunkFormat.HEADER_SIZE, slot.length));
        } catch (ChunkFormatException e) {
            // Expected for malformed slots
        }
    }

    private static BlobMetadata metadata(FuzzedDataProvider data) {
        Map<BlobMetadataName, BlobMetadataValue> entries = new LinkedHashMap<>();
        int entryCount = data.consumeInt(0, 4);
        for (int i = 0; i < entryCount; i++) {
            entries.put(new BlobMetadataName(name(data)), new BlobMetadataValue(data.consumeString(127)));
        }
        return new BlobMetadata(ImmutableMap.copyOf(entries));
    }

    private static String name(FuzzedDataProvider data) {
        StringBuilder name = new StringBuilder();
        int length = data.consumeInt(1, 20);
        for (int i = 0; i < length; i++) {
            name.append(NAME_ALPHABET.charAt(data.consumeInt(0, NAME_ALPHABET.length() - 1)));
        }
        return name.toString();
    }
}
