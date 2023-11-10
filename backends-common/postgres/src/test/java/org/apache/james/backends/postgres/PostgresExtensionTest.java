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

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresExtensionTest {
    static PostgresTable TABLE_1 = PostgresTable.name("table1")
        .createTableStep((dslContext, tableName) -> dslContext.createTable(tableName)
            .column("column1", SQLDataType.UUID.notNull())
            .column("column2", SQLDataType.INTEGER)
            .column("column3", SQLDataType.VARCHAR(255).notNull()))
        .disableRowLevelSecurity();

    static PostgresIndex INDEX_1 = PostgresIndex.name("index1")
        .createIndexStep((dslContext, indexName) -> dslContext.createIndex(indexName)
            .on(DSL.table("table1"), DSL.field("column1").asc()));

    static PostgresTable TABLE_2 = PostgresTable.name("table2")
        .createTableStep((dslContext, tableName) -> dslContext.createTable(tableName)
            .column("column1", SQLDataType.INTEGER))
        .disableRowLevelSecurity();

    static PostgresIndex INDEX_2 = PostgresIndex.name("index2")
        .createIndexStep((dslContext, indexName) -> dslContext.createIndex(indexName)
            .on(DSL.table("table2"), DSL.field("column1").desc()));

    static PostgresModule POSTGRES_MODULE = PostgresModule.builder()
        .addTable(TABLE_1, TABLE_2)
        .addIndex(INDEX_1, INDEX_2)
        .build();

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(POSTGRES_MODULE);

    @Test
    void postgresExtensionShouldProvisionTablesAndIndexes() {
        assertThat(getColumnNameAndDataType("table1"))
            .containsExactlyInAnyOrder(
                Pair.of("column1", "uuid"),
                Pair.of("column2", "integer"),
                Pair.of("column3", "character varying"));

        assertThat(getColumnNameAndDataType("table2"))
            .containsExactlyInAnyOrder(Pair.of("column1", "integer"));

        assertThat(listIndexToTableMappings())
            .contains(
                Pair.of("index1", "table1"),
                Pair.of("index2", "table2"));
    }

    private List<Pair<String, String>> getColumnNameAndDataType(String tableName) {
        return postgresExtension.getConnection()
                .flatMapMany(connection -> Flux.from(Mono.from(connection.createStatement("SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_name = $1;")
                        .bind("$1", tableName)
                        .execute())
                    .flatMapMany(result -> result.map((row, rowMetadata) ->
                        Pair.of(row.get("column_name", String.class), row.get("data_type", String.class))))))
            .collectList()
            .block();
    }

    private List<Pair<String, String>> listIndexToTableMappings() {
        return postgresExtension.getConnection()
            .flatMapMany(connection -> Mono.from(connection.createStatement("SELECT indexname, tablename FROM pg_indexes;")
                    .execute())
                .flatMapMany(result ->
                    result.map((row, rowMetadata) ->
                        Pair.of(row.get("indexname", String.class), row.get("tablename", String.class)))))
            .collectList()
            .block();
    }
}
