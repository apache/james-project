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

package org.apache.james.backends.postgres.utils;

import java.time.Duration;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.lifecycle.api.Disposable;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.reactivestreams.Publisher;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PoolPostgresExecutor implements PostgresExecutor, Disposable {
    public static final String POOL_INJECT_NAME = "pool";
    static int INITIAL_SIZE = 10;
    static int MAX_SIZE = 50;
    static Duration MAX_IDLE_TIME = Duration.ofMillis(5000);
    private final ConnectionPool pool;

    @Inject
    public PoolPostgresExecutor(ConnectionFactory connectionFactory) {
        ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(connectionFactory)
            .maxIdleTime(MAX_IDLE_TIME)
            .initialSize(INITIAL_SIZE)
            .maxSize(MAX_SIZE)
            .build();
        pool = new ConnectionPool(configuration);
    }

    @Override
    public Mono<Connection> connection() {
        return pool.create();
    }

    @Override
    public Mono<Void> executeVoid(Function<DSLContext, Mono<?>> queryFunction) {
        return Mono.usingWhen(pool.create(),
            connection -> dslContext(connection)
                .flatMap(queryFunction)
                .retryWhen(RETRY_BACKOFF_SPEC)
                .then(),
            Connection::close);
    }

    @Override
    public Flux<Record> executeRows(Function<DSLContext, Flux<Record>> queryFunction) {
        return Flux.usingWhen(pool.create(),
            connection -> dslContext(connection)
                .flatMapMany(queryFunction)
                .retryWhen(RETRY_BACKOFF_SPEC),
            Connection::close);
    }

    @Override
    public Mono<Record> executeRow(Function<DSLContext, Publisher<Record>> queryFunction) {
        return Mono.usingWhen(pool.create(),
            connection -> dslContext(connection)
                .flatMap(queryFunction.andThen(Mono::from))
                .retryWhen(RETRY_BACKOFF_SPEC),
            Connection::close);
    }

    @Override
    public Mono<Integer> executeCount(Function<DSLContext, Mono<Record1<Integer>>> queryFunction) {
        return Mono.usingWhen(pool.create(),
            connection -> dslContext(connection)
                .flatMap(queryFunction)
                .retryWhen(RETRY_BACKOFF_SPEC)
                .map(Record1::value1),
            Connection::close);
    }

    @Override
    public Mono<Long> executeReturnAffectedRowsCount(Function<DSLContext, Mono<Integer>> queryFunction) {
        return Mono.usingWhen(pool.create(),
            connection -> dslContext(connection)
                .flatMap(queryFunction)
                .cast(Long.class)
                .retryWhen(RETRY_BACKOFF_SPEC),
            Connection::close);
    }

    private Mono<DSLContext> dslContext(Connection connection) {
        return Mono.fromCallable(() -> DSL.using(connection, PGSQL_DIALECT, SETTINGS));
    }

    @Override
    public void dispose() {
        pool.dispose();
    }
}
