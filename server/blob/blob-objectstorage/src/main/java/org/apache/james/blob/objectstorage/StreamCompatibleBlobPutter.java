/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.blob.objectstorage;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.james.blob.api.BlobId;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.KeyNotFoundException;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.options.CopyOptions;
import org.jclouds.domain.Location;
import org.jclouds.http.HttpResponseException;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Retry;

public class StreamCompatibleBlobPutter implements BlobPutter {

    private static final int MAX_RETRIES = 3;
    private static final Duration FIRST_BACK_OFF = Duration.ofMillis(100);
    private static final Duration FOREVER = Duration.ofMillis(Long.MAX_VALUE);
    private static final Location DEFAULT_LOCATION = null;
    private static final long RETRY_ONE_LAST_TIME_ON_CONCURRENT_SAVING = 1;

    private final BlobStore blobStore;

    public StreamCompatibleBlobPutter(BlobStore blobStore) {
        this.blobStore = blobStore;
    }

    @Override
    public Mono<Void> putDirectly(ObjectStorageBucketName bucketName, Blob blob) {
        return Mono.fromRunnable(() -> blobStore.putBlob(bucketName.asString(), blob))
            .publishOn(Schedulers.elastic())
            .retryWhen(Retry.onlyIf(retryContext -> needToCreateBucket(retryContext.exception(), bucketName))
                .exponentialBackoff(FIRST_BACK_OFF, FOREVER)
                .withBackoffScheduler(Schedulers.elastic())
                .retryMax(MAX_RETRIES)
                .doOnRetry(retryContext -> blobStore.createContainerInLocation(DEFAULT_LOCATION, bucketName.asString())))
            .retryWhen(Retry.onlyIf(RetryContext -> isPutMethod(RetryContext.exception()))
                .withBackoffScheduler(Schedulers.elastic())
                .exponentialBackoff(FIRST_BACK_OFF, FOREVER)
                .retryMax(RETRY_ONE_LAST_TIME_ON_CONCURRENT_SAVING))
            .then();
    }

    @Override
    public Mono<BlobId> putAndComputeId(ObjectStorageBucketName bucketName, Blob initialBlob, Supplier<BlobId> blobIdSupplier) {
        return putDirectly(bucketName, initialBlob)
            .then(Mono.fromCallable(blobIdSupplier::get))
            .map(blobId -> updateBlobId(bucketName, initialBlob.getMetadata().getName(), blobId));
    }

    private BlobId updateBlobId(ObjectStorageBucketName bucketName, String from, BlobId to) {
        String bucketNameAsString = bucketName.asString();
        blobStore.copyBlob(bucketNameAsString, from, bucketNameAsString, to.asString(), CopyOptions.NONE);
        blobStore.removeBlob(bucketNameAsString, from);
        return to;
    }

    private boolean needToCreateBucket(Throwable throwable, ObjectStorageBucketName bucketName) {
        return Optional.of(throwable)
            .filter(t -> t instanceof HttpResponseException || t instanceof KeyNotFoundException)
            .flatMap(this::extractHttpException)
            .map(ex -> isPutMethod(ex) && !bucketExists(bucketName))
            .orElse(false);
    }

    private boolean isPutMethod(Throwable throwable) {
        return throwable instanceof HttpResponseException
            && isPutMethod((HttpResponseException) throwable);
    }

    private boolean isPutMethod(HttpResponseException ex) {
        return ex.getCommand()
            .getCurrentRequest()
            .getMethod()
            .equals("PUT");
    }

    private boolean bucketExists(ObjectStorageBucketName bucketName) {
        return blobStore.containerExists(bucketName.asString());
    }

    private Optional<HttpResponseException> extractHttpException(Throwable throwable) {
        if (throwable instanceof HttpResponseException) {
            return Optional.of((HttpResponseException) throwable);
        } else if (throwable.getCause() instanceof HttpResponseException) {
            return Optional.of((HttpResponseException) throwable.getCause());
        }

        return Optional.empty();
    }
}