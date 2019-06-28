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

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
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
import reactor.core.scheduler.Schedulers;

public class ObjectStorageBlobsDAO implements BlobStore {
    private static final Location DEFAULT_LOCATION = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorageBlobsDAO.class);


    private final BlobId.Factory blobIdFactory;

    private final BucketName defaultBucketName;
    private final org.jclouds.blobstore.BlobStore blobStore;
    private final PutBlobFunction putBlobFunction;
    private final PayloadCodec payloadCodec;

    ObjectStorageBlobsDAO(BucketName defaultBucketName, BlobId.Factory blobIdFactory,
                          org.jclouds.blobstore.BlobStore blobStore,
                          PutBlobFunction putBlobFunction,
                          PayloadCodec payloadCodec) {
        this.blobIdFactory = blobIdFactory;
        this.defaultBucketName = defaultBucketName;
        this.blobStore = blobStore;
        this.putBlobFunction = putBlobFunction;
        this.payloadCodec = payloadCodec;
    }

    public static ObjectStorageBlobsDAOBuilder.RequireDefaultBucketName builder(SwiftTempAuthObjectStorage.Configuration testConfig) {
        return SwiftTempAuthObjectStorage.daoBuilder(testConfig);
    }

    public static ObjectStorageBlobsDAOBuilder.RequireDefaultBucketName builder(SwiftKeystone2ObjectStorage.Configuration testConfig) {
        return SwiftKeystone2ObjectStorage.daoBuilder(testConfig);
    }

    public static ObjectStorageBlobsDAOBuilder.RequireDefaultBucketName builder(SwiftKeystone3ObjectStorage.Configuration testConfig) {
        return SwiftKeystone3ObjectStorage.daoBuilder(testConfig);
    }

    public static ObjectStorageBlobsDAOBuilder.RequireDefaultBucketName builder(AwsS3AuthConfiguration testConfig) {
        return AwsS3ObjectStorage.daoBuilder(testConfig);
    }

    public Mono<BucketName> createBucket(BucketName name) {
        return Mono.fromCallable(() -> blobStore.createContainerInLocation(DEFAULT_LOCATION, name.asString()))
            .filter(created -> created == false)
            .doOnNext(ignored -> LOGGER.debug("{} already existed", name))
            .thenReturn(name);
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] data) {
        Preconditions.checkNotNull(data);
        BlobId blobId = blobIdFactory.forPayload(data);
        Payload payload = payloadCodec.write(data);

        Blob blob = blobStore.blobBuilder(blobId.asString())
            .payload(payload.getPayload())
            .contentLength(payload.getLength().orElse(new Long(data.length)))
            .build();

        return save(bucketName, blob)
            .thenReturn(blobId);
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, InputStream data) {
        Preconditions.checkNotNull(data);

        BlobId tmpId = blobIdFactory.randomId();
        return save(bucketName, data, tmpId)
            .flatMap(id -> updateBlobId(bucketName, tmpId, id));
    }

    private Mono<BlobId> updateBlobId(BucketName bucketName, BlobId from, BlobId to) {
        String bucketNameAsString = bucketName.asString();
        return Mono
            .fromCallable(() -> blobStore.copyBlob(bucketNameAsString, from.asString(), bucketNameAsString, to.asString(), CopyOptions.NONE))
            .then(Mono.fromRunnable(() -> blobStore.removeBlob(bucketNameAsString, from.asString())))
            .thenReturn(to);
    }

    private Mono<BlobId> save(BucketName bucketName, InputStream data, BlobId id) {
        HashingInputStream hashingInputStream = new HashingInputStream(Hashing.sha256(), data);
        Payload payload = payloadCodec.write(hashingInputStream);
        Blob blob = blobStore.blobBuilder(id.asString())
                            .payload(payload.getPayload())
                            .build();

        return save(bucketName, blob)
            .then(Mono.fromCallable(() -> blobIdFactory.from(hashingInputStream.hash().toString())));
    }

    private Mono<Void> save(BucketName bucketName, Blob blob) {
        return Mono.fromRunnable(() -> putBlobFunction.putBlob(bucketName, blob));
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> IOUtils.toByteArray(read(bucketName, blobId)));
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreException {
        Blob blob = blobStore.getBlob(bucketName.asString(), blobId.asString());

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

    @Override
    public BucketName getDefaultBucketName() {
        return defaultBucketName;
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Mono.<Void>fromRunnable(() -> blobStore.deleteContainer(bucketName.asString()))
            .subscribeOn(Schedulers.elastic());
    }

    public PayloadCodec getPayloadCodec() {
        return payloadCodec;
    }
}
