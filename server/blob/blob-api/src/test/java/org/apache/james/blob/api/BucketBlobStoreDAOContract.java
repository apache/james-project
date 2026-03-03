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

import static org.apache.james.blob.api.BlobStoreDAOFixture.CUSTOM_BUCKET_NAME;
import static org.apache.james.blob.api.BlobStoreDAOFixture.OTHER_TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_STRING;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BucketBlobStoreDAOContract {

    BlobStoreDAO testee();

    @Test
    default void deleteBucketShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.deleteBucket(null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteBucketShouldDeleteExistingBucketWithItsData() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.deleteBucket(TEST_BUCKET_NAME)).block();

        assertThatThrownBy(() -> store.readBlob(TEST_BUCKET_NAME, TEST_BLOB_ID).payload().read())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void deleteBucketShouldBeIdempotent() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.deleteBucket(TEST_BUCKET_NAME)).block();

        assertThatCode(() -> Mono.from(store.deleteBucket(TEST_BUCKET_NAME)).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void saveBytesShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.saveBlob(null, TEST_BLOB_ID, SHORT_BYTEARRAY.asInputStream())).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveStringShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.saveBlob(null, TEST_BLOB_ID, BlobStoreDAO.BytesBlob.of(SHORT_STRING))).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveInputStreamShouldThrowWhenNullBucketName() throws Exception {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.saveBlob(null, TEST_BLOB_ID, SHORT_BYTEARRAY.asInputStream())).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        assertThatThrownBy(() -> store.readBlob(null, TEST_BLOB_ID))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readBytesShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        assertThatThrownBy(() -> Mono.from(store.readBytesBlob(null, TEST_BLOB_ID)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void readStreamShouldThrowWhenBucketDoesNotExist() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        assertThatThrownBy(() -> store.readBlob(CUSTOM_BUCKET_NAME, TEST_BLOB_ID).payload().read())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void readBytesShouldThrowWhenBucketDoesNotExistWithBigData() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThatThrownBy(() -> Mono.from(store.readBytesBlob(CUSTOM_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void shouldBeAbleToSaveDataInMultipleBuckets() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.saveBlob(CUSTOM_BUCKET_NAME, OTHER_TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        byte[] bytesDefault = Mono.from(store.readBytesBlob(TEST_BUCKET_NAME, TEST_BLOB_ID)).block().payload();
        byte[] bytesCustom = Mono.from(store.readBytesBlob(CUSTOM_BUCKET_NAME, OTHER_TEST_BLOB_ID)).block().payload();

        assertThat(bytesDefault).isEqualTo(bytesCustom);
    }

    @Test
    default void saveConcurrentlyWithNonPreExistingBucketShouldNotFail() throws Exception {
        BlobStoreDAO store = testee();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) ->
                Mono.from(store.saveBlob(
                    TEST_BUCKET_NAME,
                    new TestBlobId("id-" + threadNumber + step),
                    BlobStoreDAO.BytesBlob.of(SHORT_STRING + threadNumber + step))).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void deleteBucketConcurrentlyShouldNotFail() throws Exception {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        ConcurrentTestRunner.builder()
            .reactorOperation(((threadNumber, step) -> store.deleteBucket(TEST_BUCKET_NAME)))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void listBucketsShouldReturnEmptyWhenNone() {
        BlobStoreDAO store = testee();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .isEmpty();
    }

    @Test
    default void listBucketsShouldReturnBucketInUse() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .containsOnly(TEST_BUCKET_NAME);
    }

    @Test
    default void listBucketsShouldNotReturnDuplicates() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .hasSize(1);
    }

    @Test
    default void listBucketsShouldReturnAllBucketsInUse() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.saveBlob(CUSTOM_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .containsOnly(TEST_BUCKET_NAME, CUSTOM_BUCKET_NAME);
    }

    @Test
    default void listBucketsShouldNotReturnDeletedBuckets() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        Mono.from(store.deleteBucket(TEST_BUCKET_NAME)).block();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .isEmpty();
    }

    @Test
    default void listBucketsShouldReturnBucketsWithNoBlob() {
        BlobStoreDAO store = testee();

        Mono.from(store.saveBlob(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .containsOnly(TEST_BUCKET_NAME);
    }
}
