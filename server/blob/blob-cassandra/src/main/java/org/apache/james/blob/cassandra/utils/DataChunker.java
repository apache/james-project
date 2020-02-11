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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DataChunker {

    private static final String CHUNK_SIZE_MUST_BE_STRICTLY_POSITIVE = "ChunkSize must be strictly positive";

    public Flux<ByteBuffer> chunk(byte[] data, int chunkSize) {
        Preconditions.checkNotNull(data);
        Preconditions.checkArgument(chunkSize > 0, CHUNK_SIZE_MUST_BE_STRICTLY_POSITIVE);

        int size = data.length;
        int fullChunkCount = size / chunkSize;

        return Flux.concat(
            Flux.range(0, fullChunkCount)
                .map(i -> ByteBuffer.wrap(data, i * chunkSize, chunkSize)),
            lastChunk(data, chunkSize * fullChunkCount, fullChunkCount));
    }

    private Mono<ByteBuffer> lastChunk(byte[] data, int offset, int index) {
        if (offset == data.length && index > 0) {
            return Mono.empty();
        }
        return Mono.just(ByteBuffer.wrap(data, offset, data.length - offset));
    }

    public Flux<ByteBuffer> chunkStream(InputStream data, int chunkSize) {
        Preconditions.checkNotNull(data);
        Preconditions.checkArgument(chunkSize > 0, CHUNK_SIZE_MUST_BE_STRICTLY_POSITIVE);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(data);
        return Flux
            .<ByteBuffer>generate(sink -> {
                try {
                    byte[] buffer = new byte[chunkSize];

                    int size = bufferedInputStream.read(buffer);
                    if (size <= 0) {
                        sink.complete();
                    } else {
                        sink.next(ByteBuffer.wrap(buffer, 0, size));
                    }
                } catch (IOException e) {
                    sink.error(e);
                }
            })
            .defaultIfEmpty(ByteBuffer.wrap(new byte[0]));
    }
}
