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

import static org.apache.james.modules.blobstore.BlobStoreConfiguration.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.FakePropertiesProvider;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class BlobStoreConfigurationTest {

    private static final String OBJECT_STORAGE = "objectstorage";
    private static final String CASSANDRA = "cassandra";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(BlobStoreConfiguration.class)
            .verify();
    }

    @Test
    void provideChoosingConfigurationShouldThrowWhenMissingPropertyField() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> parse(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provideChoosingConfigurationShouldThrowWhenEmptyPropertyField() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> parse(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provideChoosingConfigurationShouldThrowWhenPropertyFieldIsNotInSupportedList() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "gabouzomeuh");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> parse(propertyProvider))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provideChoosingConfigurationShouldReturnCassandraWhenNoFile() throws Exception {
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register("other_configuration_file", new PropertiesConfiguration())
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.cassandra());
    }

    @Test
    void provideChoosingConfigurationShouldReturnObjectStorageFactoryWhenConfigurationImplIsObjectStorage() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.OBJECTSTORAGE.getName());
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.objectStorage().disableCache());
    }

    @Test
    void provideChoosingConfigurationShouldReturnCassandraFactoryWhenConfigurationImplIsCassandra() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.CASSANDRA.getName());
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.cassandra());
    }


    @Test
    void fromShouldThrowWhenBlobStoreImplIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("implementation property is missing please use one of supported values in: cassandra, objectstorage");
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", null);

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("implementation property is missing please use one of supported values in: cassandra, objectstorage");
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "");

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("implementation property is missing please use one of supported values in: cassandra, objectstorage");
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsNotInSupportedList() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "un_supported");

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("un_supported is not a valid name of BlobStores, please use one of supported values in: cassandra, objectstorage");
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsCassandra() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", CASSANDRA);

        assertThat(
            BlobStoreConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(CASSANDRA);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsObjectStorage() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", OBJECT_STORAGE);

        assertThat(
            BlobStoreConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(OBJECT_STORAGE);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsSupportedAndCaseInsensitive() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "OBjecTStorAGE");

        assertThat(
            BlobStoreConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(OBJECT_STORAGE);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsSupportedAndHasExtraSpaces() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", " cassandra ");

        assertThat(
            BlobStoreConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(CASSANDRA);
    }

    @Test
    void cacheEnabledShouldBeTrueWhenSpecified() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.OBJECTSTORAGE.getName());
        configuration.addProperty("cache.enable", true);

        assertThat(BlobStoreConfiguration.from(configuration).cacheEnabled())
            .isTrue();
    }

    @Test
    void cacheEnabledShouldBeFalseWhenSpecified() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.OBJECTSTORAGE.getName());
        configuration.addProperty("cache.enable", false);

        assertThat(BlobStoreConfiguration.from(configuration).cacheEnabled())
            .isFalse();
    }

    @Test
    void cacheEnabledShouldDefaultToFalse() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.OBJECTSTORAGE.getName());

        assertThat(BlobStoreConfiguration.from(configuration).cacheEnabled())
            .isFalse();
    }
}