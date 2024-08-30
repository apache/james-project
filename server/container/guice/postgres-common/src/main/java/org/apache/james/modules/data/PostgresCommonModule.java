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
package org.apache.james.modules.data;

import static org.apache.james.backends.postgres.PostgresTableManager.INITIALIZATION_PRIORITY;
import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;

import java.io.FileNotFoundException;
import java.util.Set;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.postgres.PostgresConfiguration;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTableManager;
import org.apache.james.backends.postgres.RowLevelSecurity;
import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PoolBackedPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresConnectionClosure;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.postgres.utils.PostgresHealthCheck;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;

public class PostgresCommonModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("POSTGRES");

    @Override
    public void configure() {
        Multibinder.newSetBinder(binder(), PostgresModule.class);

        bind(PostgresExecutor.Factory.class).in(Scopes.SINGLETON);
        bind(PostgresConnectionClosure.class).asEagerSingleton();

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding().to(PostgresHealthCheck.class);
    }

    @Provides
    @Singleton
    PostgresConfiguration provideConfiguration(PropertiesProvider propertiesProvider) throws FileNotFoundException, ConfigurationException {
        return PostgresConfiguration.from(propertiesProvider.getConfiguration(PostgresConfiguration.POSTGRES_CONFIGURATION_NAME));
    }

    @Provides
    @Singleton
    JamesPostgresConnectionFactory provideJamesPostgresConnectionFactory(PostgresConfiguration postgresConfiguration,
                                                                         ConnectionFactory connectionFactory) {
        return new PoolBackedPostgresConnectionFactory(postgresConfiguration.getRowLevelSecurity(),
            postgresConfiguration.poolInitialSize(),
            postgresConfiguration.poolMaxSize(),
            connectionFactory);
    }

    @Provides
    @Named(JamesPostgresConnectionFactory.BY_PASS_RLS_INJECT)
    @Singleton
    JamesPostgresConnectionFactory provideJamesPostgresConnectionFactoryWithRLSBypass(PostgresConfiguration postgresConfiguration,
                                                                                      JamesPostgresConnectionFactory jamesPostgresConnectionFactory,
                                                                                      @Named(JamesPostgresConnectionFactory.BY_PASS_RLS_INJECT) ConnectionFactory connectionFactory) {
        if (!postgresConfiguration.getRowLevelSecurity().isRowLevelSecurityEnabled()) {
            return jamesPostgresConnectionFactory;
        }
        return new PoolBackedPostgresConnectionFactory(RowLevelSecurity.DISABLED,
            postgresConfiguration.byPassRLSPoolInitialSize(),
            postgresConfiguration.byPassRLSPoolMaxSize(),
            connectionFactory);
    }

    @Provides
    @Singleton
    ConnectionFactory postgresqlConnectionFactory(PostgresConfiguration postgresConfiguration) {
        return new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(postgresConfiguration.getHost())
            .port(postgresConfiguration.getPort())
            .username(postgresConfiguration.getDefaultCredential().getUsername())
            .password(postgresConfiguration.getDefaultCredential().getPassword())
            .database(postgresConfiguration.getDatabaseName())
            .schema(postgresConfiguration.getDatabaseSchema())
            .sslMode(postgresConfiguration.getSslMode())
            .build());
    }

    @Provides
    @Named(JamesPostgresConnectionFactory.BY_PASS_RLS_INJECT)
    @Singleton
    ConnectionFactory postgresqlConnectionFactoryRLSBypass(PostgresConfiguration postgresConfiguration) {
        return new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(postgresConfiguration.getHost())
            .port(postgresConfiguration.getPort())
            .username(postgresConfiguration.getByPassRLSCredential().getUsername())
            .password(postgresConfiguration.getByPassRLSCredential().getPassword())
            .database(postgresConfiguration.getDatabaseName())
            .schema(postgresConfiguration.getDatabaseSchema())
            .sslMode(postgresConfiguration.getSslMode())
            .build());
    }

    @Provides
    @Singleton
    PostgresModule composePostgresDataDefinitions(Set<PostgresModule> modules) {
        return PostgresModule.aggregateModules(modules);
    }

    @Provides
    @Singleton
    PostgresTableManager postgresTableManager(PostgresExecutor postgresExecutor,
                                              PostgresModule postgresModule,
                                              PostgresConfiguration postgresConfiguration) {
        return new PostgresTableManager(postgresExecutor, postgresModule, postgresConfiguration);
    }

    @Provides
    @Named(PostgresExecutor.BY_PASS_RLS_INJECT)
    @Singleton
    PostgresExecutor.Factory postgresExecutorFactoryWithRLSBypass(@Named(PostgresExecutor.BY_PASS_RLS_INJECT) JamesPostgresConnectionFactory singlePostgresConnectionFactory,
                                                                  PostgresConfiguration postgresConfiguration,
                                                                  MetricFactory metricFactory) {
        return new PostgresExecutor.Factory(singlePostgresConnectionFactory, postgresConfiguration, metricFactory);
    }

    @Provides
    @Named(DEFAULT_INJECT)
    @Singleton
    PostgresExecutor defaultPostgresExecutor(PostgresExecutor.Factory factory) {
        return factory.create();
    }

    @Provides
    @Named(PostgresExecutor.BY_PASS_RLS_INJECT)
    @Singleton
    PostgresExecutor postgresExecutorWithRLSBypass(@Named(PostgresExecutor.BY_PASS_RLS_INJECT) PostgresExecutor.Factory factory) {
        return factory.create();
    }

    @Provides
    @Singleton
    PostgresExecutor postgresExecutor(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor) {
        return postgresExecutor;
    }

    @ProvidesIntoSet
    InitializationOperation provisionPostgresTablesAndIndexes(PostgresTableManager postgresTableManager) {
        return InitilizationOperationBuilder
            .forClass(PostgresTableManager.class, INITIALIZATION_PRIORITY)
            .init(postgresTableManager::initPostgres);
    }
}
