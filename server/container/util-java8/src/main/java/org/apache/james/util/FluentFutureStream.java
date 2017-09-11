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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class FluentFutureStream<T> {

    private final CompletableFuture<Stream<T>> completableFuture;

    /**
     * Constructs a FluentFutureStream from a future of Stream.
     */
    public static <T> FluentFutureStream<T> of(CompletableFuture<Stream<T>> completableFuture) {
        return new FluentFutureStream<>(completableFuture);
    }

    /**
     * Constructs a FluentFutureStream from a Stream of Future
     */
    public static <T> FluentFutureStream<T> of(Stream<CompletableFuture<T>> completableFutureStream) {
        return new FluentFutureStream<>(CompletableFutureUtil.allOf(completableFutureStream));
    }

    /**
     * Constructs a FluentFutureStream from a Stream of Future of Stream.
     *
     * Underlying streams are flatMapped.
     */
    public static <T> FluentFutureStream<T> ofNestedStreams(Stream<CompletableFuture<Stream<T>>> completableFuture) {
        return of(completableFuture)
            .flatMap(Function.identity());
    }

    /**
     * Constructs a FluentFutureStream from a Stream of Future of Optionals.
     *
     * Underlying optionals are unboxed.
     */
    public static <T> FluentFutureStream<T> ofOptionals(Stream<CompletableFuture<Optional<T>>> completableFuture) {
        return of(completableFuture)
            .flatMapOptional(Function.identity());
    }

    /**
     * Constructs a FluentFutureStream from the supplied futures.
     */
    @SafeVarargs
    public static <T> FluentFutureStream<T> ofFutures(CompletableFuture<T>... completableFutures) {
        return new FluentFutureStream<>(CompletableFutureUtil.allOfArray(completableFutures));
    }

    private FluentFutureStream(CompletableFuture<Stream<T>> completableFuture) {
        this.completableFuture = completableFuture;
    }

    /**
     * For all values of the underlying stream, an action will be performed.
     */
    public FluentFutureStream<T> performOnAll(Function<T, CompletableFuture<Void>> action) {
        return FluentFutureStream.of(
            CompletableFutureUtil.performOnAll(completableFuture(), action));
    }

    /**
     * Apply a transformation to all values of the underlying stream.
     */
    public <U> FluentFutureStream<U> map(Function<T, U> function) {
        return FluentFutureStream.of(
            CompletableFutureUtil.map(completableFuture(), function));
    }

    /**
     * Apply a transformation to all value of the underlying stream.
     *
     * As the supplied transformation produces streams, the results will be flatMapped.
     */
    public <U> FluentFutureStream<U> flatMap(Function<T, Stream<U>> function) {
        return FluentFutureStream.of(completableFuture().thenApply(stream ->
            stream.flatMap(function)));
    }

    /**
     * Apply a transformation to all value of the underlying stream.
     *
     * As the supplied transformation produces optionals, the results will be unboxed.
     */
    public <U> FluentFutureStream<U> flatMapOptional(Function<T, Optional<U>> function) {
        return map(function)
            .flatMap(OptionalUtils::toStream);
    }

    /**
     * Apply a transformation to all value of the underlying stream.
     *
     * As the supplied transformation produces futures, we need to compose the returned values.
     */
    public <U> FluentFutureStream<U> thenComposeOnAll(Function<T, CompletableFuture<U>> function) {
        return FluentFutureStream.of(
            CompletableFutureUtil.thenComposeOnAll(completableFuture(), function));
    }

    /**
     * Apply a transformation to all value of the underlying stream.
     *
     * As the supplied transformation produces futures of stream, we need to compose then flatMap the returned values.
     */
    public <U> FluentFutureStream<U> thenFlatCompose(Function<T, CompletableFuture<Stream<U>>> function) {
        return FluentFutureStream.of(
            CompletableFutureUtil.thenComposeOnAll(completableFuture(), function))
            .flatMap(Function.identity());
    }

    /**
     * Apply a transformation to all value of the underlying stream.
     *
     * As the supplied transformation produces futures of optionals, we need to compose then unbox the returned values.
     */
    public <U> FluentFutureStream<U> thenFlatComposeOnOptional(Function<T, CompletableFuture<Optional<U>>> function) {
        return FluentFutureStream.of(
            CompletableFutureUtil.thenComposeOnAll(completableFuture(), function))
            .flatMapOptional(Function.identity());
    }

    /**
     * Filter the values of the underlying stream.
     */
    public FluentFutureStream<T> filter(Predicate<T> predicate) {
        return FluentFutureStream.of(completableFuture
            .thenApply(stream -> stream.filter(predicate)));
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
