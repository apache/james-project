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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.github.steveash.guavate.Guavate;

public class FluentFutureStreamTest {

    @Test
    void ofFutureShouldConstructAFluentFutureStream() {
        assertThat(
            FluentFutureStream.ofFutures(
                CompletableFuture.completedFuture(1),
                CompletableFuture.completedFuture(2),
                CompletableFuture.completedFuture(3))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }

    @Test
    void ofShouldConstructAFluentFutureStreamWhenProvidedAFutureOfStream() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }

    @Test
    void ofShouldConstructAFluentFutureStreamWhenProvidedAStreamOfFuture() {
        assertThat(
            FluentFutureStream.of(
                Stream.of(
                    CompletableFuture.completedFuture(1),
                    CompletableFuture.completedFuture(2),
                    CompletableFuture.completedFuture(3)))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }

    @Test
    void ofNestedStreamsShouldConstructAFluentFutureStreamWhenProvidedAStreamOfFutureOfStream() {
        assertThat(
            FluentFutureStream.<Stream<Integer>, Integer>of(
                Stream.of(
                    CompletableFuture.completedFuture(Stream.of(1, 2)),
                    CompletableFuture.completedFuture(Stream.of()),
                    CompletableFuture.completedFuture(Stream.of(3))),
                    FluentFutureStream::unboxStream)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }


    @Test
    void ofOptionalsShouldConstructAFluentFutureStreamWhenProvidedAStreamOfFutureOfOptionals() {
        assertThat(
            FluentFutureStream.<Optional<Integer>, Integer>of(
                Stream.of(
                    CompletableFuture.completedFuture(Optional.of(1)),
                    CompletableFuture.completedFuture(Optional.of(2)),
                    CompletableFuture.completedFuture(Optional.empty()),
                    CompletableFuture.completedFuture(Optional.of(3))),
                    FluentFutureStream::unboxOptional)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }

    @Test
    void completableFutureShouldReturnAFutureOfTheUnderLayingStream() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .completableFuture()
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }

    @Test
    void mapShouldTransformUnderlyingValues() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .map(i -> i + 1)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(2, 3, 4);
    }

    @Test
    void flatMapShouldTransformUnderlyingValuesAndFlatMapResult() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .map(i -> Stream.of(i, i + 1), FluentFutureStream::unboxStream)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 2, 3, 3, 4);
    }

    @Test
    void flatMapOptionalShouldTransformUnderlyingValuesAndUnboxResult() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .map(i -> Optional.of(i + 1)
                    .filter(j -> j % 2 == 0),
                    FluentFutureStream::unboxOptional)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(2, 4);
    }

    @Test
    void reduceShouldGatherAllValuesOfTheUnderlyingStream() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .reduce((a, b) -> a + b)
                .join())
            .contains(6);
    }

    @Test
    void reduceShouldGatherAllValuesOfTheUnderlyingStreamWithAnEmptyValue() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .reduce(0, (a, b) -> a + b)
                .join())
            .isEqualTo(6);
    }

    @Test
    void filterShouldBeAppliedOnTheUnderlyingStream() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .filter(i -> i % 2 == 1)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 3);
    }

    @Test
    void thenFilterShouldBeAppliedOnTheUnderlyingStream() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .thenFilter(i -> CompletableFuture.completedFuture(i % 2 == 1))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 3);
    }

    @Test
    void thenComposeOnAllShouldTransformUnderlyingValuesAndComposeFutures() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .map(i -> CompletableFuture.completedFuture(i + 1), FluentFutureStream::unboxFuture)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(2, 3, 4);
    }

    @Test
    void thenFlatComposeShouldTransformUnderlyingValuesAndComposeFuturesWithStreamUnboxing() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .map(i -> FluentFutureStream.of(CompletableFuture.completedFuture(Stream.of(i, i + 1))), FluentFutureStream::unboxFluentFuture)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 2, 3, 3, 4);
    }

    @Test
    void thenFlatComposeOnOptionalShouldTransformUnderlyingValuesAndComposeFuturesWithOptionalUnboxing() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(
                    Stream.of(1, 2, 3)))
                .map(i -> CompletableFuture.completedFuture(
                    Optional.of(i + 1).filter(j -> j % 2 == 0)),
                    FluentFutureStream::unboxFutureOptional)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(2, 4);
    }

    @Test
    void thenPerformOnAllShouldGenerateASynchronousSideEffectForAllElementsOfTheUnderlyingStream() {
        ConcurrentLinkedDeque<Integer> sideEffects = new ConcurrentLinkedDeque<>();

        FluentFutureStream.of(
            CompletableFuture.completedFuture(
                Stream.of(1, 2, 3)))
            .performOnAll(i -> {
                sideEffects.addLast(i);
                return CompletableFuture.completedFuture(null);
            })
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(sideEffects).containsOnly(1, 2, 3);
    }

    @Test
    void collectShouldReturnTheCollectionOfData() {
        assertThat(
            FluentFutureStream.of(
                Stream.of(
                    CompletableFuture.completedFuture(1),
                    CompletableFuture.completedFuture(2),
                    CompletableFuture.completedFuture(3)))
                .collect(Guavate.toImmutableList())
                .join())
            .containsExactly(1, 2, 3);
    }

    @Test
    void collectShouldReturnEmptyWhenStreamIsEmpty() {
        assertThat(
            FluentFutureStream.ofFutures()
                .collect(Guavate.toImmutableList())
                .join())
            .isEmpty();
    }

    @Test
    void sortedShouldReturnInOrderElements() {
        assertThat(
            FluentFutureStream.of(
                CompletableFuture.completedFuture(Stream.of(4L, 3L, 2L, 1L)))
                .sorted(Long::compareTo)
                .join())
            .containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void sortedShouldReturnEmptyWhenEmpty() {
        CompletableFuture<Stream<Long>> completableFutureStream = CompletableFuture.completedFuture(Stream.of());
        assertThat(
            FluentFutureStream.of(completableFutureStream)
                .sorted(Long::compareTo)
                .join())
            .isEmpty();
    }
}
