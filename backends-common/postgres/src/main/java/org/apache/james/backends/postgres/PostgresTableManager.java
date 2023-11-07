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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.lifecycle.api.Startable;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresTableManager implements Startable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresTableManager.class);
    private final PostgresExecutor postgresExecutor;
    private final PostgresModule module;
    private final boolean rlsEnabled;

    @Inject
    public PostgresTableManager(JamesPostgresConnectionFactory postgresConnectionFactory,
                                PostgresModule module,
                                PostgresConfiguration postgresConfiguration) {
        this.postgresExecutor = new PostgresExecutor(postgresConnectionFactory.getConnection(Optional.empty()));
        this.module = module;
        this.rlsEnabled = postgresConfiguration.rlsEnabled();
    }

    @VisibleForTesting
    public PostgresTableManager(PostgresExecutor postgresExecutor, PostgresModule module, boolean rlsEnabled) {
        this.postgresExecutor = postgresExecutor;
        this.module = module;
        this.rlsEnabled = rlsEnabled;
    }

    public Mono<Void> initializeTables() {
        return postgresExecutor.dslContext()
            .flatMap(dsl -> Flux.fromIterable(module.tables())
                .flatMap(table -> Mono.from(table.getCreateTableStepFunction().apply(dsl))
                    .then(alterTableEnableRLSIfNeed(table))
                    .doOnSuccess(any -> LOGGER.info("Table {} created", table.getName()))
                    .onErrorResume(DataAccessException.class, exception -> {
                        if (exception.getMessage().contains(String.format("\"%s\" already exists", table.getName()))) {
                            return Mono.empty();
                        }
                        return Mono.error(exception);
                    })
                    .doOnError(e -> LOGGER.error("Error while creating table {}", table.getName(), e)))
                .then());
    }

    private Mono<Void> alterTableEnableRLSIfNeed(PostgresTable table) {
        if (rlsEnabled && table.isEnableRowLevelSecurity()) {
            return alterTableEnableRLS(table);
        }
        return Mono.empty();
    }

    public Mono<Void> alterTableEnableRLS(PostgresTable table) {
        return postgresExecutor.connection()
            .flatMapMany(con -> con.createStatement(getAlterRLSStatement(table.getName())).execute())
            .flatMap(Result::getRowsUpdated)
            .then();
    }

    private String getAlterRLSStatement(String tableName) {
        return "SET app.current_domain = ''; ALTER TABLE " + tableName + " ADD DOMAIN varchar(255) not null DEFAULT current_setting('app.current_domain')::text;" +
            "ALTER TABLE " + tableName + " ENABLE ROW LEVEL SECURITY; " +
            "CREATE POLICY DOMAIN_" + tableName + "_POLICY ON " + tableName + " USING (DOMAIN = current_setting('app.current_domain')::text);";
    }

    public Mono<Void> truncate() {
        return postgresExecutor.dslContext()
            .flatMap(dsl -> Flux.fromIterable(module.tables())
                .flatMap(table -> Mono.from(dsl.truncateTable(table.getName()))
                    .doOnSuccess(any -> LOGGER.info("Table {} truncated", table.getName()))
                    .doOnError(e -> LOGGER.error("Error while truncating table {}", table.getName(), e)))
                .then());
    }

    public Mono<Void> initializeTableIndexes() {
        return postgresExecutor.dslContext()
            .flatMap(dsl -> Flux.fromIterable(module.tableIndexes())
                .concatMap(index -> Mono.from(index.getCreateIndexStepFunction().apply(dsl))
                    .doOnSuccess(any -> LOGGER.info("Index {} created", index.getName()))
                    .onErrorResume(DataAccessException.class, exception -> {
                        if (exception.getMessage().contains(String.format("\"%s\" already exists", index.getName()))) {
                            return Mono.empty();
                        }
                        return Mono.error(exception);
                    })
                    .doOnError(e -> LOGGER.error("Error while creating index {}", index.getName(), e)))
                .then());
    }
}
