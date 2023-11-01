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

package org.apache.james.backends.postgres;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import reactor.core.publisher.Mono;

public class PostgresExtension implements GuiceModuleTestExtension {
    private final PostgresModule postgresModule;
    private PostgresExecutor postgresExecutor;

    public PostgresExtension(PostgresModule postgresModule) {
        this.postgresModule = postgresModule;
    }

    public PostgresExtension() {
        this(PostgresModule.EMPTY_MODULE);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        if (!DockerPostgresSingleton.SINGLETON.isRunning()) {
            DockerPostgresSingleton.SINGLETON.start();
        }
        initPostgresSession();
    }

    private void initPostgresSession() {
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(getHost())
            .port(getMappedPort())
            .username(PostgresFixture.Database.DB_USER)
            .password(PostgresFixture.Database.DB_PASSWORD)
            .database(PostgresFixture.Database.DB_NAME)
            .schema(PostgresFixture.Database.SCHEMA)
            .build());

        postgresExecutor = new PostgresExecutor(connectionFactory.create()
            .cache()
            .cast(Connection.class));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        disposePostgresSession();
    }

    private void disposePostgresSession() {
        postgresExecutor.dispose().block();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        initTablesAndIndexes();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        resetSchema();
    }

    @Override
    public Module getModule() {
        // TODO: return PostgresConfiguration bean when doing https://github.com/linagora/james-project/issues/4910
        return GuiceModuleTestExtension.super.getModule();
    }

    public String getHost() {
        return DockerPostgresSingleton.SINGLETON.getHost();
    }

    public Integer getMappedPort() {
        return DockerPostgresSingleton.SINGLETON.getMappedPort(PostgresFixture.PORT);
    }

    public Mono<Connection> getConnection() {
        return postgresExecutor.connection();
    }

    private void initTablesAndIndexes() {
        PostgresTableManager postgresTableManager = new PostgresTableManager(postgresExecutor, postgresModule);
        postgresTableManager.initializeTables().block();
        postgresTableManager.initializeTableIndexes().block();
    }

    private void resetSchema() {
        getConnection()
            .flatMapMany(connection -> Mono.from(connection.createStatement("DROP SCHEMA " + PostgresFixture.Database.SCHEMA + " CASCADE").execute())
                .then(Mono.from(connection.createStatement("CREATE SCHEMA " + PostgresFixture.Database.SCHEMA).execute()))
                .flatMap(result -> Mono.from(result.getRowsUpdated())))
            .collectList()
            .block();
    }
}
