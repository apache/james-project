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

package org.apache.james.modules.blobstore;

import java.io.InputStream;

import javax.inject.Inject;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStore;
import org.reactivestreams.Publisher;

import com.google.common.io.ByteSource;


/**
 * This class is a workaround while waiting for a real DumbBlobStore to be extracted from ObjectStorageBlobStore
 */
public class NoopDumbBlobStore implements DumbBlobStore {
    private final ObjectStorageBlobStore objectStorageBlobStore;

    @Inject
    public NoopDumbBlobStore(ObjectStorageBlobStore objectStorageBlobStore) {
        this.objectStorageBlobStore = objectStorageBlobStore;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        return objectStorageBlobStore.deleteEffectively(bucketName, blobId);
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        throw new NotImplementedException("Not implemented");
    }
}
