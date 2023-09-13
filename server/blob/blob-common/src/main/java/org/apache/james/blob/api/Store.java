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
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobStore.StoragePolicy;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
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
            T decode(Map<BlobType, CloseableByteSource> streams);
        }

        private final BlobPartsId.Factory<I> idFactory;
        private final Encoder<T> encoder;
        private final Decoder<T> decoder;
        private final BlobStore blobStore;
        private final BucketName bucketName;

        public Impl(BlobPartsId.Factory<I> idFactory, Encoder<T> encoder, Decoder<T> decoder, BlobStore blobStore, BucketName bucketName) {
            this.idFactory = idFactory;
            this.encoder = encoder;
            this.decoder = decoder;
            this.blobStore = blobStore;
            this.bucketName = bucketName;
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
                .zipWith(entry.getRight().saveIn(bucketName, blobStore));
        }

        @Override
        public Mono<T> read(I blobIds) {
            return Flux.fromIterable(blobIds.asMap().entrySet())
                .publishOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                .flatMap(entry -> readByteSource(bucketName, entry.getValue(), entry.getKey().getStoragePolicy())
                    .map(result -> Pair.of(entry.getKey(), result)))
                .collectMap(Map.Entry::getKey, Pair::getValue)
                // Critical to correctly propagate errors.
                // Replacing by `map` would cause the error not to be catch downstream. No idea why, failed to reproduce with a test.
                // Impact: unacknowledged messages for RabbitMQ mailQueue that eventually piles up to interruption of service.
                .flatMap(e -> Mono.fromCallable(() -> decoder.decode(e))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER));
        }

        private Mono<CloseableByteSource> readByteSource(BucketName bucketName, BlobId blobId, StoragePolicy storagePolicy) {
            return Mono.usingWhen(blobStore.readReactive(bucketName, blobId, storagePolicy),
                Throwing.function(in -> {
                    FileBackedOutputStream out = new FileBackedOutputStream(FILE_THRESHOLD);
                    long size = in.transferTo(out);
                    return Mono.just(new DelegateCloseableByteSource(out.asByteSource(), out::reset, size));
                }),
                stream -> Mono.fromRunnable(Throwing.runnable(stream::close)));
        }

        @Override
        public Publisher<Void> delete(I blobIds) {
            return Flux.fromIterable(blobIds.asMap().values())
                .flatMap(id -> blobStore.delete(bucketName, id), DEFAULT_CONCURRENCY)
                .then();
        }
    }

    abstract class CloseableByteSource extends ByteSource implements Closeable {

    }

    class DelegateCloseableByteSource extends CloseableByteSource {
        private final ByteSource wrapped;
        private final Closeable closeable;
        private final long size;

        DelegateCloseableByteSource(ByteSource wrapped, Closeable closeable, long size) {
            this.wrapped = wrapped;
            this.closeable = closeable;
            this.size = size;
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
            return Optional.of(size);
        }

        @Override
        public long size() {
            return size;
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
