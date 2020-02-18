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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

class StreamUtilsTest {

    @Test
    void flattenShouldReturnEmptyWhenEmptyStreams() {
        assertThat(
            StreamUtils.<Integer>flatten(ImmutableList.of())
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void flattenShouldPreserveSingleStreams() {
        assertThat(
            StreamUtils.flatten(ImmutableList.of(
                Stream.of(1, 2, 3)))
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }

    @Test
    void flattenShouldMergeSeveralStreamsTogether() {
        assertThat(
            StreamUtils.flatten(ImmutableList.of(
                Stream.of(1, 2, 3),
                Stream.of(4, 5)))
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void flattenShouldAcceptEmptyStreams() {
        assertThat(
            StreamUtils.flatten(ImmutableList.of(
                Stream.of()))
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void flattenShouldMergeEmptyStreamsWithOtherData() {
        assertThat(
            StreamUtils.flatten(ImmutableList.of(
                Stream.of(1, 2),
                Stream.of(),
                Stream.of(3)))
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }

    @Test
    void flattenShouldAcceptEmptyVarArg() {
        assertThat(
            StreamUtils.flatten()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void flattenShouldThrowOnNullVarArg() {
        Stream<String>[] streams = null;
        assertThatThrownBy(() -> StreamUtils.flatten(streams).collect(Guavate.toImmutableList()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void flattenShouldFlattenNonEmptyVarArg() {
        assertThat(StreamUtils.flatten(Stream.of(1), Stream.of(2)).collect(Guavate.toImmutableList()))
            .containsExactly(1, 2);
    }

    @Test
    void ofNullableShouldReturnEmptyStreamWhenNull() {
        assertThat(StreamUtils.ofNullable(null)
            .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void ofNullableShouldReturnAStreamWithElementsOfTheArray() {
        assertThat(StreamUtils.ofNullable(ImmutableList.of(1, 2).toArray())
            .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2);
    }

    @Test
    void unfoldShouldGenerateAnFiniteStream() {
        Stream<Integer> unfolded = StreamUtils.unfold(1, i -> {
            if (i < 10) {
                return Optional.of(i + 1);
            } else {
                return Optional.empty();
            }
        });

        assertThat(unfolded.collect(Guavate.toImmutableList()))
            .contains(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void unfoldShouldGenerateALazyInfiniteStream() {
        AtomicInteger counter = new AtomicInteger(0);
        Stream<Integer> unfolded = StreamUtils.unfold(1, i -> {
            counter.incrementAndGet();
            return Optional.of(i + 1);
        });

        assertThat(unfolded.limit(10).collect(Guavate.toImmutableList()))
            .contains(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertThat(counter.get())
            .isEqualTo(10);
    }

    @Test
    void unfoldShouldHaveAtLeastTheSeed() {
        Stream<Integer> unfolded = StreamUtils.unfold(1, i -> Optional.empty());

        assertThat(unfolded.collect(Guavate.toImmutableList()))
            .contains(1);
    }

    @Test
    void iterateWithANegativeLimitShouldThrow() {
        assertThatCode(() -> StreamUtils.iterate(1, (long) -1, Stream::of))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iterateWithZeroLimitShouldHaveOnlyTheSeed() {
        Stream<Integer> generated = StreamUtils.iterate(1, (long) 0, Stream::of);

        assertThat(generated.collect(Guavate.toImmutableList()))
            .containsOnly(1);
    }

    @Test
    void iterateWithEmptyGeneratorShouldHaveOnlyTheSeed() {
        Stream<Integer> generated = StreamUtils.iterate(1, (long) 10, i -> Stream.of());

        assertThat(generated.collect(Guavate.toImmutableList()))
            .containsOnly(1);
    }

    @Test
    void iterateWithGeneratorShouldHaveOnlyTheLimitedElements() {
        Stream<Integer> generated = StreamUtils.iterate(1, (long) 5, i -> Stream.of(i + 1));

        assertThat(generated.collect(Guavate.toImmutableList()))
            .containsOnly(1, 2, 3, 4, 5, 6);
    }
}
