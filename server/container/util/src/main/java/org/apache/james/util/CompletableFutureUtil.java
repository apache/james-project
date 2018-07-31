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
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CompletableFutureUtil {

    public static <T> CompletableFuture<Optional<T>> unwrap(CompletableFuture<Optional<CompletableFuture<T>>> base) {
        return base.thenCompose(
            optional -> optional.map(future -> future.thenApply(Optional::of))
                .orElse(CompletableFuture.completedFuture(Optional.empty())));
    }

    @SafeVarargs
    public static <T> CompletableFuture<Stream<T>> allOfArray(CompletableFuture<T>... futures) {
        return allOf(Stream.of(futures));
    }

    public static <T, U, V> CompletableFuture<V> combine(CompletableFuture<T> t, CompletableFuture<U> u, BiFunction<T,U,V> combiner) {
        return t.thenCompose(valueT ->
            u.thenApply(valueU -> combiner.apply(valueT, valueU)));
    }

    public static <T> CompletableFuture<Stream<T>> allOf(Stream<CompletableFuture<T>> futureStream) {
        return futureStream
            .map((CompletableFuture<T> future) -> future.thenApply(Stream::of))
            .parallel()
            .reduce((future1, future2) ->
            future1.thenCompose(
                stream1 -> future2.thenCompose(
                    stream2 -> {
                        Stream<T> concatStream = Stream.concat(stream1, stream2);
                        return CompletableFuture.completedFuture(concatStream);
                    })))
            .orElse(CompletableFuture.completedFuture(Stream.of()));
    }

    public static <R, T> CompletableFuture<Stream<R>> chainAll(Stream<T> futureStream,
        Function<T, CompletableFuture<R>> transformationToChain) {
        return futureStream
            .map(t -> (Supplier<CompletableFuture<R>>) (() -> transformationToChain.apply(t)))
            .reduce(CompletableFuture.<Stream<R>>completedFuture(Stream.of()),
                (accumulator, supplier) ->
                    accumulator.thenCompose(
                        accumulatedStream ->
                            supplier.get()
                                .thenCompose(r ->
                                    CompletableFuture.completedFuture(Stream.<R>concat(accumulatedStream, Stream.of(r))))
                    ),
                getCompletableFutureBinaryOperator());
    }

    private static <R> BinaryOperator<CompletableFuture<Stream<R>>> getCompletableFutureBinaryOperator() {
        return (future1, future2) ->
            future1.thenCompose(stream1 ->
                future2.<Stream<R>>thenCompose(stream2 ->
                    CompletableFuture.completedFuture(Stream.concat(stream1, stream2))));
    }

    public static <T> CompletableFuture<Stream<T>> performOnAll(CompletableFuture<Stream<T>> futurStream, Function<T, CompletableFuture<Void>> action) {
        return thenComposeOnAll(futurStream, value ->
            keepValue(() ->
                action.apply(value),
                value));
    }

    public static <T, U> CompletableFuture<Stream<U>> thenComposeOnAll(CompletableFuture<Stream<T>> futurStream, Function<T, CompletableFuture<U>> action) {
        return futurStream
            .thenCompose(stream ->
                CompletableFutureUtil.allOf(
                    stream.map(action)));
    }

    public static <T, U> CompletableFuture<Stream<U>> map(CompletableFuture<Stream<T>> futurStream, Function<T, U> action) {
        return futurStream
            .thenApply(stream ->
                stream.map(action));
    }

    public static <T> CompletableFuture<Optional<T>> reduce(BinaryOperator<T> binaryOperator, CompletableFuture<Stream<T>> futureStream) {
        return futureStream.thenApply(stream -> stream.reduce(binaryOperator));
    }

    public static <T> CompletableFuture<T> reduce(BinaryOperator<T> binaryOperator, CompletableFuture<Stream<T>> futureStream, T emptyAccumulator) {
        return futureStream.thenApply(stream -> stream.reduce(binaryOperator).orElse(emptyAccumulator));
    }

    public static <T> CompletableFuture<T> keepValue(Supplier<CompletableFuture<Void>> supplier, T value) {
        return supplier.get().thenApply(any -> value);
    }

    public static <T> Function<Boolean, CompletableFuture<Boolean>> composeIfTrue(Supplier<CompletableFuture<T>> composeOperation) {
        return b -> {
            if (b) {
                return composeOperation.get().thenApply(any -> b);
            }
            return CompletableFuture.completedFuture(b);
        };
    }
}
