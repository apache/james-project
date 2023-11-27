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

import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;

import java.io.FileNotFoundException;
import java.util.Set;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.postgres.PostgresConfiguration;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTableManager;
import org.apache.james.backends.postgres.utils.DomainImplPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.postgres.utils.SinglePostgresConnectionFactory;
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
import reactor.core.publisher.Mono;

public class PostgresCommonModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("POSTGRES");

    @Override
    public void configure() {
        Multibinder.newSetBinder(binder(), PostgresModule.class);
        bind(PostgresExecutor.Factory.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    PostgresConfiguration provideConfiguration(PropertiesProvider propertiesProvider) throws FileNotFoundException, ConfigurationException {
        return PostgresConfiguration.from(propertiesProvider.getConfiguration("postgres"));
    }

    @Provides
    @Singleton
    JamesPostgresConnectionFactory provideJamesPostgresConnectionFactory(PostgresConfiguration postgresConfiguration, ConnectionFactory connectionFactory) {
        if (postgresConfiguration.rowLevelSecurityEnabled()) {
            LOGGER.info("PostgreSQL row level security enabled");
            LOGGER.info("Implementation for PostgreSQL connection factory: {}", DomainImplPostgresConnectionFactory.class.getName());
            return new DomainImplPostgresConnectionFactory(connectionFactory);
        }
        LOGGER.info("Implementation for PostgreSQL connection factory: {}", SinglePostgresConnectionFactory.class.getName());
        return new SinglePostgresConnectionFactory(Mono.from(connectionFactory.create()).block());
    }

    @Provides
    @Singleton
    ConnectionFactory postgresqlConnectionFactory(PostgresConfiguration postgresConfiguration) {
        return new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(postgresConfiguration.getUri().getHost())
            .port(postgresConfiguration.getUri().getPort())
            .username(postgresConfiguration.getCredential().getUsername())
            .password(postgresConfiguration.getCredential().getPassword())
            .database(postgresConfiguration.getDatabaseName())
            .schema(postgresConfiguration.getDatabaseSchema())
            .build());
    }

    @Provides
    @Singleton
    PostgresModule composePostgresDataDefinitions(Set<PostgresModule> modules) {
        return PostgresModule.aggregateModules(modules);
    }

    @Provides
    @Singleton
    PostgresTableManager postgresTableManager(@Named(DEFAULT_INJECT) PostgresExecutor defaultPostgresExecutor,
                                              PostgresModule postgresModule,
                                              PostgresConfiguration postgresConfiguration) {
        return new PostgresTableManager(defaultPostgresExecutor, postgresModule, postgresConfiguration);
    }

    @Provides
    @Named(DEFAULT_INJECT)
    @Singleton
    PostgresExecutor defaultPostgresExecutor(PostgresExecutor.Factory factory) {
        return factory.create();
    }

    @ProvidesIntoSet
    InitializationOperation provisionPostgresTablesAndIndexes(PostgresTableManager postgresTableManager) {
        return InitilizationOperationBuilder
            .forClass(PostgresTableManager.class)
            .init(() -> postgresTableManager.initializePostgresExtension()
                .then(postgresTableManager.initializeTables())
                .then(postgresTableManager.initializeTableIndexes())
                .block());
    }
}
