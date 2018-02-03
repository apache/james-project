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

package org.apache.james.modules.mailbox;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.init.CassandraSessionConfiguration;
import org.apache.james.backends.cassandra.init.ClusterBuilder;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.QueryLoggerConfiguration;
import org.apache.james.util.Host;
import org.apache.james.utils.RetryExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PerHostPercentileTracker;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.github.steveash.guavate.Guavate;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import com.nurkiewicz.asyncretry.function.RetryCallable;

@Singleton
public class ResilientClusterProvider implements Provider<Cluster> {

    public static final String CASSANDRA_KEYSPACE = "cassandra.keyspace";
    private static final int DEFAULT_CONNECTION_MAX_RETRIES = 10;
    private static final int DEFAULT_CONNECTION_MIN_DELAY = 5000;
    private static final long CASSANDRA_HIGHEST_TRACKABLE_LATENCY_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final int DEFAULT_REPLICATION_FACTOR = 1;
    private static final String DEFAULT_KEYSPACE = "apache_james";
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    private static final String CASSANDRA_NODES = "cassandra.nodes";

    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientClusterProvider.class);
    private final Cluster cluster;

    @Inject
    private ResilientClusterProvider(CassandraSessionConfiguration cassandraSessionConfiguration, AsyncRetryExecutor executor) throws ConfigurationException, ExecutionException, InterruptedException {
        PropertiesConfiguration configuration = cassandraSessionConfiguration.getConfiguration();
        List<Host> servers = listCassandraServers(configuration);

        QueryLoggerConfiguration queryLoggerConfiguration = getCassandraQueryLoggerConf(configuration);

        int maxRetries = configuration.getInt("cassandra.retryConnection.maxRetries", DEFAULT_CONNECTION_MAX_RETRIES);
        int minDelay = configuration.getInt("cassandra.retryConnection.minDelay", DEFAULT_CONNECTION_MIN_DELAY);

        cluster = RetryExecutorUtil.retryOnExceptions(executor, maxRetries, minDelay, NoHostAvailableException.class)
                .getWithRetry(getClusterRetryCallable(configuration, servers, queryLoggerConfiguration))
                .get();
    }

    private RetryCallable<Cluster> getClusterRetryCallable(PropertiesConfiguration configuration, List<Host> servers, QueryLoggerConfiguration queryLoggerConfiguration) {
        LOGGER.info("Trying to connect to Cassandra service at {} (list {})", LocalDateTime.now(), servers);

        return context -> {
            Cluster cluster = ClusterBuilder.builder()
                    .servers(servers)
                    .poolingOptions(readPoolingOptions(configuration))
                    .queryLoggerConfiguration(queryLoggerConfiguration)
                    .readTimeoutMillis(configuration.getInt("cassandra.readTimeoutMillis", DEFAULT_READ_TIMEOUT_MILLIS))
                    .connectTimeoutMillis(configuration.getInt("cassandra.connectTimeoutMillis", DEFAULT_CONNECT_TIMEOUT_MILLIS))
                    .build();
            try {
                return ClusterWithKeyspaceCreatedFactory
                    .config(cluster,
                            configuration.getString(CASSANDRA_KEYSPACE, DEFAULT_KEYSPACE))
                    .replicationFactor(configuration.getInt("cassandra.replication.factor", DEFAULT_REPLICATION_FACTOR))
                    .clusterWithInitializedKeyspace();
            } catch (Exception e) {
                cluster.close();
                throw e;
            }
        };
    }

    private Optional<PoolingOptions> readPoolingOptions(PropertiesConfiguration configuration) {

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

    private List<Host> listCassandraServers(PropertiesConfiguration configuration) {
        String[] ipAndPorts = configuration.getStringArray(CASSANDRA_NODES);

        return Arrays.stream(ipAndPorts)
                .map(string -> Host.parseConfString(string, ClusterBuilder.DEFAULT_CASSANDRA_PORT))
                .collect(Guavate.toImmutableList());
    }

    private Optional<Integer> getOptionalIntegerFromConf(PropertiesConfiguration configuration, String key) {
        return Optional.ofNullable(configuration.getInteger(key, null));
    }

    private Optional<Double> getOptionalDoubleFromConf(PropertiesConfiguration configuration, String key) {
        return Optional.ofNullable(configuration.getDouble(key, null));
    }

    private QueryLoggerConfiguration getCassandraQueryLoggerConf(PropertiesConfiguration configuration) {
        QueryLoggerConfiguration.Builder builder = QueryLoggerConfiguration.builder();

        Optional<Long> constantThreshold = getOptionalIntegerFromConf(configuration, "cassandra.query.logger.constant.threshold")
            .map(Long::valueOf);

        constantThreshold.ifPresent(builder::withConstantThreshold);

        getOptionalIntegerFromConf(configuration, "cassandra.query.logger.max.logged.parameters")
            .ifPresent(builder::withMaxLoggedParameters);

        getOptionalIntegerFromConf(configuration, "cassandra.query.logger.max.query.string.length")
            .ifPresent(builder::withMaxQueryStringLength);

        getOptionalIntegerFromConf(configuration, "cassandra.query.logger.max.parameter.value.length")
            .ifPresent(builder::withMaxParameterValueLength);

        Optional<Double> percentileLatencyConf = getOptionalDoubleFromConf(configuration, "cassandra.query.slow.query.latency.threshold.percentile");

        if (!percentileLatencyConf.isPresent() && !constantThreshold.isPresent()) {
            percentileLatencyConf = Optional.of(QueryLogger.DEFAULT_SLOW_QUERY_THRESHOLD_PERCENTILE);
        }

        percentileLatencyConf.ifPresent(slowQueryLatencyThresholdPercentile -> {
            PerHostPercentileTracker tracker = PerHostPercentileTracker
                .builder(CASSANDRA_HIGHEST_TRACKABLE_LATENCY_MILLIS)
                .build();

            builder.withDynamicThreshold(tracker, slowQueryLatencyThresholdPercentile);
        });

        return builder.build();
    }

    public Cluster get() {
        return cluster;
    }

    @PreDestroy
    public void stop() {
        cluster.closeAsync();
    }
    
}
