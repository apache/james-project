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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;
import reactor.util.context.Context;

public class ReactorUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReactorUtils.class);
    public static final String MDC_KEY_PREFIX = "MDC-";
    public static final int DEFAULT_CONCURRENCY = 16;

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
        PipedInputStream in = new PipedInputStream();
        byteArrays.subscribeOn(Schedulers.elastic())
            .subscribe(new PipedStreamSubscriber(in));
        return in;
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

    static class PipedStreamSubscriber extends BaseSubscriber<ByteBuffer> {
        private final PipedOutputStream out;
        private final WritableByteChannel channel;
        private Optional<Subscription> subscription;

        PipedStreamSubscriber(PipedInputStream in) {
            try {
                this.out = new PipedOutputStream(in);
                this.channel = Channels.newChannel(out);
                this.subscription = Optional.empty();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            this.subscription = Optional.of(subscription);
            subscription.request(2);
        }

        @Override
        protected void hookOnNext(ByteBuffer payload) {
            try {
                subscription.ifPresent(sub -> sub.request(1));
                channel.write(payload);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void hookOnComplete() {
            close();
        }

        @Override
        protected void hookOnError(Throwable error) {
            LOGGER.error("Failure processing stream", error);
            close();
        }

        @Override
        protected void hookOnCancel() {
            close();
        }

        private void close() {
            try {
                channel.close();
                out.close();
            } catch (IOException e) {
                LOGGER.warn("Error closing pipe", e);
            }
        }
    }

    public static Consumer<Signal<?>> logOnError(Consumer<Throwable> errorLogStatement) {
        return signal -> {
            if (!signal.isOnError()) {
                return;
            }
            try {
                try (Closeable mdc = retrieveMDCBuilder(signal).build()) {
                    errorLogStatement.accept(signal.getThrowable());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static Consumer<Signal<?>> log(Runnable logStatement) {
        return signal -> {
            try (Closeable mdc = retrieveMDCBuilder(signal).build()) {
                logStatement.run();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static Context context(String keySuffix, MDCBuilder mdcBuilder) {
        return Context.of(mdcKey(keySuffix), mdcBuilder);
    }

    private static String mdcKey(String value) {
        return MDC_KEY_PREFIX + value;
    }

    private static MDCBuilder retrieveMDCBuilder(Signal<?> signal) {
        return signal.getContext().stream()
            .filter(entry -> entry.getKey() instanceof String)
            .filter(entry -> entry.getValue() instanceof MDCBuilder)
            .filter(entry -> ((String) entry.getKey()).startsWith(MDC_KEY_PREFIX))
            .map(entry -> (MDCBuilder) entry.getValue())
            .reduce(MDCBuilder.create(), MDCBuilder::addContext);
    }

}
