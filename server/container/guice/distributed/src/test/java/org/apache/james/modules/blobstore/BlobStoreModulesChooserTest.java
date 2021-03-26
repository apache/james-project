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

package org.apache.james.modules.blobstore;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.aes.CryptoConfig;
import org.junit.jupiter.api.Test;

class BlobStoreModulesChooserTest {

    @Test
    void provideBlobStoreShouldReturnObjectStoreBlobStoreWhenObjectStoreConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
                    .s3()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.ObjectStorageBlobStoreDAODeclarationModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnCassandraBlobStoreWhenCassandraConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
                .cassandra()
                .disableCache()
                .passthrough()
                .noCryptoConfig()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.CassandraBlobStoreDAODeclarationModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnNoEncryptionWhenNoneConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
                    .s3()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.NoEncryptionModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnEncryptionWhenConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
                .cassandra()
                .disableCache()
                .passthrough()
                .cryptoConfig(CryptoConfig.builder()
                    .password("myPass".toCharArray())
                    // Hex.encode("salty".getBytes(StandardCharsets.UTF_8))
                    .salt("73616c7479")
                    .build())))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.EncryptionModule)
            .hasSize(1);
    }
}