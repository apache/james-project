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

package org.apache.james.backends.cassandra.init;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraTable.InitializationStatus;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CassandraTableManager {

    private final CqlSession session;
    private final CassandraModule module;

    @Inject
    public CassandraTableManager(CassandraModule module, CqlSession session) {
        this.session = session;
        this.module = module;
    }

    public InitializationStatus initializeTables(CassandraTypesProvider typesProvider) {
        KeyspaceMetadata keyspaceMetadata = session.getMetadata().getKeyspaces().get(session.getKeyspace().get());

        return Flux.fromIterable(module.moduleTables())
            .flatMap(table -> table.initialize(keyspaceMetadata, session, typesProvider), DEFAULT_CONCURRENCY)
            .reduce(InitializationStatus::reduce)
            .switchIfEmpty(Mono.just(InitializationStatus.ALREADY_DONE))
            .block();
    }

    public void clearTables(Predicate<CassandraTable> condition) {
        CassandraAsyncExecutor executor = new CassandraAsyncExecutor(session);
        Flux.fromIterable(module.moduleTables())
                .filter(condition)
                .publishOn(Schedulers.boundedElastic())
                .map(CassandraTable::getName)
                .flatMap(name -> truncate(executor, name), DEFAULT_CONCURRENCY)
                .then()
                .block();
    }

    private Mono<Void> truncate(CassandraAsyncExecutor executor, String name) {
        return executor.executeRows(
            selectFrom(name)
                .all()
                .limit(1)
                .build())
            .next()
            .flatMap(ignored -> executor.executeVoid(QueryBuilder.truncate(name).build()))
            .onErrorResume(e -> executor.executeVoid(QueryBuilder.truncate(name).build()));
    }
}
