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
import static org.apache.james.blob.api.DumbBlobStoreFixture.ELEVEN_KILOBYTES;
import static org.apache.james.blob.api.DumbBlobStoreFixture.OTHER_TEST_BLOB_ID;
import static org.apache.james.blob.api.DumbBlobStoreFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.DumbBlobStoreFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.DumbBlobStoreFixture.TEST_BUCKET_NAME;
import static org.apache.james.blob.api.DumbBlobStoreFixture.TWELVE_MEGABYTES;
import static org.apache.james.blob.api.DumbBlobStoreFixture.TWELVE_MEGABYTES_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.IOUtils;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public interface DeleteDumbBlobStoreContract  {

    DumbBlobStore testee();

    @Test
    default void deleteShouldNotThrowWhenBlobDoesNotExist() {
        DumbBlobStore store = testee();

        assertThatCode(() -> store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldNotThrowWhenBucketDoesNotExist() {
        DumbBlobStore store = testee();

        assertThatCode(() -> store.delete(BucketName.of("not_existing_bucket_name"), TEST_BLOB_ID).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldDeleteExistingBlobData() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID,  SHORT_BYTEARRAY).block();
        store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThatThrownBy(() -> store.read(TEST_BUCKET_NAME, TEST_BLOB_ID))
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void deleteShouldBeIdempotent() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        assertThatCode(() -> store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldNotDeleteOtherBlobs() {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        store.save(TEST_BUCKET_NAME, OTHER_TEST_BLOB_ID, ELEVEN_KILOBYTES).block();

        store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        InputStream read = store.read(TEST_BUCKET_NAME, OTHER_TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    default void deleteConcurrentlyShouldNotFail() throws Exception {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void deleteShouldThrowWhenNullBucketName() {
        DumbBlobStore store = testee();
        assertThatThrownBy(() -> store.delete(null, TEST_BLOB_ID).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteShouldNotDeleteFromOtherBucket() {
        DumbBlobStore store = testee();

        store.save(CUSTOM_BUCKET_NAME, OTHER_TEST_BLOB_ID, "custom").block();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();

        store.delete(CUSTOM_BUCKET_NAME, OTHER_TEST_BLOB_ID).block();

        InputStream read = store.read(TEST_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void deleteShouldNotDeleteFromOtherBucketWhenSameBlobId() {
        DumbBlobStore store = testee();

        store.save(CUSTOM_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();
        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY).block();

        store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID).block();

        InputStream read = store.read(CUSTOM_BUCKET_NAME, TEST_BLOB_ID);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void readShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() throws Exception {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES).block();

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

                store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID).block();
            }))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(3));
    }

    @Test
    default void readBytesShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() throws Exception {
        DumbBlobStore store = testee();

        store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> {
                try {
                    byte[] read = store.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID).block();
                    String string = IOUtils.toString(read, StandardCharsets.UTF_8.displayName());
                    if (!string.equals(TWELVE_MEGABYTES_STRING)) {
                        throw new RuntimeException("Should not read partial blob when an other thread is deleting it. Size : " + string.length());
                    }
                } catch (ObjectNotFoundException exception) {
                    // normal behavior here
                }

                store.delete(TEST_BUCKET_NAME, TEST_BLOB_ID).block();
            }))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(3));
    }

    @Test
    default void mixingSaveReadAndDeleteShouldReturnConsistentState() throws ExecutionException, InterruptedException {
        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (thread, iteration) -> testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, TWELVE_MEGABYTES),
                (thread, iteration) -> testee().delete(TEST_BUCKET_NAME, TEST_BLOB_ID),
                (thread, iteration) -> checkConcurrentMixedOperation()
            )
            .threadCount(10)
            .operationCount(100)
            .runSuccessfullyWithin(Duration.ofMinutes(2));
    }

    default Mono<Void> checkConcurrentMixedOperation() {
        return Mono
            .fromCallable(() ->
                testee().read(TEST_BUCKET_NAME, TEST_BLOB_ID))
            .doOnNext(inputStream -> assertThat(inputStream).hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES)))
            .doOnError(throwable -> assertThat(throwable).isInstanceOf(ObjectNotFoundException.class))
            .onErrorResume(throwable -> Mono.empty())
            .then();
    }
}
