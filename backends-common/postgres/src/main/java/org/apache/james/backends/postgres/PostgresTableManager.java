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

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.lifecycle.api.Startable;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresTableManager implements Startable {
    public static final int INITIALIZATION_PRIORITY = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresTableManager.class);
    private final PostgresExecutor postgresExecutor;
    private final PostgresModule module;
    private final boolean rowLevelSecurityEnabled;

    @Inject
    public PostgresTableManager(PostgresExecutor postgresExecutor,
                                PostgresModule module,
                                PostgresConfiguration postgresConfiguration) {
        this.postgresExecutor = postgresExecutor;
        this.module = module;
        this.rowLevelSecurityEnabled = postgresConfiguration.rowLevelSecurityEnabled();
    }

    @VisibleForTesting
    public PostgresTableManager(PostgresExecutor postgresExecutor, PostgresModule module, boolean rowLevelSecurityEnabled) {
        this.postgresExecutor = postgresExecutor;
        this.module = module;
        this.rowLevelSecurityEnabled = rowLevelSecurityEnabled;
    }

    public void initPostgres() {
        initializePostgresExtension()
            .then(initializeTables())
            .then(initializeTableIndexes())
            .block();
    }

    public Mono<Void> initializePostgresExtension() {
        return Mono.usingWhen(postgresExecutor.connectionFactory().getConnection(Optional.empty()),
            connection -> Mono.just(connection)
                .flatMapMany(pgConnection -> pgConnection.createStatement("CREATE EXTENSION IF NOT EXISTS hstore")
                    .execute())
                .flatMap(Result::getRowsUpdated)
                .then(),
            connection -> postgresExecutor.connectionFactory().closeConnection(connection));
    }

    public Mono<Void> initializeTables() {
        return Mono.usingWhen(postgresExecutor.connectionFactory().getConnection(Optional.empty()),
            connection -> postgresExecutor.dslContext(connection)
                .flatMapMany(dsl -> listExistTables()
                    .flatMapMany(existTables -> Flux.fromIterable(module.tables())
                        .filter(table -> !existTables.contains(table.getName()))
                        .flatMap(table -> createAndAlterTable(table, dsl, connection))))
                .then(),
            connection -> postgresExecutor.connectionFactory().closeConnection(connection));
    }

    private Mono<Void> createAndAlterTable(PostgresTable table, DSLContext dsl, Connection connection) {
        return Mono.from(table.getCreateTableStepFunction().apply(dsl))
            .then(alterTableIfNeeded(table, connection))
            .doOnSuccess(any -> LOGGER.info("Table {} created", table.getName()))
            .onErrorResume(exception -> handleTableCreationException(table, exception));
    }

    public Mono<List<String>> listExistTables() {
        return Mono.usingWhen(postgresExecutor.connectionFactory().getConnection(Optional.empty()),
            connection -> postgresExecutor.dslContext(connection)
                .flatMapMany(d -> Flux.from(d.select(DSL.field("tablename"))
                    .from("pg_tables")
                    .where(DSL.field("schemaname")
                        .eq(DSL.currentSchema()))))
                .map(r -> r.get(0, String.class))
                .collectList(),
            connection -> postgresExecutor.connectionFactory().closeConnection(connection));
    }

    private Mono<Void> handleTableCreationException(PostgresTable table, Throwable e) {
        if (e instanceof DataAccessException && e.getMessage().contains(String.format("\"%s\" already exists", table.getName()))) {
            return Mono.empty();
        }
        LOGGER.error("Error while creating table: {}", table.getName(), e);
        return Mono.error(e);
    }

    private Mono<Void> alterTableIfNeeded(PostgresTable table, Connection connection) {
        return executeAdditionalAlterQueries(table, connection)
            .then(enableRLSIfNeeded(table, connection));
    }

    private Mono<Void> executeAdditionalAlterQueries(PostgresTable table, Connection connection) {
        return Flux.fromIterable(table.getAdditionalAlterQueries())
            .concatMap(alterSQLQuery -> Mono.just(connection)
                .flatMapMany(pgConnection -> pgConnection.createStatement(alterSQLQuery)
                    .execute())
                .flatMap(Result::getRowsUpdated)
                .then()
                .onErrorResume(e -> {
                    if (e.getMessage().contains("already exists")) {
                        return Mono.empty();
                    }
                    LOGGER.error("Error while executing ALTER query for table {}", table.getName(), e);
                    return Mono.error(e);
                }))
            .then();
    }

    private Mono<Void> enableRLSIfNeeded(PostgresTable table, Connection connection) {
        if (rowLevelSecurityEnabled && table.supportsRowLevelSecurity()) {
            return alterTableEnableRLS(table, connection);
        }
        return Mono.empty();
    }

    private Mono<Void> alterTableEnableRLS(PostgresTable table, Connection connection) {
        return Mono.just(connection)
            .flatMapMany(pgConnection -> pgConnection.createStatement(rowLevelSecurityAlterStatement(table.getName()))
                .execute())
            .flatMap(Result::getRowsUpdated)
            .then();
    }

    private String rowLevelSecurityAlterStatement(String tableName) {
        String policyName = "domain_" + tableName + "_policy";
        return "set app.current_domain = ''; alter table " + tableName + " add column if not exists domain varchar(255) not null default current_setting('app.current_domain')::text ;" +
            "do $$ \n" +
            "begin \n" +
            "    if not  exists( select policyname from pg_policies where policyname = '" + policyName + "') then \n" +
            "        execute 'alter table " + tableName + " enable row level security; alter table " + tableName + " force row level security; create policy " + policyName + " on " + tableName + " using (domain = current_setting(''app.current_domain'')::text)';\n" +
            "    end if;\n" +
            "end $$;";
    }

    public Mono<Void> truncate() {
        return Mono.usingWhen(postgresExecutor.connectionFactory().getConnection(Optional.empty()),
            connection -> postgresExecutor.dslContext(connection)
                .flatMap(dsl -> Flux.fromIterable(module.tables())
                    .flatMap(table -> Mono.from(dsl.truncateTable(table.getName()))
                        .doOnSuccess(any -> LOGGER.info("Table {} truncated", table.getName()))
                        .doOnError(e -> LOGGER.error("Error while truncating table {}", table.getName(), e)))
                    .then()),
            connection -> postgresExecutor.connectionFactory().closeConnection(connection));
    }

    public Mono<Void> initializeTableIndexes() {
        return Mono.usingWhen(postgresExecutor.connectionFactory().getConnection(Optional.empty()),
            connection -> postgresExecutor.dslContext(connection)
                .flatMapMany(dsl -> listExistIndexes(dsl)
                    .flatMapMany(existIndexes -> Flux.fromIterable(module.tableIndexes())
                        .filter(index -> !existIndexes.contains(index.getName()))
                        .flatMap(index -> createTableIndex(index, dsl))))
                .then(),
            connection -> postgresExecutor.connectionFactory().closeConnection(connection));
    }

    private Mono<List<String>> listExistIndexes(DSLContext dslContext) {
        return Mono.just(dslContext)
            .flatMapMany(dsl -> Flux.from(dsl.select(DSL.field("indexname"))
                .from("pg_indexes")
                .where(DSL.field("schemaname")
                    .eq(DSL.currentSchema()))))
            .map(r -> r.get(0, String.class))
            .collectList();
    }

    private Mono<Void> createTableIndex(PostgresIndex index, DSLContext dsl) {
        return Mono.from(index.getCreateIndexStepFunction().apply(dsl))
            .doOnSuccess(any -> LOGGER.info("Index {} created", index.getName()))
            .onErrorResume(e -> handleIndexCreationException(index, e))
            .then();
    }

    private Mono<? extends Integer> handleIndexCreationException(PostgresIndex index, Throwable e) {
        if (e instanceof DataAccessException && e.getMessage().contains(String.format("\"%s\" already exists", index.getName()))) {
            return Mono.empty();
        }
        LOGGER.error("Error while creating index {}", index.getName(), e);
        return Mono.error(e);
    }

}
