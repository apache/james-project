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

package org.apache.james.modules.objectstore;

import static org.apache.james.modules.objectstore.BlobStoreChoosingModule.BLOBSTORE_CONFIGURATION_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.modules.objectstorage.FakePropertiesProvider;
import org.apache.james.modules.objectstore.BlobStoreChoosingConfiguration.BlobStoreImplName;
import org.junit.jupiter.api.Test;

import com.google.inject.Provider;

class BlobStoreChoosingModuleTest {

    private static CassandraBlobsDAO CASSANDRA_BLOBSTORE = mock(CassandraBlobsDAO.class);
    private static Provider<CassandraBlobsDAO> CASSANDRA_BLOBSTORE_PROVIDER = () -> CASSANDRA_BLOBSTORE;
    private static ObjectStorageBlobsDAO SWIFT_BLOBSTORE = mock(ObjectStorageBlobsDAO.class);
    private static Provider<ObjectStorageBlobsDAO> SWIFT_BLOBSTORE_PROVIDER = () -> SWIFT_BLOBSTORE;

    @Test
    void provideBlobStoreFactoryShouldThrowWhenMissingPropertyField() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", "");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(BLOBSTORE_CONFIGURATION_NAME, configuration)
            .build();

        assertThatThrownBy(() -> module.provideBlobStore(propertyProvider, CASSANDRA_BLOBSTORE_PROVIDER, SWIFT_BLOBSTORE_PROVIDER))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provideBlobStoreFactoryShouldThrowWhenEmptyPropertyField() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", "");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(BLOBSTORE_CONFIGURATION_NAME, configuration)
            .build();

        assertThatThrownBy(() -> module.provideBlobStore(propertyProvider, CASSANDRA_BLOBSTORE_PROVIDER, SWIFT_BLOBSTORE_PROVIDER))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provideBlobStoreFactoryShouldThrowWhenPropertyFieldIsNotInSupportedList() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", "gabouzomeuh");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(BLOBSTORE_CONFIGURATION_NAME, configuration)
            .build();

        assertThatThrownBy(() -> module.provideBlobStore(propertyProvider, CASSANDRA_BLOBSTORE_PROVIDER, SWIFT_BLOBSTORE_PROVIDER))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provideBlobStoreFactoryShouldReturnCassandraWhenNoFile() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register("other_configuration_file", new PropertiesConfiguration())
            .build();

        assertThat(module.provideBlobStore(propertyProvider, CASSANDRA_BLOBSTORE_PROVIDER, SWIFT_BLOBSTORE_PROVIDER))
            .isEqualTo(CASSANDRA_BLOBSTORE);
    }

    @Test
    void provideBlobStoreFactoryShouldReturnSwiftFactoryWhenConfigurationImplIsSwift() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", BlobStoreImplName.SWIFT.getName());
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(BLOBSTORE_CONFIGURATION_NAME, configuration)
            .build();

        assertThat(module.provideBlobStore(propertyProvider, CASSANDRA_BLOBSTORE_PROVIDER, SWIFT_BLOBSTORE_PROVIDER))
            .isEqualTo(SWIFT_BLOBSTORE);
    }

    @Test
    void provideBlobStoreFactoryShouldReturnCassandraFactoryWhenConfigurationImplIsCassandra() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", BlobStoreImplName.CASSANDRA.getName());
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(BLOBSTORE_CONFIGURATION_NAME, configuration)
            .build();

        assertThat(module.provideBlobStore(propertyProvider, CASSANDRA_BLOBSTORE_PROVIDER, SWIFT_BLOBSTORE_PROVIDER))
            .isEqualTo(CASSANDRA_BLOBSTORE);
    }
}