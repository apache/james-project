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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

public interface BucketBlobStoreContract {
    String SHORT_STRING = "toto";
    byte[] SHORT_BYTEARRAY = SHORT_STRING.getBytes(StandardCharsets.UTF_8);
    BucketName CUSTOM = BucketName.of("custom");

    BlobStore testee();

    BlobId.Factory blobIdFactory();

    @Test
    default void deleteBucketShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        assertThatThrownBy(() -> store.deleteBucket(null).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteBucketShouldDeleteExistingBucketWithItsData() {
        BlobStore store = testee();

        BlobId blobId = store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST).block();
        store.deleteBucket(CUSTOM).block();

        assertThatThrownBy(() -> store.read(CUSTOM, blobId).read())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void deleteBucketShouldBeIdempotent() {
        BlobStore store = testee();

        store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST).block();
        store.deleteBucket(CUSTOM).block();

        assertThatCode(() -> store.deleteBucket(CUSTOM).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void saveBytesShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        assertThatThrownBy(() -> store.save(null, SHORT_BYTEARRAY, LOW_COST).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveStringShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        assertThatThrownBy(() -> store.save(null, SHORT_STRING, LOW_COST).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveInputStreamShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        assertThatThrownBy(() -> store.save(null, new ByteArrayInputStream(SHORT_BYTEARRAY), LOW_COST).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        BlobId blobId = store.save(BucketName.DEFAULT, SHORT_BYTEARRAY, LOW_COST).block();
        assertThatThrownBy(() -> store.read(null, blobId))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readBytesStreamShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        BlobId blobId = store.save(BucketName.DEFAULT, SHORT_BYTEARRAY, LOW_COST).block();
        assertThatThrownBy(() -> store.readBytes(null, blobId).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readStringShouldThrowWhenBucketDoesNotExist() {
        BlobStore store = testee();

        BlobId blobId = store.save(BucketName.DEFAULT, SHORT_BYTEARRAY, LOW_COST).block();
        assertThatThrownBy(() -> store.read(CUSTOM, blobId).read())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void readBytesStreamShouldThrowWhenBucketDoesNotExist() {
        BlobStore store = testee();

        BlobId blobId = store.save(BucketName.DEFAULT, SHORT_BYTEARRAY, LOW_COST).block();
        assertThatThrownBy(() -> store.readBytes(CUSTOM, blobId).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void shouldBeAbleToSaveDataInMultipleBuckets() {
        BlobStore store = testee();

        BlobId blobIdDefault = store.save(BucketName.DEFAULT, SHORT_BYTEARRAY, LOW_COST).block();
        BlobId blobIdCustom = store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST).block();

        byte[] bytesDefault = store.readBytes(BucketName.DEFAULT, blobIdDefault).block();
        byte[] bytesCustom = store.readBytes(CUSTOM, blobIdCustom).block();

        assertThat(bytesDefault).isEqualTo(bytesCustom);
    }

    @Test
    default void saveConcurrentlyWithNonPreExistingBucketShouldNotFail() throws Exception {
        BlobStore store = testee();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> store.save(CUSTOM, SHORT_STRING + threadNumber + step, LOW_COST).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void deleteBucketConcurrentlyShouldNotFail() throws Exception {
        BlobStore store = testee();

        store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> store.deleteBucket(CUSTOM).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }
}
