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

package org.apache.james.backends.es;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.util.Host;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ElasticSearchConfiguration {

    public static class Builder {

        private final ImmutableList.Builder<Host> hosts;
        private Optional<Integer> nbShards;
        private Optional<Integer> nbReplica;
        private Optional<Integer> waitForActiveShards;
        private Optional<Integer> minDelay;
        private Optional<Integer> maxRetries;
        private Optional<Duration> requestTimeout;

        public Builder() {
            hosts = ImmutableList.builder();
            nbShards = Optional.empty();
            nbReplica = Optional.empty();
            waitForActiveShards = Optional.empty();
            minDelay = Optional.empty();
            maxRetries = Optional.empty();
            requestTimeout = Optional.empty();
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
                requestTimeout.orElse(DEFAULT_REQUEST_TIMEOUT));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final String ELASTICSEARCH_HOSTS = "elasticsearch.hosts";
    public static final String ELASTICSEARCH_MASTER_HOST = "elasticsearch.masterHost";
    public static final String ELASTICSEARCH_PORT = "elasticsearch.port";
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

    public static final ElasticSearchConfiguration DEFAULT_CONFIGURATION = builder()
        .addHost(Host.from(LOCALHOST, DEFAULT_PORT))
        .build();

    public static ElasticSearchConfiguration fromProperties(Configuration configuration) throws ConfigurationException {
        return builder()
            .addHosts(getHosts(configuration))
            .nbShards(configuration.getInteger(ELASTICSEARCH_NB_SHARDS, DEFAULT_NB_SHARDS))
            .nbReplica(configuration.getInteger(ELASTICSEARCH_NB_REPLICA, DEFAULT_NB_REPLICA))
            .waitForActiveShards(configuration.getInteger(WAIT_FOR_ACTIVE_SHARDS, DEFAULT_WAIT_FOR_ACTIVE_SHARDS))
            .minDelay(Optional.ofNullable(configuration.getInteger(ELASTICSEARCH_RETRY_CONNECTION_MIN_DELAY, null)))
            .maxRetries(Optional.ofNullable(configuration.getInteger(ELASTICSEARCH_RETRY_CONNECTION_MAX_RETRIES, null)))
            .build();
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

    private ElasticSearchConfiguration(ImmutableList<Host> hosts, int nbShards, int nbReplica, int waitForActiveShards, int minDelay, int maxRetries, Duration requestTimeout) {
        this.hosts = hosts;
        this.nbShards = nbShards;
        this.nbReplica = nbReplica;
        this.waitForActiveShards = waitForActiveShards;
        this.minDelay = minDelay;
        this.maxRetries = maxRetries;
        this.requestTimeout = requestTimeout;
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
                && Objects.equals(this.requestTimeout, that.requestTimeout);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(hosts, nbShards, nbReplica, waitForActiveShards, minDelay, maxRetries, requestTimeout);
    }
}
