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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class FluentFutureStream<T> {

    public static <T> FluentFutureStream<T> unboxStream(FluentFutureStream<Stream<T>> streams) {
        return FluentFutureStream.of(
            streams.completableFuture()
                .thenApply(StreamUtils::flatten));
    }

    public static <T> FluentFutureStream<T> unboxOptional(FluentFutureStream<Optional<T>> optionals) {
        return unboxStream(optionals.map(OptionalUtils::toStream));
    }

    public static <T> FluentFutureStream<T> unboxFuture(FluentFutureStream<CompletableFuture<T>> futures) {
        return FluentFutureStream.of(futures.completableFuture()
            .thenCompose(CompletableFutureUtil::allOf));
    }

    public static <T> FluentFutureStream<T> unboxFluentFuture(FluentFutureStream<FluentFutureStream<T>> futures) {
        return unboxStream(
            unboxFuture(
                futures.map(FluentFutureStream::completableFuture)));
    }

    public static <T> FluentFutureStream<T> unboxFutureOptional(FluentFutureStream<CompletableFuture<Optional<T>>> futures) {
        return unboxOptional(unboxFuture(futures));
    }

    private final CompletableFuture<Stream<T>> completableFuture;

    /**
     * Constructs a FluentFutureStream from a future of Stream.
     */
    public static <T> FluentFutureStream<T> of(CompletableFuture<Stream<T>> completableFuture) {
        return new FluentFutureStream<>(completableFuture);
    }

    public static <T, U> FluentFutureStream<U> of(Stream<CompletableFuture<T>> completableFuture,
                                               Function<FluentFutureStream<T>, FluentFutureStream<U>> unboxer) {
        return unboxer.apply(of(completableFuture));
    }

    /**
     * Constructs a FluentFutureStream from a Stream of Future
     */
    public static <T> FluentFutureStream<T> of(Stream<CompletableFuture<T>> completableFutureStream) {
        return new FluentFutureStream<>(CompletableFutureUtil.allOf(completableFutureStream));
    }

    @SafeVarargs
    public static <T> FluentFutureStream<T> ofFutures(CompletableFuture<T>... completableFutures) {
        return of(Arrays.stream(completableFutures));
    }

    private FluentFutureStream(CompletableFuture<Stream<T>> completableFuture) {
        this.completableFuture = completableFuture;
    }

    /**
     * For all values of the underlying stream, an action will be performed.
     */
    public FluentFutureStream<T> performOnAll(Function<T, CompletableFuture<Void>> action) {
        return map(t -> action.apply(t).thenApply(any -> t),
            FluentFutureStream::unboxFuture);
    }

    /**
     * Apply a transformation to all values of the underlying stream.
     */
    public <U> FluentFutureStream<U> map(Function<T, U> function) {
        return FluentFutureStream.of(
            CompletableFutureUtil.map(completableFuture(), function));
    }

    public <U, V> FluentFutureStream<V> map(Function<T, U> function, Function<FluentFutureStream<U>, FluentFutureStream<V>> unboxer) {
        return unboxer.apply(map(function));
    }

    /**
     * Filter the values of the underlying stream.
     */
    public FluentFutureStream<T> filter(Predicate<T> predicate) {
        return FluentFutureStream.of(completableFuture
            .thenApply(stream -> stream.filter(predicate)));
    }

    public FluentFutureStream<T> thenFilter(Function<T, CompletableFuture<Boolean>> futurePredicate) {
        return map(t -> futurePredicate.apply(t)
            .thenApply(isKept -> Optional.of(t).filter(any -> isKept)),
            FluentFutureStream::unboxFutureOptional);
    }

    /**
     * Reduces the underlying stream. Reduced value is supplied as a Future of optional, as no empty value is supplied.
     */
    public CompletableFuture<Optional<T>> reduce(BinaryOperator<T> combiner) {
        return CompletableFutureUtil.reduce(combiner, completableFuture);
    }

    /**
     * educes the underlying stream. Reduced value is supplied as a Future, as an empty value is specified.
     */
    public CompletableFuture<T> reduce(T emptyAccumulator, BinaryOperator<T> combiner) {
        return CompletableFutureUtil.reduce(combiner, completableFuture, emptyAccumulator);
    }

    /**
     * sort all elements of the stream by the provided {@code Comparator}.
     */
    public FluentFutureStream<T> sorted(Comparator<T> comparator) {
        return FluentFutureStream.of(
            CompletableFutureUtil.sorted(completableFuture(), comparator));
    }

    /**
     * Returns a future of the underlying stream.
     */
    public CompletableFuture<Stream<T>> completableFuture() {
        return this.completableFuture;
    }

    /**
     * Returns the future of the underlying collected stream.
     */
    public <C> CompletableFuture<C> collect(Collector<T, ?, C> collector) {
        return this.completableFuture
            .thenApply(stream -> stream.collect(collector));
    }

    /**
     * Join and returns the underlying stream.
     */
    public Stream<T> join() {
        return completableFuture().join();
    }
}
