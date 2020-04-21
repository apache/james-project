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

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class OptionalUtils {

    @FunctionalInterface
    public interface Operation {
        void perform();
    }

    public static <T> Optional<T> executeIfEmpty(Optional<T> optional, Operation operation) {
        if (!optional.isPresent()) {
            operation.perform();
        }
        return optional;
    }

    @SafeVarargs
    public static <T> Optional<T> orSuppliers(Supplier<Optional<T>>... suppliers) {
        return orStream(Arrays.stream(suppliers)
            .map(Supplier::get));
    }

    private static <T> Optional<T> orStream(Stream<Optional<T>> stream) {
        return stream
            .flatMap(Optional::stream)
            .findFirst();
    }

    public static <T> boolean containsDifferent(Optional<T> requestValue, T storeValue) {
        return requestValue
            .filter(value -> !value.equals(storeValue))
            .isPresent();
    }

    public static <T, U> boolean matches(Optional<T> optional1, Optional<U> optional2, BiPredicate<T, U> biPredicate) {
        return optional1.map(value1 ->
            optional2
                .map(value2 -> biPredicate.test(value1, value2))
                .orElse(false))
            .orElse(false);
    }
}
