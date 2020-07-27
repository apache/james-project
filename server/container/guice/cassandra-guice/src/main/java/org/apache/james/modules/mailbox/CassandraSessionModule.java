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
import java.util.Set;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.init.KeyspaceFactory;
import org.apache.james.backends.cassandra.init.ResilientClusterProvider;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraHealthCheck;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.server.CassandraProbe;
import org.apache.james.util.Host;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class CassandraSessionModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraSessionModule.class);

    private static final String LOCALHOST = "127.0.0.1";
    private static final String BATCHSIZES_FILE_NAME = "batchsizes";
    private static final String CASSANDRA_FILE_NAME = "cassandra";
    private static final int CASSANDRA_PORT = 9042;

    @Override
    protected void configure() {
        bind(CassandraUtils.class).in(Scopes.SINGLETON);
        bind(Cluster.class).toProvider(ResilientClusterProvider.class);

        bind(InitializedCluster.class).in(Scopes.SINGLETON);

        bind(Session.class).toProvider(SessionWithInitializedTablesFactory.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraZonedDateTimeModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraSchemaVersionModule.MODULE);

        bind(CassandraSchemaVersionManager.class).in(Scopes.SINGLETON);
        bind(CassandraSchemaVersionDAO.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), StartUpCheck.class)
            .addBinding().to(CassandraSchemaVersionStartUpCheck.class);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(CassandraProbe.class);

        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding().to(CassandraHealthCheck.class);
    }

    @Singleton
    @Provides
    SessionWithInitializedTablesFactory provideSessionFactory(KeyspaceConfiguration keyspaceConfiguration,
                                               InitializedCluster cluster,
                                               CassandraModule module) {
        return new SessionWithInitializedTablesFactory(keyspaceConfiguration, cluster.cluster, module);
    }

    @Provides
    @Singleton
    CassandraModule composeDataDefinitions(Set<CassandraModule> modules) {
        return CassandraModule.aggregateModules(modules);
    }

    @Provides
    @Singleton
    BatchSizes getBatchSizesConfiguration(PropertiesProvider propertiesProvider) {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(BATCHSIZES_FILE_NAME);
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
            LOGGER.info("Could not locate batchsizes configuration file. Using default values.");
            return BatchSizes.defaultValues();
        }
    }

    @VisibleForTesting
    @Provides
    @Singleton
    CassandraConfiguration provideCassandraConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return CassandraConfiguration.from(propertiesProvider.getConfiguration(CASSANDRA_FILE_NAME));
        } catch (FileNotFoundException e) {
            return CassandraConfiguration.DEFAULT_CONFIGURATION;
        }
    }

    @VisibleForTesting
    @Provides
    @Singleton
    CassandraConsistenciesConfiguration provideCassandraConsistenciesConfiguration(CassandraConfiguration configuration) {
        return CassandraConsistenciesConfiguration.fromConfiguration(configuration);
    }

    @Provides
    @Singleton
    ClusterConfiguration provideClusterConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return ClusterConfiguration.from(propertiesProvider.getConfiguration(CASSANDRA_FILE_NAME));
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not locate cassandra configuration file. Defaulting to node " + LOCALHOST + ":" + CASSANDRA_PORT);
            return ClusterConfiguration.builder()
                .host(Host.from(LOCALHOST, CASSANDRA_PORT))
                .build();
        }
    }

    @Provides
    @Singleton
    KeyspacesConfiguration provideKeyspacesConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return KeyspacesConfiguration.from(propertiesProvider.getConfiguration(CASSANDRA_FILE_NAME));
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not locate cassandra configuration file. Using default keyspaces configuration instead");
            return KeyspacesConfiguration.builder().build();
        }
    }

    @Provides
    @Singleton
    KeyspaceConfiguration provideMainKeyspaceConfiguration(KeyspacesConfiguration keyspacesConfiguration) {
        return keyspacesConfiguration.mainKeyspaceConfiguration();
    }

    static class InitializedCluster {
        private final Cluster cluster;

        @Inject
        private InitializedCluster(Cluster cluster, ClusterConfiguration clusterConfiguration, KeyspacesConfiguration keyspacesConfiguration) {
            this.cluster = cluster;

            if (clusterConfiguration.shouldCreateKeyspace()) {
                KeyspaceFactory.createKeyspace(keyspacesConfiguration.mainKeyspaceConfiguration(), cluster);
            }
        }
    }
}
