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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.james.util.ReactorUtils.Throttler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ReactorUtilsTest {
    static final int BUFFER_SIZE = 5;
    
    @Nested
    class Throttling {
        @Test
        void windowShouldThrowWhenMaxSizeIsNegative() {
            assertThatThrownBy(() -> Throttler.forOperation(Mono::just)
                    .window(-1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void windowShouldThrowWhenMaxSizeIsZero() {
            assertThatThrownBy(() -> Throttler.forOperation(Mono::just)
                    .window(0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void windowShouldThrowWhenDurationIsNegative() {
            assertThatThrownBy(() -> Throttler.forOperation(Mono::just)
                    .window(1, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void windowShouldThrowWhenDurationIsZero() {
            assertThatThrownBy(() -> Throttler.forOperation(Mono::just)
                    .window(1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throttleShouldApplyMaxSize() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(100);

            Stopwatch stopwatch = Stopwatch.createUnstarted();

            Flux<Integer> originalFlux = Flux.range(0, 10);
            ImmutableList<Long> windowMembership = Throttler.<Integer, Long>forOperation(
                    i -> Mono.fromCallable(() -> stopwatch.elapsed(TimeUnit.MILLISECONDS)))
                .window(windowMaxSize, windowDuration)
                .throttle(originalFlux)
                .doOnSubscribe(signal -> stopwatch.start())
                .map(i -> i / 100)
                .collect(Guavate.toImmutableList())
                .block();

            assertThat(windowMembership)
                .containsExactly(0L, 0L, 0L, 1L, 1L, 1L, 2L, 2L, 2L, 3L);
        }

        @Test
        void throttleDownStreamConcurrencyShouldNotExceedWindowMaxSize() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(100);

            AtomicInteger ongoingProcessing = new AtomicInteger();

            Flux<Integer> originalFlux = Flux.range(0, 10);
            Function<Integer, Publisher<Integer>> longRunningOperation =
                any -> Mono.fromCallable(ongoingProcessing::incrementAndGet)
                    .flatMap(i -> Mono.delay(windowDuration.multipliedBy(2)).thenReturn(i))
                    .flatMap(i -> Mono.fromRunnable(ongoingProcessing::decrementAndGet).thenReturn(i));

            ImmutableList<Integer> ongoingProcessingUponComputationStart = Throttler.forOperation(longRunningOperation)
                .window(windowMaxSize, windowDuration)
                .throttle(originalFlux)
                .collect(Guavate.toImmutableList())
                .block();

            assertThat(ongoingProcessingUponComputationStart)
                .allSatisfy(processingCount -> assertThat(processingCount).isLessThanOrEqualTo(windowMaxSize));
        }
    }

    @Nested
    class ExecuteAndEmpty {
        @Test
        void shouldExecuteTheRunnableAndReturnEmpty() {
            Counter counter = new Counter(1);

            Mono<?> reactor = Mono.empty()
                    .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> counter.increment(2)))
                    .map(FunctionalUtils.toFunction(any -> counter.increment(4)));

            assertThat(reactor.hasElement().block()).isFalse();
            assertThat(counter.getCounter()).isEqualTo(3);
        }

        @Test
        void shouldNotExecuteTheRunnableAndReturnTheValue() {
            Counter counter = new Counter(1);

            Mono<?> reactor = Mono.just(42)
                    .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> counter.increment(2)))
                    .map(FunctionalUtils.toFunction(any -> counter.increment(4)));

            assertThat(reactor.hasElement().block()).isTrue();
            assertThat(counter.getCounter()).isEqualTo(5);
        }

        private class Counter {
            private Integer counter;

            public Counter(Integer counter) {
                this.counter = counter;
            }

            public void increment(Integer other) {
                counter += other;
            }

            public Integer getCounter() {
                return counter;
            }
        }
    }

    @Nested
    class ToInputStream {

        @Test
        void givenAFluxOf3BytesShouldReadSuccessfullyTheWholeSource() {
            byte[] bytes = "foo bar ...".getBytes(StandardCharsets.US_ASCII);

            Flux<ByteBuffer> source = Flux.fromIterable(Bytes.asList(bytes))
                .window(3)
                .flatMapSequential(Flux::collectList)
                .map(Bytes::toArray)
                .map(ByteBuffer::wrap);

            InputStream inputStream = ReactorUtils.toInputStream(source);

            assertThat(inputStream).hasSameContentAs(new ByteArrayInputStream(bytes));
        }

        @Test
        void givenALongFluxBytesShouldReadSuccessfullyTheWholeSource() {
            byte[] bytes = RandomStringUtils.randomAlphabetic(41111).getBytes(StandardCharsets.US_ASCII);

            Flux<ByteBuffer> source = Flux.fromIterable(Bytes.asList(bytes))
                .window(3)
                .flatMapSequential(Flux::collectList)
                .map(Bytes::toArray)
                .map(ByteBuffer::wrap);

            InputStream inputStream = ReactorUtils.toInputStream(source);

            assertThat(inputStream).hasSameContentAs(new ByteArrayInputStream(bytes));
        }

        @Test
        void givenAFluxOnOneByteShouldConsumeOnlyTheReadBytesAndThePrefetch() throws IOException, InterruptedException {
            AtomicInteger generateElements = new AtomicInteger(0);
            Flux<ByteBuffer> source = Flux.range(0, 10)
                .subscribeOn(Schedulers.elastic())
                .limitRate(2)
                .doOnRequest(request -> generateElements.getAndAdd((int) request))
                .map(index -> new byte[] {(byte) (int) index})
                .map(ByteBuffer::wrap);

            InputStream inputStream = ReactorUtils.toInputStream(source);
            byte[] readBytes = IOUtils.readFully(inputStream, 5);

            assertThat(readBytes).contains(0, 1, 2, 3, 4);
            //make sure reactor is done with prefetch
            Thread.sleep(200);
            assertThat(generateElements.get()).isEqualTo(6);
        }

        @Test
        void givenAFluxOf3BytesShouldConsumeOnlyTheReadBytesAndThePrefetch() throws IOException, InterruptedException {
            AtomicInteger generateElements = new AtomicInteger(0);
            Flux<ByteBuffer> source = Flux.just(
                new byte[] {0, 1, 2},
                new byte[] {3, 4, 5},
                new byte[] {6, 7, 8})
                    .subscribeOn(Schedulers.elastic())
                    .map(ByteBuffer::wrap)
                    .limitRate(2)
                    .doOnRequest(request -> generateElements.getAndAdd((int) request));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            byte[] readBytes = IOUtils.readFully(inputStream, 5);

            assertThat(readBytes).contains(0, 1, 2, 3, 4);
            //make sure reactor is done with prefetch
            Thread.sleep(200);
            assertThat(generateElements.get()).isEqualTo(3);
        }

        @Test
        void givenAFluxOf3BytesWithAnEmptyByteArrayShouldConsumeOnlyTheReadBytesAndThePrefetch() throws IOException {
            AtomicInteger generateElements = new AtomicInteger(0);
            Flux<ByteBuffer> source = Flux.just(
                new byte[] {0, 1, 2},
                new byte[] {},
                new byte[] {3, 4, 5},
                new byte[] {6, 7, 8},
                new byte[] {9, 10, 11})
                    .subscribeOn(Schedulers.elastic())
                    .map(ByteBuffer::wrap)
                    .limitRate(2)
                    .doOnRequest(request -> generateElements.getAndAdd((int) request));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            IOUtils.readFully(inputStream, 5);

            byte[] readBytesBis = IOUtils.readFully(inputStream, 2);
            assertThat(readBytesBis).contains(5,6);
        }

        @Test
        void givenAnEmptyFluxShouldConsumeOnlyThePrefetch() throws IOException, InterruptedException {
            AtomicInteger generateElements = new AtomicInteger(0);
            Flux<ByteBuffer> source = Flux.<byte[]>empty()
                    .subscribeOn(Schedulers.elastic())
                    .map(ByteBuffer::wrap)
                    .limitRate(2)
                    .doOnRequest(request -> generateElements.getAndAdd((int) request));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            byte[] readBytes = new byte[5];
            inputStream.read(readBytes, 0, readBytes.length);

            assertThat(readBytes).contains(0, 0, 0, 0, 0);
            //make sure reactor is done with prefetch
            Thread.sleep(200);
            assertThat(generateElements.get()).isEqualTo(1);
        }
    }

    @Nested
    class ToChunks {
        @Test
        void givenInputStreamSmallerThanBufferSizeShouldReturnOneChunk() {
            byte[] bytes = "foo".getBytes(StandardCharsets.UTF_8);
            InputStream source = new ByteArrayInputStream(bytes);

            List<ByteBuffer> expected = ImmutableList.of(ByteBuffer.wrap(bytes));

            List<ByteBuffer> chunks = ReactorUtils.toChunks(source, BUFFER_SIZE)
                .collectList()
                .block();

            assertThat(chunks).isEqualTo(expected);
        }

        @Test
        void givenInputStreamEqualToBufferSizeShouldReturnOneChunk() {
            byte[] bytes = "foooo".getBytes(StandardCharsets.UTF_8);
            InputStream source = new ByteArrayInputStream(bytes);

            List<ByteBuffer> expected = ImmutableList.of(ByteBuffer.wrap(bytes));

            List<ByteBuffer> chunks = ReactorUtils.toChunks(source, BUFFER_SIZE)
                .collectList()
                .block();

            assertThat(chunks).isEqualTo(expected);
        }

        @Test
        void givenInputStreamSlightlyBiggerThanBufferSizeShouldReturnTwoChunks() {
            byte[] bytes = "foobar...".getBytes(StandardCharsets.UTF_8);
            InputStream source = new ByteArrayInputStream(bytes);

            List<ByteBuffer> expected = ImmutableList.of(
                ByteBuffer.wrap("fooba".getBytes(StandardCharsets.UTF_8)),
                ByteBuffer.wrap("r...".getBytes(StandardCharsets.UTF_8)));

            List<ByteBuffer> chunks = ReactorUtils.toChunks(source, BUFFER_SIZE)
                .collectList()
                .block();

            assertThat(chunks).isEqualTo(expected);
        }

        @Test
        void givenInputStreamBiggerThanBufferSizeShouldReturnMultipleChunks() {
            byte[] bytes = RandomStringUtils.randomAlphabetic(41111).getBytes(StandardCharsets.UTF_8);
            InputStream source = new ByteArrayInputStream(bytes);

            List<ByteBuffer> expected = Flux.fromIterable(Bytes.asList(bytes))
                .window(BUFFER_SIZE)
                .flatMapSequential(Flux::collectList)
                .map(Bytes::toArray)
                .map(ByteBuffer::wrap)
                .collectList()
                .block();

            List<ByteBuffer> chunks = ReactorUtils.toChunks(source, BUFFER_SIZE)
                .collectList()
                .block();

            assertThat(chunks).isEqualTo(expected);
        }

        @Test
        void givenEmptyInputStreamShouldReturnEmptyChunk() {
            byte[] bytes = "".getBytes(StandardCharsets.UTF_8);
            InputStream source = new ByteArrayInputStream(bytes);

            List<ByteBuffer> chunks = ReactorUtils.toChunks(source, BUFFER_SIZE)
                .collectList()
                .block();

            List<ByteBuffer> expected = ImmutableList.of(ByteBuffer.wrap(bytes));

            assertThat(chunks).isEqualTo(expected);
        }
    }

    @Nested
    class MDCTest {
        @Test
        void contextShouldEnhanceMDC() {
            String value = "value";
            String key = "key";

            Flux.just(1)
                .doOnEach(ReactorUtils.log(() -> {
                    assertThat(MDC.get(key)).isEqualTo(value);
                }))
                .subscriberContext(ReactorUtils.context("test", MDCBuilder.of(key, value)))
                .blockLast();
        }

        @Test
        void contextShouldNotOverwritePreviousKeys() {
            String value1 = "value1";
            String value2 = "value2";
            String key = "key";

            Flux.just(1)
                .doOnEach(ReactorUtils.log(() -> {
                    assertThat(MDC.get(key)).isEqualTo(value1);
                }))
                .subscriberContext(ReactorUtils.context("test", MDCBuilder.of(key, value1)))
                .subscriberContext(ReactorUtils.context("test", MDCBuilder.of(key, value2)))
                .blockLast();
        }

        @Test
        void contextShouldCombineMDCs() {
            String value1 = "value1";
            String value2 = "value2";
            String key1 = "key1";
            String key2 = "key2";

            Flux.just(1)
                .doOnEach(ReactorUtils.log(() -> {
                    assertThat(MDC.get(key1)).isEqualTo(value1);
                    assertThat(MDC.get(key2)).isEqualTo(value2);
                }))
                .subscriberContext(ReactorUtils.context("test1", MDCBuilder.of(key1, value1)))
                .subscriberContext(ReactorUtils.context("test2", MDCBuilder.of(key2, value2)))
                .blockLast();
        }
    }
}
