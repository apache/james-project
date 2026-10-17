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

package org.apache.james.blob.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ChunkMarkerTest {
    @ParameterizedTest
    @MethodSource("chunkIds")
    void shouldRecognizeChunkIds(String blobId) {
        assertThat(ChunkMarker.isChunkId(new PlainBlobId(blobId))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nonChunkIds")
    void shouldRejectNonChunkIds(String blobId) {
        assertThat(ChunkMarker.isChunkId(new PlainBlobId(blobId))).isFalse();
    }

    private static Stream<String> chunkIds() {
        return Stream.of(
            "1_0_chunk0123456789abcdef",
            "12_34_chunk0123456789abcdef_ABC-xyz",
            "1_0_chunk0123456789abcdef~0~123",
            "1_690_chunkshort",
            "1/690/chunkshort",
            "12/12345/chunk550e8400-e29b-41d4-a716-446655440000",
            "1_12345_chunk550e8400-e29b-41d4-a716-446655440000",
            "1/690/chunkidentifier.with+other=characters~0~123",
            "1_690_chunkidentifier.with+other=characters",
            "1/690/chunk",
            "1_690_chunk");
    }

    private static Stream<String> nonChunkIds() {
        return Stream.of(
            "0_0_chunk0123456789abcdef",
            "1_-1_chunk0123456789abcdef",
            "0/690/chunkidentifier",
            "1/-1/chunkidentifier",
            "1/690/ordinary-blob",
            "1/690/hash_chunk_identifier",
            "1/690_chunkidentifier",
            "1_690/chunkidentifier",
            "1_0_not-a-chunk0123456789abcdef",
            "a_chunk0123456789abcdef",
            "plain-blob-id");
    }
}
