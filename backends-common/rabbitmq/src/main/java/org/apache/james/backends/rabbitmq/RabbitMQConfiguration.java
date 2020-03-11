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

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class RabbitMQConfiguration {

    public static class ManagementCredentials {

        static ManagementCredentials from(Configuration configuration) {
            String user = configuration.getString(MANAGEMENT_CREDENTIAL_USER_PROPERTY);
            Preconditions.checkState(!Strings.isNullOrEmpty(user), "You need to specify the " +
                MANAGEMENT_CREDENTIAL_USER_PROPERTY + " property as username of rabbitmq management admin account");

            String passwordString = configuration.getString(MANAGEMENT_CREDENTIAL_PASSWORD_PROPERTY);
            Preconditions.checkState(!Strings.isNullOrEmpty(passwordString), "You need to specify the " +
                MANAGEMENT_CREDENTIAL_PASSWORD_PROPERTY + " property as password of rabbitmq management admin account");

            return new ManagementCredentials(user, passwordString.toCharArray());
        }

        private static final String MANAGEMENT_CREDENTIAL_USER_PROPERTY = "management.user";
        private static final String MANAGEMENT_CREDENTIAL_PASSWORD_PROPERTY = "management.password";
        private final String user;
        private final char[] password;

        ManagementCredentials(String user, char[] password) {
            Preconditions.checkNotNull(user);
            Preconditions.checkNotNull(password);
            this.user = user;
            this.password = password;
        }

        public String getUser() {
            return user;
        }

        public char[] getPassword() {
            return password;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ManagementCredentials) {
                ManagementCredentials that = (ManagementCredentials) o;
                return Objects.equals(this.user, that.user)
                    && Arrays.equals(this.password, that.password);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(user, Arrays.hashCode(password));
        }
    }

    @FunctionalInterface
    public interface RequireAmqpUri {
        RequireManagementUri amqpUri(URI amqpUri);
    }

    @FunctionalInterface
    public interface RequireManagementUri {
        RequireManagementCredentials managementUri(URI managementUri);
    }

    @FunctionalInterface
    public interface RequireManagementCredentials {
        Builder managementCredentials(ManagementCredentials managementCredentials);
    }

    public static class Builder {
        static final int DEFAULT_MAX_RETRIES = 7;
        static final int DEFAULT_MIN_DELAY = 3000;
        static final int DEFAULT_CONNECTION_TIMEOUT = 60_000;
        static final int DEFAULT_CHANNEL_RPC_TIMEOUT = 60_000;
        static final int DEFAULT_HANDSHAKE_TIMEOUT = 10_000;
        static final int DEFAULT_SHUTDOWN_TIMEOUT = 10_000;
        static final int DEFAULT_NETWORK_RECOVERY_INTERVAL = 5_000;

        private final URI amqpUri;
        private final URI managementUri;
        private final ManagementCredentials managementCredentials;
        private Optional<Integer> maxRetries;
        private Optional<Integer> minDelayInMs;
        private Optional<Integer> connectionTimeoutInMs;
        private Optional<Integer> channelRpcTimeoutInMs;
        private Optional<Integer> handshakeTimeoutInMs;
        private Optional<Integer> shutdownTimeoutInMs;
        private Optional<Integer> networkRecoveryIntervalInMs;

        private Builder(URI amqpUri, URI managementUri, ManagementCredentials managementCredentials) {
            this.amqpUri = amqpUri;
            this.managementUri = managementUri;
            this.managementCredentials = managementCredentials;
            this.maxRetries = Optional.empty();
            this.minDelayInMs = Optional.empty();
            this.connectionTimeoutInMs = Optional.empty();
            this.channelRpcTimeoutInMs = Optional.empty();
            this.handshakeTimeoutInMs = Optional.empty();
            this.shutdownTimeoutInMs = Optional.empty();
            this.networkRecoveryIntervalInMs = Optional.empty();
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Optional.of(maxRetries);
            return this;
        }

        public Builder minDelayInMs(int minDelay) {
            this.minDelayInMs = Optional.of(minDelay);
            return this;
        }

        public Builder connectionTimeoutInMs(int connectionTimeout) {
            this.connectionTimeoutInMs = Optional.of(connectionTimeout);
            return this;
        }

        public Builder channelRpcTimeoutInMs(int channelRpcTimeout) {
            this.channelRpcTimeoutInMs = Optional.of(channelRpcTimeout);
            return this;
        }

        public Builder handshakeTimeoutInMs(int handshakeTimeout) {
            this.handshakeTimeoutInMs = Optional.of(handshakeTimeout);
            return this;
        }

        public Builder shutdownTimeoutInMs(int shutdownTimeout) {
            this.shutdownTimeoutInMs = Optional.of(shutdownTimeout);
            return this;
        }

        public Builder networkRecoveryIntervalInMs(int networkRecoveryInterval) {
            this.networkRecoveryIntervalInMs = Optional.of(networkRecoveryInterval);
            return this;
        }

        public RabbitMQConfiguration build() {
            Preconditions.checkNotNull(amqpUri, "'amqpUri' should not be null");
            Preconditions.checkNotNull(managementUri, "'managementUri' should not be null");
            Preconditions.checkNotNull(managementCredentials, "'managementCredentials' should not be null");
            return new RabbitMQConfiguration(amqpUri,
                    managementUri,
                    managementCredentials,
                    maxRetries.orElse(DEFAULT_MAX_RETRIES),
                    minDelayInMs.orElse(DEFAULT_MIN_DELAY),
                    connectionTimeoutInMs.orElse(DEFAULT_CONNECTION_TIMEOUT),
                    channelRpcTimeoutInMs.orElse(DEFAULT_CHANNEL_RPC_TIMEOUT),
                    handshakeTimeoutInMs.orElse(DEFAULT_HANDSHAKE_TIMEOUT),
                    shutdownTimeoutInMs.orElse(DEFAULT_SHUTDOWN_TIMEOUT),
                    networkRecoveryIntervalInMs.orElse(DEFAULT_NETWORK_RECOVERY_INTERVAL)
                );
        }
    }

    private static final String URI_PROPERTY_NAME = "uri";
    private static final String MANAGEMENT_URI_PROPERTY_NAME = "management.uri";

    public static RequireAmqpUri builder() {
        return amqpUri -> managementUri -> managementCredentials -> new Builder(amqpUri, managementUri, managementCredentials);
    }

    public static RabbitMQConfiguration from(Configuration configuration) {
        String uriAsString = configuration.getString(URI_PROPERTY_NAME);
        Preconditions.checkState(!Strings.isNullOrEmpty(uriAsString), "You need to specify the URI of RabbitMQ");
        URI amqpUri = checkURI(uriAsString);

        String managementUriAsString = configuration.getString(MANAGEMENT_URI_PROPERTY_NAME);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementUriAsString), "You need to specify the management URI of RabbitMQ");
        URI managementUri = checkURI(managementUriAsString);

        ManagementCredentials managementCredentials = ManagementCredentials.from(configuration);
        return builder()
            .amqpUri(amqpUri)
            .managementUri(managementUri)
            .managementCredentials(managementCredentials)
            .build();
    }

    private static URI checkURI(String uri) {
        try {
            return URI.create(uri);
        } catch (Exception e) {
            throw new IllegalStateException("You need to specify a valid URI", e);
        }
    }

    private final URI uri;
    private final URI managementUri;
    private final int maxRetries;
    private final int minDelayInMs;
    private final int connectionTimeoutInMs;
    private final int channelRpcTimeoutInMs;
    private final int handshakeTimeoutInMs;
    private final int shutdownTimeoutInMs;
    private final int networkRecoveryIntervalInMs;


    private final ManagementCredentials managementCredentials;

    private RabbitMQConfiguration(URI uri, URI managementUri, ManagementCredentials managementCredentials, int maxRetries, int minDelayInMs,
                                  int connectionTimeoutInMs, int channelRpcTimeoutInMs, int handshakeTimeoutInMs, int shutdownTimeoutInMs, int networkRecoveryIntervalInMs) {
        this.uri = uri;
        this.managementUri = managementUri;
        this.managementCredentials = managementCredentials;
        this.maxRetries = maxRetries;
        this.minDelayInMs = minDelayInMs;
        this.connectionTimeoutInMs = connectionTimeoutInMs;
        this.channelRpcTimeoutInMs = channelRpcTimeoutInMs;
        this.handshakeTimeoutInMs = handshakeTimeoutInMs;
        this.shutdownTimeoutInMs = shutdownTimeoutInMs;
        this.networkRecoveryIntervalInMs = networkRecoveryIntervalInMs;
    }

    public URI getUri() {
        return uri;
    }

    public URI getManagementUri() {
        return managementUri;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getMinDelayInMs() {
        return minDelayInMs;
    }

    public int getConnectionTimeoutInMs() {
        return connectionTimeoutInMs;
    }

    public int getChannelRpcTimeoutInMs() {
        return channelRpcTimeoutInMs;
    }

    public int getHandshakeTimeoutInMs() {
        return handshakeTimeoutInMs;
    }

    public int getShutdownTimeoutInMs() {
        return shutdownTimeoutInMs;
    }

    public int getNetworkRecoveryIntervalInMs() {
        return networkRecoveryIntervalInMs;
    }

    public ManagementCredentials getManagementCredentials() {
        return managementCredentials;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RabbitMQConfiguration) {
            RabbitMQConfiguration that = (RabbitMQConfiguration) o;

            return Objects.equals(this.uri, that.uri)
                && Objects.equals(this.managementUri, that.managementUri)
                && Objects.equals(this.maxRetries, that.maxRetries)
                && Objects.equals(this.minDelayInMs, that.minDelayInMs)
                && Objects.equals(this.connectionTimeoutInMs, that.connectionTimeoutInMs)
                && Objects.equals(this.channelRpcTimeoutInMs, that.channelRpcTimeoutInMs)
                && Objects.equals(this.handshakeTimeoutInMs, that.handshakeTimeoutInMs)
                && Objects.equals(this.shutdownTimeoutInMs, that.shutdownTimeoutInMs)
                && Objects.equals(this.networkRecoveryIntervalInMs, that.networkRecoveryIntervalInMs)
                && Objects.equals(this.managementCredentials, that.managementCredentials
            );
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uri, managementUri, maxRetries, minDelayInMs, connectionTimeoutInMs,
            channelRpcTimeoutInMs, handshakeTimeoutInMs, shutdownTimeoutInMs, networkRecoveryIntervalInMs, managementCredentials);
    }
}
