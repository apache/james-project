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
import java.util.function.Supplier;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.jclouds.blobstore.BlobStore;
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

    private final BlobStore blobStore;

    public StreamCompatibleBlobPutter(BlobStore blobStore) {
        this.blobStore = blobStore;
    }

    @Override
    public void putDirectly(BucketName bucketName, Blob blob) {
        Mono.fromRunnable(() -> blobStore.putBlob(bucketName.asString(), blob))
            .publishOn(Schedulers.elastic())
            .retryWhen(Retry.onlyIf(retryContext -> needToCreateBucket(retryContext.exception(), bucketName))
                .exponentialBackoff(FIRST_BACK_OFF, FOREVER)
                .withBackoffScheduler(Schedulers.elastic())
                .retryMax(MAX_RETRIES)
                .doOnRetry(retryContext -> blobStore.createContainerInLocation(DEFAULT_LOCATION, bucketName.asString())))
            .block();
    }

    @Override
    public BlobId putAndComputeId(BucketName bucketName, Blob initialBlob, Supplier<BlobId> blobIdSupplier) {
        putDirectly(bucketName, initialBlob);
        BlobId finalId = blobIdSupplier.get();
        updateBlobId(bucketName, initialBlob.getMetadata().getName(), finalId.asString());
        return finalId;
    }

    private void updateBlobId(BucketName bucketName, String from, String to) {
        String bucketNameAsString = bucketName.asString();
        blobStore.copyBlob(bucketNameAsString, from, bucketNameAsString, to, CopyOptions.NONE);
        blobStore.removeBlob(bucketNameAsString, from);
    }

    private boolean needToCreateBucket(Throwable throwable, BucketName bucketName) {
        if (throwable instanceof HttpResponseException) {
            HttpResponseException ex = (HttpResponseException) throwable;
            return ex.getCommand().getCurrentRequest().getMethod().equals("PUT")
                && !blobStore.containerExists(bucketName.asString());
        }

        return false;
    }
}