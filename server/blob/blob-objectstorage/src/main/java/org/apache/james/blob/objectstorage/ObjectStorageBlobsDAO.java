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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectStoreException;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.options.CopyOptions;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedBlobStoreContext;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.inject.Inject;
import com.google.inject.Module;

class ObjectStorageBlobsDAO implements BlobStore {
    private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);
    private static final Iterable<Module> JCLOUDS_MODULES = ImmutableSet.of(new SLF4JLoggingModule());

    private final BlobId.Factory blobIdFactory;
    private final org.jclouds.blobstore.BlobStore blobStore;
    private final ContainerName containerName;

    @Inject
    public ObjectStorageBlobsDAO(ContainerName containerName, HashBlobId.Factory blobIdFactory,
                                 ObjectStorageConfiguration objectStorageConfiguration) {
        this.blobIdFactory = blobIdFactory;
        this.containerName = containerName;

        RegionScopedBlobStoreContext blobStoreContext = ContextBuilder.newBuilder("openstack-swift")
            .endpoint(objectStorageConfiguration.getEndpoint().toString())
            .credentials(
                objectStorageConfiguration.getIdentity().value(),
                objectStorageConfiguration.getCredentials().value())
            .overrides(objectStorageConfiguration.getOverrides())
            .modules(JCLOUDS_MODULES)
            .buildView(RegionScopedBlobStoreContext.class);

        blobStore = objectStorageConfiguration
            .getRegion()
            .map(region -> blobStoreContext.getBlobStore(region.value()))
            .orElse(blobStoreContext.getBlobStore());
    }

    @Override
    public CompletableFuture<BlobId> save(byte[] data) {
        return save(new ByteArrayInputStream(data));
    }

    @Override
    public CompletableFuture<BlobId> save(InputStream data) {
        Preconditions.checkNotNull(data);

        BlobId tmpId = blobIdFactory.randomId();
        BlobId id = save(data, tmpId);
        updateBlobId(tmpId, id);

        return CompletableFuture.completedFuture(id);
    }

    private void updateBlobId(BlobId from, BlobId to) {
        String containerName = this.containerName.value();
        blobStore.copyBlob(containerName, from.asString(), containerName, to.asString(), CopyOptions.NONE);
        blobStore.removeBlob(containerName, from.asString());
    }

    private BlobId save(InputStream data, BlobId id) {
        String containerName = this.containerName.value();
        HashingInputStream hashingInputStream = new HashingInputStream(Hashing.sha256(), data);
        Blob blob = blobStore.blobBuilder(id.asString()).payload(hashingInputStream).build();
        blobStore.putBlob(containerName, blob);
        return blobIdFactory.from(hashingInputStream.hash().toString());
    }

    @Override
    public CompletableFuture<byte[]> readBytes(BlobId blobId) {
        return CompletableFuture
            .supplyAsync(Throwing.supplier(() -> IOUtils.toByteArray(read(blobId))).sneakyThrow());
    }

    @Override
    public InputStream read(BlobId blobId) throws ObjectStoreException {
        Blob blob = blobStore.getBlob(containerName.value(), blobId.asString());

        try {
            if (blob != null) {
                return blob.getPayload().openStream();
            } else {
                return EMPTY_STREAM;
            }
        } catch (IOException cause) {
            throw new ObjectStoreException(
                "Failed to read blob " + blobId.asString(),
                cause);
        }

    }
}
