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

import java.util.function.Supplier;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.options.CopyOptions;

public class StreamCompatibleBlobPutter implements BlobPutter {
    private final org.jclouds.blobstore.BlobStore blobStore;

    public StreamCompatibleBlobPutter(BlobStore blobStore) {
        this.blobStore = blobStore;
    }

    @Override
    public void putDirectly(BucketName bucketName, Blob blob) {
        blobStore.putBlob(bucketName.asString(), blob);
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
}