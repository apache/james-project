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
package org.apache.james.blob.cassandra.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_SECOND;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

import reactor.core.publisher.Mono;

public interface BlobStoreCacheContract {

    byte[] EIGHT_KILOBYTES = Strings.repeat("0123456\n", 1024).getBytes(StandardCharsets.UTF_8);

    BlobStoreCache testee();

    BlobId.Factory blobIdFactory();

    @Test
    default void shouldSaveWhenCacheSmallByteData() {
        BlobId blobId = blobIdFactory().of(UUID.randomUUID().toString());
        assertThatCode(Mono.from(testee().cache(blobId, EIGHT_KILOBYTES))::block)
            .doesNotThrowAnyException();

        byte[] actual = Mono.from(testee().read(blobId)).block();
        assertThat(actual).containsExactly(EIGHT_KILOBYTES);
    }

    @Test
    default void shouldReturnExactlyDataWhenRead() {
        BlobId blobId = blobIdFactory().of(UUID.randomUUID().toString());
        Mono.from(testee().cache(blobId, EIGHT_KILOBYTES)).block();

        byte[] actual = Mono.from(testee().read(blobId)).block();
        assertThat(actual).containsExactly(EIGHT_KILOBYTES);
    }

    @Test
    default void shouldReturnEmptyWhenReadWithTimeOut() {
        BlobId blobId = blobIdFactory().of(UUID.randomUUID().toString());
        Mono.from(testee().cache(blobId, EIGHT_KILOBYTES)).block();
    }

    @Test
    default void shouldReturnNothingWhenDelete() {
        BlobId blobId = blobIdFactory().of(UUID.randomUUID().toString());
        Mono.from(testee().cache(blobId, EIGHT_KILOBYTES)).block();
        Mono.from(testee().remove(blobId)).block();

        Optional<byte[]> actual = Mono.from(testee().read(blobId)).blockOptional();
        assertThat(actual).isEmpty();
    }

    @Test
    default void shouldDeleteExactlyAndReturnNothingWhenDelete() {
        BlobId blobId = blobIdFactory().of(UUID.randomUUID().toString());
        BlobId blobId2 = blobIdFactory().of(UUID.randomUUID().toString());
        Mono.from(testee().cache(blobId, EIGHT_KILOBYTES)).block();
        Mono.from(testee().cache(blobId2, EIGHT_KILOBYTES)).block();
        Mono.from(testee().remove(blobId)).block();

        byte[] readBlobId2 = Mono.from(testee().read(blobId2)).block();
        assertThat(readBlobId2).containsExactly(EIGHT_KILOBYTES);
    }

    @Test
    default void shouldReturnDataWhenCacheSmallDataInConfigurationTTL() {
        BlobId blobId = blobIdFactory().of(UUID.randomUUID().toString());
        assertThatCode(Mono.from(testee().cache(blobId, EIGHT_KILOBYTES))::block)
            .doesNotThrowAnyException();

        await().atMost(ONE_SECOND).await().untilAsserted(()
            -> assertThat(Mono.from(testee().read(blobId)).block()).containsExactly(EIGHT_KILOBYTES));
    }

    @Test
    default void shouldNotReturnDataWhenCachedSmallDataOutOfConfigurationTTL() {
        BlobId blobId = blobIdFactory().of(UUID.randomUUID().toString());
        assertThatCode(Mono.from(testee().cache(blobId, EIGHT_KILOBYTES))::block)
            .doesNotThrowAnyException();
        //add some time after the TTL to avoid threshold effect
        await().atMost(Duration.ofSeconds(2).plus(FIVE_HUNDRED_MILLISECONDS)).await().untilAsserted(()
            -> assertThat(Mono.from(testee().read(blobId)).blockOptional()).isEmpty());
    }

    @Test
    default void readShouldReturnEmptyCachedByteArray() {
        BlobId blobId = blobIdFactory().of(UUID.randomUUID().toString());
        byte[] emptyByteArray = new byte[] {};

        Mono.from(testee().cache(blobId, emptyByteArray)).block();

        assertThat(new ByteArrayInputStream(Mono.from(testee().read(blobId)).block()))
            .hasSameContentAs(new ByteArrayInputStream(emptyByteArray));
    }
}
