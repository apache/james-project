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

package org.apache.james.blob.api;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.util.FluentFutureStream;

import com.google.common.collect.ImmutableMap;

public interface Store<T> {
    class BlobType {
        private final String name;

        public BlobType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof BlobType) {
                BlobType blobType = (BlobType) o;

                return Objects.equals(this.name, blobType.name);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(name);
        }
    }

    interface Encoder<T> {
        Map<BlobType, InputStream> encode(T t);
    }

    interface Decoder<T> {
        T decode(Map<BlobType, byte[]> streams);
    }

    CompletableFuture<Map<BlobType, BlobId>> save(T t);

    CompletableFuture<T> read(Map<BlobType, BlobId> blobIds);

    class Impl<T> implements Store<T> {
        private final Encoder<T> encoder;
        private final Decoder<T> decoder;
        private final BlobStore blobStore;

        public Impl(Encoder<T> encoder, Decoder<T> decoder, BlobStore blobStore) {
            this.encoder = encoder;
            this.decoder = decoder;
            this.blobStore = blobStore;
        }

        @Override
        public CompletableFuture<Map<BlobType, BlobId>> save(T t) {
            return FluentFutureStream.of(
                encoder.encode(t)
                    .entrySet()
                    .stream()
                    .map(this::saveEntry))
                .completableFuture()
                .thenApply(pairStream -> pairStream.collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue)));
        }

        private CompletableFuture<Pair<BlobType, BlobId>> saveEntry(Map.Entry<BlobType, InputStream> entry) {
            return blobStore.save(entry.getValue())
                .thenApply(blobId -> Pair.of(entry.getKey(), blobId));
        }

        @Override
        public CompletableFuture<T> read(Map<BlobType, BlobId> blobIds) {
            CompletableFuture<ImmutableMap<BlobType, byte[]>> binaries = FluentFutureStream.of(blobIds.entrySet()
                .stream()
                .map(entry -> blobStore.readBytes(entry.getValue())
                    .thenApply(bytes -> Pair.of(entry.getKey(), bytes))))
                .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));

            return binaries.thenApply(decoder::decode);
        }
    }
}
