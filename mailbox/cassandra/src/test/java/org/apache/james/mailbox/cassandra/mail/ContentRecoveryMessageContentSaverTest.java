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

package org.apache.james.mailbox.cassandra.mail;

import static org.apache.james.mailbox.cassandra.mail.ContentRecoveryMessageContentSaver.HEADER_BLOB_ID_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreCacheCallback;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * The header blob id is not a free-form string: the garbage collection reads its generation back out of
 * it, and the recovery runner filters on its {@value ContentRecoveryMessageContentSaver#HEADER_BLOB_ID_SUFFIX}
 * suffix and on the family and generation prefix pushed down to the object store as a listing prefix.
 *
 * <p>Those three properties have to hold for every {@link BlobId.Factory} a deployment may be configured
 * with, which is what this pins down.</p>
 */
class ContentRecoveryMessageContentSaverTest {
    private static final byte[] HEADER_BYTES = "Subject: test\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final ByteSource BODY = ByteSource.wrap("body".getBytes(StandardCharsets.UTF_8));

    /**
     * 2026-09-04T00:00:00Z is exactly 690 times the default 30 days generation duration, which keeps the
     * expected prefixes below readable rather than recomputed from the formula under test.
     */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    static Stream<Arguments> blobIdFactories() {
        return Stream.of(
            Arguments.of("PlainBlobId", new PlainBlobId.Factory(), ""),
            Arguments.of("GenerationAwareBlobId",
                new GenerationAwareBlobId.Factory(CLOCK, new PlainBlobId.Factory(), GenerationAwareBlobId.Configuration.DEFAULT),
                "1_690_"),
            Arguments.of("MinIOGenerationAwareBlobId",
                new MinIOGenerationAwareBlobId.Factory(CLOCK, GenerationAwareBlobId.Configuration.DEFAULT, new PlainBlobId.Factory()),
                "1/690/"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blobIdFactories")
    void headerBlobIdShouldBeSuffixed(String name, BlobId.Factory blobIdFactory, String expectedPrefix) {
        assertThat(saveContent(blobIdFactory).getT1().asString())
            .endsWith(HEADER_BLOB_ID_SUFFIX);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blobIdFactories")
    void headerBlobIdShouldCarryFamilyAndGenerationOfItsFactory(String name, BlobId.Factory blobIdFactory, String expectedPrefix) {
        assertThat(saveContent(blobIdFactory).getT1().asString())
            .startsWith(expectedPrefix);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blobIdFactories")
    void headerBlobIdShouldRoundTripThroughItsFactory(String name, BlobId.Factory blobIdFactory, String expectedPrefix) {
        String headerBlobId = saveContent(blobIdFactory).getT1().asString();

        assertThat(blobIdFactory.parse(headerBlobId).asString())
            .isEqualTo(headerBlobId);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blobIdFactories")
    void headerBlobIdShouldNotRepeatItself(String name, BlobId.Factory blobIdFactory, String expectedPrefix) {
        assertThat(saveContent(blobIdFactory).getT1())
            .isNotEqualTo(saveContent(blobIdFactory).getT1());
    }

    private Tuple2<BlobId, BlobId> saveContent(BlobId.Factory blobIdFactory) {
        BlobStoreDAO blobStoreDAO = mock(BlobStoreDAO.class);
        when(blobStoreDAO.save(any(BucketName.class), any(BlobId.class), any(BlobStoreDAO.Blob.class))).thenReturn(Mono.empty());

        BlobStore blobStore = mock(BlobStore.class);
        when(blobStore.getDefaultBucketName()).thenReturn(BucketName.DEFAULT);
        when(blobStore.save(any(BucketName.class), any(ByteSource.class), any(BlobStore.StoragePolicy.class)))
            .thenReturn(Mono.just(blobIdFactory.of("body")));

        return new ContentRecoveryMessageContentSaver(blobStore, blobStoreDAO, blobIdFactory, BlobStoreCacheCallback.NOOP)
            .saveContent(HEADER_BYTES, BODY)
            .block();
    }
}
