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

package org.apache.james.backends.es.v7;

import static org.apache.james.backends.es.v7.ElasticSearchConfiguration.SSLConfiguration.SSLValidationStrategy.OVERRIDE;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration.SSLConfiguration.HostNameVerifier;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration.SSLConfiguration.SSLTrustStore;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration.SSLConfiguration.SSLValidationStrategy;
import org.apache.james.util.Host;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ElasticSearchConfiguration {

    public enum HostScheme {
        HTTP("http"),
        HTTPS("https");

        public static HostScheme of(String schemeValue) {
            Preconditions.checkNotNull(schemeValue);

            return Arrays.stream(values())
                .filter(hostScheme -> hostScheme.value.toLowerCase(Locale.US)
                    .equals(schemeValue.toLowerCase(Locale.US)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Unknown HostScheme '%s'", schemeValue)));
        }

        private final String value;

        HostScheme(String value) {
            this.value = value;
        }
    }

    public static class Credential {

        public static Credential of(String username, String password) {
            return new Credential(username, password);
        }

        private final String username;
        private final char[] password;

        private Credential(String username, String password) {
            Preconditions.checkNotNull(username, "username cannot be null when password is specified");
            Preconditions.checkNotNull(password, "password cannot be null when username is specified");

            this.username = username;
            this.password = password.toCharArray();
        }

        public String getUsername() {
            return username;
        }

        public char[] getPassword() {
            return password;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Credential) {
                Credential that = (Credential) o;

                return Objects.equals(this.username, that.username)
                    && Arrays.equals(this.password, that.password);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(username, Arrays.hashCode(password));
        }
    }

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

        public static class SSLTrustStore {

            public static SSLTrustStore of(String filePath, String password) {
                return new SSLTrustStore(filePath, password);
            }

            private final File file;
            private final char[] password;

            private SSLTrustStore(String filePath, String password) {
                Preconditions.checkNotNull(filePath, "%s cannot be null when %s is specified",
                    ELASTICSEARCH_HTTPS_TRUST_STORE_PATH, ELASTICSEARCH_HTTPS_TRUST_STORE_PASSWORD);
                Preconditions.checkNotNull(password,
                     "%s cannot be null when %s is specified",
                    ELASTICSEARCH_HTTPS_TRUST_STORE_PASSWORD, ELASTICSEARCH_HTTPS_TRUST_STORE_PATH);
                Preconditions.checkArgument(Files.exists(Paths.get(filePath)),
                    "the file '%s' from property '%s' doesn't exist", filePath, ELASTICSEARCH_HTTPS_TRUST_STORE_PATH);

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

            interface RequireSSLStrategyTrustStore {
                RequireHostNameVerifier sslStrategy(SSLValidationStrategy strategy, Optional<SSLTrustStore> trustStore);

                default RequireHostNameVerifier strategyIgnore() {
                    return sslStrategy(SSLValidationStrategy.IGNORE, Optional.empty());
                }

                default RequireHostNameVerifier strategyOverride(SSLTrustStore trustStore) {
                    return sslStrategy(SSLValidationStrategy.OVERRIDE, Optional.of(trustStore));
                }

                default RequireHostNameVerifier strategyDefault() {
                    return sslStrategy(SSLValidationStrategy.DEFAULT, Optional.empty());
                }
            }

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

                private ReadyToBuild(SSLValidationStrategy sslValidationStrategy, HostNameVerifier hostNameVerifier, Optional<SSLTrustStore> sslTrustStore) {
                    this.sslValidationStrategy = sslValidationStrategy;
                    this.hostNameVerifier = hostNameVerifier;
                    this.sslTrustStore = sslTrustStore;
                }

                public ReadyToBuild sslTrustStore(SSLTrustStore sslTrustStore) {
                    this.sslTrustStore = Optional.of(sslTrustStore);
                    return this;
                }

                public SSLConfiguration build() {
                    return new SSLConfiguration(sslValidationStrategy, hostNameVerifier, sslTrustStore);
                }
            }
        }

        static SSLConfiguration defaultBehavior() {
            return new SSLConfiguration(SSLValidationStrategy.DEFAULT, HostNameVerifier.DEFAULT, Optional.empty());
        }

        static Builder.RequireSSLStrategyTrustStore builder() {
            return (strategy, trustStore) -> hostNameVerifier -> new Builder.ReadyToBuild(strategy, hostNameVerifier, trustStore);
        }

        private final SSLValidationStrategy strategy;
        private final HostNameVerifier hostNameVerifier;
        private final Optional<SSLTrustStore> trustStore;

        private SSLConfiguration(SSLValidationStrategy strategy, HostNameVerifier hostNameVerifier, Optional<SSLTrustStore> trustStore) {
            Preconditions.checkNotNull(strategy);
            Preconditions.checkNotNull(trustStore);
            Preconditions.checkNotNull(hostNameVerifier);
            Preconditions.checkArgument(strategy != OVERRIDE || trustStore.isPresent(),  "%s strategy requires trustStore to be present", OVERRIDE.name());

            this.strategy = strategy;
            this.trustStore = trustStore;
            this.hostNameVerifier = hostNameVerifier;
        }

        public SSLValidationStrategy getStrategy() {
            return strategy;
        }

        public Optional<SSLTrustStore> getTrustStore() {
            return trustStore;
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
                    && Objects.equals(this.hostNameVerifier, that.hostNameVerifier);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(strategy, trustStore, hostNameVerifier);
        }
    }

    public static class Builder {

        private final ImmutableList.Builder<Host> hosts;
        private Optional<Integer> nbShards;
        private Optional<Integer> nbReplica;
        private Optional<Integer> waitForActiveShards;
        private Optional<Integer> minDelay;
        private Optional<Integer> maxRetries;
        private Optional<Duration> requestTimeout;
        private Optional<HostScheme> hostScheme;
        private Optional<Credential> credential;
        private Optional<SSLConfiguration> sslTrustConfiguration;

        public Builder() {
            hosts = ImmutableList.builder();
            nbShards = Optional.empty();
            nbReplica = Optional.empty();
            waitForActiveShards = Optional.empty();
            minDelay = Optional.empty();
            maxRetries = Optional.empty();
            requestTimeout = Optional.empty();
            hostScheme = Optional.empty();
            credential = Optional.empty();
            sslTrustConfiguration = Optional.empty();
        }

        public Builder addHost(Host host) {
            this.hosts.add(host);
            return this;
        }

        public Builder addHosts(Collection<Host> hosts) {
            this.hosts.addAll(hosts);
            return this;
        }

        public Builder nbShards(int nbShards) {
            Preconditions.checkArgument(nbShards > 0, "You need the number of shards to be strictly positive");
            this.nbShards = Optional.of(nbShards);
            return this;
        }

        public Builder nbReplica(int nbReplica) {
            Preconditions.checkArgument(nbReplica >= 0, "You need the number of replica to be positive");
            this.nbReplica = Optional.of(nbReplica);
            return this;
        }

        public Builder waitForActiveShards(int waitForActiveShards) {
            Preconditions.checkArgument(waitForActiveShards >= 0, "You need the number of waitForActiveShards to be positive");
            this.waitForActiveShards = Optional.of(waitForActiveShards);
            return this;
        }

        public Builder minDelay(Optional<Integer> minDelay) {
            this.minDelay = minDelay;
            return this;
        }

        public Builder maxRetries(Optional<Integer> maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder requestTimeout(Optional<Duration> requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder hostScheme(Optional<HostScheme> hostScheme) {
            this.hostScheme = hostScheme;
            return this;
        }

        public Builder credential(Optional<Credential> credential) {
            this.credential = credential;
            return this;
        }

        public Builder sslTrustConfiguration(SSLConfiguration sslConfiguration) {
            this.sslTrustConfiguration = Optional.of(sslConfiguration);
            return this;
        }

        public Builder sslTrustConfiguration(Optional<SSLConfiguration> sslTrustStore) {
            this.sslTrustConfiguration = sslTrustStore;
            return this;
        }

        public ElasticSearchConfiguration build() {
            ImmutableList<Host> hosts = this.hosts.build();
            Preconditions.checkState(!hosts.isEmpty(), "You need to specify ElasticSearch host");
            return new ElasticSearchConfiguration(
                hosts,
                nbShards.orElse(DEFAULT_NB_SHARDS),
                nbReplica.orElse(DEFAULT_NB_REPLICA),
                waitForActiveShards.orElse(DEFAULT_WAIT_FOR_ACTIVE_SHARDS),
                minDelay.orElse(DEFAULT_CONNECTION_MIN_DELAY),
                maxRetries.orElse(DEFAULT_CONNECTION_MAX_RETRIES),
                requestTimeout.orElse(DEFAULT_REQUEST_TIMEOUT),
                hostScheme.orElse(DEFAULT_SCHEME),
                credential,
                sslTrustConfiguration.orElse(DEFAULT_SSL_TRUST_CONFIGURATION));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final String ELASTICSEARCH_HOSTS = "elasticsearch.hosts";
    public static final String ELASTICSEARCH_MASTER_HOST = "elasticsearch.masterHost";
    public static final String ELASTICSEARCH_PORT = "elasticsearch.port";
    public static final String ELASTICSEARCH_HOST_SCHEME = "elasticsearch.hostScheme";
    public static final String ELASTICSEARCH_HTTPS_SSL_VALIDATION_STRATEGY = "elasticsearch.hostScheme.https.sslValidationStrategy";
    public static final String ELASTICSEARCH_HTTPS_HOSTNAME_VERIFIER = "elasticsearch.hostScheme.https.hostNameVerifier";
    public static final String ELASTICSEARCH_HTTPS_TRUST_STORE_PATH = "elasticsearch.hostScheme.https.trustStorePath";
    public static final String ELASTICSEARCH_HTTPS_TRUST_STORE_PASSWORD = "elasticsearch.hostScheme.https.trustStorePassword";
    public static final String ELASTICSEARCH_USER = "elasticsearch.user";
    public static final String ELASTICSEARCH_PASSWORD = "elasticsearch.password";
    public static final String ELASTICSEARCH_NB_REPLICA = "elasticsearch.nb.replica";
    public static final String WAIT_FOR_ACTIVE_SHARDS = "elasticsearch.index.waitForActiveShards";
    public static final String ELASTICSEARCH_NB_SHARDS = "elasticsearch.nb.shards";
    public static final String ELASTICSEARCH_RETRY_CONNECTION_MIN_DELAY = "elasticsearch.retryConnection.minDelay";
    public static final String ELASTICSEARCH_RETRY_CONNECTION_MAX_RETRIES = "elasticsearch.retryConnection.maxRetries";

    public static final int DEFAULT_CONNECTION_MAX_RETRIES = 7;
    public static final int DEFAULT_CONNECTION_MIN_DELAY = 3000;
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_NB_SHARDS = 5;
    public static final int DEFAULT_NB_REPLICA = 1;
    public static final int DEFAULT_WAIT_FOR_ACTIVE_SHARDS = 1;
    public static final int DEFAULT_PORT = 9200;
    public static final String LOCALHOST = "127.0.0.1";
    public static final Optional<Integer> DEFAULT_PORT_AS_OPTIONAL = Optional.of(DEFAULT_PORT);
    public static final HostScheme DEFAULT_SCHEME = HostScheme.HTTP;
    public static final SSLConfiguration DEFAULT_SSL_TRUST_CONFIGURATION = SSLConfiguration.defaultBehavior();

    public static final ElasticSearchConfiguration DEFAULT_CONFIGURATION = builder()
        .addHost(Host.from(LOCALHOST, DEFAULT_PORT))
        .build();

    public static ElasticSearchConfiguration fromProperties(Configuration configuration) throws ConfigurationException {
        return builder()
            .addHosts(getHosts(configuration))
            .hostScheme(getHostScheme(configuration))
            .credential(getCredential(configuration))
            .sslTrustConfiguration(sslTrustConfiguration(configuration))
            .nbShards(configuration.getInteger(ELASTICSEARCH_NB_SHARDS, DEFAULT_NB_SHARDS))
            .nbReplica(configuration.getInteger(ELASTICSEARCH_NB_REPLICA, DEFAULT_NB_REPLICA))
            .waitForActiveShards(configuration.getInteger(WAIT_FOR_ACTIVE_SHARDS, DEFAULT_WAIT_FOR_ACTIVE_SHARDS))
            .minDelay(Optional.ofNullable(configuration.getInteger(ELASTICSEARCH_RETRY_CONNECTION_MIN_DELAY, null)))
            .maxRetries(Optional.ofNullable(configuration.getInteger(ELASTICSEARCH_RETRY_CONNECTION_MAX_RETRIES, null)))
            .build();
    }

    private static SSLConfiguration sslTrustConfiguration(Configuration configuration) {
        SSLValidationStrategy sslStrategy = Optional
            .ofNullable(configuration.getString(ELASTICSEARCH_HTTPS_SSL_VALIDATION_STRATEGY))
            .map(SSLValidationStrategy::from)
            .orElse(SSLValidationStrategy.DEFAULT);

        HostNameVerifier hostNameVerifier = Optional
            .ofNullable(configuration.getString(ELASTICSEARCH_HTTPS_HOSTNAME_VERIFIER))
            .map(HostNameVerifier::from)
            .orElse(HostNameVerifier.DEFAULT);

        return SSLConfiguration.builder()
            .sslStrategy(sslStrategy, getSSLTrustStore(configuration))
            .hostNameVerifier(hostNameVerifier)
            .build();
    }

    private static Optional<SSLTrustStore> getSSLTrustStore(Configuration configuration) {
        String trustStorePath = configuration.getString(ELASTICSEARCH_HTTPS_TRUST_STORE_PATH);
        String trustStorePassword = configuration.getString(ELASTICSEARCH_HTTPS_TRUST_STORE_PASSWORD);

        if (trustStorePath == null && trustStorePassword == null) {
            return Optional.empty();
        }

        return Optional.of(SSLTrustStore.of(trustStorePath, trustStorePassword));
    }

    private static Optional<HostScheme> getHostScheme(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_HOST_SCHEME))
            .map(HostScheme::of);
    }

    private static Optional<Credential> getCredential(Configuration configuration) {
        String username = configuration.getString(ELASTICSEARCH_USER);
        String password = configuration.getString(ELASTICSEARCH_PASSWORD);

        if (username == null && password == null) {
            return Optional.empty();
        }

        return Optional.of(Credential.of(username, password));
    }

    private static ImmutableList<Host> getHosts(Configuration propertiesReader) throws ConfigurationException {
        Optional<String> masterHost = Optional.ofNullable(
            propertiesReader.getString(ELASTICSEARCH_MASTER_HOST, null));
        Optional<Integer> masterPort = Optional.ofNullable(
            propertiesReader.getInteger(ELASTICSEARCH_PORT, null));
        List<String> multiHosts = Arrays.asList(propertiesReader.getStringArray(ELASTICSEARCH_HOSTS));

        validateHostsConfigurationOptions(masterHost, masterPort, multiHosts);

        if (masterHost.isPresent()) {
            return ImmutableList.of(
                Host.from(masterHost.get(),
                masterPort.get()));
        } else {
            return multiHosts.stream()
                .map(ipAndPort -> Host.parse(ipAndPort, DEFAULT_PORT_AS_OPTIONAL))
                .collect(Guavate.toImmutableList());
        }
    }

    @VisibleForTesting
    static void validateHostsConfigurationOptions(Optional<String> masterHost,
                                                  Optional<Integer> masterPort,
                                                  List<String> multiHosts) throws ConfigurationException {
        if (masterHost.isPresent() != masterPort.isPresent()) {
            throw new ConfigurationException(ELASTICSEARCH_MASTER_HOST + " and " + ELASTICSEARCH_PORT + " should be specified together");
        }
        if (!multiHosts.isEmpty() && masterHost.isPresent()) {
            throw new ConfigurationException("You should choose between mono host set up and " + ELASTICSEARCH_HOSTS);
        }
        if (multiHosts.isEmpty() && !masterHost.isPresent()) {
            throw new ConfigurationException("You should specify either (" + ELASTICSEARCH_MASTER_HOST + " and " + ELASTICSEARCH_PORT + ") or " + ELASTICSEARCH_HOSTS);
        }
    }

    private final ImmutableList<Host> hosts;
    private final int nbShards;
    private final int nbReplica;
    private final int waitForActiveShards;
    private final int minDelay;
    private final int maxRetries;
    private final Duration requestTimeout;
    private final HostScheme hostScheme;
    private final Optional<Credential> credential;
    private final SSLConfiguration sslConfiguration;

    private ElasticSearchConfiguration(ImmutableList<Host> hosts, int nbShards, int nbReplica, int waitForActiveShards, int minDelay, int maxRetries, Duration requestTimeout,
                                       HostScheme hostScheme, Optional<Credential> credential, SSLConfiguration sslConfiguration) {
        this.hosts = hosts;
        this.nbShards = nbShards;
        this.nbReplica = nbReplica;
        this.waitForActiveShards = waitForActiveShards;
        this.minDelay = minDelay;
        this.maxRetries = maxRetries;
        this.requestTimeout = requestTimeout;
        this.hostScheme = hostScheme;
        this.credential = credential;
        this.sslConfiguration = sslConfiguration;
    }

    public ImmutableList<Host> getHosts() {
        return hosts;
    }

    public int getNbShards() {
        return nbShards;
    }

    public int getNbReplica() {
        return nbReplica;
    }

    public int getWaitForActiveShards() {
        return waitForActiveShards;
    }

    public int getMinDelay() {
        return minDelay;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public HostScheme getHostScheme() {
        return hostScheme;
    }

    public Optional<Credential> getCredential() {
        return credential;
    }

    public SSLConfiguration getSslConfiguration() {
        return sslConfiguration;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ElasticSearchConfiguration) {
            ElasticSearchConfiguration that = (ElasticSearchConfiguration) o;

            return Objects.equals(this.nbShards, that.nbShards)
                && Objects.equals(this.nbReplica, that.nbReplica)
                && Objects.equals(this.waitForActiveShards, that.waitForActiveShards)
                && Objects.equals(this.minDelay, that.minDelay)
                && Objects.equals(this.maxRetries, that.maxRetries)
                && Objects.equals(this.hosts, that.hosts)
                && Objects.equals(this.requestTimeout, that.requestTimeout)
                && Objects.equals(this.hostScheme, that.hostScheme)
                && Objects.equals(this.credential, that.credential)
                && Objects.equals(this.sslConfiguration, that.sslConfiguration);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(hosts, nbShards, nbReplica, waitForActiveShards, minDelay, maxRetries, requestTimeout,
            hostScheme, credential, sslConfiguration);
    }
}
