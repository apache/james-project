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

package org.apache.james.blob.aes;

import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

class AESBlobStoreDAOTest implements BlobStoreDAOContract {
    private static final String SAMPLE_SALT = "c603a7327ee3dcbc031d8d34b1096c605feca5e1";
    private static final CryptoConfig CRYPTO_CONFIG = CryptoConfig.builder()
        .salt(SAMPLE_SALT)
        .password("testing".toCharArray())
        .build();

    private AESBlobStoreDAO testee;
    private MemoryBlobStoreDAO underlying;

    @BeforeEach
    void setUp() {
        underlying = new MemoryBlobStoreDAO();
        testee = new AESBlobStoreDAO(underlying, CRYPTO_CONFIG);
    }

    @Override
    public BlobStoreDAO testee() {
        return testee;
    }

    @Test
    void underlyingDataShouldBeEncrypted() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        byte[] bytes = Mono.from(underlying.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(bytes).isNotEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    void underlyingDataShouldBeEncryptedWhenUsingStream() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(SHORT_BYTEARRAY))).block();

        byte[] bytes = Mono.from(underlying.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(bytes).isNotEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    void underlyingDataShouldBeEncryptedWhenUsingByteSource() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        byte[] bytes = Mono.from(underlying.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(bytes).isNotEqualTo(SHORT_BYTEARRAY);
    }
}