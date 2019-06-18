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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

/**
 * This supplier is based on memorized supplier from guava(since guava-25.1-jre) with additional
 * information about value initializing state. Because guava's memorized supplier
 * doesn't support client to check whether value is initialized or not.
 */
public class MemoizedSupplier<T> implements Supplier<T> {
    public static <T> MemoizedSupplier<T> of(Supplier<T> originalSupplier) {
        return new MemoizedSupplier<>(originalSupplier);
    }

    private final Supplier<T> memorizeSupplier;
    private final AtomicReference<T> valueReference;

    public MemoizedSupplier(Supplier<T> originalSupplier) {
        this.memorizeSupplier = Suppliers.memoize(originalSupplier::get);
        this.valueReference = new AtomicReference<>();
    }

    public void ifInitialized(Consumer<T> valueConsumer) {
        T value = valueReference.get();
        if (value != null) {
            valueConsumer.accept(value);
        }
    }

    @Override
    public T get() {
        return this.valueReference.updateAndGet(originalValue -> memorizeSupplier.get());
    }
}
