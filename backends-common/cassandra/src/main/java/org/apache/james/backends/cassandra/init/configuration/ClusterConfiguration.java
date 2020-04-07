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

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.Host;

import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class ClusterConfiguration {

    public static class Builder {
        private ImmutableList.Builder<Host> hosts;
        private boolean createKeyspace;
        private Optional<Integer> minDelay;
        private Optional<Integer> maxRetry;
        private Optional<QueryLoggerConfiguration> queryLoggerConfiguration;
        private Optional<PoolingOptions> poolingOptions;
        private Optional<Integer> readTimeoutMillis;
        private Optional<Integer> connectTimeoutMillis;
        private Optional<Boolean> useSsl;
        private Optional<String> username;
        private Optional<String> password;

        public Builder() {
            hosts = ImmutableList.builder();
            createKeyspace = false;
            minDelay = Optional.empty();
            maxRetry = Optional.empty();
            queryLoggerConfiguration = Optional.empty();
            poolingOptions = Optional.empty();
            readTimeoutMillis = Optional.empty();
            connectTimeoutMillis = Optional.empty();
            username = Optional.empty();
            password = Optional.empty();
            useSsl = Optional.empty();
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

        public Builder createKeyspace() {
            this.createKeyspace = true;
            return this;
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

        public Builder username(Optional<String> username) {
            this.username = username;
            return this;
        }

        public Builder username(String username) {
            return username(Optional.of(username));
        }

        public Builder password(Optional<String> password) {
            this.password = password;
            return this;
        }

        public Builder password(String password) {
            return password(Optional.of(password));
        }

        public Builder useSsl(Optional<Boolean> useSsl) {
            this.useSsl = useSsl;
            return this;
        }

        public Builder useSsl(boolean useSsl) {
            return useSsl(Optional.of(useSsl));
        }

        public Builder useSsl() {
            return useSsl(true);
        }

        public Builder connectTimeoutMillis(int connectTimeoutMillis) {
            return connectTimeoutMillis(Optional.of(connectTimeoutMillis));
        }

        public ClusterConfiguration build() {
            return new ClusterConfiguration(
                hosts.build(),
                createKeyspace,
                minDelay.orElse(DEFAULT_CONNECTION_MIN_DELAY),
                maxRetry.orElse(DEFAULT_CONNECTION_MAX_RETRIES),
                queryLoggerConfiguration,
                poolingOptions,
                readTimeoutMillis.orElse(DEFAULT_READ_TIMEOUT_MILLIS),
                connectTimeoutMillis.orElse(DEFAULT_CONNECT_TIMEOUT_MILLIS),
                useSsl.orElse(false),
                username,
                password);
        }
    }

    private static final String CASSANDRA_NODES = "cassandra.nodes";
    public static final String CASSANDRA_CREATE_KEYSPACE = "cassandra.keyspace.create";
    public static final String CASSANDRA_USER = "cassandra.user";
    public static final String CASSANDRA_PASSWORD = "cassandra.password";
    public static final String CASSANDRA_SSL = "cassandra.ssl";
    public static final String READ_TIMEOUT_MILLIS = "cassandra.readTimeoutMillis";
    public static final String CONNECT_TIMEOUT_MILLIS = "cassandra.connectTimeoutMillis";
    public static final String CONNECTION_MAX_RETRY = "cassandra.retryConnection.maxRetries";
    public static final String CONNECTION_RETRY_MIN_DELAY = "cassandra.retryConnection.minDelay";

    private static final int DEFAULT_CONNECTION_MAX_RETRIES = 10;
    private static final int DEFAULT_CONNECTION_MIN_DELAY = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    public static final int DEFAULT_CASSANDRA_PORT = 9042;

    private static final boolean DEFAULT_SSL = false;

    public static Builder builder() {
        return new Builder();
    }

    public static ClusterConfiguration from(Configuration configuration) {
        boolean createKeySpace = Optional.ofNullable(configuration.getBoolean(CASSANDRA_CREATE_KEYSPACE, null))
            .filter(Boolean::booleanValue)
            .isPresent();

        ClusterConfiguration.Builder builder = ClusterConfiguration.builder()
            .hosts(listCassandraServers(configuration))
            .minDelay(Optional.ofNullable(configuration.getInteger(CONNECTION_RETRY_MIN_DELAY, null)))
            .maxRetry(Optional.ofNullable(configuration.getInteger(CONNECTION_MAX_RETRY, null)))
            .queryLoggerConfiguration(QueryLoggerConfiguration.from(configuration))
            .poolingOptions(readPoolingOptions(configuration))
            .readTimeoutMillis(Optional.ofNullable(configuration.getInteger(READ_TIMEOUT_MILLIS, null)))
            .connectTimeoutMillis(Optional.ofNullable(configuration.getInteger(CONNECT_TIMEOUT_MILLIS, null)))
            .useSsl(Optional.ofNullable(configuration.getBoolean(CASSANDRA_SSL, null)))
            .username(Optional.ofNullable(configuration.getString(CASSANDRA_USER, null)))
            .password(Optional.ofNullable(configuration.getString(CASSANDRA_PASSWORD, null)));
        if (createKeySpace) {
            builder = builder.createKeyspace();
        }
        return builder.build();
    }

    private static List<Host> listCassandraServers(Configuration configuration) {
        String[] ipAndPorts = configuration.getStringArray(CASSANDRA_NODES);

        return Arrays.stream(ipAndPorts)
            .map(string -> Host.parseConfString(string, DEFAULT_CASSANDRA_PORT))
            .collect(Guavate.toImmutableList());
    }

    private static Optional<PoolingOptions> readPoolingOptions(Configuration configuration) {
        Optional<Integer> maxConnections = Optional.ofNullable(configuration.getInteger("cassandra.pooling.local.max.connections", null));
        Optional<Integer> maxRequests = Optional.ofNullable(configuration.getInteger("cassandra.pooling.local.max.requests", null));
        Optional<Integer> poolingTimeout = Optional.ofNullable(configuration.getInteger("cassandra.pooling.timeout", null));
        Optional<Integer> heartbeatTimeout = Optional.ofNullable(configuration.getInteger("cassandra.pooling.heartbeat.timeout", null));
        Optional<Integer> maxQueueSize = Optional.ofNullable(configuration.getInteger("cassandra.pooling.max.queue.size", null));

        if (!maxConnections.isPresent()
            && !maxRequests.isPresent()
            && !poolingTimeout.isPresent()
            && !heartbeatTimeout.isPresent()
            && !maxQueueSize.isPresent()) {
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
        maxQueueSize.ifPresent(result::setMaxQueueSize);

        return Optional.of(result);
    }

    private final List<Host> hosts;
    private final boolean createKeyspace;
    private final int minDelay;
    private final int maxRetry;
    private final Optional<QueryLoggerConfiguration> queryLoggerConfiguration;
    private final Optional<PoolingOptions> poolingOptions;
    private final int readTimeoutMillis;
    private final int connectTimeoutMillis;
    private final boolean useSsl;
    private final Optional<String> username;
    private final Optional<String> password;

    public ClusterConfiguration(List<Host> hosts, boolean createKeyspace, int minDelay, int maxRetry,
                                Optional<QueryLoggerConfiguration> queryLoggerConfiguration, Optional<PoolingOptions> poolingOptions,
                                int readTimeoutMillis, int connectTimeoutMillis, boolean useSsl, Optional<String> username,
                                Optional<String> password) {
        this.hosts = hosts;
        this.createKeyspace = createKeyspace;
        this.minDelay = minDelay;
        this.maxRetry = maxRetry;
        this.queryLoggerConfiguration = queryLoggerConfiguration;
        this.poolingOptions = poolingOptions;
        this.readTimeoutMillis = readTimeoutMillis;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.useSsl = useSsl;
        this.username = username;
        this.password = password;
    }

    public List<Host> getHosts() {
        return hosts;
    }

    public boolean shouldCreateKeyspace() {
        return createKeyspace;
    }

    public int getMinDelay() {
        return minDelay;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public Optional<QueryLoggerConfiguration> getQueryLoggerConfiguration() {
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

    public boolean useSsl() {
        return useSsl;
    }

    public Optional<String> getUsername() {
        return username;
    }

    public Optional<String> getPassword() {
        return password;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ClusterConfiguration) {
            ClusterConfiguration that = (ClusterConfiguration) o;

            return Objects.equals(this.minDelay, that.minDelay)
                && Objects.equals(this.maxRetry, that.maxRetry)
                && Objects.equals(this.hosts, that.hosts)
                && Objects.equals(this.createKeyspace, that.createKeyspace)
                && Objects.equals(this.queryLoggerConfiguration, that.queryLoggerConfiguration)
                && Objects.equals(this.poolingOptions, that.poolingOptions)
                && Objects.equals(this.readTimeoutMillis, that.readTimeoutMillis)
                && Objects.equals(this.connectTimeoutMillis, that.connectTimeoutMillis)
                && Objects.equals(this.useSsl, that.useSsl)
                && Objects.equals(this.username, that.username)
                && Objects.equals(this.password, that.password);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(hosts, createKeyspace, minDelay, maxRetry, queryLoggerConfiguration, poolingOptions,
            readTimeoutMillis, connectTimeoutMillis, username, useSsl, password);
    }
}
