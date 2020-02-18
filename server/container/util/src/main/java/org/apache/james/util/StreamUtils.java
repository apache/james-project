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
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

public class StreamUtils {

    private static final boolean PARALLEL = true;

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

    public static <T> Stream<T> unfold(T seed, Function<T, Optional<T>> generator) {
        return StreamSupport.stream(new UnfoldSpliterator(seed, generator), !PARALLEL);
    }

    public static <T> Stream<T> iterate(T seed, Long limit, Function<T, Stream<T>> generator) {
        Preconditions.checkArgument(limit >= 0, "StreamUtils.iterate have a given limit ok '{}', while it should not be negative", limit);
        return StreamUtils.unfold(Arrays.asList(seed), conservativeGenerator(generator))
            .limit(limit + 1)
            .flatMap(List::stream);
    }

    private static <T> Function<List<T>, Optional<List<T>>> conservativeGenerator(Function<T, Stream<T>> generator) {
        return previous -> {
            List<T> generated = previous.stream()
                .flatMap(generator)
                .collect(Guavate.toImmutableList());

            if (generated.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(generated);
            }
        };
    }

    private static class UnfoldSpliterator<T> implements Spliterator<T> {

        private static final Spliterator<?> NOT_ABLE_TO_SPLIT_SPLITERATOR = null;
        private Optional<T> current;
        private final Function<T, Optional<T>> generator;

        private UnfoldSpliterator(T seed, Function<T, Optional<T>> generator) {
            this.current = Optional.of(seed);
            this.generator = generator;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            current.ifPresent(action);
            current = current.flatMap(generator);
            return current.isPresent();
        }

        @Override
        public Spliterator<T> trySplit() {
            return (Spliterator<T>) NOT_ABLE_TO_SPLIT_SPLITERATOR;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return Spliterator.IMMUTABLE & Spliterator.NONNULL & Spliterator.ORDERED;
        }
    }
}
