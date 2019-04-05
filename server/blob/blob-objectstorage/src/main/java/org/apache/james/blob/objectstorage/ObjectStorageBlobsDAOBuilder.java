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
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.james.blob.api.BlobId;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class ObjectStorageBlobsDAOBuilder {

    public static RequireContainerName forBlobStore(Supplier<BlobStore> supplier) {
        return containerName -> blobIdFactory -> new ReadyToBuild(supplier, blobIdFactory, containerName);
    }

    @FunctionalInterface
    public interface RequireContainerName {
        RequireBlobIdFactory container(ContainerName containerName);
    }

    @FunctionalInterface
    public interface RequireBlobIdFactory {
        ReadyToBuild blobIdFactory(BlobId.Factory blobIdFactory);
    }

    public static class ReadyToBuild {

        private final Supplier<BlobStore> supplier;
        private final ContainerName containerName;
        private final BlobId.Factory blobIdFactory;
        private Optional<PayloadCodec> payloadCodec;
        private Optional<Function<Blob, String>> putBlob;

        public ReadyToBuild(Supplier<BlobStore> supplier, BlobId.Factory blobIdFactory, ContainerName containerName) {
            this.blobIdFactory = blobIdFactory;
            this.containerName = containerName;
            this.payloadCodec = Optional.empty();
            this.supplier = supplier;
            this.putBlob = Optional.empty();
        }

        public ReadyToBuild payloadCodec(PayloadCodec payloadCodec) {
            this.payloadCodec = Optional.of(payloadCodec);
            return this;
        }

        public ReadyToBuild payloadCodec(Optional<PayloadCodec> payloadCodec) {
            this.payloadCodec = payloadCodec;
            return this;
        }

        public ReadyToBuild putBlob(Optional<Function<Blob, String>> putBlob) {
            this.putBlob = putBlob;
            return this;
        }

        public ObjectStorageBlobsDAO build() {
            Preconditions.checkState(containerName != null);
            Preconditions.checkState(blobIdFactory != null);

            BlobStore blobStore = supplier.get();

            return new ObjectStorageBlobsDAO(containerName,
                blobIdFactory,
                blobStore,
                putBlob.orElse((blob) -> blobStore.putBlob(containerName.value(), blob)),
                payloadCodec.orElse(PayloadCodec.DEFAULT_CODEC));
        }

        @VisibleForTesting
        Supplier<BlobStore> getSupplier() {
            return supplier;
        }
    }

}
