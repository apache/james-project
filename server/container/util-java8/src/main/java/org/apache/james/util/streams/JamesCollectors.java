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

package org.apache.james.util.streams;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

public class JamesCollectors {
    public static <D> Collector<D, ?, Stream<Collection<D>>> chunker(int chunkSize) {
        return new ChunkCollector<>(chunkSize);
    }

    public static class ChunkCollector<D> implements Collector<D, Multimap<Integer, D>, Stream<Collection<D>>> {
        private final int chunkSize;
        private final AtomicInteger counter;

        private ChunkCollector(int chunkSize) {
            Preconditions.checkArgument(chunkSize > 0, "ChunkSize should be strictly positive");
            this.chunkSize = chunkSize;
            this.counter = new AtomicInteger(-1);
        }

        @Override
        public Supplier<Multimap<Integer, D>> supplier() {
            return ArrayListMultimap::create;
        }

        @Override
        public BiConsumer<Multimap<Integer, D>, D> accumulator() {
            return (accumulator, value) -> accumulator.put(counter.incrementAndGet() / chunkSize, value);
        }

        @Override
        public BinaryOperator<Multimap<Integer, D>> combiner() {
            return (accumulator1, accumulator2) -> {
                accumulator1.putAll(accumulator2);
                return accumulator1;
            };
        }

        @Override
        public Function<Multimap<Integer, D>, Stream<Collection<D>>> finisher() {
            return accumulator -> accumulator.asMap().values().stream();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return ImmutableSet.of();
        }
    }
}
