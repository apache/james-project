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

import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.postgres.PostgresConfiguration;
import org.apache.james.lifecycle.api.Disposable;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PoolPostgresExecutor implements PostgresExecutor, Disposable {
    public static final String POOL_INJECT_NAME = "pool";
    public static final Logger LOGGER = LoggerFactory.getLogger(PoolPostgresExecutor.class);

    private static ConnectionPoolConfiguration getConnectionPoolConfiguration(ConnectionFactory connectionFactory, PostgresConfiguration postgresConfiguration) {
        return postgresConfiguration.pool()
            .map(poolConfiguration -> Optional.of(ConnectionPoolConfiguration.builder(connectionFactory))
                .map(builder -> poolConfiguration.getInitialSize()
                    .map(builder::initialSize)
                    .orElse(builder))
                .map(builder -> poolConfiguration.getMaxSize()
                    .map(builder::maxSize)
                    .orElse(builder))
                .map(builder -> poolConfiguration.getMaxIdleTime()
                    .map(builder::maxIdleTime)
                    .orElse(builder))
                .map(builder -> poolConfiguration.getAcquireRetry()
                    .map(builder::acquireRetry)
                    .orElse(builder))
                .map(builder -> poolConfiguration.getMinIdle()
                    .map(builder::minIdle)
                    .orElse(builder))
                .map(builder -> poolConfiguration.getMaxLifeTime()
                    .map(builder::maxLifeTime)
                    .orElse(builder))
                .map(builder -> poolConfiguration.getMaxAcquireTime()
                    .map(builder::maxAcquireTime)
                    .orElse(builder))
                .map(builder -> poolConfiguration.getMaxCreateConnectionTime()
                    .map(builder::maxCreateConnectionTime)
                    .orElse(builder))
                .map(builder -> poolConfiguration.getMaxValidationTime()
                    .map(builder::maxValidationTime)
                    .orElse(builder))
                .map(builder -> poolConfiguration.getPoolName()
                    .map(builder::name)
                    .orElse(builder))
                .get().build())
            .orElseGet(() -> ConnectionPoolConfiguration.builder(connectionFactory).build());
    }

    private final ConnectionPool pool;

    @Inject
    public PoolPostgresExecutor(ConnectionFactory connectionFactory, PostgresConfiguration postgresConfiguration) {
        ConnectionPoolConfiguration configuration = getConnectionPoolConfiguration(connectionFactory, postgresConfiguration);
        LOGGER.info("Starting PoolPostgresExecutor with configuration: {}", postgresConfiguration.pool().map(Object::toString)
            .orElse("No pool configuration, using default one"));
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
                .flatMapMany(queryFunction),
            Connection::close);
    }

    @Override
    public Mono<Record> executeRow(Function<DSLContext, Publisher<Record>> queryFunction) {
        return Mono.usingWhen(pool.create(),
            connection -> dslContext(connection)
                .flatMap(queryFunction.andThen(Mono::from)),
            Connection::close);
    }

    @Override
    public Mono<Integer> executeCount(Function<DSLContext, Mono<Record1<Integer>>> queryFunction) {
        return Mono.usingWhen(pool.create(),
            connection -> dslContext(connection)
                .flatMap(queryFunction)
                .map(Record1::value1),
            Connection::close);
    }

    @Override
    public Mono<Long> executeReturnAffectedRowsCount(Function<DSLContext, Mono<Integer>> queryFunction) {
        return Mono.usingWhen(pool.create(),
            connection -> dslContext(connection)
                .flatMap(queryFunction)
                .cast(Long.class),
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
