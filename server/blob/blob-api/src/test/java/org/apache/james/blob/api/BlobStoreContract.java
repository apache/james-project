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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

public interface BlobStoreContract extends DeleteBlobStoreContract, BucketBlobStoreContract {

    String SHORT_STRING = "toto";
    byte[] EMPTY_BYTEARRAY = {};
    byte[] SHORT_BYTEARRAY = SHORT_STRING.getBytes(StandardCharsets.UTF_8);
    byte[] ELEVEN_KILOBYTES = Strings.repeat("0123456789\n", 1000).getBytes(StandardCharsets.UTF_8);
    byte[] TWELVE_MEGABYTES = Strings.repeat("0123456789\r\n", 1024 * 1024).getBytes(StandardCharsets.UTF_8);

    BlobStore testee();

    BlobId.Factory blobIdFactory();

    @Test
    default void saveShouldThrowWhenNullData() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> store.save(defaultBucketName, (byte[]) null, LOW_COST).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldThrowWhenNullString() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> store.save(defaultBucketName, (String) null, LOW_COST).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldThrowWhenNullInputStream() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> store.save(defaultBucketName, (InputStream) null, LOW_COST).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveShouldSaveEmptyData() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, EMPTY_BYTEARRAY, LOW_COST).block();

        byte[] bytes = store.readBytes(defaultBucketName, blobId).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyString() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, new String(), LOW_COST).block();

        byte[] bytes = store.readBytes(defaultBucketName, blobId).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldSaveEmptyInputStream() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, new ByteArrayInputStream(EMPTY_BYTEARRAY), LOW_COST).block();

        byte[] bytes = store.readBytes(defaultBucketName, blobId).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    default void saveShouldReturnBlobId() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, SHORT_BYTEARRAY, LOW_COST).block();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void saveShouldReturnBlobIdOfString() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, SHORT_STRING, LOW_COST).block();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void saveShouldReturnBlobIdOfInputStream() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, new ByteArrayInputStream(SHORT_BYTEARRAY), LOW_COST).block();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void readBytesShouldThrowWhenNoExisting() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> store.readBytes(defaultBucketName, blobIdFactory().from("unknown")).block())
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void readBytesShouldReturnSavedData() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, SHORT_BYTEARRAY, LOW_COST).block();

        byte[] bytes = store.readBytes(defaultBucketName, blobId).block();

        assertThat(bytes).isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    default void readBytesShouldReturnLongSavedData() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, ELEVEN_KILOBYTES, LOW_COST).block();

        byte[] bytes = store.readBytes(defaultBucketName, blobId).block();

        assertThat(bytes).isEqualTo(ELEVEN_KILOBYTES);
    }

    @Test
    default void readBytesShouldReturnBigSavedData() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, TWELVE_MEGABYTES, LOW_COST).block();

        byte[] bytes = store.readBytes(defaultBucketName, blobId).block();

        assertThat(bytes).isEqualTo(TWELVE_MEGABYTES);
    }

    @Test
    default void readShouldThrowWhenNoExistingStream() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> store.read(defaultBucketName, blobIdFactory().from("unknown")))
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    default void readShouldReturnSavedData() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, SHORT_BYTEARRAY, LOW_COST).block();

        InputStream read = store.read(defaultBucketName, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void readShouldReturnLongSavedData() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, ELEVEN_KILOBYTES, LOW_COST).block();

        InputStream read = store.read(defaultBucketName, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    default void readShouldReturnBigSavedData() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        // 12 MB of text
        BlobId blobId = store.save(defaultBucketName, TWELVE_MEGABYTES, LOW_COST).block();

        InputStream read = store.read(defaultBucketName, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES));
    }
}
