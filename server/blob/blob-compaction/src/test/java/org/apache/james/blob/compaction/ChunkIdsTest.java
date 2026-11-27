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
import org.apache.james.blob.api.ChunkMarker;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.junit.jupiter.api.Test;

class ChunkIdsTest {
    private static final PlainBlobId.Factory PLAIN = new PlainBlobId.Factory();
    private static final GenerationAwareBlobId.Factory GENERATION_AWARE = new GenerationAwareBlobId.Factory(
        Clock.systemUTC(), PLAIN, GenerationAwareBlobId.Configuration.DEFAULT);
    private static final MinIOGenerationAwareBlobId.Factory MINIO = new MinIOGenerationAwareBlobId.Factory(
        Clock.systemUTC(), GenerationAwareBlobId.Configuration.DEFAULT, PLAIN);

    @Test
    void shouldKeepTheGenerationOfTheUnderscoreLayout() {
        BlobId chunk = ChunkIds.newChunk(GENERATION_AWARE.parse("2_689_abcdef"), GENERATION_AWARE);

        assertThat(chunk.asString()).matches("2_689_chunk_[0-9a-f]{32}");
        assertThat(ChunkMarker.isChunkId(chunk)).isTrue();
        assertThat(chunk).isInstanceOf(GenerationAwareBlobId.class);
    }

    @Test
    void shouldKeepTheGenerationOfTheMinioLayout() {
        BlobId chunk = ChunkIds.newChunk(MINIO.parse("2/689/a/b/cdef"), MINIO);

        assertThat(chunk.asString()).matches("2/689/chunk/[0-9a-f]{32}");
        assertThat(ChunkMarker.isChunkId(chunk)).isTrue();
        assertThat(chunk).isInstanceOf(MinIOGenerationAwareBlobId.class);
    }

    @Test
    void shouldAllocateDistinctChunks() {
        BlobId sample = GENERATION_AWARE.parse("2_689_abcdef");

        assertThat(ChunkIds.newChunk(sample, GENERATION_AWARE)).isNotEqualTo(ChunkIds.newChunk(sample, GENERATION_AWARE));
    }

    @Test
    void shouldRejectAFamilyTheFactoryCannotRepresent() {
        BlobId overflowingFamily = MINIO.parse("99999999999/1/");

        assertThatThrownBy(() -> ChunkIds.newChunk(overflowingFamily, MINIO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectANonCanonicalGeneration() {
        BlobId nonCanonical = PLAIN.of("1/007/abc");

        assertThatThrownBy(() -> ChunkIds.newChunk(nonCanonical, MINIO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAGenerationTheFactoryCannotParse() {
        BlobId overflowingGeneration = PLAIN.of("1_99999999999_abc");

        assertThatThrownBy(() -> ChunkIds.newChunk(overflowingGeneration, GENERATION_AWARE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAFamilyLeavingNoRoomForSlotAddresses() {
        BlobId longFamily = PLAIN.of("1".repeat(150) + "_1_abc");

        assertThatThrownBy(() -> ChunkIds.newChunk(longFamily, PLAIN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlobIdsWithoutGeneration() {
        assertThatThrownBy(() -> ChunkIds.newChunk(PLAIN.of("abcdef"), PLAIN)).isInstanceOf(IllegalArgumentException.class);
    }
}
