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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactorUtils {
    public static <T> Mono<T> executeAndEmpty(Runnable runnable) {
        return Mono.fromRunnable(runnable).then(Mono.empty());
    }

    public static InputStream toInputStream(Flux<byte[]> byteArrays) {
        return new StreamInputStream(byteArrays.toStream(1));
    }

    private static  class StreamInputStream extends InputStream {
        private static final int NO_MORE_DATA = -1;

        private final Stream<byte[]> source;
        private final Spliterator<byte[]> spliterator;
        private Optional<ByteArrayInputStream> currentItemByteStream;

        StreamInputStream(Stream<byte[]> source) {
            this.source = source;
            this.spliterator = source.spliterator();
            this.currentItemByteStream = Optional.empty();
        }

        @Override
        public int read() {
            try {
                if (!dataAvailableToRead()) {
                    switchToNextChunk();
                }

                if (!dataAvailableToRead()) {
                    source.close();
                    return NO_MORE_DATA;
                }

                return currentItemByteStream.map(ByteArrayInputStream::read)
                    .filter(readResult -> readResult != NO_MORE_DATA)
                    .orElseGet(this::readNextChunk);
            } catch (Throwable t) {
                source.close();
                throw t;
            }
        }

        private boolean dataAvailableToRead() {
            return currentItemByteStream.isPresent();
        }

        private void switchToNextChunk() {
            spliterator.tryAdvance(bytes ->
                currentItemByteStream = Optional.of(new ByteArrayInputStream(bytes)));
        }

        private Integer readNextChunk() {
            currentItemByteStream = Optional.empty();
            return read();
        }

        @Override
        public void close() throws IOException {
            try {
                source.close();
            } finally {
                super.close();
            }
        }
    }
}
