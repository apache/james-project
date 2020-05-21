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

import static org.apache.james.modules.blobstore.BlobStoreModulesChooser.HybridDeclarationModule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.FakePropertiesProvider;
import org.apache.james.blob.union.HybridBlobStore;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.junit.jupiter.api.Test;

class BlobStoreModulesChooserTest {
    @Test
    void providesHybridBlobStoreConfigurationShouldThrowWhenNegative() {
        HybridDeclarationModule module = new HybridDeclarationModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("hybrid.size.threshold", -1);
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> module.providesHybridBlobStoreConfiguration(propertyProvider))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providesHybridBlobStoreConfigurationShouldNotThrowWhenZero() {
        HybridDeclarationModule module = new HybridDeclarationModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("hybrid.size.threshold", 0);
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(module.providesHybridBlobStoreConfiguration(propertyProvider))
            .isEqualTo(new HybridBlobStore.Configuration(0));
    }

    @Test
    void providesHybridBlobStoreConfigurationShouldReturnConfiguration() {
        HybridDeclarationModule module = new HybridDeclarationModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("hybrid.size.threshold", 36);
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(module.providesHybridBlobStoreConfiguration(propertyProvider))
            .isEqualTo(new HybridBlobStore.Configuration(36));
    }

    @Test
    void providesHybridBlobStoreConfigurationShouldReturnConfigurationWhenLegacyFile() {
        HybridDeclarationModule module = new HybridDeclarationModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("hybrid.size.threshold", 36);
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.LEGACY, configuration)
            .build();

        assertThat(module.providesHybridBlobStoreConfiguration(propertyProvider))
            .isEqualTo(new HybridBlobStore.Configuration(36));
    }

    @Test
    void provideBlobStoreShouldReturnObjectStoreBlobStoreWhenObjectStoreConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.objectStorage().disableCache()))
            .first()
            .isInstanceOf(BlobStoreModulesChooser.ObjectStorageDeclarationModule.class);
    }

    @Test
    void provideBlobStoreShouldReturnCassandraBlobStoreWhenCassandraConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.cassandra()))
            .first()
            .isInstanceOf(BlobStoreModulesChooser.CassandraDeclarationModule.class);
    }

    @Test
    void provideBlobStoreShouldReturnHybridBlobStoreWhenHybridConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.hybrid()))
            .first()
            .isInstanceOf(BlobStoreModulesChooser.HybridDeclarationModule.class);
    }
}