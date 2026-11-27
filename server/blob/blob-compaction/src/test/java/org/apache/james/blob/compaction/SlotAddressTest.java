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

import java.time.Clock;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SlotAddressTest {
    private static final PlainBlobId.Factory PLAIN = new PlainBlobId.Factory();
    private static final GenerationAwareBlobId.Factory GENERATION_AWARE = new GenerationAwareBlobId.Factory(
        Clock.systemUTC(), PLAIN, GenerationAwareBlobId.Configuration.DEFAULT);
    private static final MinIOGenerationAwareBlobId.Factory MINIO = new MinIOGenerationAwareBlobId.Factory(
        Clock.systemUTC(), GenerationAwareBlobId.Configuration.DEFAULT, PLAIN);
    private static final SlotLocation LOCATION = new SlotLocation(1024, 300);

    @Test
    void shouldRoundTripWithTheUnderscoreLayout() {
        BlobId chunk = GENERATION_AWARE.parse("1_689_chunk_0123456789abcdef0123456789abcdef");
        SlotAddress address = new SlotAddress(chunk, LOCATION);

        BlobId blobId = address.asBlobId(GENERATION_AWARE);

        assertThat(blobId.asString()).isEqualTo("1_689_chunk_0123456789abcdef0123456789abcdef~1024~300");
        assertThat(SlotAddress.parse(blobId, GENERATION_AWARE)).contains(address);
    }

    @Test
    void shouldRoundTripWithTheMinioLayout() {
        BlobId chunk = MINIO.parse("1/689/chunk/0123456789abcdef0123456789abcdef");
        SlotAddress address = new SlotAddress(chunk, LOCATION);

        BlobId blobId = address.asBlobId(MINIO);

        assertThat(blobId.asString()).isEqualTo("1/689/chunk/0123456789abcdef0123456789abcdef~1024~300");
        assertThat(SlotAddress.parse(blobId, MINIO)).contains(address);
    }

    @Test
    void addressOfAFreshChunkShouldFitMailboxBlobIdColumns() {
        BlobId chunk = ChunkIds.newChunk(GENERATION_AWARE.parse("12_99999999_blob"), GENERATION_AWARE);

        BlobId blobId = new SlotAddress(chunk, new SlotLocation(Integer.MAX_VALUE, Integer.MAX_VALUE)).asBlobId(GENERATION_AWARE);

        // PostgreSQL stores message body blob ids as VARCHAR(200).
        assertThat(blobId.asString()).hasSizeLessThan(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1_689_0123456789abcdef",
        "1_689_chunk_0123456789abcdef",
        "1_689_chunk_abc~1024",
        "1_689_chunk_abc~1024~300~1",
        "1_689_chunk_abc~01024~300",
        "1_689_chunk_abc~1024~+300",
        "1_689_chunk_abc~-1~300",
        "1_689_chunk_abc~99999999999999999999~300",
        "1_689_chunk_abc~0~300",
        "1_689_chunk_abc~1024~2",
        "1_0689_chunk_abc~1024~300",
        "0_689_chunk_abc~1024~300",
        "1_689_chunkabc~1024~300",
        "~1024~300"
    })
    void shouldNotParseOtherBlobIds(String blobId) {
        assertThat(SlotAddress.parse(PLAIN.of(blobId), GENERATION_AWARE)).isEmpty();
    }

    @Test
    void shouldRejectAddressesLongerThanMailboxColumns() {
        BlobId longChunk = PLAIN.of("1_689_chunk_" + "a".repeat(180));

        assertThatThrownBy(() -> new SlotAddress(longChunk, LOCATION)).isInstanceOf(IllegalArgumentException.class);
        assertThat(SlotAddress.parse(PLAIN.of(longChunk.asString() + "~1024~300"), PLAIN)).isEmpty();
    }

    @Test
    void shouldRejectNonChunkIds() {
        assertThatThrownBy(() -> new SlotAddress(PLAIN.of("1_689_blob"), LOCATION)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNestedSlotAddresses() {
        assertThatThrownBy(() -> new SlotAddress(PLAIN.of("1_689_chunk_abc~1024~300"), LOCATION)).isInstanceOf(IllegalArgumentException.class);
    }
}
