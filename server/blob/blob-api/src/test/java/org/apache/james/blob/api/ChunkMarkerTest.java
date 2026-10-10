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
        assertThat(ChunkMarker.isChunkId(blobId)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nonChunkIds")
    void shouldRejectNonChunkIds(String blobId) {
        assertThat(ChunkMarker.isChunkId(blobId)).isFalse();
    }

    private static Stream<String> chunkIds() {
        return Stream.of(
            "1_0_chunk0123456789abcdef",
            "12_34_chunk0123456789abcdef_ABC-xyz",
            "1_0_chunk0123456789abcdef~0~123");
    }

    private static Stream<String> nonChunkIds() {
        return Stream.of(
            "0_0_chunk0123456789abcdef",
            "1_-1_chunk0123456789abcdef",
            "1_0_chunk0123456789abcde",
            "1_0_chunk0123456789abcdef~1",
            "1_0_chunk0123456789abcdef~1~-1",
            "1_0_not-a-chunk0123456789abcdef",
            "a_chunk0123456789abcdef",
            "plain-blob-id");
    }
}
