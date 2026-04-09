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

import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public interface MetadataAwareBlobStoreDAOContract {
    BlobStoreDAO testee();

    @Test
    default void readBytesShouldPreserveMetadata() {
        BlobStoreDAO testee = testee();

        BlobStoreDAO.BytesBlob bytesBlob = BlobStoreDAO.BytesBlob.of("payload".getBytes(),
            BlobStoreDAO.BlobMetadata.empty()
                .withMetadata(new BlobStoreDAO.BlobMetadataName("name"), new BlobStoreDAO.BlobMetadataValue("value")));

        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytesBlob)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block().metadata().underlyingMap())
            .containsAllEntriesOf(bytesBlob.metadata().underlyingMap());
    }

    @Test
    default void readStreamShouldPreserveMetadata() {
        BlobStoreDAO testee = testee();

        BlobStoreDAO.InputStreamBlob inputStreamBlob = BlobStoreDAO.BytesBlob.of("payload".getBytes(),
            BlobStoreDAO.BlobMetadata.empty()
                .withMetadata(new BlobStoreDAO.BlobMetadataName("name"), new BlobStoreDAO.BlobMetadataValue("value")))
            .asInputStream();

        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, inputStreamBlob)).block();

        assertThat(Mono.from(testee.readReactive(TEST_BUCKET_NAME, TEST_BLOB_ID)).block().metadata().underlyingMap())
            .containsAllEntriesOf(inputStreamBlob.metadata().underlyingMap());
    }

    @Test
    default void readByteSourceShouldPreserveMetadata() {
        BlobStoreDAO testee = testee();

        BlobStoreDAO.ByteSourceBlob byteSourceBlob = BlobStoreDAO.BytesBlob.of("payload".getBytes(),
            BlobStoreDAO.BlobMetadata.empty()
                .withMetadata(new BlobStoreDAO.BlobMetadataName("name"), new BlobStoreDAO.BlobMetadataValue("value")))
            .asByteSource();

        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, byteSourceBlob)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block().metadata().underlyingMap())
            .containsAllEntriesOf(byteSourceBlob.metadata().underlyingMap());
    }

    @Test
    default void storeMultipleMetadataEntriesShouldSucceed() {
        BlobStoreDAO testee = testee();

        BlobStoreDAO.ByteSourceBlob byteSourceBlob = BlobStoreDAO.BytesBlob.of("payload".getBytes(),
                BlobStoreDAO.BlobMetadata.empty()
                    .withMetadata(new BlobStoreDAO.BlobMetadataName("name1"), new BlobStoreDAO.BlobMetadataValue("value1"))
                    .withMetadata(new BlobStoreDAO.BlobMetadataName("name2"), new BlobStoreDAO.BlobMetadataValue("value2")))
            .asByteSource();

        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, byteSourceBlob)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block().metadata().underlyingMap())
            .containsAllEntriesOf(byteSourceBlob.metadata().underlyingMap());
    }

    @Test
    default void retrieveContentTransferEncodingShouldSucceed() {
        BlobStoreDAO testee = testee();

        BlobStoreDAO.BytesBlob bytesBlob = BlobStoreDAO.BytesBlob.of("payload".getBytes(),
            BlobStoreDAO.BlobMetadata.empty()
                .withContentTransferEncoding(BlobStoreDAO.ContentTransferEncoding.ZSTD));

        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytesBlob)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block().metadata().contentTransferEncoding())
            .contains(BlobStoreDAO.ContentTransferEncoding.ZSTD);
    }

    @Test
    default void saveShouldPreserveEmptyMetadata() {
        BlobStoreDAO testee = testee();

        BlobStoreDAO.BytesBlob bytesBlob = BlobStoreDAO.BytesBlob.of("payload".getBytes(), BlobStoreDAO.BlobMetadata.empty());

        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, bytesBlob)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block().metadata())
            .isEqualTo(BlobStoreDAO.BlobMetadata.empty());
    }

    @Test
    default void saveShouldOverwriteExistingBlobMetadata() {
        BlobStoreDAO testee = testee();

        BlobStoreDAO.BytesBlob initialBlob = BlobStoreDAO.BytesBlob.of("payload".getBytes(),
            BlobStoreDAO.BlobMetadata.empty()
                .withMetadata(new BlobStoreDAO.BlobMetadataName("first"), new BlobStoreDAO.BlobMetadataValue("value1")));
        BlobStoreDAO.BytesBlob updatedBlob = BlobStoreDAO.BytesBlob.of("payload".getBytes(),
            BlobStoreDAO.BlobMetadata.empty()
                .withMetadata(new BlobStoreDAO.BlobMetadataName("second"), new BlobStoreDAO.BlobMetadataValue("value2")));

        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, initialBlob)).block();
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, updatedBlob)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block().metadata().underlyingMap())
            .containsAllEntriesOf(updatedBlob.metadata().underlyingMap())
            .doesNotContainKey(new BlobStoreDAO.BlobMetadataName("first"));
    }
}
