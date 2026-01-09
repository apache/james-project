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

package org.apache.james.blob.objectstorage.aws;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.RetryBackoffSpec;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

@Singleton
public class S3BlobStoreDAO implements BlobStoreDAO {

    private static class FileBackedOutputStreamByteSource extends ByteSource {
        private final FileBackedOutputStream stream;
        private final long size;

        private FileBackedOutputStreamByteSource(FileBackedOutputStream stream, long size) {
            Preconditions.checkArgument(size >= 0, "'size' must be positive");
            this.stream = stream;
            this.size = size;
        }

        @Override
        public InputStream openStream() throws IOException {
            return stream.asByteSource().openStream();
        }

        @Override
        public Optional<Long> sizeIfKnown() {
            return Optional.of(size);
        }

        @Override
        public long size() {
            return size;
        }
    }

    private static final int CHUNK_SIZE = 1024 * 100;
    private static final int EMPTY_BUCKET_BATCH_SIZE = 1000;
    private static final int FILE_THRESHOLD = 1024 * 100;
    private static final Duration FIRST_BACK_OFF = Duration.ofMillis(100);
    private static final boolean LAZY = false;
    private static final int MAX_RETRIES = 5;

    private final BucketNameResolver bucketNameResolver;
    private final S3AsyncClient client;
    private final S3BlobStoreConfiguration configuration;
    private final BlobId.Factory blobIdFactory;
    private final S3RequestOption s3RequestOption;
    private final java.util.Optional<BucketName> fallbackNamespace;

    @Inject
    public S3BlobStoreDAO(S3ClientFactory s3ClientFactory,
                          S3BlobStoreConfiguration configuration,
                          BlobId.Factory blobIdFactory,
                          S3RequestOption s3RequestOption) {
        this.configuration = configuration;
        this.client = s3ClientFactory.get();
        this.blobIdFactory = blobIdFactory;
        this.s3RequestOption = s3RequestOption;
        this.fallbackNamespace = configuration.getFallbackNamespace();

        bucketNameResolver = BucketNameResolver.builder()
            .prefix(configuration.getBucketPrefix())
            .namespace(configuration.getNamespace())
            .build();
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        BucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return ReactorUtils.toInputStream(getObject(resolvedBucketName, blobId)
            .onErrorMap(NoSuchBucketException.class, e -> new ObjectNotFoundException("Bucket not found " + resolvedBucketName.asString(), e))
            .onErrorMap(NoSuchKeyException.class, e -> new ObjectNotFoundException("Blob not found " + blobId.asString() + " in bucket " + resolvedBucketName.asString(), e))
            .block()
            .flux);
    }

    @Override
    public Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId) {
        BucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return getObject(resolvedBucketName, blobId)
            .onErrorMap(NoSuchBucketException.class, e -> new ObjectNotFoundException("Bucket not found " + resolvedBucketName.asString(), e))
            .onErrorMap(NoSuchKeyException.class, e -> new ObjectNotFoundException("Blob not found " + blobId.asString() + " in bucket " + resolvedBucketName.asString(), e))
            .publishOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .map(res -> ReactorUtils.toInputStream(res.flux));
    }

    private static class FluxResponse {
        final CompletableFuture<FluxResponse> supportingCompletableFuture = new CompletableFuture<>();
        GetObjectResponse sdkResponse;
        Flux<ByteBuffer> flux;
    }

    private Mono<FluxResponse> getObject(BucketName bucketName, BlobId blobId) {
        return getObjectFromStore(bucketName, blobId)
            .onErrorResume(e -> e instanceof NoSuchKeyException || e instanceof NoSuchBucketException, e -> {
                if (fallbackNamespace.isPresent() && bucketNameResolver.isNameSpace(bucketName)) {
                    BucketName resolvedFallbackBucketName = bucketNameResolver.resolve(fallbackNamespace.get());
                    return getObjectFromStore(resolvedFallbackBucketName, blobId);
                }
                return Mono.error(e);
            });
    }

    private Mono<FluxResponse> getObjectFromStore(BucketName bucketName, BlobId blobId) {
        return buildGetObjectRequestBuilder(bucketName, blobId)
            .flatMap(getObjectRequestBuilder -> Mono.fromFuture(() ->
                    client.getObject(getObjectRequestBuilder.build(),
                        new AsyncResponseTransformer<GetObjectResponse, FluxResponse>() {

                            FluxResponse response;

                            @Override
                            public CompletableFuture<FluxResponse> prepare() {
                                response = new FluxResponse();
                                return response.supportingCompletableFuture;
                            }

                            @Override
                            public void onResponse(GetObjectResponse response) {
                                this.response.sdkResponse = response;
                            }

                            @Override
                            public void exceptionOccurred(Throwable error) {
                                this.response.supportingCompletableFuture.completeExceptionally(error);
                            }

                            @Override
                            public void onStream(SdkPublisher<ByteBuffer> publisher) {
                                response.flux = Flux.from(publisher);
                                response.supportingCompletableFuture.complete(response);
                            }
                        }))
                .switchIfEmpty(Mono.error(() -> new ObjectStoreIOException("Request was unexpectedly canceled, no GetObjectResponse"))));
    }


    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        BucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return getObjectBytes(resolvedBucketName, blobId)
                .onErrorMap(NoSuchBucketException.class, e -> new ObjectNotFoundException("Bucket not found " + resolvedBucketName.asString(), e))
                .onErrorMap(NoSuchKeyException.class, e -> new ObjectNotFoundException("Blob not found " + blobId.asString() + " in bucket " + resolvedBucketName.asString(), e))
                .publishOn(Schedulers.parallel())
                .map(BytesWrapper::asByteArrayUnsafe)
                .onErrorMap(e -> e.getCause() instanceof OutOfMemoryError, Throwable::getCause);
    }

    private Mono<ResponseBytes<GetObjectResponse>> getObjectBytes(BucketName bucketName, BlobId blobId) {
        return getObjectBytesFromStore(bucketName, blobId)
                .onErrorResume(e -> e instanceof NoSuchKeyException || e instanceof NoSuchBucketException, e -> {
                    if (fallbackNamespace.isPresent() && bucketNameResolver.isNameSpace(bucketName)) {
                        BucketName resolvedFallbackBucketName = bucketNameResolver.resolve(fallbackNamespace.get());
                        return getObjectBytesFromStore(resolvedFallbackBucketName, blobId);
                    }
                    return Mono.error(e);
                });
    }

    private Mono<ResponseBytes<GetObjectResponse>> getObjectBytesFromStore(BucketName bucketName, BlobId blobId) {
        return buildGetObjectRequestBuilder(bucketName, blobId)
            .flatMap(putObjectRequest -> Mono.fromFuture(() ->
                client.getObject(putObjectRequest.build(), new MinimalCopyBytesResponseTransformer(configuration, blobId))));
    }

    private Mono<GetObjectRequest.Builder> buildGetObjectRequestBuilder(BucketName bucketName, BlobId blobId) {
        GetObjectRequest.Builder baseBuilder = GetObjectRequest.builder()
            .bucket(bucketName.asString())
            .key(blobId.asString());

        if (s3RequestOption.ssec().enable()) {
            return Mono.from(s3RequestOption.ssec().sseCustomerKeyFactory().get()
                    .generate(bucketName, blobId))
                .map(sseCustomerKey -> baseBuilder
                    .sseCustomerAlgorithm(sseCustomerKey.ssecAlgorithm())
                    .sseCustomerKey(sseCustomerKey.customerKey())
                    .sseCustomerKeyMD5(sseCustomerKey.md5()));
        }

        return Mono.just(baseBuilder);
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        BucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return buildPutObjectRequestBuilder(resolvedBucketName, data.length, blobId)
            .flatMap(putObjectRequest -> Mono.fromFuture(() ->
                    client.putObject(putObjectRequest.build(), AsyncRequestBody.fromBytes(data)))
                .retryWhen(createBucketOnRetry(resolvedBucketName))
                .publishOn(Schedulers.parallel()))
            .then();
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(inputStream);

        return uploadUsingFile(bucketName, blobId, inputStream);
    }

    private Mono<Void> uploadUsingFile(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        return Mono.using(
            () -> new FileBackedOutputStream(FILE_THRESHOLD),
            fileBackedOutputStream ->
                Mono.fromCallable(() -> IOUtils.copy(inputStream, fileBackedOutputStream))
                    .flatMap(size -> save(bucketName, blobId, new FileBackedOutputStreamByteSource(fileBackedOutputStream, size))),
            Throwing.consumer(FileBackedOutputStream::reset),
            LAZY)
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob", e))
            .publishOn(Schedulers.parallel());
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        BucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return Mono.fromCallable(content::size)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(contentLength ->
                Mono.usingWhen(Mono.fromCallable(content::openStream).subscribeOn(Schedulers.boundedElastic()),
                    stream -> save(resolvedBucketName, blobId, stream, contentLength),
                    stream -> Mono.fromRunnable(Throwing.runnable(stream::close))))
            .retryWhen(createBucketOnRetry(resolvedBucketName))
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob", e))
            .retryWhen(configuration.uploadRetrySpec())
            .onErrorMap(SdkClientException.class, e -> new ObjectStoreIOException("Error saving blob", e))
            .then();
    }

    private Mono<PutObjectResponse> save(BucketName resolvedBucketName, BlobId blobId, InputStream stream, long contentLength) {
        int chunkSize = Math.min((int) contentLength, CHUNK_SIZE);

        return buildPutObjectRequestBuilder(resolvedBucketName, contentLength, blobId)
            .flatMap(putObjectRequest -> Mono.fromFuture(() -> client.putObject(putObjectRequest.build(),
                AsyncRequestBody.fromPublisher(chunkStream(chunkSize, stream)
                    .subscribeOn(Schedulers.boundedElastic())))));
    }

    private Mono<PutObjectRequest.Builder> buildPutObjectRequestBuilder(BucketName bucketName, long contentLength, BlobId blobId) {
        PutObjectRequest.Builder baseBuilder = PutObjectRequest.builder()
            .bucket(bucketName.asString())
            .key(blobId.asString())
            .contentLength(contentLength);

        if (s3RequestOption.ssec().enable()) {
            return Mono.from(s3RequestOption.ssec().sseCustomerKeyFactory().get().generate(bucketName, blobId))
                .map(sseCustomerKey -> baseBuilder
                    .sseCustomerAlgorithm(sseCustomerKey.ssecAlgorithm())
                    .sseCustomerKey(sseCustomerKey.customerKey())
                    .sseCustomerKeyMD5(sseCustomerKey.md5()));
        }

        return Mono.just(baseBuilder);
    }

    private Flux<ByteBuffer> chunkStream(int chunkSize, InputStream stream) {
        if (chunkSize == 0) {
            return Flux.empty();
        }
        return ReactorUtils.toChunks(stream, chunkSize)
            .subscribeOn(Schedulers.boundedElastic());
    }

    private RetryBackoffSpec createBucketOnRetry(BucketName bucketName) {
        return RetryBackoffSpec.backoff(MAX_RETRIES, FIRST_BACK_OFF)
            .maxAttempts(MAX_RETRIES)
            .doBeforeRetryAsync(retrySignal -> {
                if (retrySignal.failure() instanceof NoSuchBucketException) {
                    return Mono.fromFuture(client.createBucket(builder -> builder.bucket(bucketName.asString())))
                        .onErrorResume(BucketAlreadyOwnedByYouException.class, e -> Mono.empty())
                        .then();
                } else {
                    return Mono.error(retrySignal.failure());
                }
            });
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        BucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return Mono.fromFuture(() ->
                client.deleteObject(delete -> delete.bucket(resolvedBucketName.asString()).key(blobId.asString())))
            .then()
            .onErrorResume(NoSuchBucketException.class, e -> Mono.empty())
            .publishOn(Schedulers.parallel());
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return deleteObjects(bucketName,
            blobIds.stream()
                .map(BlobId::asString)
                .map(id -> ObjectIdentifier.builder().key(id).build())
                .collect(ImmutableList.toImmutableList()))
            .then()
            .onErrorResume(NoSuchBucketException.class, e -> Mono.empty());
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        BucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return deleteResolvedBucket(resolvedBucketName);
    }

    private Mono<Void> deleteResolvedBucket(BucketName bucketName) {
        return emptyBucket(bucketName)
            .onErrorResume(throwable -> throwable instanceof CompletionException && throwable.getCause() instanceof NoSuchBucketException, t -> Mono.just(bucketName))
            .flatMap(ignore -> Mono.fromFuture(() ->
                client.deleteBucket(builder -> builder.bucket(bucketName.asString()))))
            .onErrorResume(NoSuchBucketException.class, t -> Mono.empty())
            .then()
            .publishOn(Schedulers.parallel());
    }

    private Mono<BucketName> emptyBucket(BucketName bucketName) {
        return Flux.from(client.listObjectsV2Paginator(builder -> builder.bucket(bucketName.asString())))
            .flatMap(response -> Flux.fromIterable(response.contents())
                .window(EMPTY_BUCKET_BATCH_SIZE)
                .flatMap(this::buildListForBatch, DEFAULT_CONCURRENCY)
                .flatMap(identifiers -> deleteObjects(bucketName, identifiers), DEFAULT_CONCURRENCY)
                .then(Mono.just(response)))
            .then(Mono.just(bucketName));
    }

    private Mono<List<ObjectIdentifier>> buildListForBatch(Flux<S3Object> batch) {
        return batch
            .map(element -> ObjectIdentifier.builder().key(element.key()).build())
            .collect(ImmutableList.toImmutableList());
    }

    private Mono<DeleteObjectsResponse> deleteObjects(BucketName bucketName, List<ObjectIdentifier> identifiers) {
        return Mono.fromFuture(() -> client.deleteObjects(builder ->
            builder.bucket(bucketName.asString()).delete(delete -> delete.objects(identifiers))));
    }

    @VisibleForTesting
    public Mono<Void> deleteAllBuckets() {
        return Mono.fromFuture(client::listBuckets)
            .publishOn(Schedulers.parallel())
            .flatMapIterable(ListBucketsResponse::buckets)
                .flatMap(bucket -> deleteResolvedBucket(BucketName.of(bucket.name())), DEFAULT_CONCURRENCY)
            .then();
    }

    public Publisher<BucketName> listBuckets() {
        return Mono.fromFuture(client::listBuckets)
            .flatMapIterable(ListBucketsResponse::buckets)
            .map(Bucket::name)
            .handle((bucket, sink) -> bucketNameResolver.unresolve(BucketName.of(bucket))
                .ifPresent(sink::next));
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return Flux.from(client.listObjectsV2Paginator(builder -> builder.bucket(bucketName.asString())))
            .flatMapIterable(ListObjectsV2Response::contents)
            .map(S3Object::key)
            .map(blobIdFactory::parse)
            .onErrorResume(e -> e.getCause() instanceof NoSuchBucketException, e -> Flux.empty())
            .onErrorResume(NoSuchBucketException.class, e -> Flux.empty());
    }
}
