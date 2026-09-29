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
import java.util.stream.IntStream;

import org.apache.james.backends.postgres.utils.PoolBackedPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresExecutorTest {
    private static final Duration JOOQ_REACTIVE_TIMEOUT = Duration.ofSeconds(1);
    private static final Field<Integer> ID = DSL.field("id", SQLDataType.INTEGER.notNull());
    private static final int PAGE_SIZE = 10;
    private static final int ROW_COUNT = 25;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    private PostgresExecutor postgresExecutor;

    @BeforeEach
    void beforeEach() {
        PostgresConfiguration configuration = PostgresConfiguration.builder()
            .username("james")
            .password("secret")
            .jooqReactiveTimeout(Optional.of(JOOQ_REACTIVE_TIMEOUT))
            .build();
        postgresExecutor = new PostgresExecutor.Factory(
            new PoolBackedPostgresConnectionFactory(RowLevelSecurity.DISABLED, postgresExtension.getConnectionFactory()),
            configuration,
            new RecordingMetricFactory())
            .create();

        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.createTableIfNotExists("paginated")
                .column(ID)
                .constraints(DSL.constraint().primaryKey(ID))))
            .block();
        Flux.fromStream(IntStream.range(0, ROW_COUNT).boxed())
            .concatMap(id -> postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(DSL.table("paginated"), ID)
                .values(id))))
            .then()
            .block();
    }

    @AfterEach
    void afterEach() {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.dropTableIfExists("paginated")))
            .block();
    }

    @Test
    void executeRowsPaginatedShouldReturnAllRows() {
        List<Integer> ids = postgresExecutor.executeRowsPaginated((dslContext, lastRecord) -> dslContext.select(ID)
                    .from(DSL.table("paginated"))
                    .where(lastRecord.map(record -> ID.greaterThan(record.get(ID))).orElseGet(DSL::noCondition))
                    .orderBy(ID), PAGE_SIZE)
            .map(record -> record.get(ID))
            .collectList()
            .block();

        assertThat(ids).containsExactlyElementsOf(IntStream.range(0, ROW_COUNT).boxed().collect(ImmutableList.toImmutableList()));
    }

    @Test
    void executeRowsPaginatedShouldNotTimeoutWhenConsumerIsSlowerThanTheReactiveTimeout() {
        // Consuming a page takes 2 seconds while the reactive timeout is 1 second: the next page must not wait
        // for downstream demand while holding its connection
        List<Integer> ids = postgresExecutor.executeRowsPaginated((dslContext, lastRecord) -> dslContext.select(ID)
                    .from(DSL.table("paginated"))
                    .where(lastRecord.map(record -> ID.greaterThan(record.get(ID))).orElseGet(DSL::noCondition))
                    .orderBy(ID), PAGE_SIZE)
            .map(record -> record.get(ID))
            .delayElements(Duration.ofMillis(200))
            .collectList()
            .block();

        assertThat(ids).containsExactlyElementsOf(IntStream.range(0, ROW_COUNT).boxed().collect(ImmutableList.toImmutableList()));
    }
}
