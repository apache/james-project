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
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.backends.postgres.utils.DefaultPostgresExecutor;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresExecutorThreadSafetyTest {
    static final int NUMBER_OF_THREAD = 100;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    private static DefaultPostgresExecutor postgresExecutor;

    @BeforeAll
    static void beforeAll() {
        postgresExecutor = postgresExtension.getPostgresExecutor();
    }

    @BeforeEach
    void beforeEach() {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.createTableIfNotExists("person")
            .column("id", SQLDataType.INTEGER.identity(true))
            .column("name", SQLDataType.VARCHAR(50).nullable(false))
            .constraints(DSL.constraint().primaryKey("id"))
            .unique("name")))
            .block();
    }

    @AfterEach
    void afterEach() {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.dropTableIfExists("person")))
            .block();
    }

    @Test
    void postgresExecutorShouldWorkWellWhenItIsUsedByMultipleThreadsAndAllQueriesAreSelect() throws Exception {
        provisionData(NUMBER_OF_THREAD);

        List<String> actual = new Vector<>();
        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> getData(threadNumber)
                .doOnNext(actual::add)
                .then())
            .threadCount(NUMBER_OF_THREAD)
            .operationCount(1)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        Set<String> expected = Stream.iterate(0, i -> i + 1).limit(NUMBER_OF_THREAD).map(i -> i + "|Peter" + i).collect(ImmutableSet.toImmutableSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void postgresExecutorShouldWorkWellWhenItIsUsedByMultipleThreadsAndAllQueriesAreInsert() throws Exception {
        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> createData(threadNumber))
            .threadCount(NUMBER_OF_THREAD)
            .operationCount(1)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        List<String> actual = getData(0, NUMBER_OF_THREAD);
        Set<String> expected = Stream.iterate(0, i -> i + 1).limit(NUMBER_OF_THREAD).map(i -> i + "|Peter" + i).collect(ImmutableSet.toImmutableSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void postgresExecutorShouldWorkWellWhenItIsUsedByMultipleThreadsAndInsertQueriesAreDuplicated() throws Exception {
        AtomicInteger numberOfSuccess = new AtomicInteger(0);
        AtomicInteger numberOfFail = new AtomicInteger(0);
        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> createData(threadNumber % 10)
                .then(Mono.fromCallable(numberOfSuccess::incrementAndGet))
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
    void postgresExecutorShouldWorkWellWhenItIsUsedByMultipleThreadsAndQueriesIncludeBothSelectAndInsert() throws Exception {
        provisionData(50);

        List<String> actualSelect = new Vector<>();
        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> {
                if (threadNumber < 50) {
                    return getData(threadNumber)
                        .doOnNext(actualSelect::add)
                        .then();
                } else {
                    return createData(threadNumber);
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

    public Flux<String> getData(int threadNumber) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext
                .select(DSL.field("id"), DSL.field("name"))
                .from(DSL.table("person"))
                .where(DSL.field("id").eq(threadNumber))))
            .map(recordToString());
    }

    public Mono<Void> createData(int threadNumber) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext
            .insertInto(DSL.table("person"), DSL.field("id"), DSL.field("name"))
            .values(threadNumber, "Peter" + threadNumber)));
    }

    private List<String> getData(int lowerBound, int upperBound) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext
            .select(DSL.field("id"), DSL.field("name"))
            .from(DSL.table("person"))
            .where(DSL.field("id").greaterOrEqual(lowerBound).and(DSL.field("id").lessThan(upperBound)))))
            .map(recordToString())
            .collectList()
            .block();
    }

    private void provisionData(int upperBound) {
        Flux.range(0, upperBound)
            .flatMap(i -> insertPerson(i, "Peter" + i))
            .then()
            .block();
    }

    private Mono<Void> insertPerson(int id, String name) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(DSL.table("person"), DSL.field("id"), DSL.field("name"))
            .values(id, name)));
    }

    private Function<Record, String> recordToString() {
        return record -> record.get(DSL.field("id", Long.class)) + "|" + record.get(DSL.field("name", String.class));
    }
}
