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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BucketBlobStoreContract {
    String SHORT_STRING = "toto";
    byte[] SHORT_BYTEARRAY = SHORT_STRING.getBytes(StandardCharsets.UTF_8);
    Bucket CUSTOM = BucketName.of("custom").asBucket();

    BlobStore testee();

    BlobId.Factory blobIdFactory();

    @Test
    default void deleteBucketShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        assertThatThrownBy(() -> Mono.from(store.deleteBucket(null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteBucketShouldDeleteExistingBucketWithItsData() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST)).block();
        Mono.from(store.deleteBucket(CUSTOM)).block();

        assertThatThrownBy(() -> store.read(CUSTOM, blobId).read())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void deleteBucketShouldBeIdempotent() {
        BlobStore store = testee();

        Mono.from(store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST)).block();
        Mono.from(store.deleteBucket(CUSTOM)).block();

        assertThatCode(() -> Mono.from(store.deleteBucket(CUSTOM)).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void saveBytesShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(null, SHORT_BYTEARRAY, LOW_COST)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveStringShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(null, SHORT_STRING, LOW_COST)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveInputStreamShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(null, new ByteArrayInputStream(SHORT_BYTEARRAY), LOW_COST)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(BucketName.DEFAULT.asBucket(), SHORT_BYTEARRAY, LOW_COST)).block();
        assertThatThrownBy(() -> store.read(null, blobId))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readBytesStreamShouldThrowWhenNullBucketName() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(BucketName.DEFAULT.asBucket(), SHORT_BYTEARRAY, LOW_COST)).block();
        assertThatThrownBy(() -> Mono.from(store.readBytes(null, blobId)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readStringShouldThrowWhenBucketDoesNotExist() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(BucketName.DEFAULT.asBucket(), SHORT_BYTEARRAY, LOW_COST)).block();
        assertThatThrownBy(() -> store.read(CUSTOM, blobId).read())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void readBytesStreamShouldThrowWhenBucketDoesNotExist() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(BucketName.DEFAULT.asBucket(), SHORT_BYTEARRAY, LOW_COST)).block();
        assertThatThrownBy(() -> Mono.from(store.readBytes(CUSTOM, blobId)).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void shouldBeAbleToSaveDataInMultipleBuckets() {
        BlobStore store = testee();

        BlobId blobIdDefault = Mono.from(store.save(BucketName.DEFAULT.asBucket(), SHORT_BYTEARRAY, LOW_COST)).block();
        BlobId blobIdCustom = Mono.from(store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST)).block();

        byte[] bytesDefault = Mono.from(store.readBytes(BucketName.DEFAULT.asBucket(), blobIdDefault)).block();
        byte[] bytesCustom = Mono.from(store.readBytes(CUSTOM, blobIdCustom)).block();

        assertThat(bytesDefault).isEqualTo(bytesCustom);
    }

    @Test
    default void saveConcurrentlyWithNonPreExistingBucketShouldNotFail() throws Exception {
        BlobStore store = testee();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> Mono.from(store.save(CUSTOM, SHORT_STRING + threadNumber + step, LOW_COST)).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void deleteBucketConcurrentlyShouldNotFail() throws Exception {
        BlobStore store = testee();

        Mono.from(store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST)).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> Mono.from(store.deleteBucket(CUSTOM)).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void listBucketsShouldReturnDefaultBucket() {
        BlobStore store = testee();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .containsOnly(store.getDefaultBucketName());
    }

    @Test
    default void listBucketsShouldReturnACustomBucket() {
        BlobStore store = testee();

        Mono.from(store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST)).block();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .containsOnly(store.getDefaultBucketName(), CUSTOM.bucketName());
    }

    @Test
    default void listBucketsShouldNotReturnADeletedBucket() {
        BlobStore store = testee();

        Mono.from(store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST)).block();
        Mono.from(store.deleteBucket(CUSTOM)).block();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .containsOnly(store.getDefaultBucketName());
    }
}
