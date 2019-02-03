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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

public class CompletableFutureUtil {

    public static <T> CompletableFuture<Optional<T>> unwrap(CompletableFuture<Optional<CompletableFuture<T>>> base) {
        return base.thenCompose(
            optional -> optional.map(future -> future.thenApply(Optional::of))
                .orElse(CompletableFuture.completedFuture(Optional.empty())));
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<Stream<T>> allOf(Stream<CompletableFuture<T>> futureStream) {
        CompletableFuture<T>[] arrayOfFutures = futureStream.toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(arrayOfFutures)
            .thenApply(any -> Arrays.stream(arrayOfFutures)
                .map(CompletableFuture::join));
    }

    public static <R, T> CompletableFuture<Stream<R>> chainAll(Stream<T> futureStream, Function<T, CompletableFuture<R>> transformationToChain) {
        ImmutableList<T> elements = futureStream.collect(ImmutableList.toImmutableList());
        ArrayList<R> results = new ArrayList<>(elements.size());

        CompletableFuture<Void> futureEmptyStream = CompletableFuture.completedFuture(null);

        BiFunction<CompletableFuture<?>, Supplier<CompletableFuture<R>>, CompletableFuture<?>> accumulator =
            (future, supplier) -> future.thenCompose(any -> supplier.get().thenAccept(results::add));

        BinaryOperator<CompletableFuture<?>> combiner = (f1, f2) -> f1.thenCompose(any -> f2);

        return elements.stream()
            .map(t -> (Supplier<CompletableFuture<R>>) (() -> transformationToChain.apply(t)))
            .reduce(futureEmptyStream, accumulator, combiner)
            .thenApply(any -> results.stream());
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

    public static <T> Function<Boolean, CompletableFuture<Boolean>> composeIfTrue(Supplier<CompletableFuture<T>> composeOperation) {
        return b -> {
            if (b) {
                return composeOperation.get().thenApply(any -> b);
            }
            return CompletableFuture.completedFuture(b);
        };
    }

    public static <T> CompletableFuture<Stream<T>> sorted(CompletableFuture<Stream<T>> futureStream, Comparator<T> comparator) {
        return futureStream
            .thenApply(stream ->
                stream.sorted(comparator));
    }

    public static <T> CompletableFuture<T> exceptionallyFuture(Throwable throwable) {
        CompletableFuture<T> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(throwable);
        return failedFuture;
    }
}
