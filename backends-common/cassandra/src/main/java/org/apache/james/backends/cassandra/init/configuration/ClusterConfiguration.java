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

package org.apache.james.backends.cassandra.init.configuration;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.init.ClusterBuilder;
import org.apache.james.util.Host;

import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class ClusterConfiguration {

    public static class Builder {
        private ImmutableList.Builder<Host> hosts;
        private Optional<String> keyspace;
        private Optional<Integer> replicationFactor;
        private Optional<Integer> minDelay;
        private Optional<Integer> maxRetry;
        private Optional<QueryLoggerConfiguration> queryLoggerConfiguration;
        private Optional<PoolingOptions> poolingOptions;
        private Optional<Integer> readTimeoutMillis;
        private Optional<Integer> connectTimeoutMillis;

        public Builder() {
            hosts = ImmutableList.builder();
            keyspace = Optional.empty();
            replicationFactor = Optional.empty();
            minDelay = Optional.empty();
            maxRetry = Optional.empty();
            queryLoggerConfiguration = Optional.empty();
            poolingOptions = Optional.empty();
            readTimeoutMillis = Optional.empty();
            connectTimeoutMillis = Optional.empty();
        }

        public Builder host(Host host) {
            this.hosts.add(host);
            return this;
        }

        public Builder hosts(Collection<Host> hosts) {
            this.hosts.addAll(hosts);
            return this;
        }

        public Builder hosts(Host... hosts) {
            this.hosts.addAll(Arrays.asList(hosts));
            return this;
        }

        public Builder keyspace(Optional<String> keyspace) {
            this.keyspace = keyspace;
            return this;
        }

        public Builder keyspace(String keyspace) {
            return keyspace(Optional.of(keyspace));
        }

        public Builder replicationFactor(Optional<Integer> replicationFactor) {
            this.replicationFactor = replicationFactor;
            return this;
        }

        public Builder replicationFactor(int replicationFactor) {
            return replicationFactor(Optional.of(replicationFactor));
        }

        public Builder minDelay(Optional<Integer> minDelay) {
            this.minDelay = minDelay;
            return this;
        }

        public Builder minDelay(int minDelay) {
            return minDelay(Optional.of(minDelay));
        }

        public Builder maxRetry(Optional<Integer> maxRetry) {
            this.maxRetry = maxRetry;
            return this;
        }

        public Builder maxRetry(int maxRetry) {
            return maxRetry(Optional.of(maxRetry));
        }

        public Builder queryLoggerConfiguration(QueryLoggerConfiguration queryLoggerConfiguration) {
            this.queryLoggerConfiguration = Optional.of(queryLoggerConfiguration);
            return this;
        }

        public Builder poolingOptions(Optional<PoolingOptions> poolingOptions) {
            this.poolingOptions = poolingOptions;
            return this;
        }

        public Builder poolingOptions(PoolingOptions poolingOptions) {
            return poolingOptions(Optional.of(poolingOptions));
        }

        public Builder readTimeoutMillis(Optional<Integer> readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
            return this;
        }

        public Builder readTimeoutMillis(int readTimeoutMillis) {
            return readTimeoutMillis(Optional.of(readTimeoutMillis));
        }

        public Builder connectTimeoutMillis(Optional<Integer> connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            return this;
        }

        public Builder connectTimeoutMillis(int connectTimeoutMillis) {
            return connectTimeoutMillis(Optional.of(connectTimeoutMillis));
        }

        public ClusterConfiguration build() {
            return new ClusterConfiguration(
                hosts.build(),
                keyspace.orElse(DEFAULT_KEYSPACE),
                replicationFactor.orElse(DEFAULT_REPLICATION_FACTOR),
                minDelay.orElse(DEFAULT_CONNECTION_MIN_DELAY),
                maxRetry.orElse(DEFAULT_CONNECTION_MAX_RETRIES),
                queryLoggerConfiguration.orElse(QueryLoggerConfiguration.DEFAULT),
                poolingOptions,
                readTimeoutMillis.orElse(DEFAULT_READ_TIMEOUT_MILLIS),
                connectTimeoutMillis.orElse(DEFAULT_CONNECT_TIMEOUT_MILLIS));
        }
    }

    private static final String CASSANDRA_NODES = "cassandra.nodes";
    public static final String CASSANDRA_KEYSPACE = "cassandra.keyspace";
    public static final String REPLICATION_FACTOR = "cassandra.replication.factor";
    public static final String READ_TIMEOUT_MILLIS = "cassandra.readTimeoutMillis";
    public static final String CONNECT_TIMEOUT_MILLIS = "cassandra.connectTimeoutMillis";
    public static final String CONNECTION_MAX_RETRY = "cassandra.retryConnection.maxRetries";
    public static final String CONNECTION_RETRY_MIN_DELAY = "cassandra.retryConnection.minDelay";

    private static final String DEFAULT_KEYSPACE = "apache_james";
    private static final int DEFAULT_REPLICATION_FACTOR = 1;
    private static final int DEFAULT_CONNECTION_MAX_RETRIES = 10;
    private static final int DEFAULT_CONNECTION_MIN_DELAY = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;

    public static Builder builder() {
        return new Builder();
    }

    public static ClusterConfiguration from(PropertiesConfiguration configuration) {
        return ClusterConfiguration.builder()
            .hosts(listCassandraServers(configuration))
            .keyspace(Optional.ofNullable(configuration.getString(CASSANDRA_KEYSPACE, null)))
            .replicationFactor(Optional.ofNullable(configuration.getInteger(REPLICATION_FACTOR, null)))
            .minDelay(Optional.ofNullable(configuration.getInteger(CONNECTION_RETRY_MIN_DELAY, null)))
            .maxRetry(Optional.ofNullable(configuration.getInteger(CONNECTION_MAX_RETRY, null)))
            .queryLoggerConfiguration(QueryLoggerConfiguration.from(configuration))
            .poolingOptions(readPoolingOptions(configuration))
            .readTimeoutMillis(Optional.ofNullable(configuration.getInteger(READ_TIMEOUT_MILLIS, null)))
            .connectTimeoutMillis(Optional.ofNullable(configuration.getInteger(CONNECT_TIMEOUT_MILLIS, null)))
            .build();
    }

    private static List<Host> listCassandraServers(PropertiesConfiguration configuration) {
        String[] ipAndPorts = configuration.getStringArray(CASSANDRA_NODES);

        return Arrays.stream(ipAndPorts)
            .map(string -> Host.parseConfString(string, ClusterBuilder.DEFAULT_CASSANDRA_PORT))
            .collect(Guavate.toImmutableList());
    }

    private static Optional<PoolingOptions> readPoolingOptions(PropertiesConfiguration configuration) {
        Optional<Integer> maxConnections = Optional.ofNullable(configuration.getInteger("cassandra.pooling.local.max.connections", null));
        Optional<Integer> maxRequests = Optional.ofNullable(configuration.getInteger("cassandra.pooling.local.max.requests", null));
        Optional<Integer> poolingTimeout = Optional.ofNullable(configuration.getInteger("cassandra.pooling.timeout", null));
        Optional<Integer> heartbeatTimeout = Optional.ofNullable(configuration.getInteger("cassandra.pooling.heartbeat.timeout", null));

        if (!maxConnections.isPresent()
            && !maxRequests.isPresent()
            && !poolingTimeout.isPresent()
            && !heartbeatTimeout.isPresent()) {
            return Optional.empty();
        }
        PoolingOptions result = new PoolingOptions();

        maxConnections.ifPresent(value -> {
            result.setMaxConnectionsPerHost(HostDistance.LOCAL, value);
            result.setMaxConnectionsPerHost(HostDistance.REMOTE, value);
        });
        maxRequests.ifPresent(value -> {
            result.setMaxRequestsPerConnection(HostDistance.LOCAL, value);
            result.setMaxRequestsPerConnection(HostDistance.REMOTE, value);
        });
        poolingTimeout.ifPresent(result::setPoolTimeoutMillis);
        heartbeatTimeout.ifPresent(result::setHeartbeatIntervalSeconds);

        return Optional.of(result);
    }

    private final List<Host> hosts;
    private final String keyspace;
    private final int replicationFactor;
    private final int minDelay;
    private final int maxRetry;
    private final QueryLoggerConfiguration queryLoggerConfiguration;
    private final Optional<PoolingOptions> poolingOptions;
    private final int readTimeoutMillis;
    private final int connectTimeoutMillis;

    public ClusterConfiguration(List<Host> hosts, String keyspace, int replicationFactor, int minDelay, int maxRetry,
                                QueryLoggerConfiguration queryLoggerConfiguration, Optional<PoolingOptions> poolingOptions,
                                int readTimeoutMillis, int connectTimeoutMillis) {
        this.hosts = hosts;
        this.keyspace = keyspace;
        this.replicationFactor = replicationFactor;
        this.minDelay = minDelay;
        this.maxRetry = maxRetry;
        this.queryLoggerConfiguration = queryLoggerConfiguration;
        this.poolingOptions = poolingOptions;
        this.readTimeoutMillis = readTimeoutMillis;
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public List<Host> getHosts() {
        return hosts;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public int getReplicationFactor() {
        return replicationFactor;
    }

    public int getMinDelay() {
        return minDelay;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public QueryLoggerConfiguration getQueryLoggerConfiguration() {
        return queryLoggerConfiguration;
    }

    public Optional<PoolingOptions> getPoolingOptions() {
        return poolingOptions;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ClusterConfiguration) {
            ClusterConfiguration that = (ClusterConfiguration) o;

            return Objects.equals(this.minDelay, that.minDelay)
                && Objects.equals(this.maxRetry, that.maxRetry)
                && Objects.equals(this.hosts, that.hosts)
                && Objects.equals(this.keyspace, that.keyspace)
                && Objects.equals(this.replicationFactor, that.replicationFactor)
                && Objects.equals(this.queryLoggerConfiguration, that.queryLoggerConfiguration)
                && Objects.equals(this.poolingOptions, that.poolingOptions)
                && Objects.equals(this.readTimeoutMillis, that.readTimeoutMillis)
                && Objects.equals(this.connectTimeoutMillis, that.connectTimeoutMillis);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(hosts, keyspace, replicationFactor, minDelay, maxRetry, queryLoggerConfiguration, poolingOptions,
            readTimeoutMillis, connectTimeoutMillis);
    }
}
