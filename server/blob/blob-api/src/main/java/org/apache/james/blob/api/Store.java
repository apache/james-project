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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.util.FluentFutureStream;

import com.google.common.collect.ImmutableMap;

public interface Store<T, I> {

    CompletableFuture<I> save(T t);

    CompletableFuture<T> read(I blobIds);

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

    class Impl<T, I extends BlobPartsId> implements Store<T, I> {

        public interface Encoder<T> {
            Stream<Pair<BlobType, InputStream>> encode(T t);
        }

        public interface Decoder<T> {
            T decode(Stream<Pair<BlobType, byte[]>> streams);
        }

        private final BlobPartsId.Factory<I> idFactory;
        private final Encoder<T> encoder;
        private final Decoder<T> decoder;
        private final BlobStore blobStore;

        public Impl(BlobPartsId.Factory<I> idFactory, Encoder<T> encoder, Decoder<T> decoder, BlobStore blobStore) {
            this.idFactory = idFactory;
            this.encoder = encoder;
            this.decoder = decoder;
            this.blobStore = blobStore;
        }

        @Override
        public CompletableFuture<I> save(T t) {
            return FluentFutureStream.of(
                encoder.encode(t)
                    .map(this::saveEntry))
                .completableFuture()
                .thenApply(pairStream -> pairStream.collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue)))
                .thenApply(idFactory::generate);
        }

        private CompletableFuture<Pair<BlobType, BlobId>> saveEntry(Pair<BlobType, InputStream> entry) {
            return blobStore.save(entry.getRight())
                .thenApply(blobId -> Pair.of(entry.getLeft(), blobId));
        }

        @Override
        public CompletableFuture<T> read(I blobIds) {
            CompletableFuture<Stream<Pair<BlobType, byte[]>>> binaries = FluentFutureStream.of(blobIds.asMap()
                .entrySet()
                .stream()
                .map(entry -> blobStore.readBytes(entry.getValue())
                    .thenApply(bytes -> Pair.of(entry.getKey(), bytes))))
                .completableFuture();

            return binaries.thenApply(decoder::decode);
        }
    }
}
