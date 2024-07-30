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
import static org.apache.james.blob.api.BlobStoreContract.SHORT_BYTEARRAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import reactor.core.publisher.Mono;

public interface DeduplicationBlobStoreContract {
    static Stream<Arguments> storagePolicies() {
        return Stream.of(
            Arguments.arguments(LOW_COST),
            Arguments.arguments(SIZE_BASED),
            Arguments.arguments(HIGH_PERFORMANCE));
    }

    String SHORT_STRING = "toto";

    BlobStore testee();

    BlobId.Factory blobIdFactory();

    BlobStore createBlobStore();

    @BeforeEach
    default void beforeEach() {
        System.clearProperty("james.blob.id.hash.encoding");
    }

    @Test
    default void deduplicationBlobstoreCreationShouldFailOnInvalidProperty() {
        System.setProperty("james.blob.id.hash.encoding", "invalid");

        assertThatThrownBy(this::createBlobStore)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown encoding type: invalid");
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldReturnBlobIdOfString(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, SHORT_STRING, storagePolicy)).block();

        assertThat(blobId).isEqualTo(blobIdFactory().parse("MfemXjFVhqwZi9eYtmKc5JA9CJlHbVdBqfMuLlIbamY="));
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldReturnBlobId(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, SHORT_BYTEARRAY, storagePolicy)).block();

        assertThat(blobId).isEqualTo(blobIdFactory().parse("MfemXjFVhqwZi9eYtmKc5JA9CJlHbVdBqfMuLlIbamY="));
    }

    @ParameterizedTest
    @MethodSource("storagePolicies")
    default void saveShouldReturnBlobIdOfInputStream(BlobStore.StoragePolicy storagePolicy) {
        BlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = Mono.from(store.save(defaultBucketName, new ByteArrayInputStream(SHORT_BYTEARRAY), storagePolicy)).block();
        // This fix is ok because it will only affect deduplication, after this change the same content might be assigned a different blobid
        // and thus might be duplicated in the store. No data can be lost since no api allows for externally deterministic blob id construction
        // before this change.
        assertThat(blobId).isEqualTo(blobIdFactory().of("MfemXjFVhqwZi9eYtmKc5JA9CJlHbVdBqfMuLlIbamY="));
    }
}
