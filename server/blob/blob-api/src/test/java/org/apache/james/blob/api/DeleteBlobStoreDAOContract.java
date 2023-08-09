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
import static org.apache.james.blob.api.BlobStoreDAOFixture.ELEVEN_KILOBYTES;
import static org.apache.james.blob.api.BlobStoreDAOFixture.OTHER_TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TWELVE_MEGABYTES;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TWELVE_MEGABYTES_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.IOUtils;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public interface DeleteBlobStoreDAOContract {

    BlobStoreDAO testee();

    @Test
    default void deleteShouldNotThrowWhenBlobDoesNotExist() {
        BlobStoreDAO store = testee();

        assertThatCode(() -> Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldNotThrowWhenBucketDoesNotExist() {
        BlobStoreDAO store = testee();

        assertThatCode(() -> Mono.from(store.delete(BucketName.of("not-existing-bucket-name"), TEST_BLOB_ID)).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldDeleteExistingBlobData() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID,  SHORT_BYTEARRAY)).block();
        Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThatThrownBy(() -> store.read(TEST_BUCKET_NAME, TEST_BLOB_ID).read())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void deleteShouldBeIdempotent() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThatCode(() -> Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldNotDeleteOtherBlobs() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.save(TEST_BUCKET_NAME, OTHER_TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        InputStream read = store.read(TEST_BUCKET_NAME, OTHER_TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    default void deleteSeveralShouldDeleteAll() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.save(TEST_BUCKET_NAME, OTHER_TEST_BLOB_ID, ELEVEN_KILOBYTES)).block();

        Mono.from(store.delete(TEST_BUCKET_NAME, ImmutableList.of(TEST_BLOB_ID, OTHER_TEST_BLOB_ID))).block();

        SoftAssertions.assertSoftly(soft -> {
            soft.assertThatThrownBy(() -> store.read(TEST_BUCKET_NAME, TEST_BLOB_ID).read())
                .isInstanceOf(ObjectStoreException.class);
            soft.assertThatThrownBy(() -> store.read(TEST_BUCKET_NAME, OTHER_TEST_BLOB_ID).read())
                .isInstanceOf(ObjectStoreException.class);
        });
    }

    @Test
    default void deleteConcurrentlyShouldNotFail() throws Exception {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES)).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void deleteShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();
        assertThatThrownBy(() -> Mono.from(store.delete(null, TEST_BLOB_ID)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteShouldNotDeleteFromOtherBucket() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(CUSTOM_BUCKET_NAME, OTHER_TEST_BLOB_ID, "custom")).block();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        Mono.from(store.delete(CUSTOM_BUCKET_NAME, OTHER_TEST_BLOB_ID)).block();

        InputStream read = store.read(TEST_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void deleteShouldNotDeleteFromOtherBucketWhenSameBlobId() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(CUSTOM_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        InputStream read = store.read(CUSTOM_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void readShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() throws Exception {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES)).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> {
                try {
                    InputStream read = store.read(TEST_BUCKET_NAME, TEST_BLOB_ID);

                    String string = IOUtils.toString(read, StandardCharsets.UTF_8);
                    if (!string.equals(TWELVE_MEGABYTES_STRING)) {
                        throw new RuntimeException("Should not read partial blob when an other thread is deleting it. Size : " + string.length());
                    }
                } catch (ObjectStoreException exception) {
                    // normal behavior here
                }

                Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();
            }))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(3));
    }

    @Test
    default void readBytesShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() throws Exception {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES)).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> {
                try {
                    byte[] read = Mono.from(store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();
                    String string = IOUtils.toString(read, StandardCharsets.UTF_8.displayName());
                    if (!string.equals(TWELVE_MEGABYTES_STRING)) {
                        throw new RuntimeException("Should not read partial blob when an other thread is deleting it. Size : " + string.length());
                    }
                } catch (ObjectNotFoundException exception) {
                    // normal behavior here
                }

                Mono.from(store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();
            }))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(3));
    }

    @Test
    default void mixingSaveReadAndDeleteShouldReturnConsistentState() throws ExecutionException, InterruptedException {
        BlobStoreDAO store = testee();
        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES)).block();
        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (thread, iteration) -> store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES),
                (thread, iteration) -> store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID),
                (thread, iteration) -> checkConcurrentMixedOperation()
            )
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(2));
    }

    default Mono<Void> checkConcurrentMixedOperation() {
        return
            Mono.from(testee().readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID))
                //assertj is very cpu-intensive, let's compute the assertion only when arrays are different
                .filter(bytes -> !Arrays.equals(bytes, TWELVE_MEGABYTES))
                .doOnNext(bytes -> assertThat(bytes).isEqualTo(TWELVE_MEGABYTES))
                .onErrorResume(ObjectNotFoundException.class, throwable -> Mono.empty())
                .then();
    }

}
