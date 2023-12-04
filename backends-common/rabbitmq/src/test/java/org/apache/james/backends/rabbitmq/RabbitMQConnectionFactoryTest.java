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
package org.apache.james.backends.rabbitmq;

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RabbitMQConnectionFactoryTest {
    @Test
    void creatingAFactoryShouldWorkWhenConfigurationIsValid() {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(URI.create("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(URI.create("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        new RabbitMQConnectionFactory(rabbitMQConfiguration);
    }

    @Test
    void creatingAFactoryShouldWorkWhenConfigurationIsValidWithSSl() {
        RabbitMQConfiguration.SSLConfiguration sslConfiguration = RabbitMQConfiguration.SSLConfiguration.builder()
                .strategyOverride(RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of(Paths.get("src", "test", "resources", "test-truststore-password-password").toString(), "password"))
                .defaultHostNameVerifier()
                .sslKeyStore(Optional.of(RabbitMQConfiguration.SSLConfiguration.SSLKeyStore.of(Paths.get("src", "test", "resources", "test-keystore-password-password").toString(), "password")))
                .build();

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
                .amqpUri(URI.create("amqp://james:james@rabbitmqhost:5672"))
                .managementUri(URI.create("http://james:james@rabbitmqhost:15672/api/"))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .useSsl(true)
                .sslConfiguration(sslConfiguration)
                .build();

        new RabbitMQConnectionFactory(rabbitMQConfiguration);
    }

    @Test
    void creatingAFactoryShouldThrowWhenConfigurationIsInvalid() {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(URI.create("badprotocol://james:james@rabbitmqhost:5672"))
            .managementUri(URI.create("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThatThrownBy(() -> new RabbitMQConnectionFactory(rabbitMQConfiguration))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void createShouldFailWhenConnectionCantBeDone() {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
                .amqpUri(URI.create("amqp://james:james@rabbitmqhost:5672"))
                .managementUri(URI.create("http://james:james@rabbitmqhost:15672/api/"))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .maxRetries(1)
                .minDelayInMs(1)
                .build();

        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(rabbitMQConfiguration);

        assertThatThrownBy(rabbitMQConnectionFactory::create)
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void creatingAFactoryShouldFailWhenTrustStoreFileisEmpty() {
        RabbitMQConfiguration.SSLConfiguration sslConfiguration = RabbitMQConfiguration.SSLConfiguration.builder()
                .strategyOverride(RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of(Paths.get("src", "test", "resources", "empty-store").toString(), "password"))
                .defaultHostNameVerifier()
                .build();

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
                .amqpUri(URI.create("amqp://james:james@rabbitmqhost:5672"))
                .managementUri(URI.create("http://james:james@rabbitmqhost:15672/api/"))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .maxRetries(1)
                .minDelayInMs(1)
                .useSsl(true)
                .sslConfiguration(sslConfiguration)
                .build();

        assertThatThrownBy(() -> new RabbitMQConnectionFactory(rabbitMQConfiguration))
                .isInstanceOf(RuntimeException.class)
                .hasCause(new IOException("Tag number over 30 is not supported"));
    }

    @Test
    void creatingAFactoryShouldFailWhenTrustStorePasswordIsIncorrect() {
        RabbitMQConfiguration.SSLConfiguration sslConfiguration = RabbitMQConfiguration.SSLConfiguration.builder()
                .strategyOverride(RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of(Paths.get("src", "test", "resources", "test-truststore-password-password").toString(), "wrong-password"))
                .defaultHostNameVerifier()
                .build();

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
                .amqpUri(URI.create("amqp://james:james@rabbitmqhost:5672"))
                .managementUri(URI.create("http://james:james@rabbitmqhost:15672/api/"))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .maxRetries(1)
                .minDelayInMs(1)
                .useSsl(true)
                .sslConfiguration(sslConfiguration)
                .build();

        assertThatThrownBy(() -> new RabbitMQConnectionFactory(rabbitMQConfiguration))
                .isInstanceOf(RuntimeException.class)
                .hasCause(new IOException("keystore password was incorrect"));
    }

    @Test
    void creatingAFactoryShouldFailWhenKeyStoreIsEmpty() {
        RabbitMQConfiguration.SSLConfiguration sslConfiguration = RabbitMQConfiguration.SSLConfiguration.builder()
                .strategyOverride(RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of(Paths.get("src", "test", "resources", "test-truststore-password-password").toString(), "password"))
                .defaultHostNameVerifier()
                .sslKeyStore(Optional.of(RabbitMQConfiguration.SSLConfiguration.SSLKeyStore.of(Paths.get("src", "test", "resources", "empty-store").toString(), "password")))
                .build();

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
                .amqpUri(URI.create("amqp://james:james@rabbitmqhost:5672"))
                .managementUri(URI.create("http://james:james@rabbitmqhost:15672/api/"))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .maxRetries(1)
                .minDelayInMs(1)
                .useSsl(true)
                .sslConfiguration(sslConfiguration)
                .build();

        assertThatThrownBy(() -> new RabbitMQConnectionFactory(rabbitMQConfiguration))
                .isInstanceOf(RuntimeException.class)
                .hasCause(new IOException("Tag number over 30 is not supported"));
    }

    @Test
    void creatingAFactoryShouldFailWhenKeyStorePasswordIsIncorrect() {
        RabbitMQConfiguration.SSLConfiguration sslConfiguration = RabbitMQConfiguration.SSLConfiguration.builder()
                .strategyOverride(RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of(Paths.get("src", "test", "resources", "test-truststore-password-password").toString(), "password"))
                .defaultHostNameVerifier()
                .sslKeyStore(Optional.of(RabbitMQConfiguration.SSLConfiguration.SSLKeyStore.of(Paths.get("src", "test", "resources", "test-keystore-password-password").toString(), "wrong-password")))
                .build();

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
                .amqpUri(URI.create("amqp://james:james@rabbitmqhost:5672"))
                .managementUri(URI.create("http://james:james@rabbitmqhost:15672/api/"))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .maxRetries(1)
                .minDelayInMs(1)
                .useSsl(true)
                .sslConfiguration(sslConfiguration)
                .build();

        assertThatThrownBy(() -> new RabbitMQConnectionFactory(rabbitMQConfiguration))
                .isInstanceOf(RuntimeException.class)
                .hasCause(new IOException("keystore password was incorrect"));
    }

}
