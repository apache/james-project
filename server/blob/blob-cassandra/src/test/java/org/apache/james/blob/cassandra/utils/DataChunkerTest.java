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

package org.apache.james.blob.cassandra.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

public class DataChunkerTest {

    public static final int CHUNK_SIZE = 10;

    private DataChunker testee;

    @BeforeEach
    public void setUp() {
        testee = new DataChunker();
    }

    @Test
    public void chunkShouldThrowOnNullData() {
        assertThatThrownBy(() -> testee.chunk(null, CHUNK_SIZE))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void chunkShouldThrowOnNegativeChunkSize() {
        int chunkSize = -1;
        assertThatThrownBy(() -> testee.chunk(new byte[0], chunkSize))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void chunkShouldThrowOnZeroChunkSize() {
        int chunkSize = 0;
        assertThatThrownBy(() -> testee.chunk(new byte[0], chunkSize))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void chunkShouldReturnOneEmptyArrayWhenInputEmpty() {
        Stream<Pair<Integer, ByteBuffer>> chunks = testee.chunk(new byte[0], CHUNK_SIZE);
        ByteBuffer emptyBuffer = ByteBuffer.wrap(new byte[0]);
        assertThat(chunks)
            .containsOnlyElementsOf(ImmutableList.of(Pair.of(0, emptyBuffer)));
    }

    @Test
    public void chunkShouldReturnOneArrayWhenInputLessThanChunkSize() {
        byte[] data = "12345".getBytes(StandardCharsets.UTF_8);

        Stream<Pair<Integer, ByteBuffer>> chunks = testee.chunk(data, CHUNK_SIZE);

        assertThat(chunks)
            .containsOnlyElementsOf(ImmutableList.of(Pair.of(0, ByteBuffer.wrap(data))));
    }

    @Test
    public void chunkShouldReturnOneArrayWhenInputEqualsChunkSize() {
        byte[] data = "1234567890".getBytes(StandardCharsets.UTF_8);
        assertThat(data.length).isEqualTo(CHUNK_SIZE);

        Stream<Pair<Integer, ByteBuffer>> chunks = testee.chunk(data, CHUNK_SIZE);

        assertThat(chunks)
            .containsOnlyElementsOf(ImmutableList.of(Pair.of(0, ByteBuffer.wrap(data))));
    }

    @Test
    public void chunkShouldReturnSeveralArrayWhenInputBiggerThanChunkSize() {
        byte[] part1 = "1234567890".getBytes(StandardCharsets.UTF_8);
        byte[] part2 = "12345".getBytes(StandardCharsets.UTF_8);
        byte[] data = Bytes.concat(part1, part2);

        Stream<Pair<Integer, ByteBuffer>> chunks = testee.chunk(data, CHUNK_SIZE);

        assertThat(chunks)
        .containsOnlyElementsOf(ImmutableList.of(
                Pair.of(0, ByteBuffer.wrap(part1)),
                Pair.of(1, ByteBuffer.wrap(part2))));
    }

}
