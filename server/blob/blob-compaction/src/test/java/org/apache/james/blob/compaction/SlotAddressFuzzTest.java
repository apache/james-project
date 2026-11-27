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

class SlotAddressFuzzTest {
    /**
     * Parsing never fails, accepts only chunk ids, and serializing gives back the exact input: a blob id is exactly one
     * of slot address, chunk or other blob.
     */
    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void parseShouldBeSafeAndReversible(FuzzedDataProvider data) {
        BlobId.Factory factory = FuzzedBlobIds.factory(data);
        String input = data.consumeRemainingAsString();
        if (input.isEmpty()) {
            return;
        }
        BlobId blobId = FuzzedBlobIds.PLAIN.of(input);

        SlotAddress.parse(blobId, factory).ifPresent(address -> {
            assertThat(ChunkMarker.isChunkId(blobId)).isTrue();
            assertThat(ChunkMarker.isChunkId(address.chunk())).isTrue();
            assertThat(SlotAddress.parse(address.chunk(), factory)).isEmpty();
            assertThat(input).hasSizeLessThanOrEqualTo(SlotAddress.MAX_LENGTH);
            assertThat(address.asBlobId(factory).asString()).isEqualTo(input);
        });
    }

    /** Any address built on a new chunk parses back, for every factory and layout. */
    @FuzzTest(maxDuration = "600s")
    @DictionaryFile(resourcePath = "chunk.dict")
    void addressesOfNewChunksShouldParseBack(FuzzedDataProvider data) {
        BlobId.Factory factory = FuzzedBlobIds.factory(data);
        char separator = FuzzedBlobIds.separator(data);
        String sample = data.consumeInt(1, Integer.MAX_VALUE) + "" + separator + data.consumeInt(0, Integer.MAX_VALUE) + separator + "blob";
        BlobId chunk = ChunkIds.newChunk(factory.parse(sample), factory);
        SlotAddress address = new SlotAddress(chunk, FuzzedBlobIds.location(data));

        assertThat(SlotAddress.parse(address.asBlobId(factory), factory)).contains(address);
    }
}
