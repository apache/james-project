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

package org.apache.james.backends.pulsar;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PulsarConfigurationTest {

    @Test
    void fromShouldThrowWhenBrokerURIIsNotInTheConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the pulsar " + PulsarConfiguration.BROKER_URI_PROPERTY_NAME() + " uri");
    }

    @Test
    void fromShouldThrowWhenBrokerURIIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", null);

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the pulsar " + PulsarConfiguration.BROKER_URI_PROPERTY_NAME() + " uri");
    }

    @Test
    void fromShouldThrowWhenBrokerURIIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", "");

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the pulsar " + PulsarConfiguration.BROKER_URI_PROPERTY_NAME() + " uri");
    }

    @Test
    void fromShouldThrowWhenBrokerURIIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", ":invalid");

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("':invalid' is not a valid " + PulsarConfiguration.BROKER_URI_PROPERTY_NAME() + " uri");
    }

    @Test
    void fromShouldThrowWhenAdminURIIsNotInTheConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", "pulsar://locahost.test:6650/");
        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the pulsar " + PulsarConfiguration.ADMIN_URI_PROPERTY_NAME() + " uri");
    }

    @Test
    void fromShouldThrowWhenAdminURIIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", "pulsar://locahost.test:6650/");
        configuration.addProperty("admin.uri", null);

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the pulsar " + PulsarConfiguration.ADMIN_URI_PROPERTY_NAME() + " uri");
    }

    @Test
    void fromShouldThrowWhenAdminURIIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", "pulsar://locahost.test:6650/");
        configuration.addProperty("admin.uri", "");

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the pulsar " + PulsarConfiguration.ADMIN_URI_PROPERTY_NAME() + " uri");
    }

    @Test
    void fromShouldThrowWhenAdminURIIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", "pulsar://locahost.test:6650/");
        configuration.addProperty("admin.uri", ":invalid");

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("':invalid' is not a valid " + PulsarConfiguration.ADMIN_URI_PROPERTY_NAME() + " uri");
    }

    @Test
    void fromShouldThrowWhenNamespaceIsMissingFromConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", "pulsar://locahost.test:6650/");
        configuration.addProperty("admin.uri", "https://locahost.test:8080/");

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the pulsar namespace as " + PulsarConfiguration.NAMESPACE_PROPERTY_NAME());
    }

    @Test
    void fromShouldThrowWhenNamespaceIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", "pulsar://locahost.test:6650/");
        configuration.addProperty("admin.uri", "https://locahost.test:8080/");
        configuration.addProperty("namespace", null);

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the pulsar namespace as " + PulsarConfiguration.NAMESPACE_PROPERTY_NAME());
    }

    @Test
    void fromShouldThrowWhenNamespaceIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("broker.uri", "pulsar://locahost.test:6650/");
        configuration.addProperty("admin.uri", "https://locahost.test:8080/");
        configuration.addProperty("namespace", "");

        assertThatThrownBy(() -> PulsarConfiguration.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the pulsar namespace as " + PulsarConfiguration.NAMESPACE_PROPERTY_NAME());
    }


    @Test
    void fromShouldReturnTheConfigurationWhenRequiredParametersAreGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String brokerUri = "pulsar://localhost.test:6650/";
        String adminUri = "http://localhost:8090";
        configuration.addProperty("broker.uri", brokerUri);
        configuration.addProperty("admin.uri", adminUri);
        String namespace = "namespace";

        configuration.addProperty("namespace", namespace);
        assertThat(PulsarConfiguration.from(configuration))
                .isEqualTo(new PulsarConfiguration(brokerUri, adminUri, new Namespace(namespace)));
    }


}