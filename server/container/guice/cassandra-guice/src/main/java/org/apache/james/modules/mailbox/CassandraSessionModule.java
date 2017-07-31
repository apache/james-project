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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.init.ClusterBuilder;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.QueryLoggerConfiguration;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.util.Host;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.utils.RetryExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PerHostPercentileTracker;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
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
    private static final String BATCHSIZES_FILE_NAME = "batchsizes";
    private static final String MAILBOX_MAX_RETRY_ACL = "mailbox.max.retry.acl";
    private static final String MAILBOX_MAX_RETRY_MODSEQ = "mailbox.max.retry.modseq";
    private static final String MAILBOX_MAX_RETRY_UID = "mailbox.max.retry.uid";
    private static final String MAILBOX_MAX_RETRY_MESSAGE_FLAGS_UPDATE = "mailbox.max.retry.message.flags.update";
    private static final String MAILBOX_MAX_RETRY_MESSAGE_ID_FLAGS_UPDATE = "mailbox.max.retry.message.id.flags.update";
    private static final String FETCH_ADVANCE_ROW_COUNT = "fetch.advance.row.count";
    private static final String CHUNK_SIZE_FLAGS_UPDATE = "chunk.size.flags.update";
    private static final String CHUNK_SIZE_MESSAGE_READ = "chunk.size.message.read";
    private static final String CHUNK_SIZE_EXPUNGE = "chunk.size.expunge";
    private static final String BLOB_PART_SIZE = "mailbox.blob.part.size";
    private static final String MIGRATION_V1_V2_ON_THE_FLY = "migration.v1.v2.on.the.fly";
    private static final String MIGRATION_V1_V2_THREAD_COUNT = "migration.v1.v2.thread.count";
    private static final String MIGRATION_V1_V2_QUEUE_LENGTH = "migration.v1.v2.queue.length";
    public static final String MIGRATION_V1_READ_SIZE = "migration.v1.read.fetch.size";

    @Override
    protected void configure() {
        bind(ScheduledExecutorService.class).toProvider(ScheduledExecutorServiceProvider.class);
        bind(CassandraUtils.class).in(Scopes.SINGLETON);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().to(CassandraZonedDateTimeModule.class);
        cassandraDataDefinitions.addBinding().to(CassandraSchemaVersionModule.class);

        bind(CassandraSchemaVersionManager.class).in(Scopes.SINGLETON);
        bind(CassandraSchemaVersionDAO.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(CassandraSchemaChecker.class);
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
    BatchSizes getBatchSizesConfiguration(PropertiesProvider propertiesProvider) {
        try {
            PropertiesConfiguration configuration = propertiesProvider.getConfiguration(BATCHSIZES_FILE_NAME);
            BatchSizes batchSizes = BatchSizes.builder()
                    .fetchMetadata(configuration.getInt("fetch.metadata", BatchSizes.DEFAULT_BATCH_SIZE))
                    .fetchHeaders(configuration.getInt("fetch.headers", BatchSizes.DEFAULT_BATCH_SIZE))
                    .fetchBody(configuration.getInt("fetch.body", BatchSizes.DEFAULT_BATCH_SIZE))
                    .fetchFull(configuration.getInt("fetch.full", BatchSizes.DEFAULT_BATCH_SIZE))
                    .copyBatchSize(configuration.getInt("copy", BatchSizes.DEFAULT_BATCH_SIZE))
                    .moveBatchSize(configuration.getInt("move", BatchSizes.DEFAULT_BATCH_SIZE))
                    .build();
            LOGGER.debug("BatchSize configuration: " + batchSizes);
            return batchSizes;
        } catch (FileNotFoundException | ConfigurationException e) {
            LOGGER.warn("Could not locate batchsizes configuration file. Using default values.");
            return BatchSizes.defaultValues();
        }
    }

    @Provides
    @Singleton
    Cluster provideCluster(CassandraSessionConfiguration cassandraSessionConfiguration, AsyncRetryExecutor executor) throws ConfigurationException, ExecutionException, InterruptedException {
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
        LOGGER.info("Trying to connect to Cassandra service at {}", LocalDateTime.now());

        return context -> ClusterWithKeyspaceCreatedFactory
            .config(ClusterBuilder.builder()
                    .servers(servers)
                    .poolingOptions(readPoolingOptions(configuration))
                    .queryLoggerConfiguration(queryLoggerConfiguration)
                    .readTimeoutMillis(configuration.getInt("cassandra.readTimeoutMillis", DEFAULT_READ_TIMEOUT_MILLIS))
                    .connectTimeoutMillis(configuration.getInt("cassandra.connectTimeoutMillis", DEFAULT_CONNECT_TIMEOUT_MILLIS))
                    .build(),
                    configuration.getString("cassandra.keyspace", DEFAULT_KEYSPACE))
            .replicationFactor(configuration.getInt("cassandra.replication.factor", DEFAULT_REPLICATION_FACTOR))
            .clusterWithInitializedKeyspace();
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

    @Provides
    private AsyncRetryExecutor provideAsyncRetryExecutor(ScheduledExecutorService scheduler) {
        return new AsyncRetryExecutor(scheduler);
    }

    @VisibleForTesting
    @Provides
    @Singleton
    CassandraConfiguration provideCassandraConfiguration(CassandraSessionConfiguration sessionConfiguration) throws ConfigurationException {
        PropertiesConfiguration propertiesConfiguration = sessionConfiguration.getConfiguration();

        return CassandraConfiguration.builder()
            .aclMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_ACL, null)))
            .modSeqMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_MODSEQ, null)))
            .uidMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_UID, null)))
            .flagsUpdateMessageMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_MESSAGE_FLAGS_UPDATE, null)))
            .flagsUpdateMessageIdMaxRetry(Optional.ofNullable(
                propertiesConfiguration.getInteger(MAILBOX_MAX_RETRY_MESSAGE_ID_FLAGS_UPDATE, null)))
            .fetchNextPageInAdvanceRow(Optional.ofNullable(
                propertiesConfiguration.getInteger(FETCH_ADVANCE_ROW_COUNT, null)))
            .flagsUpdateChunkSize(Optional.ofNullable(
                propertiesConfiguration.getInteger(CHUNK_SIZE_FLAGS_UPDATE, null)))
            .messageReadChunkSize(Optional.ofNullable(
                propertiesConfiguration.getInteger(CHUNK_SIZE_MESSAGE_READ, null)))
            .expungeChunkSize(Optional.ofNullable(
                propertiesConfiguration.getInteger(CHUNK_SIZE_EXPUNGE, null)))
            .blobPartSize(Optional.ofNullable(
                propertiesConfiguration.getInteger(BLOB_PART_SIZE, null)))
            .onTheFlyV1ToV2Migration(Optional.ofNullable(
                propertiesConfiguration.getBoolean(MIGRATION_V1_V2_ON_THE_FLY, null)))
            .v1ToV2ThreadCount(Optional.ofNullable(
                propertiesConfiguration.getInteger(MIGRATION_V1_V2_THREAD_COUNT, null)))
            .v1ToV2QueueLength(Optional.ofNullable(
                propertiesConfiguration.getInteger(MIGRATION_V1_V2_QUEUE_LENGTH, null)))
            .v1ReadFetchSize(Optional.ofNullable(
                propertiesConfiguration.getInteger(MIGRATION_V1_READ_SIZE, null)))
            .build();
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

    public static class CassandraSchemaChecker implements ConfigurationPerformer {
        private final CassandraSchemaVersionManager versionManager;

        @Inject
        public CassandraSchemaChecker(CassandraSchemaVersionManager versionManager) {
            this.versionManager = versionManager;
        }

        @Override
        public void initModule() {
            SchemaState schemaState = versionManager.computeSchemaState();
            switch (schemaState) {
                case TOO_OLD:
                    throw new IllegalStateException(
                        String.format("Current schema version is %d whereas minimum required version is %d. " +
                            "Recommended version is %d", versionManager.computeVersion(), versionManager.getMinimumSupportedVersion(),
                            versionManager.getMaximumSupportedVersion()));
                case TOO_RECENT:
                    throw new IllegalStateException(
                        String.format("Current schema version is %d whereas the minimum supported version is %d. " +
                            "Recommended version is %d.", versionManager.computeVersion(), versionManager.getMinimumSupportedVersion(),
                            versionManager.getMaximumSupportedVersion()));
                case UP_TO_DATE:
                    LOGGER.info("Schema version is up-to-date");
                    return;
                case UPGRADABLE:
                    LOGGER.warn("Current schema version is {}. Recommended version is {}", versionManager.computeVersion(),
                        versionManager.getMaximumSupportedVersion());
                    return;
                default:
                    throw new IllegalStateException("Unknown schema state " + schemaState);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of();
        }
    }
}
