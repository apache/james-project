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

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.apache.james.blob.api.BlobId;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Class copied for {@link software.amazon.awssdk.core.internal.async.ByteArrayAsyncResponseTransformer}
 *
 * Modified to take advantage of the content length of the get response in order to use a sized array
 * upon content copy. This avoids the usage of a ByteArrayOutputStream that yields additional copies
 * (resizing upon copy, copy of the resulting byte array).
 *
 * A defensive copy upon returning the result is also removed (responsibility transfered to the caller, no other usages)
 */
public class MinimalCopyBytesResponseTransformer implements AsyncResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>> {
    private final S3BlobStoreConfiguration configuration;
    private final BlobId blobId;
    private volatile CompletableFuture<byte[]> cf;
    private volatile GetObjectResponse response;

    public MinimalCopyBytesResponseTransformer(S3BlobStoreConfiguration configuration, BlobId blobId) {
        this.configuration = configuration;
        this.blobId = blobId;
    }

    public CompletableFuture<ResponseBytes<GetObjectResponse>> prepare() {
        this.cf = new CompletableFuture();
        // Modifcation: Remove a defensive copy of the buffer upon completion: the caller is now the sole user of the array
        return this.cf.thenApply(arr -> ResponseBytes.fromByteArrayUnsafe(response, arr));
    }

    public void onResponse(GetObjectResponse response) {
        boolean exceedMaximumSize = configuration
            .getInMemoryReadLimit().map(limit -> response.contentLength() > limit)
            .orElse(false);

        if (exceedMaximumSize) {
            throw new IllegalArgumentException(String.format("%s blob of %l size exceed maximum size allowed for in memory reads (%l)",
                blobId.asString(), response.contentLength(), configuration.getInMemoryReadLimit().orElse(-1L)));
        }

        this.response = response;
    }

    public void onStream(SdkPublisher<ByteBuffer> publisher) {
        publisher.subscribe(new BaosSubscriber(this.cf, response.contentLength().intValue()));
    }

    public void exceptionOccurred(Throwable throwable) {
        this.cf.completeExceptionally(throwable);
    }

    static class BaosSubscriber implements Subscriber<ByteBuffer> {
        private final CompletableFuture<byte[]> resultFuture;
        // Modification: use a byte array instead of the ByteArrayInputStream and track position
        private final byte[] buffer;
        private int pos = 0;
        private Subscription subscription;

        BaosSubscriber(CompletableFuture<byte[]> resultFuture, int size) {
            this.resultFuture = resultFuture;
            this.buffer = new byte[size];
        }

        public void onSubscribe(Subscription s) {
            if (this.subscription != null) {
                s.cancel();
            } else {
                this.subscription = s;
                this.subscription.request(9223372036854775807L);
            }
        }

        public void onNext(ByteBuffer byteBuffer) {
            // Modification: copy the response part in place into the result buffer and track position
            int written = byteBuffer.remaining();
            byteBuffer.get(buffer, pos, written);
            pos += written;
            this.subscription.request(1L);
        }

        public void onError(Throwable throwable) {
            this.resultFuture.completeExceptionally(throwable);
        }

        public void onComplete() {
            this.resultFuture.complete(this.buffer);
        }
    }
}
