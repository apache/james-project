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

import static org.apache.james.blob.api.DumbBlobStoreFixture.CUSTOM_BUCKET_NAME;
import static org.apache.james.blob.api.DumbBlobStoreFixture.OTHER_TEST_BLOB_ID;
import static org.apache.james.blob.api.DumbBlobStoreFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.DumbBlobStoreFixture.SHORT_STRING;
import static org.apache.james.blob.api.DumbBlobStoreFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.DumbBlobStoreFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

public interface BucketDumbBlobStoreContract {

    DumbBlobStore testee();

    @Test
    default void deleteBucketShouldThrowWhenNullBucketName() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.deleteBucket(null).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteBucketShouldDeleteExistingBucketWithItsData() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        store.deleteBucket(TEST_BUCKET_NAME).block();

        assertThatThrownBy(() -> store.read(TEST_BUCKET_NAME, TEST_BLOB_ID))
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void deleteBucketShouldBeIdempotent() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        store.deleteBucket(TEST_BUCKET_NAME).block();

        assertThatCode(() -> store.deleteBucket(TEST_BUCKET_NAME).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void saveBytesShouldThrowWhenNullBucketName() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.save(null, TEST_BLOB_ID, SHORT_BYTEARRAY).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveStringShouldThrowWhenNullBucketName() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.save(null, TEST_BLOB_ID, SHORT_STRING).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveInputStreamShouldThrowWhenNullBucketName() {
        DumbBlobStore store = testee();

        assertThatThrownBy(() -> store.save(null, TEST_BLOB_ID, new ByteArrayInputStream(SHORT_BYTEARRAY)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readShouldThrowWhenNullBucketName() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        assertThatThrownBy(() -> store.read(null, TEST_BLOB_ID))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readBytesShouldThrowWhenNullBucketName() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        assertThatThrownBy(() -> store.readBytes(null, TEST_BLOB_ID).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readStreamShouldThrowWhenBucketDoesNotExist() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        assertThatThrownBy(() -> store.read(CUSTOM_BUCKET_NAME, TEST_BLOB_ID))
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void readBytesShouldThrowWhenBucketDoesNotExist() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();

        assertThatThrownBy(() -> store.readBytes(CUSTOM_BUCKET_NAME, TEST_BLOB_ID).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void shouldBeAbleToSaveDataInMultipleBuckets() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        store.save(CUSTOM_BUCKET_NAME, OTHER_TEST_BLOB_ID, SHORT_BYTEARRAY).block();

        byte[] bytesDefault = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();
        byte[] bytesCustom = store.readBytes(CUSTOM_BUCKET_NAME, OTHER_TEST_BLOB_ID).block();

        assertThat(bytesDefault).isEqualTo(bytesCustom);
    }

    @Test
    default void saveConcurrentlyWithNonPreExistingBucketShouldNotFail() throws Exception {
        DumbBlobStore store = testee();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) ->
                store.save(
                    TEST_BUCKET_NAME,
                    new TestBlobId("id-" + threadNumber + step),
                    SHORT_STRING + threadNumber + step).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void deleteBucketConcurrentlyShouldNotFail() throws Exception {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> store.deleteBucket(TEST_BUCKET_NAME).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }
}
