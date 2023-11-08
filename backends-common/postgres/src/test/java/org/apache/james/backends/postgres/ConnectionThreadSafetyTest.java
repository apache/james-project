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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.james.backends.postgres.utils.SimpleJamesPostgresConnectionFactory;
import org.apache.james.core.Domain;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ConnectionThreadSafetyTest {
    static final int NUMBER_OF_THREAD = 100;
    static final String CREATE_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS person (\n" +
        "\tid serial PRIMARY KEY,\n" +
        "\tname VARCHAR ( 50 ) UNIQUE NOT NULL\n" +
        ");";

    @RegisterExtension
    static PostgresExtension postgresExtension = new PostgresExtension();

    private static PostgresqlConnection postgresqlConnection;
    private static SimpleJamesPostgresConnectionFactory jamesPostgresConnectionFactory;

    @BeforeAll
    static void beforeAll() {
        jamesPostgresConnectionFactory = new SimpleJamesPostgresConnectionFactory(postgresExtension.getConnectionFactory());
        postgresqlConnection = (PostgresqlConnection) postgresExtension.getConnection().block();
    }

    @BeforeEach
    void beforeEach() {
        postgresqlConnection.createStatement(CREATE_TABLE_STATEMENT)
            .execute()
            .flatMap(PostgresqlResult::getRowsUpdated)
            .then()
            .block();
    }

    @AfterEach
    void afterEach() {
        postgresqlConnection.createStatement("DROP TABLE person")
            .execute()
            .flatMap(PostgresqlResult::getRowsUpdated)
            .then()
            .block();
    }

    @Test
    void connectionShouldWorkWellWhenItIsUsedByMultipleThreadsAndAllQueriesAreSelect() throws Exception {
        createData(NUMBER_OF_THREAD);

        Connection connection = jamesPostgresConnectionFactory.getConnection(Domain.of("james")).block();

        List<String> actual = new Vector<>();
        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> getData(connection, threadNumber)
                .doOnNext(s -> actual.add(s))
                .then())
            .threadCount(NUMBER_OF_THREAD)
            .operationCount(1)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        Set<String> expected = Stream.iterate(0, i -> i + 1).limit(NUMBER_OF_THREAD).map(i -> i + "|Peter" + i).collect(ImmutableSet.toImmutableSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void connectionShouldWorkWellWhenItIsUsedByMultipleThreadsAndAllQueriesAreInsert() throws Exception {
        Connection connection = jamesPostgresConnectionFactory.getConnection(Domain.of("james")).block();

        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> createData(connection, threadNumber))
            .threadCount(NUMBER_OF_THREAD)
            .operationCount(1)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        List<String> actual = getData(0, NUMBER_OF_THREAD);
        Set<String> expected = Stream.iterate(0, i -> i + 1).limit(NUMBER_OF_THREAD).map(i -> i + "|Peter" + i).collect(ImmutableSet.toImmutableSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void connectionShouldWorkWellWhenItIsUsedByMultipleThreadsAndInsertQueriesAreDuplicated() throws Exception {
        Connection connection = jamesPostgresConnectionFactory.getConnection(Domain.of("james")).block();

        AtomicInteger numberOfSuccess = new AtomicInteger(0);
        AtomicInteger numberOfFail = new AtomicInteger(0);
        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> createData(connection, threadNumber % 10)
                .then(Mono.fromCallable(() -> numberOfSuccess.incrementAndGet()))
                .then()
                .onErrorResume(throwable -> {
                    if (throwable.getMessage().contains("duplicate key value violates unique constraint")) {
                        numberOfFail.incrementAndGet();
                    }
                    return Mono.empty();
                }))
            .threadCount(100)
            .operationCount(1)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        List<String> actual = getData(0, 100);
        Set<String> expected = Stream.iterate(0, i -> i + 1).limit(10).map(i -> i + "|Peter" + i).collect(ImmutableSet.toImmutableSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(numberOfSuccess.get()).isEqualTo(10);
        assertThat(numberOfFail.get()).isEqualTo(90);
    }

    @Test
    void connectionShouldWorkWellWhenItIsUsedByMultipleThreadsAndQueriesIncludeBothSelectAndInsert() throws Exception {
        createData(50);

        Connection connection = jamesPostgresConnectionFactory.getConnection(Optional.empty()).block();

        List<String> actualSelect = new Vector<>();
        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> {
                if (threadNumber < 50) {
                    return getData(connection, threadNumber)
                        .doOnNext(s -> actualSelect.add(s))
                        .then();
                } else {
                    return createData(connection, threadNumber);
                }
            })
            .threadCount(NUMBER_OF_THREAD)
            .operationCount(1)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        List<String> actualInsert = getData(50, 100);

        Set<String> expectedSelect = Stream.iterate(0, i -> i + 1).limit(50).map(i -> i + "|Peter" + i).collect(ImmutableSet.toImmutableSet());
        Set<String> expectedInsert = Stream.iterate(50, i -> i + 1).limit(50).map(i -> i + "|Peter" + i).collect(ImmutableSet.toImmutableSet());

        assertThat(actualSelect).containsExactlyInAnyOrderElementsOf(expectedSelect);
        assertThat(actualInsert).containsExactlyInAnyOrderElementsOf(expectedInsert);
    }

    private Flux<String> getData(Connection connection, int threadNumber) {
        return Flux.from(connection.createStatement("SELECT id, name FROM PERSON WHERE id = $1")
                .bind("$1", threadNumber)
                .execute())
            .flatMap(result -> result.map((row, rowMetadata) -> row.get("id", Long.class) + "|" + row.get("name", String.class)));
    }

    @NotNull
    private Mono<Void> createData(Connection connection, int threadNumber) {
        return Flux.from(connection.createStatement("INSERT INTO person (id, name) VALUES ($1, $2)")
                .bind("$1", threadNumber)
                .bind("$2", "Peter" + threadNumber)
                .execute())
            .flatMap(Result::getRowsUpdated)
            .then();
    }

    private List<String> getData(int lowerBound, int upperBound) {
        return Flux.from(postgresqlConnection.createStatement("SELECT id, name FROM person WHERE id >= $1 AND id < $2")
                .bind("$1", lowerBound)
                .bind("$2", upperBound)
                .execute())
            .flatMap(result -> result.map((row, rowMetadata) -> row.get("id", Long.class) + "|" + row.get("name", String.class)))
            .collect(ImmutableList.toImmutableList()).block();
    }

    private void createData(int upperBound) {
        for (int i = 0; i < upperBound; i++) {
            postgresqlConnection.createStatement("INSERT INTO person (id, name) VALUES ($1, $2)")
                .bind("$1", i)
                .bind("$2", "Peter" + i)
                .execute().flatMap(PostgresqlResult::getRowsUpdated)
                .then()
                .block();
        }
    }
}
