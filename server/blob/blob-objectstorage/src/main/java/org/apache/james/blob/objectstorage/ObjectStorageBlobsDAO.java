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
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.options.CopyOptions;
import org.jclouds.domain.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;

import reactor.core.publisher.Mono;

public class ObjectStorageBlobsDAO implements BlobStore {
    private static final Location DEFAULT_LOCATION = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorageBlobsDAO.class);


    private final BlobId.Factory blobIdFactory;

    private final ContainerName containerName;
    private final org.jclouds.blobstore.BlobStore blobStore;
    private final PutBlobFunction putBlobFunction;
    private final PayloadCodec payloadCodec;

    ObjectStorageBlobsDAO(ContainerName containerName, BlobId.Factory blobIdFactory,
                          org.jclouds.blobstore.BlobStore blobStore,
                          PutBlobFunction putBlobFunction,
                          PayloadCodec payloadCodec) {
        this.blobIdFactory = blobIdFactory;
        this.containerName = containerName;
        this.blobStore = blobStore;
        this.putBlobFunction = putBlobFunction;
        this.payloadCodec = payloadCodec;
    }

    public static ObjectStorageBlobsDAOBuilder.RequireContainerName builder(SwiftTempAuthObjectStorage.Configuration testConfig) {
        return SwiftTempAuthObjectStorage.daoBuilder(testConfig);
    }

    public static ObjectStorageBlobsDAOBuilder.RequireContainerName builder(SwiftKeystone2ObjectStorage.Configuration testConfig) {
        return SwiftKeystone2ObjectStorage.daoBuilder(testConfig);
    }

    public static ObjectStorageBlobsDAOBuilder.RequireContainerName builder(SwiftKeystone3ObjectStorage.Configuration testConfig) {
        return SwiftKeystone3ObjectStorage.daoBuilder(testConfig);
    }

    public static ObjectStorageBlobsDAOBuilder.RequireContainerName builder(AwsS3AuthConfiguration testConfig) {
        return AwsS3ObjectStorage.daoBuilder(testConfig);
    }

    public Mono<ContainerName> createContainer(ContainerName name) {
        return Mono.fromCallable(() -> blobStore.createContainerInLocation(DEFAULT_LOCATION, name.value()))
            .filter(created -> created == false)
            .doOnNext(ignored -> LOGGER.debug("{} already existed", name))
            .thenReturn(name);
    }

    @Override
    public Mono<BlobId> save(byte[] data) {
        return save(new ByteArrayInputStream(data));
    }

    @Override
    public Mono<BlobId> save(InputStream data) {
        Preconditions.checkNotNull(data);

        BlobId tmpId = blobIdFactory.randomId();
        return save(data, tmpId)
            .flatMap(id -> updateBlobId(tmpId, id));
    }

    private Mono<BlobId> updateBlobId(BlobId from, BlobId to) {
        String containerName = this.containerName.value();
        return Mono
            .fromCallable(() -> blobStore.copyBlob(containerName, from.asString(), containerName, to.asString(), CopyOptions.NONE))
            .then(Mono.fromRunnable(() -> blobStore.removeBlob(containerName, from.asString())))
            .thenReturn(to);
    }

    private Mono<BlobId> save(InputStream data, BlobId id) {
        HashingInputStream hashingInputStream = new HashingInputStream(Hashing.sha256(), data);
        Payload payload = payloadCodec.write(hashingInputStream);
        Blob blob = blobStore.blobBuilder(id.asString())
                            .payload(payload.getPayload())
                            .build();

        return Mono.fromRunnable(() -> putBlobFunction.putBlob(blob))
            .then(Mono.fromCallable(() -> blobIdFactory.from(hashingInputStream.hash().toString())));
    }

    @Override
    public Mono<byte[]> readBytes(BlobId blobId) {
        return Mono.fromCallable(() -> IOUtils.toByteArray(read(blobId)));
    }

    @Override
    public InputStream read(BlobId blobId) throws ObjectStoreException {
        Blob blob = blobStore.getBlob(containerName.value(), blobId.asString());

        try {
            if (blob != null) {
                return payloadCodec.read(new Payload(blob.getPayload(), Optional.empty()));
            } else {
                throw new ObjectStoreException("fail to load blob with id " + blobId);
            }
        } catch (IOException cause) {
            throw new ObjectStoreException(
                "Failed to readBytes blob " + blobId.asString(),
                cause);
        }

    }

    public void deleteContainer() {
        blobStore.deleteContainer(containerName.value());
    }

    public PayloadCodec getPayloadCodec() {
        return payloadCodec;
    }
}
