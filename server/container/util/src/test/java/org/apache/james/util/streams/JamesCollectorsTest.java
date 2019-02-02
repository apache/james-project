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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class JamesCollectorsTest {

    @Test
    void chunkerShouldAcceptEmptyStrem() {
        Stream<Integer> emptyStream = Stream.of();

        assertThat(emptyStream.collect(JamesCollectors.chunker(10))
            .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void chunkerShouldThrowOnZeroChunkSize() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JamesCollectors.chunker(0));
    }

    @Test
    void chunkerShouldThrowOnNegativeChunkSize() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JamesCollectors.chunker(-1));
    }

    @Test
    void chunkerShouldChunkMonoValueStreams() {
        Stream<Integer> monoValueStream = Stream.of(1);

        List<List<Integer>> values = monoValueStream.collect(JamesCollectors.chunker(10))
            .map(ImmutableList::copyOf)
            .collect(Guavate.toImmutableList());
        assertThat(values)
            .isEqualTo(ImmutableList.of(ImmutableList.of(1)));
    }

    @Test
    void chunkerShouldChunkStreamsSmallerThanChunkSize() {
        Stream<Integer> stream = Stream.of(1, 2);

        List<List<Integer>> values = stream.collect(JamesCollectors.chunker(3))
            .map(ImmutableList::copyOf)
            .collect(Guavate.toImmutableList());
        assertThat(values)
            .isEqualTo(ImmutableList.of(ImmutableList.of(1, 2)));
    }

    @Test
    void chunkerShouldChunkStreamsAsBigAsChunkSize() {
        Stream<Integer> stream = Stream.of(1, 2, 3);

        List<List<Integer>> values = stream.collect(JamesCollectors.chunker(3))
            .map(ImmutableList::copyOf)
            .collect(Guavate.toImmutableList());
        assertThat(values)
            .isEqualTo(ImmutableList.of(ImmutableList.of(1, 2, 3)));
    }

    @Test
    void chunkerShouldChunkStreamsBiggerThanChunkSize() {
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
    void chunkerShouldChunkInSeveralBuckets() {
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
