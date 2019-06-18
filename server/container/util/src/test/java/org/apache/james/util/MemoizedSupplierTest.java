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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

class MemoizedSupplierTest {

    @Test
    void getShouldReturnSuppliedValue() {
        MemoizedSupplier<Integer> supplier = MemoizedSupplier.of(() -> 42);

        assertThat(supplier.get()).isEqualTo(42);
    }

    @Test
    void getShouldBeIdempotent() {
        MemoizedSupplier<Integer> supplier = MemoizedSupplier.of(() -> 42);

        supplier.get();
        assertThat(supplier.get()).isEqualTo(42);
    }


    @Test
    void getShouldReturnSameMemorizedInstanceInParallel() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        MemoizedSupplier<Integer> supplier = MemoizedSupplier.of(counter::incrementAndGet);

        ConcurrentTestRunner.builder()
            .operation((threadNumber, operationNumber) -> supplier.get())
            .threadCount(20)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void nullValueShouldBeSupported() {
        MemoizedSupplier<Integer> supplier = MemoizedSupplier.of(() -> null);

        supplier.get();
        assertThat(supplier.get()).isNull();
    }

    @Test
    void underlyingSupplierShouldBeCalledOnlyOnce() {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        MemoizedSupplier<Integer> supplier = MemoizedSupplier.of(() -> {
            atomicInteger.incrementAndGet();
            return 42;
        });

        supplier.get();
        supplier.get();

        assertThat(atomicInteger.get()).isEqualTo(1);
    }

    @Test
    void underlyingSupplierShouldBeCalledOnlyOnceWhenReturningNullValue() {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        MemoizedSupplier<Integer> supplier = MemoizedSupplier.of(() -> {
            atomicInteger.incrementAndGet();
            return null;
        });

        supplier.get();
        supplier.get();

        assertThat(atomicInteger.get()).isEqualTo(1);
    }

    @Test
    void ifInitializedShouldPerformWhenValueIsInitialized() {
        AtomicBoolean performAfterInitialization = new AtomicBoolean(false);
        MemoizedSupplier<Integer> supplier = MemoizedSupplier.of(() -> 10);

        supplier.get();
        supplier.ifInitialized(value -> performAfterInitialization.set(true));
        assertThat(performAfterInitialization.get()).isTrue();
    }

    @Test
    void ifInitializedShouldPerformOnlyOnceWhenValueIsInitializedInParallel() throws Exception {
        AtomicInteger performAfterInitializationCounter = new AtomicInteger(0);
        MemoizedSupplier<Integer> supplier = MemoizedSupplier.of(() -> 10);

        ConcurrentTestRunner.builder()
            .operation((threadNumber, operationNumber) -> supplier.get())
            .threadCount(20)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
        supplier.ifInitialized(value -> performAfterInitializationCounter.incrementAndGet());

        assertThat(performAfterInitializationCounter.get()).isEqualTo(1);
    }


    @Test
    void ifInitializedShouldNotPerformWhenValueIsNotInitialized() {
        AtomicBoolean performAfterInitialization = new AtomicBoolean(false);
        MemoizedSupplier<Integer> supplier = MemoizedSupplier.of(() -> 10);

        supplier.ifInitialized(value -> performAfterInitialization.set(true));
        assertThat(performAfterInitialization.get()).isFalse();
    }
}