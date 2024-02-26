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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.reactivestreams.Publisher;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

public interface BlobStoreDAO {
    class ReactiveByteSource {
        private final long size;
        private final Publisher<ByteBuffer> content;

        public ReactiveByteSource(long size, Publisher<ByteBuffer> content) {
            this.size = size;
            this.content = content;
        }

        public long getSize() {
            return size;
        }

        public Publisher<ByteBuffer> getContent() {
            return content;
        }
    }


    /**
     * Reads a Blob based on its BucketName and its BlobId.
     *
     * @throws ObjectNotFoundException when the blobId or the bucket is not found
     * @throws ObjectStoreIOException when an unexpected IO error occurs
     */
    InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException;

    default Publisher<ReactiveByteSource> readAsByteSource(BucketName bucketName, BlobId blobId) {
        return Mono.from(readBytes(bucketName, blobId))
            .map(bytes -> new ReactiveByteSource(bytes.length, Mono.just(ByteBuffer.wrap(bytes))));
    }

    Publisher<InputStream> readReactive(BucketName bucketName, BlobId blobId);

    /**
     * Reads a Blob based on its BucketName and its BlobId
     *
     * @return a Mono containing the content of the blob or
     *  an ObjectNotFoundException in its error channel when the blobId or the bucket is not found
     *  or an IOObjectStoreException when an unexpected IO error occurs
     */
    Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId);


    /**
     * Save the blob with the provided blob id, and overwrite the previous blob with the same id if it already exists
     * The bucket is created if it not already exists.
     * This operation should be atomic and isolated
     * Two blobs having the same blobId must have the same content
     * @return an empty Mono when the save succeed,
     *  otherwise an IOObjectStoreException in its error channel
     */
    Publisher<Void> save(BucketName bucketName, BlobId blobId, byte[] data);

    /**
     * @see #save(BucketName, BlobId, byte[])
     *
     * The InputStream should be closed after the call to this method
     */
    Publisher<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream);

    /**
     * @see #save(BucketName, BlobId, byte[])
     */
    Publisher<Void> save(BucketName bucketName, BlobId blobId, ByteSource content);

    /**
     * @see #save(BucketName, BlobId, byte[])
     *
     * The String is stored as UTF-8.
     */
    default Publisher<Void> save(BucketName bucketName, BlobId blobId, String data) {
        return save(bucketName, blobId, data.getBytes(StandardCharsets.UTF_8));
    }

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
