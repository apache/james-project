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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.FakePropertiesProvider;
import org.apache.james.blob.cassandra.CassandraBlobStore;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStore;
import org.apache.james.blob.union.UnionBlobStore;
import org.apache.james.modules.blobstore.BlobStoreChoosingConfiguration.BlobStoreImplName;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.junit.jupiter.api.Test;

import com.google.inject.Provider;

class BlobStoreChoosingModuleTest {

    private static CassandraBlobStore CASSANDRA_BLOBSTORE = mock(CassandraBlobStore.class);
    private static Provider<CassandraBlobStore> CASSANDRA_BLOBSTORE_PROVIDER = () -> CASSANDRA_BLOBSTORE;
    private static ObjectStorageBlobStore OBJECT_STORAGE_BLOBSTORE = mock(ObjectStorageBlobStore.class);
    private static Provider<ObjectStorageBlobStore> OBJECT_STORAGE_BLOBSTORE_PROVIDER = () -> OBJECT_STORAGE_BLOBSTORE;

    @Test
    void provideChoosingConfigurationShouldThrowWhenMissingPropertyField() {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> module.provideChoosingConfiguration(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provideChoosingConfigurationShouldThrowWhenEmptyPropertyField() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> module.provideChoosingConfiguration(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provideChoosingConfigurationShouldThrowWhenPropertyFieldIsNotInSupportedList() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "gabouzomeuh");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> module.provideChoosingConfiguration(propertyProvider))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provideChoosingConfigurationShouldReturnCassandraWhenNoFile() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register("other_configuration_file", new PropertiesConfiguration())
            .build();

        assertThat(module.provideChoosingConfiguration(propertyProvider))
            .isEqualTo(BlobStoreChoosingConfiguration.cassandra());
    }

    @Test
    void provideChoosingConfigurationShouldReturnObjectStorageFactoryWhenConfigurationImplIsObjectStorage() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreImplName.OBJECTSTORAGE.getName());
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(module.provideChoosingConfiguration(propertyProvider))
            .isEqualTo(BlobStoreChoosingConfiguration.objectStorage());
    }

    @Test
    void provideChoosingConfigurationShouldReturnUnionConfigurationWhenConfigurationImplIsUnion() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreImplName.UNION.getName());
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(module.provideChoosingConfiguration(propertyProvider))
            .isEqualTo(BlobStoreChoosingConfiguration.union());
    }

    @Test
    void provideChoosingConfigurationShouldReturnCassandraFactoryWhenConfigurationImplIsCassandra() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreImplName.CASSANDRA.getName());
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(module.provideChoosingConfiguration(propertyProvider))
            .isEqualTo(BlobStoreChoosingConfiguration.cassandra());
    }

    @Test
    void provideBlobStoreShouldReturnCassandraBlobStoreWhenCassandraConfigured() {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();

        assertThat(module.provideBlobStore(BlobStoreChoosingConfiguration.cassandra(),
            CASSANDRA_BLOBSTORE_PROVIDER, OBJECT_STORAGE_BLOBSTORE_PROVIDER))
            .isEqualTo(CASSANDRA_BLOBSTORE);
    }

    @Test
    void provideBlobStoreShouldReturnObjectStoreBlobStoreWhenObjectStoreConfigured() {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();

        assertThat(module.provideBlobStore(BlobStoreChoosingConfiguration.cassandra(),
            CASSANDRA_BLOBSTORE_PROVIDER, OBJECT_STORAGE_BLOBSTORE_PROVIDER))
            .isEqualTo(CASSANDRA_BLOBSTORE);
    }

    @Test
    void provideBlobStoreShouldReturnUnionBlobStoreWhenUnionConfigured() {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();

        assertThat(module.provideBlobStore(BlobStoreChoosingConfiguration.union(),
            CASSANDRA_BLOBSTORE_PROVIDER, OBJECT_STORAGE_BLOBSTORE_PROVIDER))
            .isInstanceOf(UnionBlobStore.class);
    }
}