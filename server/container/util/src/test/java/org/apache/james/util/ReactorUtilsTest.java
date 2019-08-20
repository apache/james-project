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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class ReactorUtilsTest {

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
        void givenAFluxOnOneByteShouldConsumeOnlyTheReadBytesAndThePrefetch() throws IOException, InterruptedException {
            AtomicInteger generateElements = new AtomicInteger(0);
            Flux<ByteBuffer> source = Flux.range(0, 10)
                .subscribeOn(Schedulers.elastic())
                .limitRate(2)
                .doOnRequest(request -> generateElements.getAndAdd((int) request))
                .map(index -> new byte[] {(byte) (int) index})
                .map(ByteBuffer::wrap);

            InputStream inputStream = ReactorUtils.toInputStream(source);
            byte[] readBytes = new byte[5];
            inputStream.read(readBytes, 0, readBytes.length);

            assertThat(readBytes).contains(0, 1, 2, 3, 4);
            Thread.sleep(200);
            assertThat(generateElements.get()).isEqualTo(6);
        }

        @Test
        void givenAFluxOf3BytesShouldConsumeOnlyTheReadBytesAndThePrefetch() throws IOException, InterruptedException {
            AtomicInteger generateElements = new AtomicInteger(0);
            Flux<ByteBuffer> source = Flux.just(new byte[] {0, 1, 2}, new byte[] {3, 4, 5}, new byte[] {6, 7, 8})
                    .subscribeOn(Schedulers.elastic())
                    .map(ByteBuffer::wrap)
                    .limitRate(2)
                    .doOnRequest(request -> generateElements.getAndAdd((int) request));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            byte[] readBytes = new byte[5];
            inputStream.read(readBytes, 0, readBytes.length);

            assertThat(readBytes).contains(0, 1, 2, 3, 4);
            Thread.sleep(200);
            assertThat(generateElements.get()).isEqualTo(3);
        }

        @Test
        void givenAFluxOf3BytesWithAnEmptyByteArrayShouldConsumeOnlyTheReadBytesAndThePrefetch() throws IOException, InterruptedException {
            AtomicInteger generateElements = new AtomicInteger(0);
            Flux<ByteBuffer> source = Flux.just(new byte[] {0, 1, 2}, new byte[] {}, new byte[] {3, 4, 5}, new byte[] {6, 7, 8}, new byte[] {9, 10, 11})
                    .subscribeOn(Schedulers.elastic())
                    .map(ByteBuffer::wrap)
                    .limitRate(2)
                    .doOnRequest(request -> generateElements.getAndAdd((int) request));

            InputStream inputStream = ReactorUtils.toInputStream(source);
            byte[] readBytes = new byte[5];
            inputStream.read(readBytes, 0, readBytes.length);

            assertThat(readBytes).contains(0, 1, 2, 3, 4);
            Thread.sleep(200);
            assertThat(generateElements.get()).isEqualTo(4);
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
            Thread.sleep(200);
            assertThat(generateElements.get()).isEqualTo(1);
        }
    }
}
