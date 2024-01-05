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

import org.apache.james.core.Domain;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;

import io.r2dbc.spi.Connection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DefaultPostgresExecutor implements PostgresExecutor {

    public static class Factory {

        private final JamesPostgresConnectionFactory jamesPostgresConnectionFactory;

        @Inject
        public Factory(JamesPostgresConnectionFactory jamesPostgresConnectionFactory) {
            this.jamesPostgresConnectionFactory = jamesPostgresConnectionFactory;
        }

        public DefaultPostgresExecutor create(Optional<Domain> domain) {
            return new DefaultPostgresExecutor(jamesPostgresConnectionFactory.getConnection(domain));
        }

        public DefaultPostgresExecutor create() {
            return create(Optional.empty());
        }
    }

    private final Mono<Connection> connection;

    private DefaultPostgresExecutor(Mono<Connection> connection) {
        this.connection = connection;
    }

    private Mono<DSLContext> dslContext() {
        return connection.map(con -> DSL.using(con, PGSQL_DIALECT, SETTINGS));
    }

    @Override
    public Mono<Void> executeVoid(Function<DSLContext, Mono<?>> queryFunction) {
        return dslContext()
            .flatMap(queryFunction)
            .retryWhen(RETRY_BACKOFF_SPEC)
            .then();
    }

    @Override
    public Flux<Record> executeRows(Function<DSLContext, Flux<Record>> queryFunction) {
        return dslContext()
            .flatMapMany(queryFunction)
            .retryWhen(RETRY_BACKOFF_SPEC);
    }

    @Override
    public Mono<Record> executeRow(Function<DSLContext, Publisher<Record>> queryFunction) {
        return dslContext()
            .flatMap(queryFunction.andThen(Mono::from))
            .retryWhen(RETRY_BACKOFF_SPEC);
    }

    @Override
    public Mono<Integer> executeCount(Function<DSLContext, Mono<Record1<Integer>>> queryFunction) {
        return dslContext()
            .flatMap(queryFunction)
            .retryWhen(RETRY_BACKOFF_SPEC)
            .map(Record1::value1);
    }

    @Override
    public Mono<Long> executeReturnAffectedRowsCount(Function<DSLContext, Mono<Integer>> queryFunction) {
        return dslContext()
            .flatMap(queryFunction)
            .cast(Long.class)
            .retryWhen(RETRY_BACKOFF_SPEC);
    }

    @Override
    public Mono<Connection> connection() {
        return connection;
    }

    @VisibleForTesting
    public Mono<Void> dispose() {
        return connection.flatMap(con -> Mono.from(con.close()));
    }

}
