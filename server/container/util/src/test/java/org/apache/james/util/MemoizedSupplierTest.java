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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.Test;

public class MemoizedSupplierTest {

    @Test
    public void getShouldReturnSuppliedValue() {
        Supplier<Integer> supplier = MemoizedSupplier.of(() -> 42);

        assertThat(supplier.get()).isEqualTo(42);
    }

    @Test
    public void getShouldBeIdempotent() {
        Supplier<Integer> supplier = MemoizedSupplier.of(() -> 42);

        supplier.get();
        assertThat(supplier.get()).isEqualTo(42);
    }

    @Test
    public void nullValueShouldBeSupported() {
        Supplier<Integer> supplier = MemoizedSupplier.of(() -> null);

        supplier.get();
        assertThat(supplier.get()).isNull();
    }

    @Test
    public void underlyingSupplierShouldBeCalledOnlyOnce() {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        Supplier<Integer> supplier = MemoizedSupplier.of(() -> {
            atomicInteger.incrementAndGet();
            return 42;
        });

        supplier.get();
        supplier.get();

        assertThat(atomicInteger.get()).isEqualTo(1);
    }

    @Test
    public void underlyingSupplierShouldBeCalledOnlyOnceWhenReturningNullValue() {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        Supplier<Integer> supplier = MemoizedSupplier.of(() -> {
            atomicInteger.incrementAndGet();
            return null;
        });

        supplier.get();
        supplier.get();

        assertThat(atomicInteger.get()).isEqualTo(1);
    }

}