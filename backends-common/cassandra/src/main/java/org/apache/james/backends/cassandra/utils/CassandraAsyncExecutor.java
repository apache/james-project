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

package org.apache.james.backends.cassandra.utils;

import java.util.Optional;

import javax.inject.Inject;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;

import net.javacrumbs.futureconverter.java8guava.FutureConverter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CassandraAsyncExecutor {

    private final Session session;

    @Inject
    public CassandraAsyncExecutor(Session session) {
        this.session = session;
    }

    public Mono<ResultSet> execute(Statement statement) {
        return Mono.defer(() -> Mono.fromFuture(FutureConverter
                .toCompletableFuture(session.executeAsync(statement)))
                .publishOn(Schedulers.elastic()));
    }

    public Mono<ResultSet> execute(String statement) {
        return Mono.defer(() -> Mono.fromFuture(FutureConverter
                .toCompletableFuture(session.executeAsync(statement)))
                .publishOn(Schedulers.elastic()));
    }

    public Mono<Boolean> executeReturnApplied(Statement statement) {
        return execute(statement)
                .map(row -> row.wasApplied());
    }

    public Mono<Void> executeVoid(Statement statement) {
        return execute(statement)
                .then();
    }

    public Mono<Row> executeSingleRow(Statement statement) {
        return executeSingleRowOptional(statement)
                .handle((t, sink) -> t.ifPresent(sink::next));
    }

    public Flux<Row> executeRows(Statement statement) {
        return execute(statement)
            .flatMapMany(Flux::fromIterable);
    }

    public Mono<Optional<Row>> executeSingleRowOptional(Statement statement) {
        return execute(statement)
            .map(resultSet -> Optional.ofNullable(resultSet.one()));
    }

    public Mono<Boolean> executeReturnExists(Statement statement) {
        return executeSingleRow(statement)
                .hasElement();
    }
}
