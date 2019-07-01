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
        assertThatThrownBy(() -> testee().deleteBucket(null).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteBucketShouldDeleteExistingBucketWithItsData() {
        BlobId blobId = testee().save(CUSTOM, SHORT_BYTEARRAY).block();
        testee().deleteBucket(CUSTOM).block();

        assertThatThrownBy(() -> testee().read(CUSTOM, blobId))
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void deleteBucketShouldBeIdempotent(){
        testee().save(CUSTOM, SHORT_BYTEARRAY).block();
        testee().deleteBucket(CUSTOM).block();

        assertThatCode(() -> testee().deleteBucket(CUSTOM).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void saveBytesShouldThrowWhenNullBucketName() {
        assertThatThrownBy(() -> testee().save(null, SHORT_BYTEARRAY).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveStringShouldThrowWhenNullBucketName() {
        assertThatThrownBy(() -> testee().save(null, SHORT_STRING).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveInputStreamShouldThrowWhenNullBucketName() {
        assertThatThrownBy(() -> testee().save(null, new ByteArrayInputStream(SHORT_BYTEARRAY)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readShouldThrowWhenNullBucketName() {
        BlobId blobId = testee().save(BucketName.DEFAULT, SHORT_BYTEARRAY).block();
        assertThatThrownBy(() -> testee().read(null, blobId))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readBytesStreamShouldThrowWhenNullBucketName() {
        BlobId blobId = testee().save(BucketName.DEFAULT, SHORT_BYTEARRAY).block();
        assertThatThrownBy(() -> testee().readBytes(null, blobId).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readStringShouldThrowWhenBucketDoesNotExist() {
        BlobId blobId = testee().save(BucketName.DEFAULT, SHORT_BYTEARRAY).block();
        assertThatThrownBy(() -> testee().read(CUSTOM, blobId))
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void readBytesStreamShouldThrowWhenBucketDoesNotExist() {
        BlobId blobId = testee().save(BucketName.DEFAULT, SHORT_BYTEARRAY).block();
        assertThatThrownBy(() -> testee().readBytes(CUSTOM, blobId).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void shouldBeAbleToSaveDataInMultipleBuckets() {
        BlobId blobIdDefault = testee().save(BucketName.DEFAULT, SHORT_BYTEARRAY).block();
        BlobId blobIdCustom = testee().save(CUSTOM, SHORT_BYTEARRAY).block();

        byte[] bytesDefault = testee().readBytes(BucketName.DEFAULT, blobIdDefault).block();
        byte[] bytesCustom = testee().readBytes(CUSTOM, blobIdCustom).block();

        assertThat(bytesDefault).isEqualTo(bytesCustom);
    }

    @Test
    default void saveConcurrentlyWithNonPreExistingBucketShouldNotFail() throws Exception {
        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> testee().save(CUSTOM, SHORT_STRING + threadNumber + step).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void deleteBucketConcurrentlyShouldNotFail() throws Exception {
        testee().save(CUSTOM, SHORT_BYTEARRAY).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> testee().deleteBucket(CUSTOM).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }
}
