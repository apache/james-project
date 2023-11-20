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
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_PASSWORD_STRING;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.james.util.Host;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

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
        configuration.addProperty("uri", "amqp://james:james@rabbitmqhost:5672");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("management.uri", null);

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("management.uri", "");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management URI of RabbitMQ");
    }

    @Test
    void fromShouldThrowWhenManagementURIIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("management.uri", ":invalid");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify a valid URI");
    }

    @Test
    void fromShouldReturnTheConfigurationWhenRequiredParametersAreGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        assertThat(RabbitMQConfiguration.from(configuration))
            .isEqualTo(RabbitMQConfiguration.builder()
                .amqpUri(URI.create(amqpUri))
                .managementUri(URI.create(managementUri))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .hosts(ImmutableList.of(Host.from("rabbitmqhost", 5672)))
                .build());
    }

    @Test
    void fromShouldThrowWhenManagementCredentialsAreNotGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
        configuration.addProperty("management.uri", managementUri);

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You need to specify the management.user property as username of rabbitmq management admin account");
    }

    @Test
    void fromShouldReturnCustomValueWhenManagementCredentialsAreGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
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
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThat(rabbitMQConfiguration.getMaxRetries())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_MAX_RETRIES);
    }

    @Test
    void maxRetriesShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int maxRetries = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .maxRetries(maxRetries)
            .build();

        assertThat(rabbitMQConfiguration.getMaxRetries())
            .isEqualTo(maxRetries);
    }

    @Test
    void minDelayShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThat(rabbitMQConfiguration.getMinDelayInMs())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_MIN_DELAY);
    }

    @Test
    void minDelayShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int minDelay = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .minDelayInMs(minDelay)
            .build();

        assertThat(rabbitMQConfiguration.getMinDelayInMs())
            .isEqualTo(minDelay);
    }

    @Test
    void connectionTimeoutShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThat(rabbitMQConfiguration.getConnectionTimeoutInMs())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_CONNECTION_TIMEOUT);
    }

    @Test
    void connectionTimeoutShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int connectionTimeout = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .connectionTimeoutInMs(connectionTimeout)
            .build();

        assertThat(rabbitMQConfiguration.getConnectionTimeoutInMs())
            .isEqualTo(connectionTimeout);
    }

    @Test
    void channelRpcTimeoutShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThat(rabbitMQConfiguration.getChannelRpcTimeoutInMs())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_CHANNEL_RPC_TIMEOUT);
    }

    @Test
    void channelRpcTimeoutShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int channelRpcTimeout = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .channelRpcTimeoutInMs(channelRpcTimeout)
            .build();

        assertThat(rabbitMQConfiguration.getChannelRpcTimeoutInMs())
            .isEqualTo(channelRpcTimeout);
    }
    
    @Test
    void handshakeTimeoutShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThat(rabbitMQConfiguration.getHandshakeTimeoutInMs())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_HANDSHAKE_TIMEOUT);
    }

    @Test
    void handshakeTimeoutShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int handshakeTimeout = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .handshakeTimeoutInMs(handshakeTimeout)
            .build();

        assertThat(rabbitMQConfiguration.getHandshakeTimeoutInMs())
            .isEqualTo(handshakeTimeout);
    }   
    
    @Test
    void shutdownTimeoutShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThat(rabbitMQConfiguration.getShutdownTimeoutInMs())
            .isEqualTo(RabbitMQConfiguration.Builder.DEFAULT_SHUTDOWN_TIMEOUT);
    }

    @Test
    void shutdownTimeoutShouldEqualsCustomValueWhenGiven() throws URISyntaxException {
        int shutdownTimeout = 1;

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .shutdownTimeoutInMs(shutdownTimeout)
            .build();

        assertThat(rabbitMQConfiguration.getShutdownTimeoutInMs())
            .isEqualTo(shutdownTimeout);
    }

    @Test
    void sslConfigurationShouldHaveDefaultWhenNotSpecifiedOtherwise() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
                .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
                .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .build();

        assertThat(rabbitMQConfiguration.getSslConfiguration().getHostNameVerifier())
                .isEqualTo(RabbitMQConfiguration.SSLConfiguration.HostNameVerifier.DEFAULT);

        assertThat(rabbitMQConfiguration.getSslConfiguration().getStrategy())
                .isEqualTo(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.DEFAULT);

        assertThat(rabbitMQConfiguration.getSslConfiguration().getTrustStore())
                .isEmpty();

        assertThat(rabbitMQConfiguration.getSslConfiguration().getKeyStore())
                .isEmpty();
    }

    @Test
    void sslConfigurationShouldHaveCustomValuesIfUseInConfiguration() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
                .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
                .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .sslConfiguration(
                        RabbitMQConfiguration.SSLConfiguration.builder()
                            .strategyOverride(RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of("src/test/resources/test-truststore-password-password", "password"))
                            .acceptAnyHostNameVerifier()
                            .sslKeyStore(Optional.of(RabbitMQConfiguration.SSLConfiguration.SSLKeyStore.of("src/test/resources/test-keystore-password-password", "password")))
                            .build()
                )
                .build();

        assertThat(rabbitMQConfiguration.getSslConfiguration().getHostNameVerifier())
                .isEqualTo(RabbitMQConfiguration.SSLConfiguration.HostNameVerifier.ACCEPT_ANY_HOSTNAME);

        assertThat(rabbitMQConfiguration.getSslConfiguration().getStrategy())
                .isEqualTo(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.OVERRIDE);

        assertThat(rabbitMQConfiguration.getSslConfiguration().getTrustStore())
                .isNotEmpty();

        assertThat(rabbitMQConfiguration.getSslConfiguration().getKeyStore())
                .isNotEmpty();
    }

    @Test
    void queueTTLShouldEqualsDefaultValueWhenNotGiven() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://james:james@rabbitmqhost:5672"))
            .managementUri(new URI("http://james:james@rabbitmqhost:15672/api/"))
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        assertThat(rabbitMQConfiguration.getQueueTTL())
            .isEqualTo(Optional.empty());
    }

    @Test
    void fromShouldReturnCustomQueueTTLValueWhenGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        configuration.addProperty("notification.queue.ttl", 99999);

        assertThat(RabbitMQConfiguration.from(configuration).getQueueTTL())
            .isEqualTo(Optional.of(99999L));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void fromShouldThrowWhenQueueTTLIsNotPositive(long ttl) {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        configuration.addProperty("notification.queue.ttl", ttl);

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'notification.queue.ttl' must be strictly positive");
    }

    @Test
    void fromShouldThrowWhenQueueTTLIsNotANumber() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        configuration.addProperty("notification.queue.ttl", "notnumber");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(ConversionException.class);
    }

    @Test
    void emptyTaskQueueConsumerTimeoutShouldDefaultToOneDay() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("management.uri", "http://james:james@rabbitmqhost:15672/api/");
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        assertThat(RabbitMQConfiguration.from(configuration).getTaskQueueConsumerTimeout())
            .isEqualTo(Duration.ofDays(1));
    }

    @Test
    void parseValidTaskQueueConsumerTimeoutShouldSucceed() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("management.uri", "http://james:james@rabbitmqhost:15672/api/");
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        configuration.addProperty("task.queue.consumer.timeout", "2day");

        assertThat(RabbitMQConfiguration.from(configuration).getTaskQueueConsumerTimeout())
            .isEqualTo(Duration.ofDays(2));
    }

    @Test
    void parseTaskQueueConsumerTimeoutWithoutTimeUnitShouldDefaultToSecond() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("management.uri", "http://james:james@rabbitmqhost:15672/api/");
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        configuration.addProperty("task.queue.consumer.timeout", "3600");

        assertThat(RabbitMQConfiguration.from(configuration).getTaskQueueConsumerTimeout())
            .isEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    void parseInvalidTaskQueueConsumerTimeoutShouldFail() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("management.uri", "http://james:james@rabbitmqhost:15672/api/");
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        configuration.addProperty("task.queue.consumer.timeout", "invalid");

        assertThatThrownBy(() -> RabbitMQConfiguration.from(configuration))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void fromShouldReturnEmptyVhostValueByDefault() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        assertThat(RabbitMQConfiguration.from(configuration).getVhost())
            .isEqualTo(Optional.empty());
    }

    @Test
    void fromShouldReturnVhostValueWhenGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        configuration.addProperty("vhost", "test");

        assertThat(RabbitMQConfiguration.from(configuration).getVhost())
            .isEqualTo(Optional.of("test"));
    }

    @Test
    void fromShouldReturnVhostValueWhenDeclaredInURI() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672/test";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        assertThat(RabbitMQConfiguration.from(configuration).getVhost())
            .isEqualTo(Optional.of("test"));
    }

    @Test
    void fromShouldReturnVhostValueWhenGivenAndNotUriOne() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String amqpUri = "amqp://james:james@rabbitmqhost:5672/test";
        configuration.addProperty("uri", amqpUri);
        String managementUri = "http://james:james@rabbitmqhost:15672/api/";
        configuration.addProperty("management.uri", managementUri);
        configuration.addProperty("management.user", DEFAULT_USER);
        configuration.addProperty("management.password", DEFAULT_PASSWORD_STRING);

        configuration.addProperty("vhost", "vhosttest");

        assertThat(RabbitMQConfiguration.from(configuration).getVhost())
            .isEqualTo(Optional.of("vhosttest"));
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

    @Nested
    class SSLConfigurationTest {

        @Nested
        class SSLValidationStrategyTest {

            @Test
            void fromShouldThrowExceptionWhenUnknownStrategyNameSupplied() {
                assertThatThrownBy(() -> RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.from("random"))
                    .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void fromShouldReturnWhenCorrectNamesAreUsed() {
                assertThat(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.from("default"))
                    .isEqualTo(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.DEFAULT);

                assertThat(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.from("override"))
                        .isEqualTo(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.OVERRIDE);

                assertThat(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.from("ignore"))
                        .isEqualTo(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.IGNORE);
            }

        }

        @Nested
        class HostNameVerifierTest {

            @Test
            void fromShouldThrowExceptionWhenUnknownStrategyNameSupplied() {
                assertThatThrownBy(() -> RabbitMQConfiguration.SSLConfiguration.HostNameVerifier.from("random"))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void fromShouldReturnWhenCorrectNamesAreUsed() {
                assertThat(RabbitMQConfiguration.SSLConfiguration.HostNameVerifier.from("default"))
                        .isEqualTo(RabbitMQConfiguration.SSLConfiguration.HostNameVerifier.DEFAULT);

                assertThat(RabbitMQConfiguration.SSLConfiguration.HostNameVerifier.from("accept_any_hostname"))
                        .isEqualTo(RabbitMQConfiguration.SSLConfiguration.HostNameVerifier.ACCEPT_ANY_HOSTNAME);

            }

        }

        @Nested
        class SSLTrustStoreTest {

            @Test
            void ofShouldThrowExceptionWhenFilePathNotSupplied() {
                assertThatThrownBy(() -> RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of(null, "password"))
                    .isInstanceOf(NullPointerException.class);
            }

            @Test
            void ofShouldThrowExceptionWhenPasswordNotSupplied() {
                assertThatThrownBy(() -> RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of("/path/to/file", null))
                        .isInstanceOf(NullPointerException.class);
            }

            @Test
            void ofShouldThrowExceptionWhenFileDoesNotExist() {
                assertThatThrownBy(() -> RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of("/does/not/exist", "password"))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void ofShouldReturnWhenCorrectAttributesUsed() throws IOException {
                File tempFile = File.createTempFile("for-james-test", "");
                tempFile.deleteOnExit();

                assertThat(RabbitMQConfiguration.SSLConfiguration.SSLTrustStore.of(tempFile.getAbsolutePath(), "password"))
                        .matches(sslTrustStore -> sslTrustStore.getFile().equals(tempFile))
                        .matches(sslTrustStore -> Arrays.equals(sslTrustStore.getPassword(), "password".toCharArray()));
            }

        }

    }
}
