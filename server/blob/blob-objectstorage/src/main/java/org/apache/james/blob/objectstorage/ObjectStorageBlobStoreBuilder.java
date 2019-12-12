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

package org.apache.james.blob.objectstorage;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.jclouds.blobstore.BlobStore;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class ObjectStorageBlobStoreBuilder {

    public static RequireBlobIdFactory forBlobStore(Supplier<BlobStore> supplier) {
        return blobIdFactory -> new ReadyToBuild(supplier, blobIdFactory);
    }

    @FunctionalInterface
    public interface RequireBlobIdFactory {
        ReadyToBuild blobIdFactory(BlobId.Factory blobIdFactory);
    }

    public static class ReadyToBuild {

        private final Supplier<BlobStore> supplier;
        private final BlobId.Factory blobIdFactory;
        private Optional<PayloadCodec> payloadCodec;
        private Optional<BlobPutter> blobPutter;
        private Optional<BucketName> namespace;
        private Optional<String> bucketPrefix;

        public ReadyToBuild(Supplier<BlobStore> supplier, BlobId.Factory blobIdFactory) {
            this.blobIdFactory = blobIdFactory;
            this.payloadCodec = Optional.empty();
            this.supplier = supplier;
            this.blobPutter = Optional.empty();
            this.namespace = Optional.empty();
            this.bucketPrefix = Optional.empty();
        }

        public ReadyToBuild payloadCodec(PayloadCodec payloadCodec) {
            this.payloadCodec = Optional.of(payloadCodec);
            return this;
        }

        public ReadyToBuild payloadCodec(Optional<PayloadCodec> payloadCodec) {
            this.payloadCodec = payloadCodec;
            return this;
        }

        public ReadyToBuild blobPutter(Optional<BlobPutter> blobPutter) {
            this.blobPutter = blobPutter;
            return this;
        }

        public ReadyToBuild namespace(Optional<BucketName> namespace) {
            this.namespace = namespace;
            return this;
        }

        public ReadyToBuild namespace(BucketName namespace) {
            this.namespace = Optional.ofNullable(namespace);
            return this;
        }

        public ReadyToBuild bucketPrefix(Optional<String> bucketPrefix) {
            this.bucketPrefix = bucketPrefix;
            return this;
        }

        public ReadyToBuild bucketPrefix(String prefix) {
            this.bucketPrefix = Optional.ofNullable(prefix);
            return this;
        }

        public ObjectStorageBlobStore build() {
            Preconditions.checkState(blobIdFactory != null);

            BlobStore blobStore = supplier.get();

            ObjectStorageBucketNameResolver bucketNameResolver = ObjectStorageBucketNameResolver.builder()
                .prefix(bucketPrefix)
                .namespace(namespace)
                .build();

            return new ObjectStorageBlobStore(namespace.orElse(BucketName.DEFAULT),
                blobIdFactory,
                blobStore,
                blobPutter.orElseGet(() -> defaultPutBlob(blobStore)),
                payloadCodec.orElse(PayloadCodec.DEFAULT_CODEC),
                bucketNameResolver);
        }

        private BlobPutter defaultPutBlob(BlobStore blobStore) {
            return new StreamCompatibleBlobPutter(blobStore);
        }

        @VisibleForTesting
        Supplier<BlobStore> getSupplier() {
            return supplier;
        }
    }

}
