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

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class BlobStoreChoosingConfigurationTest {

    private static final String OBJECT_STORAGE = "objectstorage";
    private static final String CASSANDRA = "cassandra";
    private static final String UNION = "union";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(BlobStoreChoosingConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThatThrownBy(() -> BlobStoreChoosingConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("implementation property is missing please use one of supported values in: cassandra, objectstorage, union");
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", null);

        assertThatThrownBy(() -> BlobStoreChoosingConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("implementation property is missing please use one of supported values in: cassandra, objectstorage, union");
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "");

        assertThatThrownBy(() -> BlobStoreChoosingConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("implementation property is missing please use one of supported values in: cassandra, objectstorage, union");
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsNotInSupportedList() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "un_supported");

        assertThatThrownBy(() -> BlobStoreChoosingConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("un_supported is not a valid name of BlobStores, please use one of supported values in: cassandra, objectstorage, union");
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsCassandra() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", CASSANDRA);

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(CASSANDRA);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsUnion() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", UNION);

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(UNION);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsObjectStorage() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", OBJECT_STORAGE);

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(OBJECT_STORAGE);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsSupportedAndCaseInsensitive() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "OBjecTStorAGE");

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(OBJECT_STORAGE);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsSupportedAndHasExtraSpaces() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", " cassandra ");

        assertThat(
            BlobStoreChoosingConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(CASSANDRA);
    }
}