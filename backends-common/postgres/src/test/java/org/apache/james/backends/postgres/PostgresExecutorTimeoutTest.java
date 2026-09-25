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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PoolBackedPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresExecutorTimeoutTest {
    private static final Duration JOOQ_REACTIVE_TIMEOUT = Duration.ofMillis(500);
    private static final Duration SLOWER_THAN_TIMEOUT = JOOQ_REACTIVE_TIMEOUT.multipliedBy(2);
    private static final Duration LONGER_THAN_TIMEOUT_IN_SECONDS = Duration.ofSeconds(60);
    private static final int SINGLE_CONNECTION_POOL = 1;
    private static final int ROW_COUNT = 3;
    private static final int ONE_ROW_AT_A_TIME = 1;
    private static final Table<Record> TABLE = DSL.table("timeout_test");
    private static final Field<Integer> ID = DSL.field("id", SQLDataType.INTEGER);

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    private static JamesPostgresConnectionFactory singleConnectionFactory;
    private static PostgresExecutor postgresExecutor;

    @BeforeAll
    static void beforeAll() {
        PostgresConfiguration extensionConfiguration = postgresExtension.getPostgresConfiguration();
        PostgresConfiguration shortTimeoutConfiguration = PostgresConfiguration.builder()
            .databaseName(extensionConfiguration.getDatabaseName())
            .databaseSchema(extensionConfiguration.getDatabaseSchema())
            .host(extensionConfiguration.getHost())
            .port(extensionConfiguration.getPort())
            .username(extensionConfiguration.getDefaultCredential().getUsername())
            .password(extensionConfiguration.getDefaultCredential().getPassword())
            .byPassRLSUser(extensionConfiguration.getByPassRLSCredential().getUsername())
            .byPassRLSPassword(extensionConfiguration.getByPassRLSCredential().getPassword())
            .rowLevelSecurityEnabled(false)
            .jooqReactiveTimeout(Optional.of(JOOQ_REACTIVE_TIMEOUT))
            .build();
        singleConnectionFactory = new PoolBackedPostgresConnectionFactory(RowLevelSecurity.DISABLED,
            SINGLE_CONNECTION_POOL, SINGLE_CONNECTION_POOL, postgresExtension.getConnectionFactory());
        postgresExecutor = new PostgresExecutor.Factory(singleConnectionFactory, shortTimeoutConfiguration, new RecordingMetricFactory())
            .create();
    }

    @AfterAll
    static void afterAll() {
        singleConnectionFactory.close().block();
    }

    @BeforeEach
    void beforeEach() {
        PostgresExecutor setUpExecutor = postgresExtension.getDefaultPostgresExecutor();
        setUpExecutor.executeVoid(dslContext -> Mono.from(dslContext.createTableIfNotExists(TABLE)
                .column(ID)))
            .block();
        Flux.range(1, ROW_COUNT)
            .concatMap(id -> setUpExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE, ID).values(id))))
            .blockLast();
    }

    @AfterEach
    void afterEach() {
        postgresExtension.getDefaultPostgresExecutor()
            .executeVoid(dslContext -> Mono.from(dslContext.dropTableIfExists(TABLE)))
            .block();
    }

    @Test
    void executeRowsShouldNotTimeoutWhenTheConsumerIsSlowerThanTheJooqReactiveTimeout() {
        List<Integer> ids = postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(ID).from(TABLE).orderBy(ID)))
            .concatMap(record -> Mono.delay(SLOWER_THAN_TIMEOUT).thenReturn(record.get(ID)), ONE_ROW_AT_A_TIME)
            .collectList()
            .block();

        assertThat(ids).containsExactly(1, 2, 3);
    }

    @Test
    void executeRowsShouldTimeoutWhenTheDatabaseDoesNotAnswerWhileRowsAreAwaited() {
        assertThatThrownBy(() -> sleepOnTheDatabaseSide().collectList().block())
            .hasCauseInstanceOf(TimeoutException.class);
    }

    private Flux<Record> sleepOnTheDatabaseSide() {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(DSL.field("pg_sleep(" + LONGER_THAN_TIMEOUT_IN_SECONDS.toSeconds() + ")"))));
    }
}
