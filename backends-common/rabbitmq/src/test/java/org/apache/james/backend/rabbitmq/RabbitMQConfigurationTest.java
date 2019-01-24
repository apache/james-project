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
package org.apache.james.backend.rabbitmq;

import static org.apache.james.backend.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.apache.james.backend.rabbitmq.RabbitMQFixture.DEFAULT_PASSWORD_STRING;
import static org.apache.james.backend.rabbitmq.RabbitMQFixture.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RabbitMQConfigurationTest {

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(RabbitMQConfiguration.class).verify();
    }

    @Test
    void fromShouldThrowWhenURIIsNotInTheConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenURIIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", null);

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenURIIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenURIIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", ":invalid");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify a valid URI");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsNotInTheConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmq_host:5672");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmq_host:5672");
        configuration.addProperty("management.uri", null);

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmq_host:5672");
        configuration.addProperty("management.uri", "");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmq_host:5672");
        configuration.addProperty("management.uri", ":invalid");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify a valid URI");
    }

    @Test
    void fromShouldReturnTheConfigurationWhenRequiredParametersAreGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmq_host:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmq_host:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        assertThat(RabbitMQConfiguration.from(configuration))
            .isEqualTo(RabbitMQConfiguration.builder()
                .amqpUri(URI.create(amqpUri))
                .managementUri(URI.create(managementUri))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .build());
    }

    @Test
    void fromShouldThrowWhenManagementCredentialsAreNotGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmq_host:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmq_host:15672/api/";
        configuration.addProperty("management.uri", managementUri);

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management.user property as username of rabbitmq management admin account");
    }

    @Test
    void fromShouldReturnCustomValueWhenManagementCredentialsAreGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmq_host:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmq_host:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        String user = "james";
        configuration.addProperty("management.user", user);
        String passwordString = "james_password";
        configuration.addProperty("management.password", passwordString);

        RabbitMQConfiguration.ManagementCredentials credentials = new RabbitMQConfiguration.ManagementCredentials(
            user, passwordString.toCharArray());

        assertThat(RabbitMQConfiguration.from(configuration).getManagementCredentials())
            .isEqualTo(credentials);
    }

    @Test
    void maxRetriesShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThat(rabbitMQConfiguration.getMaxRetries())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_MAX_RETRIES);
    }

    @Test
    void maxRetriesShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int maxRetries = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .maxRetries(maxRetries)
            .build();

        assertThat(rabbitMQConfiguration.getMaxRetries())
            .isEqualTo(maxRetries);
    }

    @Test
    void minDelayShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThat(rabbitMQConfiguration.getMinDelayInMs())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_MIN_DELAY);
    }

    @Test
    void minDelayShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int minDelay = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmq_host:5672"))
            .managementUri(new URI("http://james:james@rabbitmq_host:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .minDelayInMs(minDelay)
            .build();

        assertThat(rabbitMQConfiguration.getMinDelayInMs())
            .isEqualTo(minDelay);
    }

    @Nested
    class ManagementCredentialsTest {
        @Test
        void managementCredentialShouldRespectBeanContract() {
            EqualsVerifier.forClass(RabbitMQConfiguration.ManagementCredentials.class)
                .verify();
        }

        @Test
        void fromShouldThrowWhenUserAndPasswordAreNotGiven() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();

            assertThatThrownBy(() -> RabbitMQConfiguration.ManagementCredentials.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the management.user property as username of rabbitmq management admin account");
        }

        @Test
        void fromShouldThrowWhenUserIsNotGiven() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            String passwordString = "password";
            configuration.addProperty("management.password", passwordString);

            assertThatThrownBy(() -> RabbitMQConfiguration.ManagementCredentials.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the management.user property as username of rabbitmq management admin account");
        }

        @Test
        void fromShouldThrowWhenPasswordIsNotGiven() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            String userString = "guest";
            configuration.addProperty("management.user", userString);

            assertThatThrownBy(() -> RabbitMQConfiguration.ManagementCredentials.from(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You need to specify the management.password property as password of rabbitmq management admin account");
        }

        @Test
        void fromShouldReturnCorrespondingCredentialWhenGiven() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            String userString = "guest";
            configuration.addProperty("management.user", userString);
            String passwordString = "password";
            configuration.addProperty("management.password", passwordString);

            RabbitMQConfiguration.ManagementCredentials credentialWithUserAndPassword = new RabbitMQConfiguration.ManagementCredentials(
                userString, passwordString.toCharArray());
            assertThat(RabbitMQConfiguration.ManagementCredentials.from(configuration))
                .isEqualTo(credentialWithUserAndPassword);
        }
    }
}
