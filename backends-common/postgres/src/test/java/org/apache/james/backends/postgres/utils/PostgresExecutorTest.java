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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.postgres.PostgresExtension;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresExecutorTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    private static PostgresExecutor postgresExecutor;

    @BeforeAll
    static void beforeAll() {
        postgresExecutor = postgresExtension.getDefaultPostgresExecutor();
    }

    @BeforeEach
    void beforeEach() {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.createTableIfNotExists("test_entity")
            .column("id", SQLDataType.INTEGER)
            .column("val", SQLDataType.VARCHAR(50))
            .constraints(DSL.constraint().primaryKey("id"))))
            .block();
    }

    @AfterEach
    void afterEach() {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.dropTableIfExists("test_entity")))
            .block();
    }

    @Test
    void executeTransactionShouldCommitWhenSuccessful() {
        postgresExecutor.executeTransaction(dslContext ->
            Mono.from(dslContext.insertInto(DSL.table("test_entity"), DSL.field("id"), DSL.field("val")).values(1, "A"))
                .then(Mono.from(dslContext.insertInto(DSL.table("test_entity"), DSL.field("id"), DSL.field("val")).values(2, "B")))
                .thenReturn("SUCCESS"))
            .block();

        Long count = postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectCount().from(DSL.table("test_entity"))))
            .map(record -> record.get(0, Long.class))
            .blockFirst();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void executeTransactionShouldRollbackOnError() {
        assertThatThrownBy(() -> postgresExecutor.executeTransaction(dslContext ->
            Mono.from(dslContext.insertInto(DSL.table("test_entity"), DSL.field("id"), DSL.field("val")).values(1, "A"))
                .then(Mono.from(dslContext.insertInto(DSL.table("test_entity"), DSL.field("id"), DSL.field("val")).values(1, "DUPLICATE_ID")))
                .thenReturn("SUCCESS"))
            .block())
            .isNotNull();

        Long count = postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectCount().from(DSL.table("test_entity"))))
            .map(record -> record.get(0, Long.class))
            .blockFirst();

        assertThat(count).isEqualTo(0L);
    }
}
