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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

public interface BlobStoreContract {

    String SHORT_STRING = "toto";
    byte[] EMPTY_BYTEARRAY = {};
    byte[] SHORT_BYTEARRAY = SHORT_STRING.getBytes(StandardCharsets.UTF_8);
    byte[] ELEVEN_KILOBYTES = Strings.repeat("0123456789\n", 1000).getBytes(StandardCharsets.UTF_8);
    byte[] TWELVE_MEGABYTES = Strings.repeat("0123456789\r\n", 1024 * 1024).getBytes(StandardCharsets.UTF_8);

    BlobStore testee();

    BlobId.Factory blobIdFactory();

    @Test
    default void saveShouldThrowWhenNullData() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        assertThatThrownBy(() -> testee().save(defaultBucketName, (byte[]) null).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldThrowWhenNullString() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        assertThatThrownBy(() -> testee().save(defaultBucketName, (String) null).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldThrowWhenNullInputStream() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        assertThatThrownBy(() -> testee().save(defaultBucketName, (InputStream) null).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldSaveEmptyData() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, EMPTY_BYTEARRAY).block();

        byte[] bytes = testee().readBytes(defaultBucketName, blobId).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyString() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, new String()).block();

        byte[] bytes = testee().readBytes(defaultBucketName, blobId).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyInputStream() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, new ByteArrayInputStream(EMPTY_BYTEARRAY)).block();

        byte[] bytes = testee().readBytes(defaultBucketName, blobId).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldReturnBlobId() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, SHORT_BYTEARRAY).block();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void saveShouldReturnBlobIdOfString() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, SHORT_STRING).block();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void saveShouldReturnBlobIdOfInputStream() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, new ByteArrayInputStream(SHORT_BYTEARRAY)).block();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void readBytesShouldThrowWhenNoExisting() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        assertThatThrownBy(() -> testee().readBytes(defaultBucketName, blobIdFactory().from("unknown")).block())
            .isExactlyInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void readBytesShouldReturnSavedData() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, SHORT_BYTEARRAY).block();

        byte[] bytes = testee().readBytes(defaultBucketName, blobId).block();

        assertThat(bytes).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    default void readBytesShouldReturnLongSavedData() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, ELEVEN_KILOBYTES).block();

        byte[] bytes = testee().readBytes(defaultBucketName, blobId).block();

        assertThat(bytes).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    default void readBytesShouldReturnBigSavedData() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, TWELVE_MEGABYTES).block();

        byte[] bytes = testee().readBytes(defaultBucketName, blobId).block();

        assertThat(bytes).isEqualTo(TWELVE_MEGABYTES);
    }

    @Test
    default void readShouldThrowWhenNoExistingStream() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        assertThatThrownBy(() -> testee().read(defaultBucketName, blobIdFactory().from("unknown")))
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void readShouldReturnSavedData() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, SHORT_BYTEARRAY).block();

        InputStream read = testee().read(defaultBucketName, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void readShouldReturnLongSavedData() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        BlobId blobId = testee().save(defaultBucketName, ELEVEN_KILOBYTES).block();

        InputStream read = testee().read(defaultBucketName, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    default void readShouldReturnBigSavedData() {
        BucketName defaultBucketName = testee().getDefaultBucketName();

        // 12 MB of text
        BlobId blobId = testee().save(defaultBucketName, TWELVE_MEGABYTES).block();

        InputStream read = testee().read(defaultBucketName, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES));
    }
}
