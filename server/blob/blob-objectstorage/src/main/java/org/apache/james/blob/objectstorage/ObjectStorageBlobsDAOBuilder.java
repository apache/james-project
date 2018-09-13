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
import org.jclouds.blobstore.BlobStore;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class ObjectStorageBlobsDAOBuilder {
    private final Supplier<BlobStore> supplier;
    private ContainerName containerName;
    private BlobId.Factory blobIdFactory;
    private Optional<PayloadCodec> payloadCodec;

    public ObjectStorageBlobsDAOBuilder(Supplier<BlobStore> supplier) {
        this.payloadCodec = Optional.empty();
        this.supplier = supplier;
    }

    public ObjectStorageBlobsDAOBuilder container(ContainerName containerName) {
        this.containerName = containerName;
        return this;
    }

    public ObjectStorageBlobsDAOBuilder blobIdFactory(BlobId.Factory blobIdFactory) {
        this.blobIdFactory = blobIdFactory;
        return this;
    }

    public ObjectStorageBlobsDAOBuilder payloadCodec(PayloadCodec payloadCodec) {
        this.payloadCodec = Optional.of(payloadCodec);
        return this;
    }
    
    public ObjectStorageBlobsDAOBuilder payloadCodec(Optional<PayloadCodec> payloadCodec) {
        this.payloadCodec = payloadCodec;
        return this;
    }

    public ObjectStorageBlobsDAO build() {
        Preconditions.checkState(containerName != null);
        Preconditions.checkState(blobIdFactory != null);

        return new ObjectStorageBlobsDAO(containerName, blobIdFactory, supplier.get(), payloadCodec.orElse(PayloadCodec.DEFAULT_CODEC));
    }

    @VisibleForTesting
    Supplier<BlobStore> getSupplier() {
        return supplier;
    }
}
