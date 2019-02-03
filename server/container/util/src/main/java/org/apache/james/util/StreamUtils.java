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
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class StreamUtils {

    @SafeVarargs
    public static <T> Stream<T> ofNullables(T... array) {
        return ofNullable(array);
    }

    public static <T> Stream<T> ofNullable(T[] array) {
        return ofOptional(Optional.ofNullable(array));
    }

    public static <T> Stream<T> ofOptional(Optional<T[]> array) {
        return array
            .map(Arrays::stream)
            .orElse(Stream.empty());
    }

    public static <T> Stream<T> flatten(Collection<Stream<T>> streams) {
        return flatten(streams.stream());
    }

    public static <T> Stream<T> flatten(Stream<Stream<T>> streams) {
        return streams.flatMap(Function.identity());
    }

    @SafeVarargs
    public static <T> Stream<T> flatten(Stream<T>... streams) {
        return flatten(Arrays.stream(streams));
    }
}
