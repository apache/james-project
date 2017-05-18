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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;

public class JamesCollectors {
    public static <D> Collector<D, ?, Map<Integer, List<D>>> chunker(int chunkSize) {
        Preconditions.checkArgument(chunkSize > 0, "ChunkSize should be strictly positive");
        AtomicInteger counter = new AtomicInteger(-1);
        return Collectors.groupingBy(x -> counter.incrementAndGet() / chunkSize);
    }

    public static <D> Function<Stream<D>, Stream<List<D>>> chunk(int chunkSize) {
        return stream -> stream.collect(chunker(chunkSize))
            .values()
            .stream();
    }
}
