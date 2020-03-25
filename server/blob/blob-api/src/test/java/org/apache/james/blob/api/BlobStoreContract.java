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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.HIGH_PERFORMANCE;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.base.Strings;

import reactor.core.publisher.Mono;

public interface BlobStoreContract extends DeleteBlobStoreContract, BucketBlobStoreContract {

    static Stream<Arguments> storagePolicies() {
        return Stream.of(
            Arguments.arguments(LOW_COST),
            Arguments.arguments(SIZE_BASED),
            Arguments.arguments(HIGH_PERFORMANCE));
    }

    String SHORT_STRING = "toto";
    byte[] EMPTY_BYTEARRAY = {};
    byte[] SHORT_BYTEARRAY = SHORT_STRING.getBytes(StandardCharsets.UTF_8);
    byte[] ELEVEN_KILOBYTES = Strings.repeat("0123456789\n", 1000).getBytes(StandardCharsets.UTF_8);
    byte[] TWELVE_MEGABYTES = Strings.repeat("0123456789\r\n", 1024 * 1024).getBytes(StandardCharsets.UTF_8);

    BlobStore testee();

    BlobId.Factory blobIdFactory();

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldThrowWhenNullData(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> Mono.from(store.save(defaultBucketName, (byte[]) null, storagePolicy)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldThrowWhenNullString(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> Mono.from(store.save(defaultBucketName, (String) null, storagePolicy)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldThrowWhenNullInputStream(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> Mono.from(store.save(defaultBucketName, (InputStream) null, storagePolicy)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldSaveEmptyData(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, EMPTY_BYTEARRAY, storagePolicy)).block();

        byte[] bytes = Mono.from(store.readBytes(defaultBucketName, blobId)).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldSaveEmptyString(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, "", storagePolicy)).block();

        byte[] bytes = Mono.from(store.readBytes(defaultBucketName, blobId)).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldSaveEmptyInputStream(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, new ByteArrayInputStream(EMPTY_BYTEARRAY), storagePolicy)).block();

        byte[] bytes = Mono.from(store.readBytes(defaultBucketName, blobId)).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldReturnBlobId(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, SHORT_BYTEARRAY, storagePolicy)).block();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldReturnBlobIdOfString(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, SHORT_STRING, storagePolicy)).block();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldReturnBlobIdOfInputStream(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, new ByteArrayInputStream(SHORT_BYTEARRAY), storagePolicy)).block();

        assertThat(blobId).isEqualTo(blobIdFactory().from("31f7a65e315586ac198bd798b6629ce4903d0899476d5741a9f32e2e521b6a66"));
    }

    @Test
    default void readBytesShouldThrowWhenNoExisting() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> Mono.from(store.readBytes(defaultBucketName, blobIdFactory().from("unknown"))).block())
            .isExactlyInstanceOf(ObjectNotFoundException.class);
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void readBytesShouldReturnSavedData(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, SHORT_BYTEARRAY, storagePolicy)).block();

        byte[] bytes = Mono.from(store.readBytes(defaultBucketName, blobId)).block();

        assertThat(bytes).isEqualTo(SHORT_BYTEARRAY);
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void readBytesShouldReturnLongSavedData(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, ELEVEN_KILOBYTES, storagePolicy)).block();

        byte[] bytes = Mono.from(store.readBytes(defaultBucketName, blobId)).block();

        assertThat(bytes).isEqualTo(ELEVEN_KILOBYTES);
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void readBytesShouldReturnBigSavedData(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, TWELVE_MEGABYTES, storagePolicy)).block();

        byte[] bytes = Mono.from(store.readBytes(defaultBucketName, blobId)).block();

        assertThat(bytes).isEqualTo(TWELVE_MEGABYTES);
    }

    @Test
    default void readShouldThrowWhenNoExistingStream() {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatThrownBy(() -> store.read(defaultBucketName, blobIdFactory().from("unknown")).read())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void readShouldReturnSavedData(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, SHORT_BYTEARRAY, storagePolicy)).block();

        InputStream read = store.read(defaultBucketName, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void readShouldReturnLongSavedData(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, ELEVEN_KILOBYTES, storagePolicy)).block();

        InputStream read = store.read(defaultBucketName, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void readShouldReturnBigSavedData(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        // 12 MB of text
        BlobId blobId = Mono.from(store.save(defaultBucketName, TWELVE_MEGABYTES, storagePolicy)).block();

        InputStream read = store.read(defaultBucketName, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES));
    }
}
