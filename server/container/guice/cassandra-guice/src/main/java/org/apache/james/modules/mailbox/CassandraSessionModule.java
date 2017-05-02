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

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.init.ClusterBuilder;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.QueryLoggerConfiguration;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.util.Host;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.utils.RetryExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PerHostPercentileTracker;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.github.steveash.guavate.Guavate;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import com.nurkiewicz.asyncretry.function.RetryCallable;

public class CassandraSessionModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraSessionModule.class);

    private static final int DEFAULT_CONNECTION_MAX_RETRIES = 10;
    private static final int DEFAULT_CONNECTION_MIN_DELAY = 5000;
    private static final long CASSANDRA_HIGHEST_TRACKABLE_LATENCY_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final int DEFAULT_REPLICATION_FACTOR = 1;
    private static final String DEFAULT_KEYSPACE = "apache_james";
    private static final String CASSANDRA_NODES = "cassandra.nodes";
    private static final String LOCALHOST = "127.0.0.1";
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;


    @Override
    protected void configure() {
        bind(ScheduledExecutorService.class).toProvider(ScheduledExecutorServiceProvider.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().to(CassandraZonedDateTimeModule.class);
    }

    @Provides
    @Singleton
    CassandraModule composeDataDefinitions(Set<CassandraModule> modules) {
        return new CassandraModuleComposite(modules.toArray(new CassandraModule[0]));
    }

    @Provides
    @Singleton
    Session provideSession(CassandraSessionConfiguration configuration, Cluster cluster, CassandraModule cassandraModule)
            throws FileNotFoundException, ConfigurationException{
        String keyspace = configuration.getConfiguration().getString("cassandra.keyspace", DEFAULT_KEYSPACE);
        return new SessionWithInitializedTablesFactory(cassandraModule).createSession(cluster, keyspace);
    }

    @Provides
    @Singleton
    CassandraSessionConfiguration getCassandraSessionConfiguration(PropertiesProvider propertiesProvider) {
        return () -> getConfiguration(propertiesProvider);
    }

    @Provides
    @Singleton
    Cluster provideCluster(CassandraSessionConfiguration cassandraSessionConfiguration, AsyncRetryExecutor executor) throws FileNotFoundException, ConfigurationException, ExecutionException, InterruptedException {
        PropertiesConfiguration configuration = cassandraSessionConfiguration.getConfiguration();
        List<Host> servers = listCassandraServers(configuration);
        QueryLoggerConfiguration queryLoggerConfiguration = getCassandraQueryLoggerConf(configuration);

        int maxRetries = configuration.getInt("cassandra.retryConnection.maxRetries", DEFAULT_CONNECTION_MAX_RETRIES);
        int minDelay = configuration.getInt("cassandra.retryConnection.minDelay", DEFAULT_CONNECTION_MIN_DELAY);

        return RetryExecutorUtil.retryOnExceptions(executor, maxRetries, minDelay, NoHostAvailableException.class)
                .getWithRetry(getClusterRetryCallable(configuration, servers, queryLoggerConfiguration))
                .get();
    }

    private RetryCallable<Cluster> getClusterRetryCallable(PropertiesConfiguration configuration, List<Host> servers, QueryLoggerConfiguration queryLoggerConfiguration) {
        LOGGER.info("Trying to connect to Cassandra service");

        return context -> ClusterWithKeyspaceCreatedFactory
            .config(ClusterBuilder.builder()
                    .servers(servers)
                    .queryLoggerConfiguration(queryLoggerConfiguration)
                    .readTimeoutMillis(configuration.getInt("cassandra.readTimeoutMillis", DEFAULT_READ_TIMEOUT_MILLIS))
                    .connectTimeoutMillis(configuration.getInt("cassandra.connectTimeoutMillis", DEFAULT_CONNECT_TIMEOUT_MILLIS))
                    .build(),
                    configuration.getString("cassandra.keyspace", DEFAULT_KEYSPACE))
            .replicationFactor(configuration.getInt("cassandra.replication.factor", DEFAULT_REPLICATION_FACTOR))
            .clusterWithInitializedKeyspace();
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
                .builderWithHighestTrackableLatencyMillis(CASSANDRA_HIGHEST_TRACKABLE_LATENCY_MILLIS)
                .build();

            builder.withDynamicThreshold(tracker, slowQueryLatencyThresholdPercentile);
        });

        return builder.build();
    }

    @Provides
    private AsyncRetryExecutor provideAsyncRetryExecutor(ScheduledExecutorService scheduler) {
        return new AsyncRetryExecutor(scheduler);
    }

    private PropertiesConfiguration getConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return propertiesProvider.getConfiguration("cassandra");
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not locate cassandra configuration file. Defaulting to node " + LOCALHOST + ":9042");
            PropertiesConfiguration propertiesConfiguration = new PropertiesConfiguration();
            propertiesConfiguration.addProperty(CASSANDRA_NODES, LOCALHOST);
            return propertiesConfiguration;
        }
    }
}
