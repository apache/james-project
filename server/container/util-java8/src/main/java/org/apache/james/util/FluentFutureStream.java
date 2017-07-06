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
import java.util.stream.Stream;

public class FluentFutureStream<T> {

    private final CompletableFuture<Stream<T>> completableFuture;

    public static <T> FluentFutureStream<T> of(CompletableFuture<Stream<T>> completableFuture) {
        return new FluentFutureStream<>(completableFuture);
    }

    public static <T> FluentFutureStream<T> ofNestedStreams(Stream<CompletableFuture<Stream<T>>> completableFuture) {
        return of(completableFuture)
            .flatMap(Function.identity());
    }

    public static <T> FluentFutureStream<T> ofOptionals(Stream<CompletableFuture<Optional<T>>> completableFuture) {
        return of(completableFuture)
            .flatMapOptional(Function.identity());
    }

    public static <T> FluentFutureStream<T> of(Stream<CompletableFuture<T>> completableFutureStream) {
        return new FluentFutureStream<>(CompletableFutureUtil.allOf(completableFutureStream));
    }

    @SafeVarargs
    public static <T> FluentFutureStream<T> ofFutures(CompletableFuture<T>... completableFutures) {
        return new FluentFutureStream<>(CompletableFutureUtil.allOfArray(completableFutures));
    }

    private FluentFutureStream(CompletableFuture<Stream<T>> completableFuture) {
        this.completableFuture = completableFuture;
    }

    public FluentFutureStream<T> performOnAll(Function<T, CompletableFuture<Void>> action) {
        return FluentFutureStream.of(
            CompletableFutureUtil.performOnAll(completableFuture(), action));
    }

    public <U> FluentFutureStream<U> map(Function<T, U> function) {
        return FluentFutureStream.of(
            CompletableFutureUtil.map(completableFuture(), function));
    }

    public CompletableFuture<Optional<T>> reduce(BinaryOperator<T> combiner) {
        return CompletableFutureUtil.reduce(combiner, completableFuture);
    }

    public CompletableFuture<T> reduce(T emptyAccumulator, BinaryOperator<T> combiner) {
        return CompletableFutureUtil.reduce(combiner, completableFuture, emptyAccumulator);
    }

    public <U> FluentFutureStream<U> thenComposeOnAll(Function<T, CompletableFuture<U>> function) {
        return FluentFutureStream.of(
            CompletableFutureUtil.thenComposeOnAll(completableFuture(), function));
    }

    public <U> FluentFutureStream<U> thenFlatCompose(Function<T, CompletableFuture<Stream<U>>> function) {
        return FluentFutureStream.of(
            CompletableFutureUtil.thenComposeOnAll(completableFuture(), function))
            .flatMap(Function.identity());
    }

    public <U> FluentFutureStream<U> thenFlatComposeOnOptional(Function<T, CompletableFuture<Optional<U>>> function) {
        return FluentFutureStream.of(
            CompletableFutureUtil.thenComposeOnAll(completableFuture(), function))
            .flatMapOptional(Function.identity());
    }

    public <U> FluentFutureStream<U> flatMap(Function<T, Stream<U>> function) {
        return FluentFutureStream.of(completableFuture().thenApply(stream ->
            stream.flatMap(function)));
    }

    public <U> FluentFutureStream<U> flatMapOptional(Function<T, Optional<U>> function) {
        return map(function)
            .flatMap(OptionalConverter::toStream);
    }

    public <U> FluentFutureStream<U> thenCompose(Function<Stream<T>, CompletableFuture<Stream<U>>> function) {
        return FluentFutureStream.of(completableFuture().thenCompose(function));
    }

    public CompletableFuture<Stream<T>> completableFuture() {
        return this.completableFuture;
    }

    public FluentFutureStream<T> filter(Predicate<T> predicate) {
        return FluentFutureStream.of(completableFuture
            .thenApply(stream -> stream.filter(predicate)));
    }

    public Stream<T> join() {
        return completableFuture().join();
    }
}
