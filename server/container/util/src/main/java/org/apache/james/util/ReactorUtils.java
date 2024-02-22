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
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        return new StreamInputStream(byteArrays.toStream(1));
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

    private static class StreamInputStream extends InputStream {
        private static final int NO_MORE_DATA = -1;

        private final Iterator<ByteBuffer> source;
        private final Stream<ByteBuffer> sourceAsStream;
        private Optional<ByteBuffer> currentItemByteStream;

        StreamInputStream(Stream<ByteBuffer> source) {
            this.source = source.iterator();
            this.sourceAsStream = source;
            this.currentItemByteStream = Optional.empty();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return nextNonEmptyBuffer()
                .map(buffer -> {
                    int toRead = Math.min(len, buffer.remaining());
                    buffer.get(b, off, toRead);
                    return toRead;
                })
                .orElse(NO_MORE_DATA);
        }

        @Override
        public int read() {
            return nextNonEmptyBuffer()
                .map(ReactorUtils::byteToInt)
                .orElse(NO_MORE_DATA);
        }

        private Optional<ByteBuffer> nextNonEmptyBuffer() {
            Boolean needsNewBuffer = currentItemByteStream.map(buffer -> !buffer.hasRemaining()).orElse(true);
            if (needsNewBuffer) {
                if (source.hasNext()) {
                    currentItemByteStream = Optional.of(source.next());
                    return nextNonEmptyBuffer();
                } else {
                    return Optional.empty();
                }
            }
            return currentItemByteStream;
        }

        @Override
        public void close() throws IOException {
            sourceAsStream.close();
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
