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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.util.DataChunker;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Retry;
import reactor.retry.RetryWithAsyncCallback;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

public class S3DumbBlobStore implements DumbBlobStore, Closeable {

    private static final int CHUNK_SIZE = 1024 * 1024;
    private static final int EMPTY_BUCKET_BATCH_SIZE = 1000;
    private static final int FILE_THRESHOLD = 1024 * 100;
    private static final Duration FIRST_BACK_OFF = Duration.ofMillis(100);
    private static final Duration FOREVER = Duration.ofMillis(Long.MAX_VALUE);
    private static final boolean LAZY = false;
    private static final int MAX_RETRIES = 5;

    private final S3AsyncClient client;

    @Inject
    S3DumbBlobStore(AwsS3AuthConfiguration configuration, Region region) {
        client = S3AsyncClient.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(configuration.getAccessKeyId(), configuration.getSecretKey())))
            .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                .maxConcurrency(100)
                .maxPendingConnectionAcquires(10_000))
            .endpointOverride(configuration.getEndpoint())
            .region(region.asAws())
            .build();
    }

    @Override
    @PreDestroy
    public void close() {
        client.close();
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return getObject(bucketName, blobId)
            .map(response -> ReactorUtils.toInputStream(response.flux))
            .onErrorMap(NoSuchBucketException.class, e -> new ObjectNotFoundException("Bucket not found " + bucketName, e))
            .onErrorMap(NoSuchKeyException.class, e -> new ObjectNotFoundException("Blob not found " + bucketName, e))
            .block();
    }

    private static class FluxResponse {
        final CompletableFuture<FluxResponse> supportingCompletableFuture = new CompletableFuture<>();
        GetObjectResponse sdkResponse;
        Flux<ByteBuffer> flux;
    }

    private Mono<FluxResponse> getObject(BucketName bucketName, BlobId blobId) {
        return Mono.fromFuture(() ->
            client.getObject(
                builder -> builder.bucket(bucketName.asString()).key(blobId.asString()),
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
                }));
    }


    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.fromFuture(() ->
                client.getObject(
                    builder -> builder.bucket(bucketName.asString()).key(blobId.asString()),
                    AsyncResponseTransformer.toBytes()))
            .onErrorMap(NoSuchBucketException.class, e -> new ObjectNotFoundException("Bucket not found " + bucketName, e))
            .onErrorMap(NoSuchKeyException.class, e -> new ObjectNotFoundException("Blob not found " + bucketName, e))
            .map(BytesWrapper::asByteArray);
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        return Mono.fromFuture(() ->
                client.putObject(
                    builder -> builder.bucket(bucketName.asString()).key(blobId.asString()).contentLength((long) data.length),
                    AsyncRequestBody.fromBytes(data)))
            .retryWhen(createBucketOnRetry(bucketName))
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
                    .flatMap(ignore -> save(bucketName, blobId, fileBackedOutputStream.asByteSource())),
            Throwing.consumer(FileBackedOutputStream::reset),
            LAZY)
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob", e));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Mono.using(content::openStream,
            stream ->
                Mono.fromFuture(() ->
                    client.putObject(
                        Throwing.<PutObjectRequest.Builder>consumer(
                            builder -> builder.bucket(bucketName.asString()).contentLength(content.size()).key(blobId.asString()))
                        .sneakyThrow(),
                        AsyncRequestBody.fromPublisher(
                            DataChunker.chunkStream(stream, CHUNK_SIZE)))),
            Throwing.consumer(InputStream::close),
            LAZY)
            .retryWhen(createBucketOnRetry(bucketName))
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob", e))
            .onErrorMap(SdkClientException.class, e -> new ObjectStoreIOException("Error saving blob", e))
            .then();
    }

    private Retry<Object> createBucketOnRetry(BucketName bucketName) {
        return RetryWithAsyncCallback.onlyIf(retryContext -> retryContext.exception() instanceof NoSuchBucketException)
            .exponentialBackoff(FIRST_BACK_OFF, FOREVER)
            .withBackoffScheduler(Schedulers.elastic())
            .retryMax(MAX_RETRIES)
            .onRetryWithMono(retryContext -> Mono
                .fromFuture(client.createBucket(builder -> builder.bucket(bucketName.asString())))
                .onErrorResume(BucketAlreadyOwnedByYouException.class, e -> Mono.empty()));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.fromFuture(() ->
                client.deleteObject(delete -> delete.bucket(bucketName.asString()).key(blobId.asString())))
            .then()
            .onErrorResume(NoSuchBucketException.class, e -> Mono.empty());
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return emptyBucket(bucketName)
            .onErrorResume(t -> Mono.just(bucketName))
            .flatMap(ignore -> Mono.fromFuture(() -> client.deleteBucket(builder -> builder.bucket(bucketName.asString()))))
            .onErrorResume(t -> Mono.empty())
            .then();
    }

    private Mono<BucketName> emptyBucket(BucketName bucketName) {
        return Mono.fromFuture(() -> client.listObjects(builder -> builder.bucket(bucketName.asString())))
            .flatMapIterable(ListObjectsResponse::contents)
            .window(EMPTY_BUCKET_BATCH_SIZE)
            .flatMap(this::buildListForBatch)
            .flatMap(identifiers -> deleteObjects(bucketName, identifiers))
            .then(Mono.just(bucketName));
    }

    private Mono<List<ObjectIdentifier>> buildListForBatch(Flux<S3Object> batch) {
        return batch
            .map(element -> ObjectIdentifier.builder().key(element.key()).build())
            .collect(Guavate.toImmutableList());
    }

    private Mono<DeleteObjectsResponse> deleteObjects(BucketName bucketName, List<ObjectIdentifier> identifiers) {
        return Mono.fromFuture(() -> client.deleteObjects(builder ->
            builder.bucket(bucketName.asString()).delete(delete -> delete.objects(identifiers))));
    }

    @VisibleForTesting
    public Mono<Void> deleteAllBuckets() {
        return Mono.fromFuture(client::listBuckets)
                .flatMapIterable(ListBucketsResponse::buckets)
                     .flatMap(bucket -> deleteBucket(BucketName.of(bucket.name())))
            .then();
    }


}
