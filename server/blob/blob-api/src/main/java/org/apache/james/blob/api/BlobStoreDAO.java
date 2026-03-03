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

package org.apache.james.blob.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

public interface BlobStoreDAO {
    record BlobMetadataName(String name) {

    }
    record BlobMetadataValue(String value) {

    }

    sealed interface Blob {
        Map<BlobMetadataName, BlobMetadataValue> metadata();

        // Have the POJOs encode some conversions ?
        InputStreamBlob asInputStream() throws IOException;

        BytesBlob asBytes() throws IOException;

        ByteSourceBlob asByteSource() throws IOException;
    }

    record BytesBlob(byte[] payload,  Map<BlobMetadataName, BlobMetadataValue> metadata) implements Blob {
        public static BytesBlob of(byte[] payload) {
            return of(payload, ImmutableMap.of());
        }

        public static BytesBlob of(String payload) {
            return of(payload.getBytes(StandardCharsets.UTF_8), ImmutableMap.of());
        }

        public static BytesBlob of(byte[] payload,  Map<BlobMetadataName, BlobMetadataValue> metadata) {
            return new BytesBlob(payload, metadata);
        }

        @Override
        public InputStreamBlob asInputStream() {
            return new InputStreamBlob(new ByteArrayInputStream(payload), metadata);
        }

        @Override
        public BytesBlob asBytes() {
            return this;
        }

        @Override
        public ByteSourceBlob asByteSource() {
            return new ByteSourceBlob(ByteSource.wrap(payload), metadata);
        }
    }

    record InputStreamBlob(InputStream payload,  Map<BlobMetadataName, BlobMetadataValue> metadata) implements Blob {
        public static InputStreamBlob of(InputStream payload) {
            return of(payload, ImmutableMap.of());
        }

        public static InputStreamBlob of(InputStream payload, Map<BlobMetadataName, BlobMetadataValue> metadata) {
            return new InputStreamBlob(payload, metadata);
        }

        @Override
        public InputStreamBlob asInputStream() {
            return this;
        }

        @Override
        public BytesBlob asBytes() throws IOException {
            return new BytesBlob(payload.readAllBytes(), metadata);
        }

        @Override
        public ByteSourceBlob asByteSource() throws IOException {
            try (FileBackedOutputStream fileBackedOutputStream = new FileBackedOutputStream(100 * 1024)) {
                payload.transferTo(fileBackedOutputStream);
                return new ByteSourceBlob(fileBackedOutputStream.asByteSource(), metadata);
            }
        }
    }

    record ByteSourceBlob(ByteSource payload, Map<BlobMetadataName, BlobMetadataValue> metadata) implements Blob {
        public static ByteSourceBlob of(ByteSource payload) {
            return of(payload, ImmutableMap.of());
        }

        public static ByteSourceBlob of(ByteSource payload, Map<BlobMetadataName, BlobMetadataValue> metadata) {
            return new ByteSourceBlob(payload, metadata);
        }

        @Override
        public InputStreamBlob asInputStream() throws IOException {
            return new InputStreamBlob(payload.openStream(), metadata);
        }

        @Override
        public BytesBlob asBytes() throws IOException {
            return new BytesBlob(payload.read(), metadata);
        }

        @Override
        public ByteSourceBlob asByteSource() {
            return this;
        }
    }

    /**
     * Reads a Blob based on its BucketName and its BlobId.
     *
     * @throws ObjectNotFoundException when the blobId or the bucket is not found
     * @throws ObjectStoreIOException when an unexpected IO error occurs
     */
    InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException;

    Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId);

    /**
     * Reads a Blob based on its BucketName and its BlobId
     *
     * @return a Mono containing the content of the blob or
     *  an ObjectNotFoundException in its error channel when the blobId or the bucket is not found
     *  or an IOObjectStoreException when an unexpected IO error occurs
     */
    Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId);

    Publisher<Void> saveBlob(BucketName bucketName, BlobId blobId, Blob blob);

    /**
     * Remove a Blob based on its BucketName and its BlobId.
     * This operation should be atomic
     *
     * @return a successful Mono if the Blob is deleted or did not exist
     * (either the blob doesn't exist in the bucket or the bucket itself doesn't exist)
     *  otherwise an IOObjectStoreException in its error channel
     */
    Publisher<Void> delete(BucketName bucketName, BlobId blobId);

    Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds);

    /**
     * Remove a bucket based on its BucketName
     *
     * Deleting a bucket is not guaranteed to be atomic nor isolated.
     * Saving or reading blobs concurrently of bucket deletion can lead
     * to an inconsistent state.
     *
     * @return a successful Publisher if the bucket is deleted or did not exist
     *  otherwise an IOObjectStoreException in its error channel
     */
    Publisher<Void> deleteBucket(BucketName bucketName);

    Publisher<BucketName> listBuckets();

    Publisher<BlobId> listBlobs(BucketName bucketName);
}
