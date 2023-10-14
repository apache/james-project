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

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.*;
import org.apache.james.lifecycle.api.Startable;
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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.TlsTrustManagersProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

public class S3BlobStoreDAO implements BlobStoreDAO, Startable, Closeable {
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
    private final BlobId.Factory blobIdFactory;
    private final S3BlobStoreConfiguration configuration;

    @Inject
    S3BlobStoreDAO(S3BlobStoreConfiguration configuration, BlobId.Factory blobIdFactory) {
        this.blobIdFactory = blobIdFactory;
        this.configuration = configuration;
        AwsS3AuthConfiguration authConfiguration = this.configuration.getSpecificAuthConfiguration();

        S3Configuration pathStyleAccess = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build();

        client = S3AsyncClient.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(authConfiguration.getAccessKeyId(), authConfiguration.getSecretKey())))
            .httpClientBuilder(httpClientBuilder(configuration))
            .endpointOverride(authConfiguration.getEndpoint())
            .region(configuration.getRegion().asAws())
            .serviceConfiguration(pathStyleAccess)
            .build();

        bucketNameResolver = BucketNameResolver.builder()
            .prefix(configuration.getBucketPrefix())
            .namespace(configuration.getNamespace())
            .build();
    }

    private NettyNioAsyncHttpClient.Builder httpClientBuilder(S3BlobStoreConfiguration configuration) {
        NettyNioAsyncHttpClient.Builder result = NettyNioAsyncHttpClient.builder()
            .tlsTrustManagersProvider(getTrustManagerProvider(configuration.getSpecificAuthConfiguration()))
            .maxConcurrency(configuration.getHttpConcurrency())
            .maxPendingConnectionAcquires(10_000);
        configuration.getWriteTimeout().ifPresent(result::writeTimeout);
        configuration.getReadTimeout().ifPresent(result::readTimeout);
        configuration.getConnectionTimeout().ifPresent(result::connectionTimeout);
        return result;
    }

    private TlsTrustManagersProvider getTrustManagerProvider(AwsS3AuthConfiguration configuration) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                configuration.getTrustStoreAlgorithm().orElse(TrustManagerFactory.getDefaultAlgorithm()));
            KeyStore trustStore = loadTrustStore(configuration);
            trustManagerFactory.init(trustStore);
            return trustManagerFactory::getTrustManagers;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private KeyStore loadTrustStore(AwsS3AuthConfiguration configuration) {
        if (configuration.getTrustStorePath().isEmpty()) {
            return null; // use java default truststore
        }
        try (FileInputStream trustStoreStream = new FileInputStream(configuration.getTrustStorePath().get())) {
            char[] secret = configuration.getTrustStoreSecret().map(String::toCharArray).orElse(null);
            KeyStore trustStore = KeyStore.getInstance(
                configuration.getTrustStoreType().orElse(KeyStore.getDefaultType()));
            trustStore.load(trustStoreStream, secret);
            return trustStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        client.close();
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        ResolvedBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return ReactorUtils.toInputStream(getObject(resolvedBucketName, blobId)
            .onErrorMap(NoSuchBucketException.class, e -> new ObjectNotFoundException("Bucket not found " + resolvedBucketName.asString(), e))
            .onErrorMap(NoSuchKeyException.class, e -> new ObjectNotFoundException("Blob not found " + blobId.asString() + " in bucket " + resolvedBucketName.asString(), e))
            .block()
            .flux);
    }

    @Override
    public Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId) {
        ResolvedBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return getObject(resolvedBucketName, blobId)
            .onErrorMap(NoSuchBucketException.class, e -> new ObjectNotFoundException("Bucket not found " + resolvedBucketName.asString(), e))
            .onErrorMap(NoSuchKeyException.class, e -> new ObjectNotFoundException("Blob not found " + blobId.asString() + " in bucket " + resolvedBucketName.asString(), e))
            .map(res -> ReactorUtils.toInputStream(res.flux));
    }

    private static class FluxResponse {
        final CompletableFuture<FluxResponse> supportingCompletableFuture = new CompletableFuture<>();
        GetObjectResponse sdkResponse;
        Flux<ByteBuffer> flux;
    }

    private Mono<FluxResponse> getObject(ResolvedBucketName bucketName, BlobId blobId) {
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
                }))
            .switchIfEmpty(Mono.error(() -> new ObjectStoreIOException("Request was unexpectedly canceled, no GetObjectResponse")));
    }


    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        ResolvedBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return Mono.fromFuture(() ->
                client.getObject(
                    builder -> builder.bucket(resolvedBucketName.asString()).key(blobId.asString()),
                    new MinimalCopyBytesResponseTransformer(configuration, blobId)))
            .onErrorMap(NoSuchBucketException.class, e -> new ObjectNotFoundException("Bucket not found " + resolvedBucketName.asString(), e))
            .onErrorMap(NoSuchKeyException.class, e -> new ObjectNotFoundException("Blob not found " + blobId.asString() + " in bucket " + resolvedBucketName.asString(), e))
            .publishOn(Schedulers.parallel())
            .map(BytesWrapper::asByteArrayUnsafe)
            .onErrorMap(e -> e.getCause() instanceof OutOfMemoryError, Throwable::getCause);
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        ResolvedBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return Mono.fromFuture(() ->
                client.putObject(
                    builder -> builder.bucket(resolvedBucketName.asString()).key(blobId.asString()).contentLength((long) data.length),
                    AsyncRequestBody.fromBytes(data)))
            .retryWhen(createBucketOnRetry(resolvedBucketName))
            .publishOn(Schedulers.parallel())
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
        ResolvedBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return Mono.fromCallable(content::size)
            .flatMap(contentLength ->
                Mono.usingWhen(Mono.fromCallable(content::openStream).subscribeOn(Schedulers.boundedElastic()),
                    stream -> save(resolvedBucketName, blobId, stream, contentLength),
                    stream -> Mono.fromRunnable(Throwing.runnable(stream::close))))
            .retryWhen(createBucketOnRetry(resolvedBucketName))
            .onErrorMap(IOException.class, e -> new ObjectStoreIOException("Error saving blob", e))
            .onErrorMap(SdkClientException.class, e -> new ObjectStoreIOException("Error saving blob", e))
            .publishOn(Schedulers.parallel())
            .then();
    }

    private Mono<PutObjectResponse> save(ResolvedBucketName resolvedBucketName, BlobId blobId, InputStream stream, long contentLength) {
        int chunkSize = Math.min((int) contentLength, CHUNK_SIZE);

        return Mono.fromFuture(() -> client.putObject(builder -> builder
                .bucket(resolvedBucketName.asString())
                .contentLength(contentLength)
                .key(blobId.asString()),
            AsyncRequestBody.fromPublisher(chunkStream(chunkSize, stream)
                .subscribeOn(Schedulers.boundedElastic()))));
    }

    private Flux<ByteBuffer> chunkStream(int chunkSize, InputStream stream) {
        if (chunkSize == 0) {
            return Flux.empty();
        }
        return ReactorUtils.toChunks(stream, chunkSize)
            .subscribeOn(Schedulers.boundedElastic());
    }

    private RetryBackoffSpec createBucketOnRetry(ResolvedBucketName bucketName) {
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
        ResolvedBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return Mono.fromFuture(() ->
                client.deleteObject(delete -> delete.bucket(resolvedBucketName.asString()).key(blobId.asString())))
            .then()
            .onErrorResume(NoSuchBucketException.class, e -> Mono.empty())
            .publishOn(Schedulers.parallel());
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        ResolvedBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return deleteObjects(resolvedBucketName,
            blobIds.stream()
                .map(BlobId::asString)
                .map(id -> ObjectIdentifier.builder().key(id).build())
                .collect(ImmutableList.toImmutableList()))
            .then();
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        ResolvedBucketName resolvedBucketName = bucketNameResolver.resolve(bucketName);

        return deleteResolvedBucket(resolvedBucketName);
    }

    private Mono<Void> deleteResolvedBucket(ResolvedBucketName bucketName) {
        return emptyBucket(bucketName)
            .onErrorResume(t -> Mono.just(bucketName))
            .flatMap(ignore -> Mono.fromFuture(() ->
                client.deleteBucket(builder -> builder.bucket(bucketName.asString()))))
            .onErrorResume(t -> Mono.empty())
            .then()
            .publishOn(Schedulers.parallel());
    }

    private Mono<ResolvedBucketName> emptyBucket(ResolvedBucketName bucketName) {
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

    private Mono<DeleteObjectsResponse> deleteObjects(ResolvedBucketName bucketName, List<ObjectIdentifier> identifiers) {
        return Mono.fromFuture(() -> client.deleteObjects(builder ->
            builder.bucket(bucketName.asString()).delete(delete -> delete.objects(identifiers))));
    }

    @VisibleForTesting
    public Mono<Void> deleteAllBuckets() {
        return Mono.fromFuture(client::listBuckets)
            .publishOn(Schedulers.parallel())
            .flatMapIterable(ListBucketsResponse::buckets)
                .flatMap(bucket -> deleteResolvedBucket(ResolvedBucketName.of(bucket.name())), DEFAULT_CONCURRENCY)
            .then();
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return Mono.fromFuture(client::listBuckets)
            .flatMapIterable(ListBucketsResponse::buckets)
            .map(Bucket::name)
            .handle((bucket, sink) -> bucketNameResolver.unresolve(ResolvedBucketName.of(bucket))
                .ifPresent(sink::next));
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return Flux.from(client.listObjectsV2Paginator(builder -> builder.bucket(bucketName.asString())))
            .flatMapIterable(ListObjectsV2Response::contents)
            .map(S3Object::key)
            .map(blobIdFactory::from)
            .onErrorResume(e -> e.getCause() instanceof NoSuchBucketException, e -> Flux.empty())
            .onErrorResume(NoSuchBucketException.class, e -> Flux.empty());
    }
}
