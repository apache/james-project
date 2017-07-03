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

package org.apache.james.mailbox.cassandra.mail.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

public class DataChunkerTest {

    public static final int CHUNK_SIZE = 10;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private DataChunker testee;

    @Before
    public void setUp() {
        testee = new DataChunker();
    }

    @Test
    public void chunkShouldThrowOnNullData() {
        expectedException.expect(NullPointerException.class);

        testee.chunk(null, CHUNK_SIZE);
    }

    @Test
    public void chunkShouldThrowOnNegativeChunkSize() {
        expectedException.expect(IllegalArgumentException.class);

        int chunkSize = -1;
        testee.chunk(new byte[0], chunkSize);
    }

    @Test
    public void chunkShouldThrowOnZeroChunkSize() {
        expectedException.expect(IllegalArgumentException.class);

        int chunkSize = 0;
        testee.chunk(new byte[0], chunkSize);
    }

    @Test
    public void chunkShouldReturnOneEmptyArrayWhenInputEmpty() {
        Stream<Pair<Integer, ByteBuffer>> chunks = testee.chunk(new byte[0], CHUNK_SIZE);
        assertThat(toArraysWithPosition(chunks))
            .containsOnly(Pair.of(0, ImmutableList.of()));
    }

    @Test
    public void chunkShouldReturnOneArrayWhenInputLessThanChunkSize() {
        byte[] data = "12345".getBytes(Charsets.UTF_8);

        Stream<Pair<Integer, ByteBuffer>> chunks = testee.chunk(data, CHUNK_SIZE);

        assertThat(toArraysWithPosition(chunks))
            .containsOnly(Pair.of(0, ImmutableList.copyOf(ArrayUtils.toObject(data))));
    }

    @Test
    public void chunkShouldReturnOneArrayWhenInputEqualsChunkSize() {
        byte[] data = "1234567890".getBytes(Charsets.UTF_8);
        assertThat(data.length).isEqualTo(CHUNK_SIZE);

        Stream<Pair<Integer, ByteBuffer>> chunks = testee.chunk(data, CHUNK_SIZE);

        assertThat(toArraysWithPosition(chunks))
            .containsOnly(Pair.of(0, ImmutableList.copyOf(ArrayUtils.toObject(data))));
    }

    @Test
    public void chunkShouldReturnSeveralArrayWhenInputBiggerThanChunkSize() {
        byte[] part1 = "1234567890".getBytes(Charsets.UTF_8);
        byte[] part2 = "12345".getBytes(Charsets.UTF_8);
        byte[] data = Bytes.concat(part1, part2);

        Stream<Pair<Integer, ByteBuffer>> chunks = testee.chunk(data, CHUNK_SIZE);

        assertThat(toArraysWithPosition(chunks))
            .containsOnly(
                Pair.of(0, ImmutableList.copyOf(ArrayUtils.toObject(part1))),
                Pair.of(1, ImmutableList.copyOf(ArrayUtils.toObject(part2))));
    }

    private ImmutableList<Pair<Integer, List<Byte>>> toArraysWithPosition(Stream<Pair<Integer, ByteBuffer>> chunks) {
        return chunks
            .map(this::toByteArrayPair)
            .collect(Guavate.toImmutableList());
    }

    private Pair<Integer, List<Byte>> toByteArrayPair(Pair<Integer, ByteBuffer> pair) {
        try {
            Byte[] bytes = ArrayUtils.toObject(IOUtils.toByteArray(new ByteBufferBackedInputStream(pair.getRight())));
            return Pair.of(pair.getKey(), Arrays.asList(bytes));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

}
