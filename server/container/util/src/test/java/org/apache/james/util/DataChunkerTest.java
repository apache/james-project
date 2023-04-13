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

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.google.common.primitives.Bytes;

import reactor.core.publisher.Flux;

class DataChunkerTest {
    public static final int CHUNK_SIZE = 10;

    @Test
    void chunkShouldThrowOnNullData() {
        assertThatThrownBy(() -> DataChunker.chunk(null, CHUNK_SIZE))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void chunkShouldThrowOnNegativeChunkSize() {
        int chunkSize = -1;
        assertThatThrownBy(() -> DataChunker.chunk(new byte[0], chunkSize))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chunkShouldThrowOnZeroChunkSize() {
        int chunkSize = 0;
        assertThatThrownBy(() -> DataChunker.chunk(new byte[0], chunkSize))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chunkShouldReturnOneEmptyArrayWhenInputEmpty() {
        Flux<ByteBuffer> chunks = DataChunker.chunk(new byte[0], CHUNK_SIZE);
        ByteBuffer emptyBuffer = ByteBuffer.wrap(new byte[0]);
        assertThat(chunks.toStream()).containsExactly(emptyBuffer);
    }

    @Test
    void chunkShouldReturnOneArrayWhenInputLessThanChunkSize() {
        byte[] data = "12345".getBytes(StandardCharsets.UTF_8);
        Flux<ByteBuffer> chunks = DataChunker.chunk(data, CHUNK_SIZE);

        assertThat(chunks.toStream()).containsExactly(ByteBuffer.wrap(data));
    }

    @Test
    void chunkShouldReturnOneArrayWhenInputEqualsChunkSize() {
        byte[] data = "1234567890".getBytes(StandardCharsets.UTF_8);
        Assumptions.assumeThat(data.length).isEqualTo(CHUNK_SIZE);

        Flux<ByteBuffer> chunks = DataChunker.chunk(data, CHUNK_SIZE);

        assertThat(chunks.toStream()).containsExactly(ByteBuffer.wrap(data));
    }

    @Test
    void chunkShouldReturnSeveralArrayWhenInputBiggerThanChunkSize() {
        byte[] part1 = "1234567890".getBytes(StandardCharsets.UTF_8);
        byte[] part2 = "12345".getBytes(StandardCharsets.UTF_8);
        Assumptions.assumeThat(part1.length).isEqualTo(CHUNK_SIZE);
        byte[] data = Bytes.concat(part1, part2);

        Flux<ByteBuffer> chunks = DataChunker.chunk(data, CHUNK_SIZE);

        assertThat(chunks.toStream()).containsExactly(ByteBuffer.wrap(part1), ByteBuffer.wrap(part2));
    }
}
