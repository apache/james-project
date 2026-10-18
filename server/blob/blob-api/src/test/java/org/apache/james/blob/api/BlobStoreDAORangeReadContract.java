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

import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public interface BlobStoreDAORangeReadContract {
    BlobStoreDAO testee();

    default byte[] sampleContent() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append(String.format("%010d", i));
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    default BlobId createSampleBlob() {
        BlobId blobId = new TestBlobId.Factory().of(UUID.randomUUID().toString());
        BlobStoreDAO.BlobMetadata metadata = BlobStoreDAO.BlobMetadata.empty()
            .withMetadata(new BlobStoreDAO.BlobMetadataName("test-metadata"), new BlobStoreDAO.BlobMetadataValue("value"));
        Mono.from(testee().save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.BytesBlob.of(sampleContent(), metadata))).block();
        return blobId;
    }

    @Test
    default void readRangeShouldReturnAnInclusivePrefixAndPreserveMetadata() throws IOException {
        BlobStoreDAO.Blob slice = testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 0, 99).block();

        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(sampleContent(), 0, 100));
        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.metadata().get(new BlobStoreDAO.BlobMetadataName("test-metadata")))
            .contains(new BlobStoreDAO.BlobMetadataValue("value"));
    }

    @Test
    default void readRangeShouldReturnAnInclusiveMiddleRange() throws IOException {
        BlobStoreDAO.Blob slice = testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 200, 399).block();

        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(sampleContent(), 200, 400));
    }

    @Test
    default void readRangeShouldRejectNegativeStart() {
        assertThatThrownBy(() -> testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), -1, -1).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void readRangeShouldReadThroughTheEndWhenEndIsMinusOne() throws IOException {
        BlobStoreDAO.Blob slice = testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 900, -1).block();

        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(sampleContent(), 900, 1000));
    }

    @Test
    default void readRangeShouldFailWhenStartIsBeyondObjectSize() {
        assertThatThrownBy(() -> testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 2000, 3000).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void readRangeShouldRejectInvalidRange() {
        assertThatThrownBy(() -> testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 300, 299).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void readRangeShouldReplaceStoredTotalObjectSize() throws IOException {
        BlobId blobId = new TestBlobId.Factory().of(UUID.randomUUID().toString());
        BlobStoreDAO.BlobMetadata metadata = BlobStoreDAO.BlobMetadata.empty()
            .withMetadata(BlobStoreDAO.TOTAL_OBJECT_SIZE, new BlobStoreDAO.BlobMetadataValue("9999"))
            .withMetadata(new BlobStoreDAO.BlobMetadataName("custom"), new BlobStoreDAO.BlobMetadataValue("retained"));
        Mono.from(testee().save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.BytesBlob.of(sampleContent(), metadata))).block();

        BlobStoreDAO.Blob slice = testee().readRange(TEST_BUCKET_NAME, blobId, 0, 9).block();

        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(sampleContent(), 0, 10));
        assertThat(slice.metadata().get(new BlobStoreDAO.BlobMetadataName("custom")))
            .contains(new BlobStoreDAO.BlobMetadataValue("retained"));
    }

    @Test
    default void readRangeShouldReturnSingleByte() throws IOException {
        BlobStoreDAO.Blob slice = testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 25, 25).block();

        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(sampleContent(), 25, 26));
    }

    @Test
    default void readRangeShouldCapEndAtObjectSize() throws IOException {
        BlobStoreDAO.Blob slice = testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 900, Long.MAX_VALUE).block();

        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(sampleContent(), 900, 1000));
    }

    @Test
    default void readRangeShouldRejectNegativeStartWithBoundedEnd() {
        assertThatThrownBy(() -> testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), -1, 0).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void readRangeShouldFailForEmptyObject() {
        BlobId blobId = new TestBlobId.Factory().of(UUID.randomUUID().toString());
        Mono.from(testee().save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.BytesBlob.of(new byte[0]))).block();

        assertThatThrownBy(() -> testee().readRange(TEST_BUCKET_NAME, blobId, 0, 0).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void readRangeShouldFailForMissingObject() {
        createSampleBlob();
        BlobId blobId = new TestBlobId.Factory().of(UUID.randomUUID().toString());

        assertThatThrownBy(() -> testee().readRange(TEST_BUCKET_NAME, blobId, 0, 9).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void readRangeShouldReturnWholeObject() throws IOException {
        BlobStoreDAO.Blob slice = testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 0, 999).block();

        assertThat(slice.asBytes().payload()).isEqualTo(sampleContent());
    }

    @Test
    default void readRangeShouldFailWhenStartEqualsObjectSize() {
        assertThatThrownBy(() -> testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 1000, 1000).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void readRangeShouldRejectNegativeEndOtherThanMinusOne() {
        assertThatThrownBy(() -> testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), 0, -2).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void readRangeShouldRejectMinimumStart() {
        assertThatThrownBy(() -> testee().readRange(TEST_BUCKET_NAME, createSampleBlob(), Long.MIN_VALUE, -1).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void readRangeShouldFailForMissingBucket() {
        BlobId blobId = createSampleBlob();

        assertThatThrownBy(() -> testee().readRange(BucketName.of("missing-" + UUID.randomUUID()), blobId, 0, 9).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }
}
