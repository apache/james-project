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

import static org.apache.james.backends.rabbitmq.RabbitMQConfiguration.Builder.DEFAULT_PORT;
import static org.apache.james.backends.rabbitmq.RabbitMQConfiguration.SSLConfiguration.HostNameVerifier;
import static org.apache.james.backends.rabbitmq.RabbitMQConfiguration.SSLConfiguration.SSLKeyStore;
import static org.apache.james.backends.rabbitmq.RabbitMQConfiguration.SSLConfiguration.SSLTrustStore;
import static org.apache.james.backends.rabbitmq.RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy;
import static org.apache.james.backends.rabbitmq.RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.OVERRIDE;
import static org.apache.james.backends.rabbitmq.RabbitMQConfiguration.SSLConfiguration.defaultBehavior;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;
import org.apache.james.util.Host;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class RabbitMQConfiguration {

    public static class SSLConfiguration {

        public enum SSLValidationStrategy {
            DEFAULT,
            IGNORE,
            OVERRIDE;

            static SSLValidationStrategy from(String rawValue) {
                Preconditions.checkNotNull(rawValue);

                return Stream.of(values())
                        .filter(strategy -> strategy.name().equalsIgnoreCase(rawValue))
                        .findAny()
                        .orElseThrow(() -> new IllegalArgumentException(String.format("invalid strategy '%s'", rawValue)));

            }
        }

        public enum HostNameVerifier {
            DEFAULT,
            ACCEPT_ANY_HOSTNAME;

            static HostNameVerifier from(String rawValue) {
                Preconditions.checkNotNull(rawValue);

                return Stream.of(values())
                        .filter(verifier -> verifier.name().equalsIgnoreCase(rawValue))
                        .findAny()
                        .orElseThrow(() -> new IllegalArgumentException(String.format("invalid HostNameVerifier '%s'", rawValue)));

            }
        }

        public static class SSLKeyStore {

            public static SSLKeyStore of(String filePath, String password) {
                return new SSLKeyStore(filePath, password);
            }

            private final File file;
            private final char[] password;

            private SSLKeyStore(String filePath, String password) {
                Preconditions.checkNotNull(filePath, "%s cannot be null when %s is specified",
                        SSL_KEY_STORE_PATH, SSL_KEY_STORE_PASSWORD);
                Preconditions.checkNotNull(password,
                        "%s cannot be null when %s is specified",
                        SSL_KEY_STORE_PASSWORD, SSL_KEY_STORE_PATH);
                Preconditions.checkArgument(Files.exists(Paths.get(filePath)),
                        "the file '%s' from property '%s' doesn't exist", filePath, SSL_KEY_STORE_PATH);

                this.file = new File(filePath);
                this.password = password.toCharArray();
            }

            public File getFile() {
                return file;
            }

            public char[] getPassword() {
                return password;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof SSLKeyStore) {
                    SSLKeyStore that = (SSLKeyStore) o;

                    return Objects.equals(this.file, that.file)
                            && Arrays.equals(this.password, that.password);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(file, Arrays.hashCode(password));
            }
        }


        public static class SSLTrustStore {

            public static SSLTrustStore of(String filePath, String password) {
                return new SSLTrustStore(filePath, password);
            }

            private final File file;
            private final char[] password;

            private SSLTrustStore(String filePath, String password) {
                Preconditions.checkNotNull(filePath, "%s cannot be null when %s is specified",
                        SSL_TRUST_STORE_PATH, SSL_TRUST_STORE_PASSWORD);
                Preconditions.checkNotNull(password,
                        "%s cannot be null when %s is specified",
                        SSL_TRUST_STORE_PASSWORD, SSL_TRUST_STORE_PATH);
                Preconditions.checkArgument(Files.exists(Paths.get(filePath)),
                        "the file '%s' from property '%s' doesn't exist", filePath, SSL_TRUST_STORE_PATH);

                this.file = new File(filePath);
                this.password = password.toCharArray();
            }

            public File getFile() {
                return file;
            }

            public char[] getPassword() {
                return password;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof SSLTrustStore) {
                    SSLTrustStore that = (SSLTrustStore) o;

                    return Objects.equals(this.file, that.file)
                            && Arrays.equals(this.password, that.password);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(file, Arrays.hashCode(password));
            }
        }

        static class Builder {

            @FunctionalInterface
            interface RequireSSLStrategyTrustStore {
                RequireHostNameVerifier sslStrategy(SSLValidationStrategy strategy, Optional<SSLTrustStore> trustStore);

                default RequireHostNameVerifier strategyIgnore() {
                    return sslStrategy(SSLValidationStrategy.IGNORE, Optional.empty());
                }

                default RequireHostNameVerifier strategyOverride(SSLTrustStore trustStore) {
                    return sslStrategy(OVERRIDE, Optional.of(trustStore));
                }

                default RequireHostNameVerifier strategyDefault() {
                    return sslStrategy(SSLValidationStrategy.DEFAULT, Optional.empty());
                }
            }

            @FunctionalInterface
            interface RequireHostNameVerifier {
                ReadyToBuild hostNameVerifier(HostNameVerifier hostNameVerifier);

                default ReadyToBuild acceptAnyHostNameVerifier() {
                    return hostNameVerifier(HostNameVerifier.ACCEPT_ANY_HOSTNAME);
                }

                default ReadyToBuild defaultHostNameVerifier() {
                    return hostNameVerifier(HostNameVerifier.DEFAULT);
                }
            }

            static class ReadyToBuild {
                private final SSLValidationStrategy sslValidationStrategy;
                private final HostNameVerifier hostNameVerifier;
                private Optional<SSLTrustStore> sslTrustStore;
                private Optional<SSLKeyStore> sslKeyStore;

                private ReadyToBuild(SSLValidationStrategy sslValidationStrategy, HostNameVerifier hostNameVerifier, Optional<SSLTrustStore> sslTrustStore) {
                    this.sslValidationStrategy = sslValidationStrategy;
                    this.hostNameVerifier = hostNameVerifier;
                    this.sslTrustStore = sslTrustStore;
                    this.sslKeyStore = Optional.empty();
                }

                public ReadyToBuild sslKeyStore(Optional<SSLKeyStore> sslKeyStore) {
                    this.sslKeyStore = sslKeyStore;
                    return this;
                }

                public SSLConfiguration build() {
                    return new SSLConfiguration(sslValidationStrategy, hostNameVerifier, sslTrustStore, sslKeyStore);
                }
            }
        }

        static SSLConfiguration defaultBehavior() {
            return new SSLConfiguration(SSLValidationStrategy.DEFAULT, HostNameVerifier.DEFAULT, Optional.empty(), Optional.empty());
        }

        static Builder.RequireSSLStrategyTrustStore builder() {
            return (strategy, trustStore) -> hostNameVerifier -> new Builder.ReadyToBuild(strategy, hostNameVerifier, trustStore);
        }

        private final SSLValidationStrategy strategy;
        private final HostNameVerifier hostNameVerifier;
        private final Optional<SSLTrustStore> trustStore;
        private final Optional<SSLKeyStore> keyStore;

        private SSLConfiguration(SSLValidationStrategy strategy, HostNameVerifier hostNameVerifier, Optional<SSLTrustStore> trustStore, Optional<SSLKeyStore> keyStore) {
            Preconditions.checkNotNull(strategy);
            Preconditions.checkNotNull(trustStore);
            Preconditions.checkNotNull(hostNameVerifier);
            Preconditions.checkArgument(strategy != OVERRIDE || trustStore.isPresent(),  "%s strategy requires trustStore to be present", OVERRIDE.name());

            this.strategy = strategy;
            this.trustStore = trustStore;
            this.keyStore = keyStore;
            this.hostNameVerifier = hostNameVerifier;
        }

        public SSLValidationStrategy getStrategy() {
            return strategy;
        }

        public Optional<SSLTrustStore> getTrustStore() {
            return trustStore;
        }

        public Optional<SSLKeyStore> getKeyStore() {
            return keyStore;
        }

        public HostNameVerifier getHostNameVerifier() {
            return hostNameVerifier;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof SSLConfiguration) {
                SSLConfiguration that = (SSLConfiguration) o;

                return Objects.equals(this.strategy, that.strategy)
                        && Objects.equals(this.trustStore, that.trustStore)
                        && Objects.equals(this.keyStore, that.keyStore)
                        && Objects.equals(this.hostNameVerifier, that.hostNameVerifier);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(strategy, trustStore, keyStore, hostNameVerifier);
        }
    }

    private static final String USE_SSL = "ssl.enabled";
    private static final String HOSTS = "hosts";
    private static final String USE_QUORUM_QUEUES = "quorum.queues.enable";
    private static final String QUORUM_QUEUES_REPLICATION_FACTOR = "quorum.queues.replication.factor";
    private static final String USE_SSL_MANAGEMENT = "ssl.management.enabled";

    private static final String SSL_TRUST_STORE_PATH = "ssl.truststore";
    private static final String SSL_TRUST_STORE_PASSWORD = "ssl.truststore.password";
    private static final String SSL_VALIDATION_STRATEGY = "ssl.validation.strategy";
    private static final String SSL_HOSTNAME_VERIFIER = "ssl.hostname.verifier";

    private static final String SSL_KEY_STORE_PATH = "ssl.keystore";
    private static final String SSL_KEY_STORE_PASSWORD = "ssl.keystore.password";
    private static final String QUEUE_TTL = "notification.queue.ttl";
    private static final String EVENT_BUS_NOTIFICATION_DURABILITY_ENABLED = "event.bus.notification.durability.enabled";
    private static final String EVENT_BUS_PUBLISH_CONFIRM_ENABLED = "event.bus.publish.confirm.enabled";
    private static final String TASK_QUEUE_CONSUMER_TIMEOUT = "task.queue.consumer.timeout";
    private static final String VHOST = "vhost";

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

        public ManagementCredentials(String user, char[] password) {
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
        static final int DEFAULT_QUORUM_QUEUE_REPLICATION_FACTOR = 1;
        static final int DEFAULT_MAX_RETRIES = 7;
        static final int DEFAULT_MIN_DELAY = 3000;
        static final int DEFAULT_CONNECTION_TIMEOUT = 60_000;
        static final int DEFAULT_CHANNEL_RPC_TIMEOUT = 60_000;
        static final int DEFAULT_HANDSHAKE_TIMEOUT = 10_000;
        static final int DEFAULT_SHUTDOWN_TIMEOUT = 10_000;
        static final int DEFAULT_NETWORK_RECOVERY_INTERVAL = 5_000;
        static final int DEFAULT_PORT = 5672;
        static final Duration DEFAULT_TASK_QUEUE_CONSUMER_TIMEOUT = Duration.ofDays(1);

        private final URI amqpUri;
        private final URI managementUri;
        private final ManagementCredentials managementCredentials;
        private final ImmutableList.Builder<Host> hosts;
        private Optional<Integer> maxRetries;
        private Optional<Integer> minDelayInMs;
        private Optional<Integer> connectionTimeoutInMs;
        private Optional<Integer> channelRpcTimeoutInMs;
        private Optional<Integer> handshakeTimeoutInMs;
        private Optional<Integer> shutdownTimeoutInMs;
        private Optional<Integer> networkRecoveryIntervalInMs;
        private Optional<Boolean> useSsl;
        private Optional<Boolean> useSslManagement;
        private Optional<Boolean> useQuorumQueues;
        private Optional<Integer> quorumQueueReplicationFactor;
        private Optional<SSLConfiguration> sslConfiguration;
        private Optional<Long> queueTTL;
        private Optional<Boolean> eventBusPublishConfirmEnabled;
        private Optional<Boolean> eventBusNotificationDurabilityEnabled;
        private Optional<String> vhost;
        private Optional<Duration> taskQueueConsumerTimeout;

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
            this.useSsl = Optional.empty();
            this.useSslManagement = Optional.empty();
            this.sslConfiguration = Optional.empty();
            this.useQuorumQueues = Optional.empty();
            this.quorumQueueReplicationFactor = Optional.empty();
            this.hosts = ImmutableList.builder();
            this.queueTTL = Optional.empty();
            this.eventBusPublishConfirmEnabled = Optional.empty();
            this.eventBusNotificationDurabilityEnabled = Optional.empty();
            this.vhost = Optional.empty();
            this.taskQueueConsumerTimeout = Optional.empty();
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

        public Builder quorumQueueReplicationFactor(Optional<Integer> quorumQueueReplicationFactor) {
            return quorumQueueReplicationFactor
                .map(this::quorumQueueReplicationFactor)
                .orElse(this);
        }

        public Builder quorumQueueReplicationFactor(int quorumQueueReplicationFactor) {
            Preconditions.checkArgument(quorumQueueReplicationFactor > 0, "'quorumQueueReplicationFactor' must be strictly positive");
            this.quorumQueueReplicationFactor = Optional.of(quorumQueueReplicationFactor);
            return this;
        }

        public Builder networkRecoveryIntervalInMs(int networkRecoveryInterval) {
            this.networkRecoveryIntervalInMs = Optional.of(networkRecoveryInterval);
            return this;
        }

        public Builder useSsl(Boolean useSsl) {
            this.useSsl = Optional.ofNullable(useSsl);
            return this;
        }

        public Builder eventBusPublishConfirmEnabled(Boolean eventBusPublishConfirmEnabled) {
            this.eventBusPublishConfirmEnabled = Optional.ofNullable(eventBusPublishConfirmEnabled);
            return this;
        }

        public Builder eventBusNotificationDurabilityEnabled(Boolean eventBusNotificationDurabilityEnabled) {
            this.eventBusNotificationDurabilityEnabled = Optional.ofNullable(eventBusNotificationDurabilityEnabled);
            return this;
        }

        public Builder useQuorumQueues(Boolean useQuorumQueues) {
            this.useQuorumQueues = Optional.ofNullable(useQuorumQueues);
            return this;
        }

        public Builder useSslManagement(Boolean useSslForManagement) {
            this.useSslManagement = Optional.of(useSslForManagement);
            return this;
        }

        public Builder sslConfiguration(SSLConfiguration sslConfiguration) {
            this.sslConfiguration = Optional.of(sslConfiguration);
            return this;
        }

        public Builder hosts(Collection<Host> hosts) {
            this.hosts.addAll(hosts);
            return this;
        }

        public Builder queueTTL(Optional<Long> ttl) {
            ttl.ifPresent(aLong -> Preconditions.checkArgument(aLong > 0, "'%s' must be strictly positive", QUEUE_TTL));
            this.queueTTL = ttl;
            return this;
        }

        public Builder vhost(Optional<String> vhost) {
            this.vhost = vhost;
            return this;
        }

        public Builder taskQueueConsumerTimeout(Optional<Duration> taskQueueConsumerTimeout) {
            this.taskQueueConsumerTimeout = taskQueueConsumerTimeout;
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
                    networkRecoveryIntervalInMs.orElse(DEFAULT_NETWORK_RECOVERY_INTERVAL),
                    useSsl.orElse(false),
                    useSslManagement.orElse(false),
                    sslConfiguration.orElse(defaultBehavior()),
                    useQuorumQueues.orElse(false),
                    quorumQueueReplicationFactor.orElse(DEFAULT_QUORUM_QUEUE_REPLICATION_FACTOR),
                    hostsDefaultingToUri(),
                    queueTTL,
                    eventBusPublishConfirmEnabled.orElse(true),
                    eventBusNotificationDurabilityEnabled.orElse(true),
                    vhost,
                    taskQueueConsumerTimeout.orElse(DEFAULT_TASK_QUEUE_CONSUMER_TIMEOUT));
        }

        private List<Host> hostsDefaultingToUri() {
            ImmutableList<Host> result = hosts.build();
            if (result.isEmpty()) {
                return ImmutableList.of(Host.from(amqpUri.getHost(), resolvePort()));
            }
            return result;
        }

        private int resolvePort() {
            if (amqpUri.getPort() == -1) {
                return DEFAULT_PORT;
            }
            return amqpUri.getPort();
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

        Boolean useSsl = configuration.getBoolean(USE_SSL, false);
        Boolean useSslForManagement = configuration.getBoolean(USE_SSL_MANAGEMENT, false);

        Boolean useQuorumQueues = configuration.getBoolean(USE_QUORUM_QUEUES, null);
        Optional<Integer> quorumQueueReplicationFactor = Optional.ofNullable(configuration.getInteger(QUORUM_QUEUES_REPLICATION_FACTOR, null));

        List<Host> hosts = Optional.ofNullable(configuration.getList(String.class, HOSTS))
            .map(hostList -> hostList.stream()
                .map(string -> Host.parseConfString(string, DEFAULT_PORT))
                .collect(ImmutableList.toImmutableList()))
            .orElse(ImmutableList.of());

        Optional<Long> queueTTL = Optional.ofNullable(configuration.getLong(QUEUE_TTL, null));

        Optional<String> vhost = Optional.ofNullable(configuration.getString(VHOST, null));

        ManagementCredentials managementCredentials = ManagementCredentials.from(configuration);

        Optional<Duration> taskQueueConsumerTimeout = Optional.ofNullable(configuration.getString(TASK_QUEUE_CONSUMER_TIMEOUT, null))
            .map(value -> DurationParser.parse(value, ChronoUnit.SECONDS));

        return builder()
            .amqpUri(amqpUri)
            .managementUri(managementUri)
            .managementCredentials(managementCredentials)
            .useSsl(useSsl)
            .useSslManagement(useSslForManagement)
            .sslConfiguration(sslConfiguration(configuration))
            .useQuorumQueues(useQuorumQueues)
            .quorumQueueReplicationFactor(quorumQueueReplicationFactor)
            .hosts(hosts)
            .queueTTL(queueTTL)
            .eventBusNotificationDurabilityEnabled(configuration.getBoolean(EVENT_BUS_NOTIFICATION_DURABILITY_ENABLED, null))
            .eventBusPublishConfirmEnabled(configuration.getBoolean(EVENT_BUS_PUBLISH_CONFIRM_ENABLED, null))
            .vhost(vhost)
            .taskQueueConsumerTimeout(taskQueueConsumerTimeout)
            .build();
    }

    private static URI checkURI(String uri) {
        try {
            return URI.create(uri);
        } catch (Exception e) {
            throw new IllegalStateException("You need to specify a valid URI", e);
        }
    }

    private static SSLConfiguration sslConfiguration(Configuration configuration) {
        SSLValidationStrategy sslStrategy = Optional
                .ofNullable(configuration.getString(SSL_VALIDATION_STRATEGY))
                .map(SSLValidationStrategy::from)
                .orElse(SSLValidationStrategy.DEFAULT);

        HostNameVerifier hostNameVerifier = Optional
                .ofNullable(configuration.getString(SSL_HOSTNAME_VERIFIER))
                .map(HostNameVerifier::from)
                .orElse(HostNameVerifier.DEFAULT);

        return SSLConfiguration.builder()
                .sslStrategy(sslStrategy, getSSLTrustStore(configuration))
                .hostNameVerifier(hostNameVerifier)
                .sslKeyStore(getSSLKeyStore(configuration))
                .build();
    }

    private static Optional<SSLTrustStore> getSSLTrustStore(Configuration configuration) {
        String trustStorePath = configuration.getString(SSL_TRUST_STORE_PATH);
        String trustStorePassword = configuration.getString(SSL_TRUST_STORE_PASSWORD);

        if (trustStorePath == null && trustStorePassword == null) {
            return Optional.empty();
        }

        return Optional.of(SSLTrustStore.of(trustStorePath, trustStorePassword));
    }

    private static Optional<SSLKeyStore> getSSLKeyStore(Configuration configuration) {
        String keyStorePath = configuration.getString(SSL_KEY_STORE_PATH);
        String keyStorePassword = configuration.getString(SSL_KEY_STORE_PASSWORD);

        if (keyStorePath == null && keyStorePassword == null) {
            return Optional.empty();
        }

        return Optional.of(SSLKeyStore.of(keyStorePath, keyStorePassword));
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
    private final Boolean useSsl;
    private final Boolean useSslManagement;
    private final SSLConfiguration sslConfiguration;
    private final boolean useQuorumQueues;
    private final int quorumQueueReplicationFactor;
    private final List<Host> hosts;
    private final ManagementCredentials managementCredentials;
    private final Optional<Long> queueTTL;
    private final boolean eventBusPublishConfirmEnabled;
    private final boolean eventBusNotificationDurabilityEnabled;
    private final Optional<String> vhost;
    private final Duration taskQueueConsumerTimeout;

    private RabbitMQConfiguration(URI uri, URI managementUri, ManagementCredentials managementCredentials, int maxRetries, int minDelayInMs,
                                  int connectionTimeoutInMs, int channelRpcTimeoutInMs, int handshakeTimeoutInMs, int shutdownTimeoutInMs,
                                  int networkRecoveryIntervalInMs, Boolean useSsl, Boolean useSslManagement, SSLConfiguration sslConfiguration,
                                  boolean useQuorumQueues, int quorumQueueReplicationFactor, List<Host> hosts, Optional<Long> queueTTL,
                                  boolean eventBusPublishConfirmEnabled, boolean eventBusNotificationDurabilityEnabled,
                                  Optional<String> vhost, Duration taskQueueConsumerTimeout) {
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
        this.useSsl = useSsl;
        this.useSslManagement = useSslManagement;
        this.sslConfiguration = sslConfiguration;
        this.useQuorumQueues = useQuorumQueues;
        this.quorumQueueReplicationFactor = quorumQueueReplicationFactor;
        this.hosts = hosts;
        this.queueTTL = queueTTL;
        this.eventBusPublishConfirmEnabled = eventBusPublishConfirmEnabled;
        this.eventBusNotificationDurabilityEnabled = eventBusNotificationDurabilityEnabled;
        this.vhost = vhost;
        this.taskQueueConsumerTimeout = taskQueueConsumerTimeout;
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

    public Boolean useSsl() {
        return useSsl;
    }

    public Boolean useSslManagement() {
        return useSslManagement;
    }

    public SSLConfiguration getSslConfiguration() {
        return sslConfiguration;
    }

    public QueueArguments.Builder workQueueArgumentsBuilder(boolean allowQuorum) {
        QueueArguments.Builder builder = QueueArguments.builder();
        if (allowQuorum && useQuorumQueues) {
            builder.quorumQueue().replicationFactor(quorumQueueReplicationFactor);
        }
        return builder;
    }

    public List<Host> rabbitMQHosts() {
        return hosts;
    }

    public Optional<Long> getQueueTTL() {
        return queueTTL;
    }

    public boolean isEventBusPublishConfirmEnabled() {
        return eventBusPublishConfirmEnabled;
    }

    public boolean isEventBusNotificationDurabilityEnabled() {
        return eventBusNotificationDurabilityEnabled;
    }

    public Optional<String> getVhost() {
        return vhost.or(this::getVhostFromPath);
    }

    private Optional<String> getVhostFromPath() {
        String vhostPath = uri.getPath();
        if (vhostPath.startsWith("/")) {
            return Optional.of(vhostPath.substring(1))
                .filter(value -> !value.isEmpty());
        }
        return Optional.empty();
    }

    public Duration getTaskQueueConsumerTimeout() {
        return taskQueueConsumerTimeout;
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
                && Objects.equals(this.managementCredentials, that.managementCredentials)
                && Objects.equals(this.useSsl, that.useSsl)
                && Objects.equals(this.useQuorumQueues, that.useQuorumQueues)
                && Objects.equals(this.quorumQueueReplicationFactor, that.quorumQueueReplicationFactor)
                && Objects.equals(this.useSslManagement, that.useSslManagement)
                && Objects.equals(this.sslConfiguration, that.sslConfiguration)
                && Objects.equals(this.hosts, that.hosts)
                && Objects.equals(this.queueTTL, that.queueTTL)
                && Objects.equals(this.eventBusPublishConfirmEnabled, that.eventBusPublishConfirmEnabled)
                && Objects.equals(this.eventBusNotificationDurabilityEnabled, that.eventBusNotificationDurabilityEnabled)
                && Objects.equals(this.vhost, that.vhost)
                && Objects.equals(this.taskQueueConsumerTimeout, that.taskQueueConsumerTimeout);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uri, managementUri, maxRetries, minDelayInMs, connectionTimeoutInMs, quorumQueueReplicationFactor, useQuorumQueues, hosts,
            channelRpcTimeoutInMs, handshakeTimeoutInMs, shutdownTimeoutInMs, networkRecoveryIntervalInMs, managementCredentials, useSsl, useSslManagement,
            sslConfiguration, queueTTL, eventBusPublishConfirmEnabled, eventBusNotificationDurabilityEnabled, vhost, taskQueueConsumerTimeout);
    }
}
