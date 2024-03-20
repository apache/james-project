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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

public class ReactorUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReactorUtils.class);
    public static final String MDC_KEY_PREFIX = "MDC-";
    public static final int DEFAULT_CONCURRENCY = 16;
    public static final int LOW_CONCURRENCY = 4;
    private static final int DEFAULT_BOUNDED_ELASTIC_SIZE = Optional.ofNullable(System.getProperty("james.schedulers.defaultBoundedElasticSize"))
        .map(Integer::parseInt)
        .orElseGet(() -> 10 * Runtime.getRuntime().availableProcessors());
    public static final int DEFAULT_BOUNDED_ELASTIC_QUEUESIZE = Optional.ofNullable(System.getProperty("james.schedulers.defaultBoundedElasticQueueSize"))
        .map(Integer::parseInt)
        .orElse(100000);
    private static final int TTL_SECONDS = 60;
    private static final boolean DAEMON = true;
    public static final Scheduler BLOCKING_CALL_WRAPPER = Schedulers.newBoundedElastic(DEFAULT_BOUNDED_ELASTIC_SIZE, DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
        "blocking-call-wrapper", TTL_SECONDS, DAEMON);


    public static <T, U> RequiresQuantity<T, U> throttle() {
        return elements -> duration -> operation -> {
            Preconditions.checkArgument(elements > 0, "'windowMaxSize' must be strictly positive");
            Preconditions.checkArgument(!duration.isNegative(), "'windowDuration' must be strictly positive");
            Preconditions.checkArgument(!duration.isZero(), "'windowDuration' must be strictly positive");

            return flux -> flux
                .onErrorContinue((e, o) -> LOGGER.error("Error encountered while generating throttled entries", e))
                .window(elements)
                .delayElements(duration)
                .concatMap(window -> window.flatMap(operation, Queues.SMALL_BUFFER_SIZE)
                    .onErrorResume(e -> {
                        LOGGER.error("Error encountered while throttling", e);
                        return Mono.empty();
                    }));
        };
    }

    @FunctionalInterface
    public interface RequiresQuantity<T, U> {
        RequiresPeriod<T, U> elements(int maxSize);
    }

    @FunctionalInterface
    public interface RequiresPeriod<T, U> {
        RequiresOperation<T, U> per(Duration duration);
    }

    @FunctionalInterface
    public interface RequiresOperation<T, U> {
        Function<Flux<T>, Flux<U>> forOperation(Function<T, Publisher<U>> operation);
    }

    public static <T> Mono<T> executeAndEmpty(Runnable runnable) {
        return Mono.fromRunnable(runnable).then(Mono.empty());
    }

    public static <T> BiConsumer<Optional<T>, SynchronousSink<T>> publishIfPresent() {
        return (element, sink) -> element.ifPresent(sink::next);
    }

    public static InputStream toInputStream(Flux<ByteBuffer> byteArrays) {
        InputStreamSubscriber inputStreamSubscriber = new InputStreamSubscriber();
        byteArrays.subscribe(inputStreamSubscriber);
        return inputStreamSubscriber;
    }

    public static Flux<ByteBuffer> toChunks(InputStream inputStream, int bufferSize) {
        return Flux.<ByteBuffer>generate(sink -> {
                try {
                    byte[] buffer = new byte[bufferSize];
                    int read = inputStream.read(buffer);
                    if (read >= 0) {
                        sink.next(ByteBuffer.wrap(buffer, 0, read));
                    } else {
                        sink.complete();
                    }
                } catch (IOException e) {
                    sink.error(e);
                }
            }).defaultIfEmpty(ByteBuffer.wrap(new byte[0]));
    }

    private static class InputStreamSubscriber extends InputStream implements Subscriber<ByteBuffer> {
        private static final int NO_MORE_DATA = -1;

        private final AtomicReference<Subscription> subscription = new AtomicReference<>();
        private final AtomicReference<Throwable> exception = new AtomicReference<>();
        private final AtomicReference<ByteBuffer> currentBuffer = new AtomicReference<>();
        private final AtomicReference<CountDownLatch> nextBufferSignal = new AtomicReference<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);

        @Override
        public void onSubscribe(Subscription s) {
            subscription.set(s);
            requestOne();
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            if (byteBuffer.remaining() == 0) {
                subscription.get().request(1);
            } else {
            currentBuffer.set(byteBuffer);
            CountDownLatch countDownLatch = nextBufferSignal.get();
            nextBufferSignal.set(null);
            Optional.ofNullable(countDownLatch).ifPresent(CountDownLatch::countDown);
            }
        }

        @Override
        public void onError(Throwable t) {
            subscription.set(null);
            exception.set(t);
            Optional.ofNullable(nextBufferSignal.get()).ifPresent(CountDownLatch::countDown);
            nextBufferSignal.set(null);
            completed.set(true);
        }

        @Override
        public void onComplete() {
            subscription.set(null);
            completed.set(true);
            Optional.ofNullable(nextBufferSignal.get()).ifPresent(CountDownLatch::countDown);
            nextBufferSignal.set(null);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return nextNonEmptyBuffer()
                .map(buffer -> {
                    int toRead = Math.min(len, buffer.remaining());
                    buffer.get(b, off, toRead);
                    if (buffer.remaining() == 0 && !completed.get()) {
                        currentBuffer.set(null);
                        requestOne();
                    }
                    return toRead;
                })
                .orElseGet(Throwing.supplier(() -> {
                    if (exception.get() != null) {
                        throw exception.get();
                    }
                    return NO_MORE_DATA;
                }).sneakyThrow());
        }

        @Override
        public int available() {
            return Optional.ofNullable(currentBuffer.get()).map(Buffer::remaining).orElse(0);
        }

        @Override
        public int read() {
            return nextNonEmptyBuffer()
                .map(byteBuffer -> {
                    int result = byteToInt(byteBuffer);
                    if (byteBuffer.remaining() == 0 && !completed.get()) {
                        currentBuffer.set(null);
                        requestOne();
                    }
                    return result;
                })
                .orElseGet(Throwing.supplier(() -> {
                    if (exception.get() != null) {
                        throw exception.get();
                    }
                    return NO_MORE_DATA;
                }).sneakyThrow());
        }

        private Optional<ByteBuffer> nextNonEmptyBuffer() {
            ByteBuffer byteBuffer = currentBuffer.get();
            if (byteBuffer == null || byteBuffer.remaining() == 0) {
                if (completed.get() || exception.get() != null || subscription.get() == null) {
                    return Optional.empty();
                } else {
                    CountDownLatch countDownLatch = nextBufferSignal.get();
                    if (countDownLatch != null) {
                        try {
                            countDownLatch.await();
                        } catch (InterruptedException e) {
                            close();
                            Thread.currentThread().interrupt();
                            return Optional.empty();
                        }
                    } else {
                        requestOne();
                    }
                    return nextNonEmptyBuffer();
                }
            }
            return Optional.of(byteBuffer);
        }

        private CountDownLatch requestOne() {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            nextBufferSignal.set(countDownLatch);
            subscription.get().request(1);
            return countDownLatch;
        }

        @Override
        public void close() {
            Optional.ofNullable(subscription.get()).ifPresent(Subscription::cancel);
            subscription.set(null);
            Optional.ofNullable(nextBufferSignal.get()).ifPresent(CountDownLatch::countDown);
            nextBufferSignal.set(null);
        }
    }

    private static int byteToInt(ByteBuffer buffer) {
        return buffer.get() & 0xff;
    }

    public static Consumer<Signal<?>> logOnError(Consumer<Throwable> errorLogStatement) {
        return signal -> {
            if (!signal.isOnError()) {
                return;
            }
            logWithContext(() -> errorLogStatement.accept(signal.getThrowable()), signal.getContextView());
        };
    }

    public static Consumer<Signal<?>> log(Runnable logStatement) {
        return signal -> logWithContext(logStatement, signal.getContextView());
    }

    private static void logWithContext(Runnable logStatement, ContextView contextView) {
        try (Closeable mdc = retrieveMDCBuilder(contextView).build()) {
            logStatement.run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Mono<Void> logAsMono(Runnable logStatement) {
        return Mono.deferContextual(contextView -> Mono.fromRunnable(() -> logWithContext(logStatement, contextView)));
    }

    public static Consumer<Signal<?>> logOnError(Class<? extends Throwable> clazz, Consumer<Throwable> errorLogStatement) {
        return signal -> {
            if (signal.hasError() && clazz.isInstance(signal.getThrowable())) {
                logWithContext(() -> errorLogStatement.accept(signal.getThrowable()), signal.getContextView());
            }
        };
    }

    public static Context context(String keySuffix, MDCBuilder mdcBuilder) {
        return Context.of(mdcKey(keySuffix), mdcBuilder);
    }

    private static String mdcKey(String value) {
        return MDC_KEY_PREFIX + value;
    }

    public static MDCBuilder retrieveMDCBuilder(Signal<?> signal) {
        return retrieveMDCBuilder(signal.getContextView());
    }

    public static MDCBuilder retrieveMDCBuilder(ContextView context) {
        return context.stream()
            .filter(entry -> entry.getKey() instanceof String)
            .filter(entry -> entry.getValue() instanceof MDCBuilder)
            .filter(entry -> ((String) entry.getKey()).startsWith(MDC_KEY_PREFIX))
            .map(entry -> (MDCBuilder) entry.getValue())
            .reduce(MDCBuilder.create(), MDCBuilder::addToContext);
    }
}
