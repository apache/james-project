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
import java.time.Duration;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.junit.jupiter.api.Test;

class ChunkIdTest {
    private static final GenerationAwareBlobId.Configuration CONFIG =
        new GenerationAwareBlobId.Configuration(1, Duration.ofDays(30));

    @Test
    void ofChunkShouldCreateValidChunkId() {
        ChunkId chunkId = ChunkId.ofChunk(CONFIG, 690);

        assertThat(chunkId.family()).isEqualTo(1);
        assertThat(chunkId.generation()).isEqualTo(690);
        assertThat(chunkId.offset()).isZero();
        assertThat(chunkId.limit()).isZero();
        assertThat(chunkId.randomPart()).hasSizeGreaterThanOrEqualTo(16);
        assertThat(chunkId.asString()).startsWith("1_690_chunk").endsWith("~0~0");
    }

    @Test
    void slotRefShouldCreateValidSlotRef() {
        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);
        ChunkId slot = ChunkId.slotRef(chunk, 12345, 678);

        assertThat(slot.family()).isEqualTo(1);
        assertThat(slot.generation()).isEqualTo(690);
        assertThat(slot.offset()).isEqualTo(12345);
        assertThat(slot.limit()).isEqualTo(678);
        assertThat(slot.chunkId()).isEqualTo(chunk.chunkId());
        assertThat(slot.asString()).isEqualTo(chunk.chunkId() + "~12345~678");
    }

    @Test
    void roundTripParseShouldPreserveAllFields() {
        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);
        ChunkId slot = ChunkId.slotRef(chunk, 54321, 999);

        ChunkId parsed = ChunkId.parse(slot.asString());

        assertThat(parsed).isEqualTo(slot);
        assertThat(parsed.family()).isEqualTo(1);
        assertThat(parsed.generation()).isEqualTo(690);
        assertThat(parsed.randomPart()).isEqualTo(chunk.randomPart());
        assertThat(parsed.offset()).isEqualTo(54321);
        assertThat(parsed.limit()).isEqualTo(999);
        assertThat(parsed.chunkId()).isEqualTo(chunk.chunkId());
        assertThat(parsed.asString()).isEqualTo(slot.asString());
    }

    @Test
    void parseShouldThrowWhenMissingTilde() {
        assertThatThrownBy(() -> ChunkId.parse("1_690_chunkAb3def1234567890"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing '~'");
    }

    @Test
    void parseShouldThrowWhenMissingSecondTilde() {
        assertThatThrownBy(() -> ChunkId.parse("1_690_chunkAb3def1234567890~12345"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing second '~'");
    }

    @Test
    void parseShouldThrowWhenBadGeneration() {
        assertThatThrownBy(() -> ChunkId.parse("1_badGen_chunkAb3def1234567890~0~0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid family or generation");
    }

    @Test
    void parseShouldThrowWhenRandomPartTooShort() {
        assertThatThrownBy(() -> ChunkId.parse("1_690_chunkShort~0~0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too short");
    }

    @Test
    void parseShouldThrowWhenNegativeOffset() {
        assertThatThrownBy(() -> ChunkId.parse("1_690_chunkAb3def1234567890~-5~10"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldThrowWhenNegativeLimit() {
        assertThatThrownBy(() -> ChunkId.parse("1_690_chunkAb3def1234567890~5~-10"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isChunkRefShouldDetectChunkRefString() {
        assertThat(ChunkId.isChunkRef("1_690_chunkAb3def1234567890~12345~678")).isTrue();
        assertThat(ChunkId.isChunkRef("1_690_chunkAb3def1234567890")).isTrue();
        assertThat(ChunkId.isChunkRef("1_690_regularHashValue")).isFalse();
        assertThat(ChunkId.isChunkRef((String) null)).isFalse();
    }

    @Test
    void isChunkRefShouldDetectChunkRefBlobId() {
        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);
        ChunkId slot = ChunkId.slotRef(chunk, 100, 200);

        assertThat(ChunkId.isChunkRef(slot)).isTrue();
        assertThat(ChunkId.isChunkRef(new PlainBlobId("regularBlobId"))).isFalse();
        assertThat(ChunkId.isChunkRef((BlobId) null)).isFalse();
    }

    @Test
    void shouldRoundTripThroughGenerationAwareBlobIdFactory() {
        GenerationAwareBlobId.Factory factory = new GenerationAwareBlobId.Factory(
            Clock.systemUTC(),
            new PlainBlobId.Factory(),
            CONFIG);

        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);
        ChunkId slot = ChunkId.slotRef(chunk, 12345, 678);

        GenerationAwareBlobId generationAwareBlobId = factory.parse(slot.asString());

        assertThat(generationAwareBlobId.asString()).isEqualTo(slot.asString());
    }

    @Test
    void withSuffixShouldThrowUnsupportedOperationException() {
        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);

        assertThatThrownBy(() -> chunk.withSuffix("suffix"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isChunkRefShouldNotMatchPlainHashesContainingChunkSubstring() {
        assertThat(ChunkId.isChunkRef("1_690_hash_chunk_test")).isFalse();
        assertThat(ChunkId.isChunkRef("some_chunk_in_the_middle")).isFalse();
        assertThat(ChunkId.isChunkRef("abc_chunk1234")).isFalse();
        assertThat(ChunkId.isChunkRef("1_690_chunkShort")).isFalse();
    }

    @Test
    void equalsShouldBeSymmetricWithPlainBlobId() {
        ChunkId chunk = ChunkId.ofChunk(CONFIG, 690);
        PlainBlobId plainWithSameString = new PlainBlobId(chunk.asString());

        assertThat(chunk.equals(plainWithSameString)).isFalse();
        assertThat(plainWithSameString.equals(chunk)).isFalse();
    }
}
