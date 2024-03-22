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

import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.field;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.jooq.DSLContext;
import org.jooq.DeleteResultStep;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SQLDialect;
import org.jooq.SelectConditionStep;
import org.jooq.conf.Settings;
import org.jooq.conf.StatementType;
import org.jooq.impl.DSL;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.R2dbcBadGrammarException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public class PostgresExecutor {

    public static final String DEFAULT_INJECT = "default";
    public static final String NON_RLS_INJECT = "non_rls";
    public static final int MAX_RETRY_ATTEMPTS = 5;
    public static final Duration MIN_BACKOFF = Duration.ofMillis(1);

    public static class Factory {

        private final JamesPostgresConnectionFactory jamesPostgresConnectionFactory;

        @Inject
        public Factory(JamesPostgresConnectionFactory jamesPostgresConnectionFactory) {
            this.jamesPostgresConnectionFactory = jamesPostgresConnectionFactory;
        }

        public PostgresExecutor create(Optional<Domain> domain) {
            return new PostgresExecutor(jamesPostgresConnectionFactory.getConnection(domain));
        }

        public PostgresExecutor create() {
            return create(Optional.empty());
        }
    }

    private static final SQLDialect PGSQL_DIALECT = SQLDialect.POSTGRES;
    private static final Settings SETTINGS = new Settings()
        .withRenderFormatted(true)
        .withStatementType(StatementType.PREPARED_STATEMENT);
    private final Mono<Connection> connection;

    private PostgresExecutor(Mono<Connection> connection) {
        this.connection = connection;
    }

    public Mono<DSLContext> dslContext() {
        return connection.map(con -> DSL.using(con, PGSQL_DIALECT, SETTINGS));
    }

    public Mono<Void> executeVoid(Function<DSLContext, Mono<?>> queryFunction) {
        return dslContext()
            .flatMap(queryFunction)
            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, MIN_BACKOFF)
                .filter(preparedStatementConflictException()))
            .then();
    }

    public Flux<Record> executeRows(Function<DSLContext, Flux<Record>> queryFunction) {
        return dslContext()
            .flatMapMany(queryFunction)
            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, MIN_BACKOFF)
                .filter(preparedStatementConflictException()));
    }

    public Flux<Record> executeDeleteAndReturnList(Function<DSLContext, DeleteResultStep<Record>> queryFunction) {
        return dslContext()
            .flatMapMany(queryFunction)
            .collectList()
            .flatMapIterable(list -> list) // The convert Flux -> Mono<List> -> Flux to avoid a hanging issue. See: https://github.com/jOOQ/jOOQ/issues/16055
            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, MIN_BACKOFF)
                .filter(preparedStatementConflictException()));
    }

    public Mono<Record> executeRow(Function<DSLContext, Publisher<Record>> queryFunction) {
        return dslContext()
            .flatMap(queryFunction.andThen(Mono::from))
            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, MIN_BACKOFF)
                .filter(preparedStatementConflictException()));
    }

    public Mono<Optional<Record>> executeSingleRowOptional(Function<DSLContext, Publisher<Record>> queryFunction) {
        return executeRow(queryFunction)
            .map(Optional::ofNullable)
            .switchIfEmpty(Mono.just(Optional.empty()));
    }

    public Mono<Integer> executeCount(Function<DSLContext, Mono<Record1<Integer>>> queryFunction) {
        return dslContext()
            .flatMap(queryFunction)
            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, MIN_BACKOFF)
                .filter(preparedStatementConflictException()))
            .map(Record1::value1);
    }

    public Mono<Boolean> executeExists(Function<DSLContext, SelectConditionStep<?>> queryFunction) {
        return executeRow(dslContext -> Mono.from(dslContext.select(field(exists(queryFunction.apply(dslContext))))))
            .map(record -> record.get(0, Boolean.class));
    }

    public Mono<Integer> executeReturnAffectedRowsCount(Function<DSLContext, Mono<Integer>> queryFunction) {
        return dslContext()
            .flatMap(queryFunction)
            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, MIN_BACKOFF)
                .filter(preparedStatementConflictException()));
    }

    public Mono<Connection> connection() {
        return connection;
    }

    @VisibleForTesting
    public Mono<Void> dispose() {
        return connection.flatMap(con -> Mono.from(con.close()));
    }

    private Predicate<Throwable> preparedStatementConflictException() {
        return throwable -> throwable.getCause() instanceof R2dbcBadGrammarException
            && throwable.getMessage().contains("prepared statement")
            && throwable.getMessage().contains("already exists");
    }
}
