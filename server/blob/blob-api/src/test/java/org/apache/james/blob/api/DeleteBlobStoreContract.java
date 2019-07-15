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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.commons.io.IOUtils;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

public interface DeleteBlobStoreContract {

    String SHORT_STRING = "toto";
    byte[] SHORT_BYTEARRAY = SHORT_STRING.getBytes(StandardCharsets.UTF_8);
    byte[] ELEVEN_KILOBYTES = Strings.repeat("0123456789\n", 1000).getBytes(StandardCharsets.UTF_8);
    String TWELVE_MEGABYTES_STRING = Strings.repeat("0123456789\r\n", 1024 * 1024);
    byte[] TWELVE_MEGABYTES = TWELVE_MEGABYTES_STRING.getBytes(StandardCharsets.UTF_8);
    BucketName CUSTOM = BucketName.of("custom");

    BlobStore testee();

    BlobId.Factory blobIdFactory();

    @Test
    default void deleteShouldNotThrowWhenBlobDoesNotExist() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        assertThatCode(() -> testee().delete(defaultBucketName, blobIdFactory().randomId()).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldDeleteExistingBlobData() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, SHORT_BYTEARRAY).block();
        testee().delete(defaultBucketName, blobId).block();

        assertThatThrownBy(() -> testee().read(defaultBucketName, blobId))
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void deleteShouldBeIdempotent() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, SHORT_BYTEARRAY).block();
        testee().delete(defaultBucketName, blobId).block();

        assertThatCode(() -> testee().delete(defaultBucketName, blobId).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldNotDeleteOtherBlobs() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobIdToDelete = testee().save(defaultBucketName, SHORT_BYTEARRAY).block();
        BlobId otherBlobId = testee().save(defaultBucketName, ELEVEN_KILOBYTES).block();

        testee().delete(defaultBucketName, blobIdToDelete).block();

        InputStream read = testee().read(defaultBucketName, otherBlobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    default void deleteConcurrentlyShouldNotFail() throws Exception {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, TWELVE_MEGABYTES).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> testee().delete(defaultBucketName, blobId).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void deleteShouldThrowWhenNullBucketName() {
        assertThatThrownBy(() -> testee().delete(null, blobIdFactory().randomId()).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteShouldNotDeleteFromOtherBucket() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId customBlobId = testee().save(CUSTOM, "custom_string").block();
        BlobId defaultBlobId = testee().save(defaultBucketName, SHORT_BYTEARRAY).block();

        testee().delete(CUSTOM, customBlobId).block();

        InputStream read = testee().read(defaultBucketName, defaultBlobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void deleteShouldNotDeleteFromOtherBucketWhenSameBlobId() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        testee().save(CUSTOM, SHORT_BYTEARRAY).block();
        BlobId blobId = testee().save(defaultBucketName, SHORT_BYTEARRAY).block();

        testee().delete(defaultBucketName, blobId).block();

        InputStream read = testee().read(CUSTOM, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void readShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() throws Exception {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, TWELVE_MEGABYTES).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> {
                try {
                    InputStream read = testee().read(defaultBucketName, blobId);

                    if (!IOUtils.toString(read, StandardCharsets.UTF_8).equals(TWELVE_MEGABYTES_STRING)) {
                        throw new RuntimeException("Should not read partial blob when an other thread is deleting it");
                    }
                } catch (ObjectStoreException exception) {
                    // normal behavior here
                }

                testee().delete(defaultBucketName, blobId).block();
            }))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }
}
