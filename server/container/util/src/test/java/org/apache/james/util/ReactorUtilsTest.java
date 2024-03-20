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
import static org.awaitility.Durations.ONE_SECOND;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ReactorUtilsTest {
    static final int BUFFER_SIZE = 5;
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    @Nested
    class Throttling {
        @Test
        void windowShouldThrowWhenMaxSizeIsNegative() {
            assertThatThrownBy(() -> ReactorUtils.<Integer, Integer>throttle()
                    .elements(-1)
                    .per(Duration.ofSeconds(1))
                    .forOperation(Mono::just))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void windowShouldThrowWhenMaxSizeIsZero() {
            assertThatThrownBy(() -> ReactorUtils.throttle()
                    .elements(0)
                    .per(Duration.ofSeconds(1))
                    .forOperation(Mono::just))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void windowShouldThrowWhenDurationIsNegative() {
            assertThatThrownBy(() -> ReactorUtils.throttle()
                    .elements(1)
                    .per(Duration.ofSeconds(-1))
                    .forOperation(Mono::just))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void windowShouldThrowWhenDurationIsZero() {
            assertThatThrownBy(() -> ReactorUtils.throttle()
                    .elements(1)
                    .per(Duration.ofSeconds(0))
                    .forOperation(Mono::just))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throttleShouldApplyMaxSize() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(100);

            Stopwatch stopwatch = Stopwatch.createUnstarted();

            ImmutableList<Long> windowMembership = Flux.range(0, 10)
                .transform(ReactorUtils.<Integer, Long>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(i -> Mono.fromCallable(() -> stopwatch.elapsed(TimeUnit.MILLISECONDS))))
                .map(i -> i / 100)
                .doOnSubscribe(signal -> stopwatch.start())
                .collect(ImmutableList.toImmutableList())
                .block();

            // delayElements also delay the first element
            assertThat(windowMembership)
                .containsExactly(1L, 1L, 1L, 2L, 2L, 2L, 3L, 3L, 3L, 4L);
        }

        @Test
        void largeWindowShouldNotOverrunIntermediateBuffers() {
            // windowMaxSize exceeds Queues.SMALL_BUFFER_SIZE & Queues.SMALL_BUFFER_SIZE (256 by default)
            // Combined with slow operations, this ensures we are not filling up intermediate buffers.
            int windowMaxSize = 3_000;
            Duration windowDuration = Duration.ofMillis(100);

            assertThatCode(() -> Flux.range(0, 10_000)
                    .transform(ReactorUtils.<Integer, Long>throttle()
                        .elements(windowMaxSize)
                        .per(windowDuration)
                        .forOperation(i -> Mono.delay(windowDuration.multipliedBy(2))))
                    .blockLast())
                .doesNotThrowAnyException();
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

            ImmutableList<Integer> ongoingProcessingUponComputationStart = originalFlux
                .transform(ReactorUtils.<Integer, Integer>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(longRunningOperation))
                .collect(ImmutableList.toImmutableList())
                .block();

            assertThat(ongoingProcessingUponComputationStart)
                .allSatisfy(processingCount -> assertThat(processingCount).isLessThanOrEqualTo(windowMaxSize));
        }

        @Test
        void throttleShouldNotAbortProcessingUponError() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(100);

            Flux<Integer> originalFlux = Flux.range(0, 10);
            Function<Integer, Publisher<Integer>> operation =
                i -> {
                    if (i == 5) {
                        return Mono.error(new RuntimeException());
                    }
                    return Mono.just(i);
                };

            List<Integer> results = originalFlux
                .transform(ReactorUtils.<Integer, Integer>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(operation))
                .collectList()
                .block();

            assertThat(results)
                .containsExactly(0, 1, 2, 3, 4, 6, 7, 8, 9);
        }

        @Test
        void throttleShouldNotAbortProcessingUponUpstreamError() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(100);

            Flux<Integer> originalFlux = Flux.range(0, 10)
                .flatMap(i -> {
                    if (i == 5) {
                        return Mono.error(new RuntimeException());
                    }
                    return Mono.just(i);
                });

            List<Integer> results = originalFlux
                .transform(ReactorUtils.<Integer, Integer>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(Mono::just))
                .collectList()
                .block();

            assertThat(results)
                .containsExactly(0, 1, 2, 3, 4, 6, 7, 8, 9);
        }

        @Test
        void throttleShouldNotOverwriteErrorHandling() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(20);

            Flux<Long> originalFlux = Flux.just(0L);
            ConcurrentLinkedDeque<Throwable> recordedExceptions = new ConcurrentLinkedDeque<>();

            originalFlux
                .transform(ReactorUtils.<Long, Long>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(any -> Mono.<Long>error(new RuntimeException())
                        .onErrorResume(e -> Mono.fromRunnable(() -> recordedExceptions.add(e)).thenReturn(any))))
                .blockLast();

            assertThat(recordedExceptions).hasSize(1);
        }

        @Test
        void throttleShouldHandleLargeFluxes() {
            int windowMaxSize = 2;
            Duration windowDuration = Duration.ofMillis(1);

            Flux<Integer> originalFlux = Flux.range(0, 10000);

            assertThatCode(() -> originalFlux
                .transform(ReactorUtils.<Integer, Integer>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(Mono::just))
                .blockLast()).doesNotThrowAnyException();
        }

        @Disabled("We no longer rely on 'windowTimeout', this breakage is expected." +
            "'windowTimeout' solves this but create other, more critical issues (large flux cannot be throttled" +
            "as described in https://github.com/reactor/reactor-core/issues/1099")
        @Test
        void throttleShouldGenerateSmallerWindowsWhenUpstreamIsSlow() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(20);
            Stopwatch stopwatch = Stopwatch.createUnstarted();

            Flux<Long> originalFlux = Flux.interval(Duration.ofMillis(10));

            ImmutableList<Long> perWindowCount = originalFlux
                .transform(ReactorUtils.<Long, Long>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(i -> Mono.fromCallable(() -> stopwatch.elapsed(TimeUnit.MILLISECONDS))))
                .map(i -> i / 20)
                .doOnSubscribe(signal -> stopwatch.start())
                .take(10)
                .groupBy(Function.identity())
                .flatMap(Flux::count)
                .collect(ImmutableList.toImmutableList())
                .block();

            // We verify that we generate 2 elements by slice and not 3
            // (as the upstream cannot generate more than 2 element per window)
            assertThat(perWindowCount)
                .allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(2));
        }

        @Test
        void throttleShouldNotDropEntriesWhenUpstreamIsSlow() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(20);

            Flux<Long> originalFlux = Flux.interval(Duration.ofMillis(10));

            ImmutableList<Long> results = originalFlux
                .transform(ReactorUtils.<Long, Long>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(Mono::just))
                .take(10)
                .collect(ImmutableList.toImmutableList())
                .block();

            assertThat(results).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
        }

        @Test
        void throttleShouldCompleteWhenOriginalFluxDoesNotFillAWindow() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(20);

            Flux<Long> originalFlux = Flux.just(0L, 1L);

            ImmutableList<Long> results = originalFlux
                .transform(ReactorUtils.<Long, Long>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(Mono::just))
                .take(10)
                .collect(ImmutableList.toImmutableList())
                .block();

            assertThat(results).containsExactly(0L, 1L);
        }

        @Test
        void throttleShouldSupportEmittingPartiallyCompleteWindowImmediately() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(20);

            ConcurrentLinkedDeque<Long> results = new ConcurrentLinkedDeque<>();
            Flux<Long> originalFlux = Flux.concat(Flux.just(0L, 1L),
                Flux.never());

            originalFlux
                .transform(ReactorUtils.<Long, Long>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(i -> {
                        results.add(i);
                        return Mono.just(i);
                    }))
                .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
                .subscribe();

            Awaitility.await().atMost(ONE_SECOND)
                .untilAsserted(() -> assertThat(results).containsExactly(0L, 1L));
        }

        @Test
        void throttleShouldTolerateSeveralEmptySlices() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(5);

            // 150 ms = 30 * window duration (which is smaller than reactor small buffers)
            Flux<Long> originalFlux = Flux.concat(Flux.just(0L, 1L),
                Mono.delay(Duration.ofMillis(150)).thenReturn(2L));

            List<Long> results = originalFlux
                .transform(ReactorUtils.<Long, Long>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(Mono::just))
                .collectList()
                .block();

            System.out.println(results);
            assertThat(results).containsExactly(0L, 1L, 2L);
        }

        @Test
        void throttleShouldTolerateManyEmptySuccessiveWindows() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(5);

            // 150 ms = 33 * window duration (which is greater than reactor small buffers)
            Flux<Long> originalFlux = Flux.concat(Flux.just(0L, 1L),
                Mono.delay(Duration.ofMillis(165)).thenReturn(2L));

            List<Long> results = originalFlux
                .transform(ReactorUtils.<Long, Long>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(Mono::just))
                .collectList()
                .block();

            System.out.println(results);
            assertThat(results).containsExactly(0L, 1L, 2L);
        }

        @Test
        void throttleShouldTolerateManyEmptyWindows() {
            int windowMaxSize = 3;
            Duration windowDuration = Duration.ofMillis(5);

            // 150 ms = 30 * window duration (which is smaller than reactor small buffers)
            Flux<Long> originalFlux = Flux.concat(Flux.just(0L, 1L),
                Mono.delay(Duration.ofMillis(150)).thenReturn(2L),
                Mono.delay(Duration.ofMillis(150)).thenReturn(3L));

            List<Long> results = originalFlux
                .transform(ReactorUtils.<Long, Long>throttle()
                    .elements(windowMaxSize)
                    .per(windowDuration)
                    .forOperation(Mono::just))
                .collectList()
                .block();

            System.out.println(results);
            assertThat(results).containsExactly(0L, 1L, 2L, 3L);
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
        void givenALongFluxBytesWhenIDoNotReadItBeforeClosingItThenTheOriginalFluxShouldBeDisposed() throws Exception {
            byte[] bytes = RandomStringUtils.randomAlphabetic(41111).getBytes(StandardCharsets.US_ASCII);

            AtomicBoolean canceled = new AtomicBoolean(false);
            Flux<ByteBuffer> source = Flux.fromIterable(Bytes.asList(bytes))
                .window(3)
                .flatMapSequential(Flux::collectList, 1, 1)
                .map(Bytes::toArray)
                .map(ByteBuffer::wrap)
                .doOnCancel(() -> canceled.set(true));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            inputStream.close();

            assertThat(canceled.get()).isTrue();
        }

        @Test
        void givenALongFluxBytesWhenIReadItPartiallyBeforeClosingItThenTheOriginalFluxShouldBeDisposed() throws Exception {
            byte[] bytes = RandomStringUtils.randomAlphabetic(41111).getBytes(StandardCharsets.US_ASCII);

            AtomicBoolean canceled = new AtomicBoolean(false);
            Flux<ByteBuffer> source = Flux.fromIterable(Bytes.asList(bytes))
                .window(3)
                .flatMapSequential(Flux::collectList, 1, 1)
                .map(Bytes::toArray)
                .map(ByteBuffer::wrap)
                .doOnCancel(() -> canceled.set(true));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            byte[] buffer = new byte[3];
            inputStream.read(buffer);
            inputStream.close();

            assertThat(canceled.get()).isTrue();
        }

        @Test
        void givenALongFluxBytesWhenIReadItFullyWithoutClosingItThenTheOriginalFluxShouldBeDisposed() throws Exception {
            byte[] bytes = RandomStringUtils.randomAlphabetic(41111).getBytes(StandardCharsets.US_ASCII);

            AtomicBoolean canceled = new AtomicBoolean(false);
            Flux<ByteBuffer> source = Flux.fromIterable(Bytes.asList(bytes))
                .window(3)
                .flatMapSequential(Flux::collectList, 1, 1)
                .map(Bytes::toArray)
                .map(ByteBuffer::wrap)
                .doFinally(any -> canceled.set(true));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            IOUtils.readFully(inputStream, 41111);
            // do not close it
            assertThat(canceled.get()).isTrue();
        }

        @Test
        void exceptionsShouldCancelOriginalFluxSubscription() {
            AtomicBoolean canceled = new AtomicBoolean(false);
            Flux<ByteBuffer> source = Flux.fromIterable(ImmutableList.of(
                Mono.just("abc"), Mono.just("def"),
                Mono.<String>error(new RuntimeException("Dummy")),
                Mono.just("mno")))
                .doFinally(any -> canceled.set(true))
                .concatMap(s -> s, 1)
                .map(String::getBytes)
                .map(ByteBuffer::wrap);

            InputStream inputStream = ReactorUtils.toInputStream(source);

            try {
                byte[] buffer = new byte[3];
                inputStream.read(buffer);
                inputStream.read(buffer);
                inputStream.read(buffer);
            } catch (Exception e) {
                // expected
            }

            assertThat(canceled.get()).isTrue();
        }

        @Test
        void exceptionsShouldBePropagated() {
            Flux<ByteBuffer> source = Flux.fromIterable(ImmutableList.of(
                Mono.just("abc"), Mono.just("def"), Mono.just("ghi"), Mono.just("jkl"),
                Mono.<String>error(new RuntimeException("Dummy")), Mono.just("mno")))
                .concatMap(s -> s, 1)
                .map(String::getBytes)
                .map(ByteBuffer::wrap);

            InputStream inputStream = ReactorUtils.toInputStream(source);

            assertThatThrownBy(() -> IOUtils.toByteArray(inputStream))
                .hasMessage("Dummy");
        }

        @Test
        void givenAFluxOnOneByteShouldConsumeOnlyTheReadBytesAndThePrefetch() throws IOException, InterruptedException {
            AtomicInteger generateElements = new AtomicInteger(0);
            Flux<ByteBuffer> source = Flux.range(0, 10)
                .subscribeOn(Schedulers.fromExecutor(Executors.newCachedThreadPool()))
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
                    .subscribeOn(Schedulers.fromExecutor(Executors.newCachedThreadPool()))
                    .map(ByteBuffer::wrap)
                    .limitRate(2)
                    .doOnRequest(request -> generateElements.getAndAdd((int) request));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            byte[] readBytes = IOUtils.readFully(inputStream, 5);

            assertThat(readBytes).contains(0, 1, 2, 3, 4);
            //make sure reactor is done with prefetch
            Thread.sleep(200);
            assertThat(generateElements.get()).isLessThanOrEqualTo(3);
        }

        @Test
        void shouldSupportNonBlockingReads() throws IOException, InterruptedException {
            // Emptying current buffer should automatically fetch the next one then polling on available() not to be null
            // is enough to ensure a full read.
            AtomicInteger generateElements = new AtomicInteger(0);
            Flux<ByteBuffer> source = Flux.just(
                new byte[] {0, 1, 2},
                new byte[] {3, 4, 5},
                new byte[] {6, 7, 8})
                    .subscribeOn(Schedulers.fromExecutor(Executors.newCachedThreadPool()))
                    .map(ByteBuffer::wrap)
                    .limitRate(2)
                    .doOnRequest(request -> generateElements.getAndAdd((int) request));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            while (inputStream.available() == 0) {
                Thread.sleep(1);
            }
            assertThat(inputStream.available()).isEqualTo(3);
            inputStream.read();
            assertThat(inputStream.available()).isEqualTo(2);
            inputStream.read();
            assertThat(inputStream.available()).isEqualTo(1);
            inputStream.read();
            Thread.sleep(10);
            assertThat(inputStream.available()).isEqualTo(3);
            while (inputStream.available() == 0) {
                Thread.sleep(1);
            }
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
                    .subscribeOn(Schedulers.fromExecutor(Executors.newCachedThreadPool()))
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
                    .subscribeOn(Schedulers.fromExecutor(Executors.newCachedThreadPool()))
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
                .contextWrite(ReactorUtils.context("test", MDCBuilder.ofValue(key, value)))
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
                .contextWrite(ReactorUtils.context("test", MDCBuilder.ofValue(key, value1)))
                .contextWrite(ReactorUtils.context("test", MDCBuilder.ofValue(key, value2)))
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
                .contextWrite(ReactorUtils.context("test1", MDCBuilder.ofValue(key1, value1)))
                .contextWrite(ReactorUtils.context("test2", MDCBuilder.ofValue(key2, value2)))
                .blockLast();
        }
    }
}
