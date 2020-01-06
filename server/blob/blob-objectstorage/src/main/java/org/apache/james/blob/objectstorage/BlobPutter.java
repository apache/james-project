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

import java.io.Closeable;
import java.util.function.Supplier;

import org.apache.james.blob.api.BlobId;
import org.jclouds.blobstore.domain.Blob;

import reactor.core.publisher.Mono;

/**
 * Implementations may have specific behaviour when uploading a blob,
 * such cases are not well handled by jClouds.
 *
 * For example:
 * AWS S3 need a length while uploading with jClouds
 * whereas you don't need one by using the S3 client.
 *
 */

public interface BlobPutter extends Closeable {

    Mono<Void> putDirectly(ObjectStorageBucketName bucketName, Blob blob);

    Mono<BlobId> putAndComputeId(ObjectStorageBucketName bucketName, Blob initialBlob, Supplier<BlobId> blobIdSupplier);
}
