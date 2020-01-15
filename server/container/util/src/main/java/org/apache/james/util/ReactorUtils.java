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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Optional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactorUtils {
    public static <T> Mono<T> executeAndEmpty(Runnable runnable) {
        return Mono.fromRunnable(runnable).then(Mono.empty());
    }


    public static InputStream toInputStream(Flux<ByteBuffer> byteArrays) {
        return new StreamInputStream(byteArrays.toIterable(1).iterator());
    }

    private static class StreamInputStream extends InputStream {
        private static final int NO_MORE_DATA = -1;

        private final Iterator<ByteBuffer> source;
        private Optional<ByteBuffer> currentItemByteStream;

        StreamInputStream(Iterator<ByteBuffer> source) {
            this.source = source;
            this.currentItemByteStream = Optional.empty();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return nextNonEmptyBuffer()
                .map(buffer -> {
                    int toRead = Math.min(len, buffer.remaining());
                    buffer.get(b, off, toRead);
                    return toRead;
                })
                .orElse(NO_MORE_DATA);
        }

        @Override
        public int read() {
            return nextNonEmptyBuffer()
                .map(ReactorUtils::byteToInt)
                .orElse(NO_MORE_DATA);
        }

        private Optional<ByteBuffer> nextNonEmptyBuffer() {
            Boolean needsNewBuffer = currentItemByteStream.map(buffer -> !buffer.hasRemaining()).orElse(true);
            if (needsNewBuffer) {
                if (source.hasNext()) {
                    currentItemByteStream = Optional.of(source.next());
                    return nextNonEmptyBuffer();
                } else {
                    return Optional.empty();
                }
            }
            return currentItemByteStream;
        }

    }

    private static int byteToInt(ByteBuffer buffer) {
        return buffer.get() & 0xff;
    }
}
