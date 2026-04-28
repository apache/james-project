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
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.reactivestreams.Publisher;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

public interface BlobStoreDAO {
    record BlobMetadataName(String name) {
        private static final CharMatcher CHAR_MATCHER = CharMatcher.inRange('a', 'z')
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.is('-'));

        public BlobMetadataName {
            Preconditions.checkArgument(CHAR_MATCHER.matchesAllOf(name), "Invalid char in metadata name. Must be a-z,A-Z,0-9 or - got " + name);
            Preconditions.checkArgument(name.length() < 128, "Metadata name is too long. Size exceed 128 chars");
            name = name.toLowerCase(Locale.US);
        }
    }

    record BlobMetadataValue(String value) {
        public BlobMetadataValue {
            Preconditions.checkArgument(value.length() < 128, "Metadata value is too long. Size exceed 128 chars");
        }
    }

    record ContentEncoding(String value) {
        public static BlobMetadataName NAME = new BlobMetadataName("content-encoding");
        public static ContentEncoding ZSTD = new ContentEncoding("zstd");

        public static ContentEncoding fromValue(BlobMetadataValue value) {
            return new ContentEncoding(value.value());
        }

        public ContentEncoding {
            Preconditions.checkArgument(value.length() < 128, "ContentEncoding value is too long. Size exceed 128 chars");
        }

        public BlobMetadataValue asValue() {
            return new BlobMetadataValue(value);
        }

    }

    record BlobMetadata(Map<BlobMetadataName, BlobMetadataValue> underlyingMap) {
        public static BlobMetadata empty() {
            return new BlobMetadata(ImmutableMap.of());
        }

        public Optional<BlobMetadataValue> get(BlobMetadataName name) {
            return Optional.ofNullable(underlyingMap.get(name));
        }

        public BlobMetadata withMetadata(BlobMetadataName name, BlobMetadataValue value) {
            return new BlobMetadata(ImmutableMap.<BlobMetadataName, BlobMetadataValue>builder()
                .putAll(underlyingMap)
                .put(name, value)
                .build());
        }

        public Optional<ContentEncoding> contentEncoding() {
            return get(ContentEncoding.NAME).map(ContentEncoding::fromValue);
        }

        public BlobMetadata withContentEncoding(ContentEncoding contentEncoding) {
            return withMetadata(ContentEncoding.NAME, contentEncoding.asValue());
        }
    }


    sealed interface Blob {
        BlobMetadata metadata();

        // Have the POJOs encode some conversions ?
        InputStreamBlob asInputStream() throws IOException;

        BytesBlob asBytes() throws IOException;

        ByteSourceBlob asByteSource() throws IOException;
    }

    record BytesBlob(byte[] payload,  BlobMetadata metadata) implements Blob {
        public static BytesBlob of(byte[] payload) {
            return of(payload, BlobMetadata.empty());
        }

        public static BytesBlob of(String payload) {
            return of(payload.getBytes(StandardCharsets.UTF_8), BlobMetadata.empty());
        }

        public static BytesBlob of(byte[] payload,  BlobMetadata metadata) {
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

        @Override
        public boolean equals(Object other) {
            if (other instanceof BytesBlob(byte[] otherPayload, BlobMetadata otherMetadata)) {
                return Arrays.equals(payload, otherPayload)
                    && metadata.equals(otherMetadata);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(payload), metadata);
        }
    }

    record InputStreamBlob(InputStream payload,  BlobMetadata metadata) implements Blob {
        public static InputStreamBlob of(InputStream payload) {
            return of(payload, BlobMetadata.empty());
        }

        public static InputStreamBlob of(InputStream payload, BlobMetadata metadata) {
            return new InputStreamBlob(payload, metadata);
        }

        private static final int FILE_THRESHOLD = 100 * 1024;

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
            try (FileBackedOutputStream fileBackedOutputStream = new FileBackedOutputStream(FILE_THRESHOLD)) {
                payload.transferTo(fileBackedOutputStream);
                return new ByteSourceBlob(fileBackedOutputStream.asByteSource(), metadata);
            }
        }
    }

    record ByteSourceBlob(ByteSource payload, BlobMetadata metadata) implements Blob {
        public static ByteSourceBlob of(ByteSource payload) {
            return of(payload, BlobMetadata.empty());
        }

        public static ByteSourceBlob of(ByteSource payload, BlobMetadata metadata) {
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
     * Reads a InputStreamBlob based on its BucketName and its BlobId.
     *
     * @throws ObjectNotFoundException when the blobId or the bucket is not found
     * @throws ObjectStoreIOException when an unexpected IO error occurs
     */
    InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException;

    /**
     * Reads reactively a InputStreamBlob based on its BucketName and its BlobId.
     *
     * @return a Publisher containing the content and metadata of the blob or
     *  an ObjectNotFoundException in its error channel when the blobId or the bucket is not found
     *  or an ObjectStoreIOException when an unexpected IO error occurs
     */
    Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId);

    /**
     * Reads reactively a BytesBlob based on its BucketName and its BlobId.
     *
     * @return a Publisher containing the content and metadata of the blob or
     *  an ObjectNotFoundException in its error channel when the blobId or the bucket is not found
     *  or an ObjectStoreIOException when an unexpected IO error occurs
     */
    Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId);

    /**
     * Save the blob with the provided blob id, and overwrite the previous blob with the same id if it already exists.
     * The bucket is created if it does not already exist.
     * This operation should be atomic and isolated.
     * Two blobs having the same blobId must have the same content.
     *
     * @return an empty Publisher when the save succeeds,
     *  otherwise an ObjectStoreIOException in its error channel
     */
    Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob);

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
