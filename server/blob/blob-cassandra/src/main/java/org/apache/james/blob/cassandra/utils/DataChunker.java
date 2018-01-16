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

package org.apache.james.blob.cassandra.utils;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.base.Preconditions;

public class DataChunker {

    public Stream<Pair<Integer, ByteBuffer>> chunk(byte[] data, int chunkSize) {
        Preconditions.checkNotNull(data);
        Preconditions.checkArgument(chunkSize > 0, "ChunkSize can not be negative");

        int size = data.length;
        int fullChunkCount = size / chunkSize;

        return Stream.concat(
            IntStream.range(0, fullChunkCount)
                .mapToObj(i -> Pair.of(i, ByteBuffer.wrap(data, i * chunkSize, chunkSize))),
            lastChunk(data, chunkSize * fullChunkCount, fullChunkCount));
    }

    private Stream<Pair<Integer, ByteBuffer>> lastChunk(byte[] data, int offset, int index) {
        if (offset == data.length && index > 0) {
            return Stream.empty();
        }
        return Stream.of(Pair.of(index, ByteBuffer.wrap(data, offset, data.length - offset)));
    }

}
