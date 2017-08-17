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

package org.apache.james.backends.cassandra.init;

import java.util.Collection;
import java.util.Optional;

import org.apache.james.util.Host;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.SocketOptions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ClusterBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterBuilder.class);
    private final static String DEFAULT_CLUSTER_IP = "localhost";
    public static final int DEFAULT_CASSANDRA_PORT = 9042;

    public static ClusterBuilder builder() {
        return new ClusterBuilder();
    }

    private Optional<String> username;
    private Optional<String> password;

    private Optional<String> host;
    private Optional<Integer> port;
    private Optional<Collection<Host>> servers;

    private Optional<Integer> refreshSchemaIntervalMillis;
    private boolean forTest;

    private Optional<QueryLoggerConfiguration> queryLogger;
    private Optional<Integer> readTimeoutMillis;
    private Optional<Integer> connectTimeoutMillis;
    private Optional<PoolingOptions> poolingOptions;

    private ClusterBuilder() {
        username = Optional.empty();
        password = Optional.empty();

        host = Optional.empty();
        port = Optional.empty();
        servers = Optional.empty();

        refreshSchemaIntervalMillis = Optional.empty();
        forTest = false;

        queryLogger = Optional.empty();
        readTimeoutMillis = Optional.empty();
        connectTimeoutMillis = Optional.empty();
        poolingOptions = Optional.empty();
    }

    public ClusterBuilder username(String username) {
        this.username = Optional.of(username);

        return this;
    }

    public ClusterBuilder password(String password) {
        this.password = Optional.of(password);

        return this;
    }

    public ClusterBuilder host(String host) {
        this.host = Optional.of(host);

        return this;
    }

    public ClusterBuilder port(int port) {
        this.port = Optional.of(port);

        return this;
    }

    public ClusterBuilder poolingOptions(PoolingOptions poolingOptions) {
        this.poolingOptions = Optional.of(poolingOptions);
        return this;
    }

    public ClusterBuilder poolingOptions(Optional<PoolingOptions> poolingOptions) {
        this.poolingOptions = poolingOptions;
        return this;
    }

    public ClusterBuilder refreshSchemaIntervalMillis(int refreshSchemaIntervalMillis) {
        this.refreshSchemaIntervalMillis = Optional.of(refreshSchemaIntervalMillis);

        return this;
    }

    public ClusterBuilder servers(Host... servers) {
        this.servers = Optional.of(ImmutableList.copyOf(servers));

        return this;
    }

    public ClusterBuilder servers(Collection<Host> servers) {
        this.servers = Optional.of(servers);

        return this;
    }

    public ClusterBuilder forTest() {
        this.forTest = true;

        return this;
    }

    public ClusterBuilder queryLoggerConfiguration(QueryLoggerConfiguration queryLogger) {
        this.queryLogger = Optional.of(queryLogger);

        return this;
    }

    public ClusterBuilder readTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = Optional.of(readTimeoutMillis);
        return this;
    }

    public ClusterBuilder connectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = Optional.of(connectTimeoutMillis);
        return this;
    }

    public Cluster build() {
        Preconditions.checkState(!(servers.isPresent() && host.isPresent()), "You can't specify a list of servers and a host at the same time");
        Preconditions.checkState(!(servers.isPresent() && port.isPresent()), "You can't specify a list of servers and a port at the same time");
        Preconditions.checkState(username.isPresent() == password.isPresent(), "If you specify username, you must specify password");
        Preconditions.checkState(forTest == refreshSchemaIntervalMillis.isPresent(), "You can't specify refreshSchemaIntervalMillis for test");

        Cluster.Builder clusterBuilder = Cluster.builder();
        getServers().forEach(
                (server) -> clusterBuilder.addContactPoint(server.getHostName()).withPort(server.getPort())
        );

        username.map(username ->
            password.map(password ->
                clusterBuilder.withCredentials(username, password)));

        getRefreshSchemaIntervalMillis().map(refreshSchemaIntervalMillis ->
            clusterBuilder.withQueryOptions(
                new QueryOptions()
                    .setRefreshSchemaIntervalMillis(refreshSchemaIntervalMillis)));

        SocketOptions socketOptions = new SocketOptions();
        readTimeoutMillis.ifPresent(socketOptions::setReadTimeoutMillis);
        connectTimeoutMillis.ifPresent(socketOptions::setConnectTimeoutMillis);
        clusterBuilder.withSocketOptions(socketOptions);
        poolingOptions.ifPresent(clusterBuilder::withPoolingOptions);

        Cluster cluster = clusterBuilder.build();

        queryLogger.map(queryLoggerConfiguration ->
            cluster.register(queryLoggerConfiguration.getQueryLogger()));

        return cluster;
    }

    private Optional<Integer> getRefreshSchemaIntervalMillis() {
        return forTest ? Optional.of(0) : refreshSchemaIntervalMillis;
    }

    private Collection<Host> getServers() {
        return servers.orElse(getServersFromHostAndPort());
    }

    private Collection<Host> getServersFromHostAndPort() {
        String host = this.host.orElseGet(() -> {
            LOGGER.info("No cassandra host specified. Falling back to {}", DEFAULT_CLUSTER_IP);
            return DEFAULT_CLUSTER_IP;
        });
        int port = this.port.orElseGet(() -> {
            LOGGER.info("No cassandra port specified. Falling back to {}", DEFAULT_CASSANDRA_PORT);
            return DEFAULT_CASSANDRA_PORT;
        });

        return ImmutableList.of(Host.from(host, port));
    }
}
