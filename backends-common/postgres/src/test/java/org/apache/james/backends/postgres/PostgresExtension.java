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

import static org.apache.james.backends.postgres.PostgresFixture.Database.DEFAULT_DATABASE;
import static org.apache.james.backends.postgres.PostgresFixture.Database.ROW_LEVEL_SECURITY_DATABASE;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.backends.postgres.utils.DomainImplPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.postgres.utils.SinglePostgresConnectionFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;

import com.github.dockerjava.api.command.PauseContainerCmd;
import com.github.dockerjava.api.command.UnpauseContainerCmd;
import com.github.fge.lambdas.Throwing;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

public class PostgresExtension implements GuiceModuleTestExtension {
    private static final boolean ROW_LEVEL_SECURITY_ENABLED = true;

    public static PostgresExtension withRowLevelSecurity(PostgresModule module) {
        return new PostgresExtension(module, ROW_LEVEL_SECURITY_ENABLED);
    }

    public static PostgresExtension withoutRowLevelSecurity(PostgresModule module) {
        return new PostgresExtension(module, !ROW_LEVEL_SECURITY_ENABLED);
    }

    public static PostgresExtension empty() {
        return withoutRowLevelSecurity(PostgresModule.EMPTY_MODULE);
    }

    public static PostgreSQLContainer<?> PG_CONTAINER = DockerPostgresSingleton.SINGLETON;
    private final PostgresModule postgresModule;
    private final boolean rlsEnabled;
    private final PostgresFixture.Database selectedDatabase;
    private PostgresConfiguration postgresConfiguration;
    private PostgresExecutor postgresExecutor;
    private PostgresqlConnectionFactory connectionFactory;
    private PostgresExecutor.Factory executorFactory;

    public void pause() {
        PG_CONTAINER.getDockerClient().pauseContainerCmd(PG_CONTAINER.getContainerId())
            .exec();
    }

    public void unpause() {
        PG_CONTAINER.getDockerClient().unpauseContainerCmd(PG_CONTAINER.getContainerId())
            .exec();
    }

    private PostgresExtension(PostgresModule postgresModule, boolean rlsEnabled) {
        this.postgresModule = postgresModule;
        this.rlsEnabled = rlsEnabled;
        if (rlsEnabled) {
            this.selectedDatabase = PostgresFixture.Database.ROW_LEVEL_SECURITY_DATABASE;
        } else {
            this.selectedDatabase = DEFAULT_DATABASE;
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        if (!PG_CONTAINER.isRunning()) {
            PG_CONTAINER.start();
        }
        querySettingRowLevelSecurityIfNeed();
        querySettingExtension();
        initPostgresSession();
    }

    private void querySettingRowLevelSecurityIfNeed() {
        if (rlsEnabled) {
            Throwing.runnable(() -> {
                PG_CONTAINER.execInContainer("psql", "-U", DEFAULT_DATABASE.dbUser(), "-c", "create user " + ROW_LEVEL_SECURITY_DATABASE.dbUser() + " WITH PASSWORD '" + ROW_LEVEL_SECURITY_DATABASE.dbPassword() + "';");
                PG_CONTAINER.execInContainer("psql", "-U", DEFAULT_DATABASE.dbUser(), "-c", "create database " + ROW_LEVEL_SECURITY_DATABASE.dbName() + ";");
                PG_CONTAINER.execInContainer("psql", "-U", DEFAULT_DATABASE.dbUser(), "-c", "grant all privileges on database " + ROW_LEVEL_SECURITY_DATABASE.dbName() + " to " + ROW_LEVEL_SECURITY_DATABASE.dbUser() + ";");
                PG_CONTAINER.execInContainer("psql", "-U", ROW_LEVEL_SECURITY_DATABASE.dbUser(), "-d", ROW_LEVEL_SECURITY_DATABASE.dbName(), "-c", "create schema if not exists " + ROW_LEVEL_SECURITY_DATABASE.schema() + ";");
            }).sneakyThrow().run();
        }
    }

    private void querySettingExtension() throws IOException, InterruptedException {
        PG_CONTAINER.execInContainer("psql", "-U", selectedDatabase.dbUser(), selectedDatabase.dbName(), "-c", String.format("CREATE EXTENSION IF NOT EXISTS hstore SCHEMA %s;", selectedDatabase.schema()));
    }

    private void initPostgresSession() throws URISyntaxException {
        postgresConfiguration = PostgresConfiguration.builder()
            .url(new URIBuilder()
                .setScheme("postgresql")
                .setHost(getHost())
                .setPort(getMappedPort())
                .setUserInfo(selectedDatabase.dbUser(), selectedDatabase.dbPassword())
                .build()
                .toString())
            .databaseName(selectedDatabase.dbName())
            .databaseSchema(selectedDatabase.schema())
            .rowLevelSecurityEnabled(rlsEnabled)
            .build();

        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(postgresConfiguration.getUri().getHost())
            .port(postgresConfiguration.getUri().getPort())
            .username(postgresConfiguration.getCredential().getUsername())
            .password(postgresConfiguration.getCredential().getPassword())
            .database(postgresConfiguration.getDatabaseName())
            .schema(postgresConfiguration.getDatabaseSchema())
            .build());


        if (rlsEnabled) {
            executorFactory = new PostgresExecutor.Factory(new DomainImplPostgresConnectionFactory(connectionFactory));
        } else {
            executorFactory = new PostgresExecutor.Factory(new SinglePostgresConnectionFactory(connectionFactory.create()
                .cache()
                .cast(Connection.class).block()));
        }

        postgresExecutor = executorFactory.create();
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

    public void restartContainer() throws URISyntaxException {
        PG_CONTAINER.stop();
        PG_CONTAINER.start();
        initPostgresSession();
    }

    @Override
    public Module getModule() {
        return Modules.combine(binder -> binder.bind(PostgresConfiguration.class)
            .toInstance(postgresConfiguration));
    }

    public String getHost() {
        return PG_CONTAINER.getHost();
    }

    public Integer getMappedPort() {
        return PG_CONTAINER.getMappedPort(PostgresFixture.PORT);
    }

    public Mono<Connection> getConnection() {
        return postgresExecutor.connection();
    }

    public PostgresExecutor getPostgresExecutor() {
        return postgresExecutor;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public PostgresExecutor.Factory getExecutorFactory() {
        return executorFactory;
    }

    public PostgresConfiguration getPostgresConfiguration() {
        return postgresConfiguration;
    }

    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", getHost(), getMappedPort(), postgresConfiguration.getDatabaseName());
    }

    private void initTablesAndIndexes() {
        PostgresTableManager postgresTableManager = new PostgresTableManager(postgresExecutor, postgresModule, postgresConfiguration.rowLevelSecurityEnabled());
        postgresTableManager.initializeTables().block();
        postgresTableManager.initializeTableIndexes().block();
    }

    private void resetSchema() {
        dropTables(listAllTables());
    }

    private void dropTables(List<String> tables) {
        String tablesToDelete = tables.stream()
            .map(tableName -> "\"" + tableName + "\"")
            .collect(Collectors.joining(", "));

        postgresExecutor.connection()
            .flatMapMany(connection -> connection.createStatement(String.format("DROP table if exists %s cascade;", tablesToDelete))
                .execute())
            .then()
            .block();
    }

    private List<String> listAllTables() {
        return postgresExecutor.connection()
            .flatMapMany(connection -> connection.createStatement(String.format("SELECT tablename FROM pg_tables WHERE schemaname = '%s'", selectedDatabase.schema()))
                .execute())
            .flatMap(result -> result.map((row, rowMetadata) -> row.get(0, String.class)))
            .collectList()
            .block();
    }
}
