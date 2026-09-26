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

import java.time.Clock;
import java.util.Optional;

import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.compaction.BlobCompactionAlgorithm;
import org.apache.james.blob.zstd.CompressionConfiguration;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

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

    @Test
    void provideBlobStoreShouldReturnNoCompressionWhenCompressionDisabled() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .disableCache()
            .deduplication()
            .noCryptoConfig()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.NoCompressionModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnCompressionWhenConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .cassandra()
            .disableCache()
            .passthrough()
            .noCryptoConfig()
            .compressionConfig(CompressionConfiguration.builder()
                .enabled(true)
                .build())))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.CompressionModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnBlobCompactionModule() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .disableCache()
            .deduplication()
            .noCryptoConfig()))
            .filteredOn(module -> module instanceof BlobCompactionModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnChunkedBlobStoreModuleWhenS3() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .disableCache()
            .deduplication()
            .noCryptoConfig()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.ChunkedBlobStoreModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnNoChunkedBlobStoreModuleWhenNotS3() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .cassandra()
            .disableCache()
            .deduplication()
            .noCryptoConfig()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.NoChunkedBlobStoreModule)
            .hasSize(1);
    }

    @Test
    void optionalBlobCompactionAlgorithmShouldReturnEmptyWhenCryptoConfigured() {
        BlobStoreConfiguration config = BlobStoreConfiguration.builder()
            .cassandra()
            .disableCache()
            .passthrough()
            .cryptoConfig(CryptoConfig.builder()
                .password("myPass".toCharArray())
                .salt("73616c7479")
                .build());

        Injector injector = Guice.createInjector(
            binder -> {
                binder.bind(BlobStoreConfiguration.class).toInstance(config);
                binder.bind(Clock.class).toInstance(Clock.systemUTC());
                binder.bind(BlobStoreDAO.class).toProvider(() -> null);
                binder.bind(BlobStoreDAO.class).annotatedWith(Names.named(BlobStoreModulesChooser.RAW)).toProvider(() -> null);
                binder.bind(BlobIdUpdater.Factory.class).toProvider(() -> null);
            },
            new BlobCompactionModule()
        );

        Optional<BlobCompactionAlgorithm> algorithm = injector.getInstance(
            Key.get(new TypeLiteral<Optional<BlobCompactionAlgorithm>>() {}));
        assertThat(algorithm).isEmpty();
    }

    @Test
    void optionalBlobCompactionAlgorithmShouldReturnEmptyWhenCompressionConfigured() {
        BlobStoreConfiguration config = BlobStoreConfiguration.builder()
            .cassandra()
            .disableCache()
            .passthrough()
            .noCryptoConfig()
            .compressionConfig(CompressionConfiguration.builder()
                .enabled(true)
                .build());

        Injector injector = Guice.createInjector(
            binder -> {
                binder.bind(BlobStoreConfiguration.class).toInstance(config);
                binder.bind(Clock.class).toInstance(Clock.systemUTC());
                binder.bind(BlobStoreDAO.class).toProvider(() -> null);
                binder.bind(BlobStoreDAO.class).annotatedWith(Names.named(BlobStoreModulesChooser.RAW)).toProvider(() -> null);
                binder.bind(BlobIdUpdater.Factory.class).toProvider(() -> null);
            },
            new BlobCompactionModule()
        );

        Optional<BlobCompactionAlgorithm> algorithm = injector.getInstance(
            Key.get(new TypeLiteral<Optional<BlobCompactionAlgorithm>>() {}));
        assertThat(algorithm).isEmpty();
    }

    @Test
    void optionalBlobCompactionAlgorithmShouldReturnEmptyInNonCassandraEnvironmentWithoutMappingDependencies() {
        BlobStoreConfiguration config = BlobStoreConfiguration.builder()
            .postgres()
            .disableCache()
            .passthrough()
            .noCryptoConfig();

        Injector injector = Guice.createInjector(
            binder -> {
                binder.bind(BlobStoreConfiguration.class).toInstance(config);
                binder.bind(Clock.class).toInstance(Clock.systemUTC());
                binder.bind(BlobStoreDAO.class).toProvider(() -> null);
                binder.bind(BlobStoreDAO.class).annotatedWith(Names.named(BlobStoreModulesChooser.RAW)).toProvider(() -> null);
            },
            new BlobCompactionModule()
        );

        Optional<BlobCompactionAlgorithm> algorithm = injector.getInstance(
            Key.get(new TypeLiteral<Optional<BlobCompactionAlgorithm>>() {}));
        assertThat(algorithm).isEmpty();
    }
}