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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobStore.StoragePolicy;
import org.reactivestreams.Publisher;

import com.google.common.base.Optional;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.FileBackedOutputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

public interface Store<T, I> {
    int FILE_THRESHOLD = 1024 * 100;

    Mono<I> save(T t);

    Mono<T> read(I blobIds);

    Publisher<Void> delete(I blobIds);

    class Impl<T, I extends BlobPartsId> implements Store<T, I> {

        public static final int DEFAULT_CONCURRENCY = 16;

        @FunctionalInterface
        public interface ValueToSave {
            Mono<BlobId> saveIn(BucketName bucketName, BlobStore blobStore);
        }

        @FunctionalInterface
        public interface Encoder<T> {
            Stream<Pair<BlobType, ValueToSave>> encode(T t);
        }

        @FunctionalInterface
        public interface Decoder<T> {
            T decode(Stream<Pair<BlobType, CloseableByteSource>> streams);
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
        public Mono<I> save(T t) {
            return Flux.fromStream(encoder.encode(t))
                .flatMapSequential(this::saveEntry)
                .collectMap(Tuple2::getT1, Tuple2::getT2)
                .map(idFactory::generate);
        }

        private Mono<Tuple2<BlobType, BlobId>> saveEntry(Pair<BlobType, ValueToSave> entry) {
            return Mono.just(entry.getLeft())
                .zipWith(entry.getRight().saveIn(blobStore.getDefaultBucketName(), blobStore));
        }

        @Override
        public Mono<T> read(I blobIds) {
            return Flux.fromIterable(blobIds.asMap().entrySet())
                .publishOn(Schedulers.elastic())
                .map(entry ->  Pair.of(entry.getKey(), readByteSource(blobStore.getDefaultBucketName(), entry.getValue(), entry.getKey().getStoragePolicy())))
                .collectList()
                .map(Collection::stream)
                .map(decoder::decode);
        }

        private CloseableByteSource readByteSource(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
            FileBackedOutputStream out = new FileBackedOutputStream(FILE_THRESHOLD);
            try {
                blobStore.read(bucketName, blobId, storagePolicy).transferTo(out);
                return new DelegateCloseableByteSource(out.asByteSource(), out::reset);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Publisher<Void> delete(I blobIds) {
            return Flux.fromIterable(blobIds.asMap().values())
                .flatMap(id -> blobStore.delete(blobStore.getDefaultBucketName(), id), DEFAULT_CONCURRENCY)
                .then();
        }
    }

    abstract class CloseableByteSource extends ByteSource implements Closeable {

    }

    class DelegateCloseableByteSource extends CloseableByteSource {
        private final ByteSource wrapped;
        private final Closeable closeable;

        DelegateCloseableByteSource(ByteSource wrapped, Closeable closeable) {
            this.wrapped = wrapped;
            this.closeable = closeable;
        }

        @Override
        public InputStream openStream() throws IOException {
            return wrapped.openStream();
        }

        @Override
        public CharSource asCharSource(Charset charset) {
            return wrapped.asCharSource(charset);
        }

        @Override
        public InputStream openBufferedStream() throws IOException {
            return wrapped.openBufferedStream();
        }

        @Override
        public ByteSource slice(long offset, long length) {
            return wrapped.slice(offset, length);
        }

        @Override
        public boolean isEmpty() throws IOException {
            return wrapped.isEmpty();
        }

        @Override
        public Optional<Long> sizeIfKnown() {
            return wrapped.sizeIfKnown();
        }

        @Override
        public long size() throws IOException {
            return wrapped.size();
        }

        @Override
        public long copyTo(OutputStream output) throws IOException {
            return wrapped.copyTo(output);
        }

        @Override
        public long copyTo(ByteSink sink) throws IOException {
            return wrapped.copyTo(sink);
        }

        @Override
        public byte[] read() throws IOException {
            return wrapped.read();
        }

        @Override
        public <T> T read(ByteProcessor<T> processor) throws IOException {
            return wrapped.read(processor);
        }

        @Override
        public HashCode hash(HashFunction hashFunction) throws IOException {
            return wrapped.hash(hashFunction);
        }

        @Override
        public boolean contentEquals(ByteSource other) throws IOException {
            return wrapped.contentEquals(other);
        }

        @Override
        public void close() throws IOException {
            closeable.close();
        }
    }

}
