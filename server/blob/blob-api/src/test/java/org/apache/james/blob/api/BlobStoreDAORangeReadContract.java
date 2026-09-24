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

import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public interface BlobStoreDAORangeReadContract {
    BlobStoreDAO testee();

    default byte[] sampleContent() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append(String.format("%010d", i)); // 100 * 10 = 1000 bytes
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    default BlobId createSampleBlob() {
        BlobId blobId = new TestBlobId.Factory().of(UUID.randomUUID().toString());
        Mono.from(testee().save(TEST_BUCKET_NAME, blobId, BlobStoreDAO.BytesBlob.of(sampleContent()))).block();
        return blobId;
    }

    @Test
    default void readRangeFirstBytesShouldReturnFirstBytes() throws IOException {
        BlobId blobId = createSampleBlob();
        byte[] content = sampleContent();
        BlobStoreDAO.Blob slice = Mono.from(testee().readRange(TEST_BUCKET_NAME, blobId, 0, 99)).block();

        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(content, 0, 100));
    }

    @Test
    default void readRangeMidObjectShouldReturnMidBytes() throws IOException {
        BlobId blobId = createSampleBlob();
        byte[] content = sampleContent();
        BlobStoreDAO.Blob slice = Mono.from(testee().readRange(TEST_BUCKET_NAME, blobId, 200, 399)).block();

        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(content, 200, 400));
    }

    @Test
    default void readRangeLastBytesNegativeShouldReturnSuffix() throws IOException {
        BlobId blobId = createSampleBlob();
        byte[] content = sampleContent();
        BlobStoreDAO.Blob slice = Mono.from(testee().readRange(TEST_BUCKET_NAME, blobId, -100, -1)).block();

        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(content, 900, 1000));
    }

    @Test
    default void readRangeFullObjectShouldReturnAllBytes() throws IOException {
        BlobId blobId = createSampleBlob();
        byte[] content = sampleContent();
        BlobStoreDAO.Blob slice = Mono.from(testee().readRange(TEST_BUCKET_NAME, blobId, 0, 999)).block();

        assertThat(BlobStoreDAO.totalObjectSize(slice)).isEqualTo(1000);
        assertThat(slice.asBytes().payload()).isEqualTo(content);
    }

    @Test
    default void crossCheckAgainstFullReadShouldMatch() throws IOException {
        BlobId blobId = createSampleBlob();
        BlobStoreDAO.Blob slice = Mono.from(testee().readRange(TEST_BUCKET_NAME, blobId, 150, 450)).block();
        byte[] fullRead = Mono.from(testee().readBytes(TEST_BUCKET_NAME, blobId)).block().payload();

        assertThat(slice.asBytes().payload()).isEqualTo(Arrays.copyOfRange(fullRead, 150, 451));
    }

    @Test
    default void readRangeBeyondObjectSizeShouldFail() {
        BlobId blobId = createSampleBlob();
        assertThatThrownBy(() -> Mono.from(testee().readRange(TEST_BUCKET_NAME, blobId, 2000, 3000)).block())
            .isNotNull();
    }
}
