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

package org.apache.james.util.streams;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class JamesCollectorsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void chunkerShouldAcceptEmptyStrem() {
        Stream<Integer> emptyStream = Stream.of();

        assertThat(emptyStream.collect(JamesCollectors.chunker(10))
            .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void chunkerShouldThrowOnZeroChunkSize() {
        expectedException.expect(IllegalArgumentException.class);

        JamesCollectors.chunker(0);
    }

    @Test
    public void chunkerShouldThrowOnNegativeChunkSize() {
        expectedException.expect(IllegalArgumentException.class);

        JamesCollectors.chunker(-1);
    }

    @Test
    public void chunkerShouldChunkMonoValueStreams() {
        Stream<Integer> monoValueStream = Stream.of(1);

        List<List<Integer>> values = monoValueStream.collect(JamesCollectors.chunker(10))
            .map(ImmutableList::copyOf)
            .collect(Guavate.toImmutableList());
        assertThat(values)
            .isEqualTo(ImmutableList.of(ImmutableList.of(1)));
    }

    @Test
    public void chunkerShouldChunkStreamsSmallerThanChunkSize() {
        Stream<Integer> stream = Stream.of(1, 2);

        List<List<Integer>> values = stream.collect(JamesCollectors.chunker(3))
            .map(ImmutableList::copyOf)
            .collect(Guavate.toImmutableList());
        assertThat(values)
            .isEqualTo(ImmutableList.of(ImmutableList.of(1, 2)));
    }

    @Test
    public void chunkerShouldChunkStreamsAsBigAsChunkSize() {
        Stream<Integer> stream = Stream.of(1, 2, 3);

        List<List<Integer>> values = stream.collect(JamesCollectors.chunker(3))
            .map(ImmutableList::copyOf)
            .collect(Guavate.toImmutableList());
        assertThat(values)
            .isEqualTo(ImmutableList.of(ImmutableList.of(1, 2, 3)));
    }

    @Test
    public void chunkerShouldChunkStreamsBiggerThanChunkSize() {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4);

        List<List<Integer>> values = stream.collect(JamesCollectors.chunker(3))
            .map(ImmutableList::copyOf)
            .collect(Guavate.toImmutableList());
        assertThat(values)
            .isEqualTo(ImmutableList.of(
                ImmutableList.of(1, 2, 3),
                ImmutableList.of(4)));
    }

    @Test
    public void chunkerShouldChunkInSeveralBuckets() {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5, 6, 7);

        List<List<Integer>> values = stream.collect(JamesCollectors.chunker(3))
            .map(ImmutableList::copyOf)
            .collect(Guavate.toImmutableList());
        assertThat(values)
            .isEqualTo(ImmutableList.of(
                ImmutableList.of(1, 2, 3),
                ImmutableList.of(4, 5, 6),
                ImmutableList.of(7)));
    }
}
