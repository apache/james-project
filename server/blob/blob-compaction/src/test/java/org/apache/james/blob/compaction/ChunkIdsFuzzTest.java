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

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.ChunkMarker;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.DictionaryFile;
import com.code_intelligence.jazzer.junit.FuzzTest;

/** Whatever the sample blob id, a chunk is either refused or allocated in the same family and generation. */
class ChunkIdsFuzzTest {
    private static final SlotLocation LARGEST_LOCATION = new SlotLocation(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2);

    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void newChunkShouldStayInTheSampleGeneration(FuzzedDataProvider data) {
        BlobId.Factory factory = FuzzedBlobIds.factory(data);
        String input = data.consumeBoolean() ? generational(data) : data.consumeRemainingAsString();
        if (input.isEmpty()) {
            return;
        }
        BlobId sample;
        BlobId chunk;
        try {
            sample = factory.parse(input);
            chunk = ChunkIds.newChunk(sample, factory);
        } catch (IllegalArgumentException e) {
            // Refused sample: no family and generation, or not representable by the factory
            return;
        }

        String chunkId = chunk.asString();
        assertThat(ChunkMarker.isChunkId(chunk)).isTrue();
        assertThat(sample.asString()).startsWith(chunkId.substring(0, chunkId.indexOf("chunk")));
        assertThat(chunkId).doesNotContain("~");
        assertThat(factory.parse(chunkId).asString()).isEqualTo(chunkId);
        assertThat(SlotAddress.parse(chunk, factory)).isEmpty();
        assertThat(ChunkIds.newChunk(sample, factory)).isNotEqualTo(chunk);

        for (SlotLocation location : new SlotLocation[] {LARGEST_LOCATION, FuzzedBlobIds.location(data)}) {
            SlotAddress address = new SlotAddress(chunk, location);
            BlobId blobId = address.asBlobId(factory);
            assertThat(blobId.asString()).hasSizeLessThanOrEqualTo(SlotAddress.MAX_LENGTH);
            assertThat(SlotAddress.parse(blobId, factory)).contains(address);
        }
    }

    private static String generational(FuzzedDataProvider data) {
        char separator = FuzzedBlobIds.separator(data);
        return FuzzedBlobIds.digits(data) + separator + FuzzedBlobIds.digits(data) + separator + data.consumeString(20);
    }
}
