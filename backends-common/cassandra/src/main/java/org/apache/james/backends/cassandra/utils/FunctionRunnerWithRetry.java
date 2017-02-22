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

package org.apache.james.backends.cassandra.utils;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import com.google.common.base.Preconditions;

public class FunctionRunnerWithRetry {

    public static class RelayingException extends RuntimeException {}

    @FunctionalInterface
    public interface OptionalSupplier<T> {
        Optional<T> getAsOptional();
    }

    private final int maxRetry;

    public FunctionRunnerWithRetry(int maxRetry) {
        Preconditions.checkArgument(maxRetry > 0);
        this.maxRetry = maxRetry;
    }

    public void execute(BooleanSupplier functionNotifyingSuccess) throws LightweightTransactionException {
        IntStream.range(0, maxRetry)
            .filter((x) -> functionNotifyingSuccess.getAsBoolean())
            .findFirst()
            .orElseThrow(() -> new LightweightTransactionException(maxRetry));
    }

    public <T> T executeAndRetrieveObject(OptionalSupplier<T> functionNotifyingSuccess) throws LightweightTransactionException {
        return IntStream.range(0, maxRetry)
            .mapToObj((x) -> functionNotifyingSuccess.getAsOptional())
            .filter(Optional::isPresent)
            .findFirst()
            .orElseThrow(() -> new LightweightTransactionException(maxRetry))
            .get();
    }

    public <T> CompletableFuture<Optional<T>> executeAsyncAndRetrieveObject(Supplier<CompletableFuture<Optional<T>>> futureSupplier) {
        return executeAsyncAndRetrieveObject(futureSupplier, 0);
    }

    public <T> CompletableFuture<Optional<T>> executeAsyncAndRetrieveObject(Supplier<CompletableFuture<Optional<T>>> futureSupplier, int tries) {
        if (tries >= maxRetry) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return futureSupplier.get()
            .thenCompose(optional -> {
                if (optional.isPresent()) {
                    return CompletableFuture.completedFuture(optional);
                }
                return executeAsyncAndRetrieveObject(futureSupplier, tries + 1);
            });
    }
}
