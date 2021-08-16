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

import com.google.common.collect.ImmutableList;

public class ClusterConfiguration {

    public static class Builder {
        private ImmutableList.Builder<Host> hosts;
        private boolean createKeyspace;
        private Optional<Integer> minDelay;
        private Optional<Integer> maxRetry;
        private Optional<String> username;
        private Optional<String> password;
        private Optional<String> localDC;

        public Builder() {
            hosts = ImmutableList.builder();
            createKeyspace = false;
            minDelay = Optional.empty();
            maxRetry = Optional.empty();
            username = Optional.empty();
            password = Optional.empty();
            localDC = Optional.empty();
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

        public Builder localDC(Optional<String> localDC) {
            this.localDC = localDC;
            return this;
        }

        public Builder localDC(String localDC) {
            this.localDC = Optional.of(localDC);
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

        public ClusterConfiguration build() {
            return new ClusterConfiguration(
                hosts.build(),
                localDC, createKeyspace,
                minDelay.orElse(DEFAULT_CONNECTION_MIN_DELAY),
                maxRetry.orElse(DEFAULT_CONNECTION_MAX_RETRIES),
                username,
                password);
        }
    }

    private static final String CASSANDRA_NODES = "cassandra.nodes";
    public static final String CASSANDRA_CREATE_KEYSPACE = "cassandra.keyspace.create";
    public static final String CASSANDRA_USER = "cassandra.user";
    public static final String CASSANDRA_PASSWORD = "cassandra.password";
    public static final String CASSANDRA_LOCAL_DC = "cassandra.local.dc";
    public static final String CONNECTION_MAX_RETRY = "cassandra.retryConnection.maxRetries";
    public static final String CONNECTION_RETRY_MIN_DELAY = "cassandra.retryConnection.minDelay";

    private static final int DEFAULT_CONNECTION_MAX_RETRIES = 10;
    private static final int DEFAULT_CONNECTION_MIN_DELAY = 5000;
    public static final int DEFAULT_CASSANDRA_PORT = 9042;

    public static Builder builder() {
        return new Builder();
    }

    public static ClusterConfiguration from(Configuration configuration) {
        boolean createKeySpace = Optional.ofNullable(configuration.getBoolean(CASSANDRA_CREATE_KEYSPACE, null))
            .filter(Boolean::booleanValue)
            .isPresent();

        ClusterConfiguration.Builder builder = ClusterConfiguration.builder()
            .hosts(listCassandraServers(configuration))
            .localDC(Optional.ofNullable(configuration.getString(CASSANDRA_LOCAL_DC, null)))
            .minDelay(Optional.ofNullable(configuration.getInteger(CONNECTION_RETRY_MIN_DELAY, null)))
            .maxRetry(Optional.ofNullable(configuration.getInteger(CONNECTION_MAX_RETRY, null)))
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
            .collect(ImmutableList.toImmutableList());
    }


    private final List<Host> hosts;
    private final Optional<String> localDC;
    private final boolean createKeyspace;
    private final int minDelay;
    private final int maxRetry;
    private final Optional<String> username;
    private final Optional<String> password;

    private ClusterConfiguration(List<Host> hosts, Optional<String> localDC, boolean createKeyspace, int minDelay, int maxRetry, Optional<String> username,
                                Optional<String> password) {
        this.hosts = hosts;
        this.localDC = localDC;
        this.createKeyspace = createKeyspace;
        this.minDelay = minDelay;
        this.maxRetry = maxRetry;
        this.username = username;
        this.password = password;
    }

    public Optional<String> getLocalDC() {
        return localDC;
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
                && Objects.equals(this.username, that.username)
                && Objects.equals(this.password, that.password);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(hosts, createKeyspace, minDelay, maxRetry, username, password);
    }
}
