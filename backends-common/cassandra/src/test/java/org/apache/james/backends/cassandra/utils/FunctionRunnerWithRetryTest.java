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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.mutable.MutableInt;
import org.junit.Test;

public class FunctionRunnerWithRetryTest {
    
    private static final int MAX_RETRY = 10;

    @Test(expected = IllegalArgumentException.class)
    public void functionRunnerWithInvalidMaxRetryShouldFail() throws Exception {
        new FunctionRunnerWithRetry(-1);
    }

    @Test(expected = LightweightTransactionException.class)
    public void functionRunnerShouldFailIfTransactionCanNotBePerformed() throws Exception {
        final MutableInt value = new MutableInt(0);
        new FunctionRunnerWithRetry(MAX_RETRY).execute(
            () -> {
                value.increment();
                return false;
            }
        );
        assertThat(value.getValue()).isEqualTo(MAX_RETRY);
    }
    
    @Test
    public void functionRunnerShouldWorkOnFirstTry() throws Exception {
        final MutableInt value = new MutableInt(0);
        new FunctionRunnerWithRetry(MAX_RETRY).execute(
            () -> {
                value.increment();
                return true;
            }
        );
        assertThat(value.getValue()).isEqualTo(1);
    }

    @Test
    public void functionRunnerShouldWorkIfNotSucceededOnFirstTry() throws Exception {
        final MutableInt value = new MutableInt(0);
        new FunctionRunnerWithRetry(MAX_RETRY).execute(
            () -> {
                value.increment();
                return (Integer) value.getValue() == MAX_RETRY / 2;
            }
        );
        assertThat(value.getValue()).isEqualTo(MAX_RETRY / 2);
    }

    @Test
    public void functionRunnerShouldWorkIfNotSucceededOnMaxRetryReached() throws Exception {
        final MutableInt value = new MutableInt(0);
        new FunctionRunnerWithRetry(MAX_RETRY).execute(
                () -> {
                    value.increment();
                    return (Integer) value.getValue() == MAX_RETRY;
                }
        );
        assertThat(value.getValue()).isEqualTo(MAX_RETRY);
    }

    @Test
    public void asyncFunctionRunnerShouldWorkIfSucceedFirstTry() throws Exception {
        int value = 18;

        Optional<Integer> result = new FunctionRunnerWithRetry(MAX_RETRY)
            .executeAsyncAndRetrieveObject(
                () -> CompletableFuture.completedFuture(Optional.of(value)))
            .join();

        assertThat(result).contains(value);
    }

    @Test
    public void asyncFunctionRunnerShouldTryOnlyOnceIfSuccess() throws Exception {
        int value = 18;
        AtomicInteger times = new AtomicInteger(0);

        new FunctionRunnerWithRetry(MAX_RETRY)
            .executeAsyncAndRetrieveObject(
                () -> {
                    times.incrementAndGet();
                    return CompletableFuture.completedFuture(Optional.of(value));
                })
            .join();

        assertThat(times.get()).isEqualTo(1);
    }

    @Test
    public void asyncFunctionRunnerShouldRetrieveValueOnRetry() throws Exception {
        int value = 18;
        AtomicInteger times = new AtomicInteger(0);

        Optional<Integer> result = new FunctionRunnerWithRetry(MAX_RETRY)
            .executeAsyncAndRetrieveObject(
                () -> {
                    int attemptCount = times.incrementAndGet();
                    if (attemptCount == MAX_RETRY) {
                        return CompletableFuture.completedFuture(Optional.of(value));
                    } else {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                })
            .join();

        assertThat(result).contains(value);
    }

    @Test
    public void asyncFunctionRunnerShouldMakeMaxRetryAttempts() throws Exception {
        int value = 18;
        AtomicInteger times = new AtomicInteger(0);

        new FunctionRunnerWithRetry(MAX_RETRY)
            .executeAsyncAndRetrieveObject(
                () -> {
                    int attemptCount = times.incrementAndGet();
                    if (attemptCount == MAX_RETRY) {
                        return CompletableFuture.completedFuture(Optional.of(value));
                    } else {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                })
            .join();

        assertThat(times.get()).isEqualTo(MAX_RETRY);
    }


    @Test
    public void asyncFunctionRunnerShouldReturnEmptyIfAllFailed() throws Exception {
        AtomicInteger times = new AtomicInteger(0);

        Optional<Integer> result = new FunctionRunnerWithRetry(MAX_RETRY)
            .executeAsyncAndRetrieveObject(
                () -> {
                    times.incrementAndGet();
                    return CompletableFuture.completedFuture(Optional.<Integer>empty());
                })
            .join();

        assertThat(result).isEmpty();
        assertThat(times.get()).isEqualTo(MAX_RETRY);
    }
    
}
