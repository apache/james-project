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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.base.Throwables;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class CompletableFutureUtilTest {
    private ExecutorService executorService;

    @Before
    public void setUp() {
        executorService = Executors.newFixedThreadPool(4);
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void combineShouldReturnCombinationOfBothSuppliedFutures() {
        int value1 = 18;
        int value2 = 12;

        assertThat(CompletableFutureUtil.combine(
            CompletableFuture.completedFuture(value1),
            CompletableFuture.completedFuture(value2),
            (a, b) -> 2 * a + b)
            .join())
            .isEqualTo(2 * value1 + value2);

    }

    @Test
    public void allOfShouldUnboxEmptyStream() {
        assertThat(
            CompletableFutureUtil.allOf(Stream.empty())
                .join()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void chainAllShouldPreserveExecutionOrder() {
        int itemCount = 10;
        ImmutableList<Integer> ints = IntStream.range(0, itemCount)
            .boxed()
            .collect(Guavate.toImmutableList());

        ConcurrentLinkedDeque<Integer> queue = new ConcurrentLinkedDeque<>();

        CompletableFutureUtil.chainAll(ints.stream(),
            i -> CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(itemCount - i);
                } catch (InterruptedException e) {
                    throw Throwables.propagate(e);
                }
                queue.add(i);
                return i;
            }, executorService))
            .join();

        assertThat(queue)
            .containsExactlyElementsOf(ints);
    }

    @Test
    public void chainAllShouldNotThrowOnEmptyStream() {
        Stream<Integer> result = CompletableFutureUtil.chainAll(Stream.<Integer>of(),
            i -> CompletableFuture.supplyAsync(() -> i, executorService))
            .join();

        assertThat(result.collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void chainAllShouldPreserveOrder() {
        int itemCount = 10;
        ImmutableList<Integer> ints = IntStream.range(0, itemCount)
            .boxed()
            .collect(Guavate.toImmutableList());

        Stream<Integer> result = CompletableFutureUtil.chainAll(ints.stream(),
            i -> CompletableFuture.supplyAsync(() -> i, executorService))
            .join();

        assertThat(result.collect(Guavate.toImmutableList()))
            .containsExactlyElementsOf(ints);
    }

    @Test
    public void allOfShouldUnboxStream() {
        long value1 = 18L;
        long value2 = 19L;
        long value3 = 20L;
        assertThat(
            CompletableFutureUtil.allOf(
                Stream.of(
                    CompletableFuture.completedFuture(value1),
                    CompletableFuture.completedFuture(value2),
                    CompletableFuture.completedFuture(value3)))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsOnly(value1, value2, value3);
    }

    @Test
    public void allOfShouldPreserveOrder() {
        long value1 = 18L;
        long value2 = 19L;
        long value3 = 20L;
        long value4 = 21L;
        long value5 = 22L;
        long value6 = 23L;
        long value7 = 24L;
        long value8 = 25L;
        long value9 = 26L;
        long value10 = 27L;
        assertThat(
            CompletableFutureUtil.allOf(
                Stream.of(
                    CompletableFuture.completedFuture(value1),
                    CompletableFuture.completedFuture(value2),
                    CompletableFuture.completedFuture(value3),
                    CompletableFuture.completedFuture(value4),
                    CompletableFuture.completedFuture(value5),
                    CompletableFuture.completedFuture(value6),
                    CompletableFuture.completedFuture(value7),
                    CompletableFuture.completedFuture(value8),
                    CompletableFuture.completedFuture(value9),
                    CompletableFuture.completedFuture(value10)))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(value1, value2, value3, value4, value5, value6, value7, value8, value9, value10);
    }

    @Test
    public void allOfArrayShouldPreserveOrder() {
        long value1 = 18L;
        long value2 = 19L;
        long value3 = 20L;
        long value4 = 21L;
        long value5 = 22L;
        long value6 = 23L;
        long value7 = 24L;
        long value8 = 25L;
        long value9 = 26L;
        long value10 = 27L;
        assertThat(
            CompletableFutureUtil.allOfArray(
                    CompletableFuture.completedFuture(value1),
                    CompletableFuture.completedFuture(value2),
                    CompletableFuture.completedFuture(value3),
                    CompletableFuture.completedFuture(value4),
                    CompletableFuture.completedFuture(value5),
                    CompletableFuture.completedFuture(value6),
                    CompletableFuture.completedFuture(value7),
                    CompletableFuture.completedFuture(value8),
                    CompletableFuture.completedFuture(value9),
                    CompletableFuture.completedFuture(value10))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(value1, value2, value3, value4, value5, value6, value7, value8, value9, value10);
    }

    @Test
    public void allOfArrayShouldUnboxNoArgs() {
        assertThat(
            CompletableFutureUtil.allOfArray()
                .join()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void allOfArrayShouldUnboxArray() {
        long value1 = 18L;
        long value2 = 19L;
        long value3 = 20L;
        assertThat(
            CompletableFutureUtil.allOfArray(
                    CompletableFuture.completedFuture(value1),
                    CompletableFuture.completedFuture(value2),
                    CompletableFuture.completedFuture(value3))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsOnly(value1, value2, value3);
    }

    @Test
    public void allOfShouldWorkOnVeryLargeStream() {
        CompletableFutureUtil.allOf(
            IntStream.range(0, 100000)
                .boxed()
                .map(CompletableFuture::completedFuture))
            .join();
    }

    @Test
    public void mapShouldMapOnStreamInsideACompletableFuturOfStream() {
        CompletableFuture<Stream<Integer>> futurOfInteger = CompletableFuture.completedFuture(Stream.of(1, 2, 3));

        assertThat(
            CompletableFutureUtil.map(futurOfInteger, integer ->
                integer * 2)
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(2, 4, 6);
    }

    @Test
    public void mapShouldReturnEmptyStreamWhenGivenAnEmptyStream() {
        CompletableFuture<Stream<Integer>> futurOfInteger = CompletableFuture.completedFuture(Stream.of());

        assertThat(
            CompletableFutureUtil.map(futurOfInteger, integer ->
                integer * 2)
                .join()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void thenComposeOnAllShouldMapOnStreamInsideACompletableFuturOfStreamAndTransformTheResultingStreamOfCompletableFutureIntoACompletableOfStreamAndFlatIt() {
        CompletableFuture<Stream<Integer>> futurOfInteger = CompletableFuture.completedFuture(Stream.of(1, 2, 3));

        assertThat(
            CompletableFutureUtil.thenComposeOnAll(futurOfInteger, integer ->
                CompletableFuture.completedFuture(integer * 2))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(2, 4, 6);
    }

    @Test
    public void thenComposeOnAllOnEmptyStreamShouldReturnAnEmptyStream() {
        CompletableFuture<Stream<Integer>> futurOfInteger = CompletableFuture.completedFuture(Stream.of());

        assertThat(
            CompletableFutureUtil.thenComposeOnAll(futurOfInteger, integer ->
                CompletableFuture.completedFuture(integer * 2))
                .join()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void keepValueShouldCompleteWhenTheGivenCompletableFutureEnd() {
        final AtomicInteger numOfFutureExecution = new AtomicInteger(0);

        Supplier<CompletableFuture<Void>> future = () ->
            CompletableFuture.runAsync(numOfFutureExecution::incrementAndGet);

        assertThat(
            CompletableFutureUtil.keepValue(future, 42)
                .join())
            .isEqualTo(42);

        assertThat(
            numOfFutureExecution.get())
            .isEqualTo(1);
    }

    @Test
    public void keepValueShouldReturnNullWithNullValue() {
        Supplier<CompletableFuture<Void>> future = () ->
            CompletableFuture.completedFuture(null);

        assertThat(
            CompletableFutureUtil.keepValue(future, null)
                .join())
            .isNull();
    }

    @Test
    public void composeIfTrueShouldReturnTrueWhenTrue() {
        assertThat(
            CompletableFutureUtil.composeIfTrue(() -> CompletableFuture.completedFuture(null))
                .apply(true)
                .join())
            .isTrue();
    }

    @Test
    public void composeIfTrueShouldReturnFalseWhenFalse() {
        assertThat(
            CompletableFutureUtil.composeIfTrue(() -> CompletableFuture.completedFuture(null))
                .apply(false)
                .join())
            .isFalse();
    }

    @Test
    public void composeIfTrueShouldComposeWhenTrue() {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        CompletableFutureUtil.composeIfTrue(() -> {
            atomicInteger.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        })
            .apply(true)
            .join();

        assertThat(atomicInteger.get()).isEqualTo(1);
    }

    @Test
    public void composeIfTrueShouldNotComposeWhenFalse() {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        CompletableFutureUtil.composeIfTrue(() -> {
            atomicInteger.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        })
            .apply(false)
            .join();

        assertThat(atomicInteger.get()).isEqualTo(0);
    }

    @Test
    public void reduceShouldReturnEmptyWhenNoValue() {
        assertThat(
            CompletableFutureUtil.reduce(
                (i, j) -> i + j,
                CompletableFutureUtil.<Long>allOfArray())
                .join())
            .isEmpty();
    }

    @Test
    public void reduceShouldWork() {
        assertThat(
            CompletableFutureUtil.reduce(
                (i, j) -> i + j,
                CompletableFutureUtil.allOfArray(
                    CompletableFuture.completedFuture(1L),
                    CompletableFuture.completedFuture(2L),
                    CompletableFuture.completedFuture(3L)
                ))
                .join())
            .contains(6L);
    }

    @Test
    public void reduceShouldReturnIdentityAccumulatorWhenNoValue() {
        long identityAccumulator = 0L;
        assertThat(
            CompletableFutureUtil.reduce(
                (i, j) -> i + j,
                CompletableFutureUtil.<Long>allOfArray(),
                identityAccumulator)
                .join())
            .isEqualTo(identityAccumulator);
    }

    @Test
    public void reduceShouldWorkWithIdentityAccumulator() {
        assertThat(
            CompletableFutureUtil.reduce(
                (i, j) -> i + j,
                CompletableFutureUtil.allOfArray(
                    CompletableFuture.completedFuture(1L),
                    CompletableFuture.completedFuture(2L),
                    CompletableFuture.completedFuture(3L)
                ),
                0L)
                .join())
            .isEqualTo(6L);
    }

    @Test
    public void unwrapShouldUnwrapWhenValue() {
        assertThat(
            CompletableFutureUtil.unwrap(
                    CompletableFuture.completedFuture(Optional.of(CompletableFuture.completedFuture(1L))))
                .join())
            .isEqualTo(Optional.of(1L));
    }

    @Test
    public void unwrapShouldUnwrapWhenEmpty() {
        assertThat(
            CompletableFutureUtil.unwrap(
                    CompletableFuture.completedFuture(Optional.empty()))
                .join())
            .isEmpty();
    }
}
