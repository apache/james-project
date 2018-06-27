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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.server.CassandraProbe;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

public class CassandraSessionModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraSessionModule.class);

    private static final String LOCALHOST = "127.0.0.1";
    private static final String BATCHSIZES_FILE_NAME = "batchsizes";
    private static final String CASSANDRA_NODES = "cassandra.nodes";

    @Override
    protected void configure() {
        bind(ScheduledExecutorService.class).toProvider(ScheduledExecutorServiceProvider.class);
        bind(CassandraUtils.class).in(Scopes.SINGLETON);
        bind(Session.class).toProvider(SessionWithInitializedTablesFactory.class);
        bind(Cluster.class).toProvider(ResilientClusterProvider.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().to(CassandraZonedDateTimeModule.class);
        cassandraDataDefinitions.addBinding().to(CassandraSchemaVersionModule.class);

        bind(CassandraSchemaVersionManager.class).in(Scopes.SINGLETON);
        bind(CassandraSchemaVersionDAO.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(CassandraSchemaChecker.class);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(CassandraProbe.class);
    }

    @Provides
    @Singleton
    CassandraModule composeDataDefinitions(Set<CassandraModule> modules) {
        return new CassandraModuleComposite(modules.toArray(new CassandraModule[0]));
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
            LOGGER.debug("BatchSize configuration: {}", batchSizes);
            return batchSizes;
        } catch (FileNotFoundException | ConfigurationException e) {
            LOGGER.warn("Could not locate batchsizes configuration file. Using default values.");
            return BatchSizes.defaultValues();
        }
    }


    @Provides
    private AsyncRetryExecutor provideAsyncRetryExecutor(ScheduledExecutorService scheduler) {
        return new AsyncRetryExecutor(scheduler);
    }

    @VisibleForTesting
    @Provides
    @Singleton
    CassandraConfiguration provideCassandraConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        return CassandraConfiguration.from(getConfiguration(propertiesProvider));
    }

    @Provides
    @Singleton
    ClusterConfiguration provideClusterConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        return ClusterConfiguration.from(getConfiguration(propertiesProvider));
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
                            "Recommended version is %d",
                            versionManager.computeVersion().getValue(),
                            versionManager.getMinimumSupportedVersion().getValue(),
                            versionManager.getMaximumSupportedVersion().getValue()));
                case TOO_RECENT:
                    throw new IllegalStateException(
                        String.format("Current schema version is %d whereas the minimum supported version is %d. " +
                            "Recommended version is %d.",
                            versionManager.computeVersion().getValue(),
                            versionManager.getMinimumSupportedVersion().getValue(),
                            versionManager.getMaximumSupportedVersion().getValue()));
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
