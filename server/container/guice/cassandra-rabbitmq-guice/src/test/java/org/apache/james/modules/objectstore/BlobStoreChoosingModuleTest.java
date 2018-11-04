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
import org.apache.james.modules.objectstorage.FakePropertiesProvider;
import org.apache.james.modules.objectstore.BlobStoreChoosingConfiguration.BlobStoreImplName;
import org.apache.james.modules.objectstore.BlobStoreChoosingModule.CassandraBlobStoreFactory;
import org.apache.james.modules.objectstore.BlobStoreChoosingModule.SwiftBlobStoreFactory;
import org.junit.jupiter.api.Test;

class BlobStoreChoosingModuleTest {

    private static CassandraBlobStoreFactory CASSANDRA_BLOBSTORE_FACTORY = mock(CassandraBlobStoreFactory.class);
    private static SwiftBlobStoreFactory SWIFT_BLOBSTORE_FACTORY = mock(SwiftBlobStoreFactory.class);

    @Test
    void provideBlobStoreFactoryShouldThrowWhenMissingPropertyField() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", "");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(BLOBSTORE_CONFIGURATION_NAME, configuration)
            .build();

        assertThatThrownBy(() -> module.provideBlobStoreFactory(propertyProvider, CASSANDRA_BLOBSTORE_FACTORY, SWIFT_BLOBSTORE_FACTORY))
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

        assertThatThrownBy(() -> module.provideBlobStoreFactory(propertyProvider, CASSANDRA_BLOBSTORE_FACTORY, SWIFT_BLOBSTORE_FACTORY))
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

        assertThatThrownBy(() -> module.provideBlobStoreFactory(propertyProvider, CASSANDRA_BLOBSTORE_FACTORY, SWIFT_BLOBSTORE_FACTORY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provideBlobStoreFactoryShouldReturnCassandraWhenNoFile() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register("other_configuration_file", new PropertiesConfiguration())
            .build();

        assertThat(module.provideBlobStoreFactory(propertyProvider, CASSANDRA_BLOBSTORE_FACTORY, SWIFT_BLOBSTORE_FACTORY))
            .isEqualTo(CASSANDRA_BLOBSTORE_FACTORY);
    }

    @Test
    void provideBlobStoreFactoryShouldReturnSwiftFactoryWhenConfigurationImplIsSwift() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", BlobStoreImplName.SWIFT.getName());
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(BLOBSTORE_CONFIGURATION_NAME, configuration)
            .build();

        assertThat(module.provideBlobStoreFactory(propertyProvider, CASSANDRA_BLOBSTORE_FACTORY, SWIFT_BLOBSTORE_FACTORY))
            .isEqualTo(SWIFT_BLOBSTORE_FACTORY);
    }

    @Test
    void provideBlobStoreFactoryShouldReturnCassandraFactoryWhenConfigurationImplIsCassandra() throws Exception {
        BlobStoreChoosingModule module = new BlobStoreChoosingModule();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstore.implementation", BlobStoreImplName.CASSANDRA.getName());
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(BLOBSTORE_CONFIGURATION_NAME, configuration)
            .build();

        assertThat(module.provideBlobStoreFactory(propertyProvider, CASSANDRA_BLOBSTORE_FACTORY, SWIFT_BLOBSTORE_FACTORY))
            .isEqualTo(CASSANDRA_BLOBSTORE_FACTORY);
    }
}