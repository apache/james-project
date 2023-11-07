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

import java.io.FileNotFoundException;
import java.util.Set;

import javax.inject.Singleton;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.postgres.PostgresConfiguration;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTableManager;
import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.SimpleJamesPostgresConnectionFactory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;

public class PostgresCommonModule extends AbstractModule {
    @Override
    public void configure() {
        bind(JamesPostgresConnectionFactory.class).to(SimpleJamesPostgresConnectionFactory.class);

        Multibinder.newSetBinder(binder(), PostgresModule.class);

        bind(SimpleJamesPostgresConnectionFactory.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    PostgresConfiguration provideConfiguration(PropertiesProvider propertiesProvider) throws FileNotFoundException, ConfigurationException {
        return PostgresConfiguration.from(propertiesProvider.getConfiguration("postgres"));
    }

    @Provides
    @Singleton
    ConnectionFactory postgresqlConnectionFactory(PostgresConfiguration postgresConfiguration) {
        return new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(postgresConfiguration.getUrl().getHost())
            .port(postgresConfiguration.getUrl().getPort())
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
    PostgresTableManager postgresTableManager(JamesPostgresConnectionFactory jamesPostgresConnectionFactory,
                                              PostgresModule postgresModule) {
        return new PostgresTableManager(jamesPostgresConnectionFactory, postgresModule);
    }

    @ProvidesIntoSet
    InitializationOperation provisionPostgresTablesAndIndexes(PostgresTableManager postgresTableManager) {
        return InitilizationOperationBuilder
            .forClass(PostgresTableManager.class)
            .init(() -> postgresTableManager.initializeTables()
                .then(postgresTableManager.initializeTableIndexes())
                .block());
    }
}
