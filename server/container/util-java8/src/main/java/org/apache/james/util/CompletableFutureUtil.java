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

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CompletableFutureUtil {

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
                    stream.map(action::apply)));
    }

    public static <T, U> CompletableFuture<Stream<U>> map(CompletableFuture<Stream<T>> futurStream, Function<T, U> action) {
        return futurStream
            .thenApply(stream ->
                stream.map(value ->
                    action.apply(value)));
    }

    public static <T> CompletableFuture<T> keepValue(Supplier<CompletableFuture<Void>> supplier, T value) {
        return supplier.get().thenApply(any -> value);
    }
}
