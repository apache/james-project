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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.spi.Connection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Testcontainers
public class PostgresTableManagerTest {

    @Container
    private static final GenericContainer<?> pgContainer = PostgresFixture.PG_CONTAINER.get();

    private PostgresqlConnectionFactory connectionFactory;

    @BeforeEach
    void beforeAll() {
        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(pgContainer.getHost())
            .port(pgContainer.getMappedPort(PostgresFixture.PORT))
            .username(PostgresFixture.Database.DB_USER)
            .password(PostgresFixture.Database.DB_PASSWORD)
            .database(PostgresFixture.Database.DB_NAME)
            .schema(PostgresFixture.Database.SCHEMA)
            .build());
    }

    @AfterEach
    void afterEach() {
        // clean data
        Flux.usingWhen(connectionFactory.create(),
                connection -> Mono.from(connection.createStatement("DROP SCHEMA " + PostgresFixture.Database.SCHEMA + " CASCADE").execute())
                    .then(Mono.from(connection.createStatement("CREATE SCHEMA " + PostgresFixture.Database.SCHEMA).execute()))
                    .flatMap(PostgresqlResult::getRowsUpdated),
                Connection::close)
            .collectList()
            .block();
    }

    Function<PostgresModule, PostgresTableManager> tableManagerFactory = module -> new PostgresTableManager(new PostgresExecutor(connectionFactory.create()
        .map(c -> c)), module);

    @Test
    void initializeTableShouldSuccessWhenModuleHasSingleTable() {
        String tableName = "tableName1";

        PostgresTable table = PostgresTable.name(tableName)
            .creteTableStep(dslContext -> dslContext.createTable(tableName)
                .column("colum1", SQLDataType.UUID.notNull())
                .column("colum2", SQLDataType.INTEGER)
                .column("colum3", SQLDataType.VARCHAR(255).notNull()))
            .build();
        PostgresModule module = PostgresModule.table(table);

        PostgresTableManager testee = tableManagerFactory.apply(module);

        testee.initializeTables()
            .block();

        assertThat(getColumnNameAndDataType(tableName))
            .containsExactlyInAnyOrder(
                Pair.of("colum1", "uuid"),
                Pair.of("colum2", "integer"),
                Pair.of("colum3", "character varying"));
    }

    @Test
    void initializeTableShouldSuccessWhenModuleHasMultiTables() {
        String tableName1 = "tableName1";

        PostgresTable table1 = PostgresTable.name(tableName1)
            .creteTableStep(dslContext -> dslContext.createTable(tableName1)
                .column("columA", SQLDataType.UUID.notNull()))
            .build();

        String tableName2 = "tableName2";
        PostgresTable table2 = PostgresTable.name(tableName2)
            .creteTableStep(dslContext -> dslContext.createTable(tableName2)
                .column("columB", SQLDataType.INTEGER))
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(PostgresModule.table(table1, table2));

        testee.initializeTables()
            .block();

        assertThat(getColumnNameAndDataType(tableName1))
            .containsExactlyInAnyOrder(
                Pair.of("columA", "uuid"));
        assertThat(getColumnNameAndDataType(tableName2))
            .containsExactlyInAnyOrder(
                Pair.of("columB", "integer"));
    }

    @Test
    void initializeTableShouldNotThrowWhenTableExists() {
        String tableName1 = "tableName1";

        PostgresTable table1 = PostgresTable.name(tableName1)
            .creteTableStep(dslContext -> dslContext.createTable(tableName1)
                .column("columA", SQLDataType.UUID.notNull()))
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(PostgresModule.table(table1));

        testee.initializeTables()
            .block();

        assertThatCode(() -> testee.initializeTables().block())
            .doesNotThrowAnyException();
    }

    @Test
    void initializeTableShouldNotChangeTableStructureOfExistTable() {
        String tableName1 = "tableName1";
        PostgresTable table1 = PostgresTable.name(tableName1)
            .creteTableStep(dslContext -> dslContext.createTable(tableName1)
                .column("columA", SQLDataType.UUID.notNull()))
            .build();

        tableManagerFactory.apply(PostgresModule.table(table1))
            .initializeTables()
            .block();

        PostgresTable table1Changed = PostgresTable.name(tableName1)
            .creteTableStep(dslContext -> dslContext.createTable(tableName1)
                .column("columB", SQLDataType.INTEGER))
            .build();

        tableManagerFactory.apply(PostgresModule.table(table1Changed))
            .initializeTables()
            .block();

        assertThat(getColumnNameAndDataType(tableName1))
            .containsExactlyInAnyOrder(
                Pair.of("columA", "uuid"));
    }

    @Test
    void initializeIndexShouldSuccessWhenModuleHasSingleIndex() {
        String tableName = "tb_test_1";

        PostgresTable table = PostgresTable.name(tableName)
            .creteTableStep(dslContext -> dslContext.createTable(tableName)
                .column("colum1", SQLDataType.UUID.notNull())
                .column("colum2", SQLDataType.INTEGER)
                .column("colum3", SQLDataType.VARCHAR(255).notNull())
            )
            .build();

        String indexName = "idx_test_1";
        PostgresIndex index = PostgresIndex.name(indexName)
            .createIndexStep(dsl -> dsl.createIndex(indexName)
                .on(DSL.table(tableName), DSL.field("colum1").asc()));

        PostgresModule module = PostgresModule.builder()
            .table(table)
            .index(index)
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(module);

        testee.initializeTables().block();

        testee.initializeTableIndexes().block();

        List<Pair<String, String>> listIndexes = listIndexes();

        assertThat(listIndexes)
            .contains(Pair.of(indexName, tableName));
    }

    @Test
    void initializeIndexShouldSuccessWhenModuleHasMultiIndexes() {
        String tableName = "tb_test_1";

        PostgresTable table = PostgresTable.name(tableName)
            .creteTableStep(dslContext -> dslContext.createTable(tableName)
                .column("colum1", SQLDataType.UUID.notNull())
                .column("colum2", SQLDataType.INTEGER)
                .column("colum3", SQLDataType.VARCHAR(255).notNull())
            )
            .build();

        String indexName1 = "idx_test_1";
        PostgresIndex index1 = PostgresIndex.name(indexName1)
            .createIndexStep(dsl -> dsl.createIndex(indexName1)
                .on(DSL.table(tableName), DSL.field("colum1").asc()));

        String indexName2 = "idx_test_2";
        PostgresIndex index2 = PostgresIndex.name(indexName2)
            .createIndexStep(dsl -> dsl.createIndex(indexName2)
                .on(DSL.table(tableName), DSL.field("colum2").desc()));

        PostgresModule module = PostgresModule.builder()
            .table(table)
            .index(index1, index2)
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(module);

        testee.initializeTables().block();

        testee.initializeTableIndexes().block();

        List<Pair<String, String>> listIndexes = listIndexes();

        assertThat(listIndexes)
            .contains(Pair.of(indexName1, tableName), Pair.of(indexName2, tableName));
    }

    @Test
    void initializeIndexShouldNotThrowWhenIndexExists() {
        String tableName = "tb_test_1";

        PostgresTable table = PostgresTable.name(tableName)
            .creteTableStep(dslContext -> dslContext.createTable(tableName)
                .column("colum1", SQLDataType.UUID.notNull())
                .column("colum2", SQLDataType.INTEGER)
                .column("colum3", SQLDataType.VARCHAR(255).notNull())
            )
            .build();

        String indexName = "idx_test_1";
        PostgresIndex index = PostgresIndex.name(indexName)
            .createIndexStep(dsl -> dsl.createIndex(indexName)
                .on(DSL.table(tableName), DSL.field("colum1").asc()));

        PostgresModule module = PostgresModule.builder()
            .table(table)
            .index(index)
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(module);

        testee.initializeTables().block();

        testee.initializeTableIndexes().block();

        assertThatCode(() -> testee.initializeTableIndexes().block())
            .doesNotThrowAnyException();
    }


    private List<Pair<String, String>> getColumnNameAndDataType(String tableName) {
        return Flux.usingWhen(connectionFactory.create(),
                connection -> Mono.from(connection.createStatement("SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_name = $1;")
                        .bind("$1", tableName)
                        .execute())
                    .flatMapMany(result ->
                        result.map((row, rowMetadata) ->
                            Pair.of(row.get("column_name", String.class),
                                row.get("data_type", String.class)))),
                Connection::close)
            .collectList()
            .block();
    }

    // return list<pair<indexName, tableName>>
    private List<Pair<String, String>> listIndexes() {
        return Flux.usingWhen(connectionFactory.create(),
                connection -> Mono.from(connection.createStatement("SELECT indexname, tablename FROM pg_indexes;")
                        .execute())
                    .flatMapMany(result ->
                        result.map((row, rowMetadata) ->
                            Pair.of(row.get("indexname", String.class), row.get("tablename", String.class)))),
                Connection::close)
            .collectList()
            .block();
    }

}
