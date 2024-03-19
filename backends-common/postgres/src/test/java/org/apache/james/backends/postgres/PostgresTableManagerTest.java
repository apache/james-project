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
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresTableManagerTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresModule.EMPTY_MODULE);

    Function<PostgresModule, PostgresTableManager> tableManagerFactory =
        module -> new PostgresTableManager(postgresExtension.getPostgresExecutor(), module, true);

    @Test
    void initializeTableShouldSuccessWhenModuleHasSingleTable() {
        String tableName = "tablename1";

        PostgresTable table = PostgresTable.name(tableName)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("colum1", SQLDataType.UUID.notNull())
                .column("colum2", SQLDataType.INTEGER)
                .column("colum3", SQLDataType.VARCHAR(255).notNull()))
            .disableRowLevelSecurity()
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
        String tableName1 = "tablename1";

        PostgresTable table1 = PostgresTable.name(tableName1)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("columA", SQLDataType.UUID.notNull())).disableRowLevelSecurity()
            .build();

        String tableName2 = "tablename2";
        PostgresTable table2 = PostgresTable.name(tableName2)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("columB", SQLDataType.INTEGER)).disableRowLevelSecurity()
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
        String tableName1 = "tablename1";

        PostgresTable table1 = PostgresTable.name(tableName1)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("columA", SQLDataType.UUID.notNull())).disableRowLevelSecurity()
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(PostgresModule.table(table1));

        testee.initializeTables()
            .block();

        assertThatCode(() -> testee.initializeTables().block())
            .doesNotThrowAnyException();
    }

    @Test
    void initializeTableShouldNotChangeTableStructureOfExistTable() {
        String tableName1 = "tablename1";
        PostgresTable table1 = PostgresTable.name(tableName1)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("columA", SQLDataType.UUID.notNull())).disableRowLevelSecurity()
            .build();

        tableManagerFactory.apply(PostgresModule.table(table1))
            .initializeTables()
            .block();

        PostgresTable table1Changed = PostgresTable.name(tableName1)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("columB", SQLDataType.INTEGER)).disableRowLevelSecurity()
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
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("colum1", SQLDataType.UUID.notNull())
                .column("colum2", SQLDataType.INTEGER)
                .column("colum3", SQLDataType.VARCHAR(255).notNull()))
            .disableRowLevelSecurity()
            .build();

        String indexName = "idx_test_1";
        PostgresIndex index = PostgresIndex.name(indexName)
            .createIndexStep((dsl, idn) -> dsl.createIndex(idn)
                .on(DSL.table(tableName), DSL.field("colum1").asc()));

        PostgresModule module = PostgresModule.builder()
            .addTable(table)
            .addIndex(index)
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(module);

        testee.initializeTables().block();

        testee.initializeTableIndexes().block();

        List<Pair<String, String>> listIndexes = listIndexToTableMappings();

        assertThat(listIndexes)
            .contains(Pair.of(indexName, tableName));
    }

    @Test
    void initializeIndexShouldSuccessWhenModuleHasMultiIndexes() {
        String tableName = "tb_test_1";

        PostgresTable table = PostgresTable.name(tableName)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("colum1", SQLDataType.UUID.notNull())
                .column("colum2", SQLDataType.INTEGER)
                .column("colum3", SQLDataType.VARCHAR(255).notNull()))
            .disableRowLevelSecurity()
            .build();

        String indexName1 = "idx_test_1";
        PostgresIndex index1 = PostgresIndex.name(indexName1)
            .createIndexStep((dsl, idn) -> dsl.createIndex(idn)
                .on(DSL.table(tableName), DSL.field("colum1").asc()));

        String indexName2 = "idx_test_2";
        PostgresIndex index2 = PostgresIndex.name(indexName2)
            .createIndexStep((dsl, idn) -> dsl.createIndex(idn)
                .on(DSL.table(tableName), DSL.field("colum2").desc()));

        PostgresModule module = PostgresModule.builder()
            .addTable(table)
            .addIndex(index1, index2)
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(module);

        testee.initializeTables().block();

        testee.initializeTableIndexes().block();

        List<Pair<String, String>> listIndexes = listIndexToTableMappings();

        assertThat(listIndexes)
            .contains(Pair.of(indexName1, tableName), Pair.of(indexName2, tableName));
    }

    @Test
    void initializeIndexShouldNotThrowWhenIndexExists() {
        String tableName = "tb_test_1";

        PostgresTable table = PostgresTable.name(tableName)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("colum1", SQLDataType.UUID.notNull())
                .column("colum2", SQLDataType.INTEGER)
                .column("colum3", SQLDataType.VARCHAR(255).notNull()))
            .disableRowLevelSecurity()
            .build();

        String indexName = "idx_test_1";
        PostgresIndex index = PostgresIndex.name(indexName)
            .createIndexStep((dsl, idn) -> dsl.createIndex(idn)
                .on(DSL.table(tableName), DSL.field("colum1").asc()));

        PostgresModule module = PostgresModule.builder()
            .addTable(table)
            .addIndex(index)
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(module);

        testee.initializeTables().block();

        testee.initializeTableIndexes().block();

        assertThatCode(() -> testee.initializeTableIndexes().block())
            .doesNotThrowAnyException();
    }

    @Test
    void truncateShouldEmptyTableData() {
        // Given table tbn1
        String tableName1 = "tbn1";
        PostgresTable table1 = PostgresTable.name(tableName1)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("column1", SQLDataType.INTEGER.notNull())).disableRowLevelSecurity()
            .build();

        PostgresTableManager testee = tableManagerFactory.apply(PostgresModule.table(table1));
        testee.initializeTables()
            .block();

        // insert data
        postgresExtension.getConnection()
            .flatMapMany(connection -> Flux.range(0, 10)
                .flatMap(i -> Mono.from(connection.createStatement("INSERT INTO " + tableName1 + " (column1) VALUES ($1);")
                    .bind("$1", i)
                    .execute())
                    .flatMap(result -> Mono.from(result.getRowsUpdated())))
                .last())
            .collectList()
            .block();

        Supplier<Long> getTotalRecordInDB = () -> postgresExtension.getConnection()
            .flatMapMany(connection -> Mono.from(connection.createStatement("select count(*) FROM " + tableName1)
                    .execute())
                .flatMapMany(result ->
                    result.map((row, rowMetadata) -> row.get("count", Long.class))))
            .last()
            .block();

        assertThat(getTotalRecordInDB.get()).isEqualTo(10L);

        // When truncate table
        testee.truncate().block();

        // Then table is empty
        assertThat(getTotalRecordInDB.get()).isEqualTo(0L);
    }

    @Test
    void createTableShouldCreateRlsColumnWhenEnableRLS() {
        String tableName = "tbn1";

        PostgresTable table = PostgresTable.name(tableName)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("clm1", SQLDataType.UUID.notNull())
                .column("clm2", SQLDataType.VARCHAR(255).notNull()))
            .supportsRowLevelSecurity()
            .build();

        PostgresModule module = PostgresModule.table(table);

        PostgresTableManager testee = tableManagerFactory.apply(module);

        testee.initializeTables()
            .block();

        assertThat(getColumnNameAndDataType(tableName))
            .containsExactlyInAnyOrder(
                Pair.of("clm1", "uuid"),
                Pair.of("clm2", "character varying"),
                Pair.of("domain", "character varying"));

        List<Pair<String, Boolean>> pgClassCheckResult = postgresExtension.getConnection()
            .flatMapMany(connection -> Mono.from(connection.createStatement("select relname, relrowsecurity " +
                    "from pg_class " +
                    "where oid = 'tbn1'::regclass;;")
                .execute())
                .flatMapMany(result ->
                    result.map((row, rowMetadata) ->
                        Pair.of(row.get("relname", String.class),
                            row.get("relrowsecurity", Boolean.class)))))
            .collectList()
            .block();

        assertThat(pgClassCheckResult)
            .containsExactlyInAnyOrder(
                Pair.of("tbn1", true));
    }

    @Test
    void createTableShouldNotCreateRlsColumnWhenDisableRLS() {
        String tableName = "tbn1";

        PostgresTable table = PostgresTable.name(tableName)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("clm1", SQLDataType.UUID.notNull())
                .column("clm2", SQLDataType.VARCHAR(255).notNull()))
            .supportsRowLevelSecurity()
            .build();

        PostgresModule module = PostgresModule.table(table);
        boolean disabledRLS = false;


        PostgresTableManager testee = new PostgresTableManager(postgresExtension.getPostgresExecutor(), module, disabledRLS);

        testee.initializeTables()
            .block();

        Pair<String, String> rlsColumn = Pair.of("domain", "character varying");
        assertThat(getColumnNameAndDataType(tableName))
            .doesNotContain(rlsColumn);
    }

    @Test
    void recreateRLSColumnWhenExistedShouldNotFail() {
        String tableName = "tablename1";

        PostgresTable rlsTable = PostgresTable.name(tableName)
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("colum1", SQLDataType.UUID.notNull()))
            .supportsRowLevelSecurity()
            .build();

        PostgresModule module = PostgresModule.table(rlsTable);

        PostgresTableManager testee = tableManagerFactory.apply(module);
        testee.initializeTables().block();

        assertThatCode(() -> testee.initializeTables().block())
            .doesNotThrowAnyException();
    }

    @Test
    void additionalAlterQueryToCreateConstraintShouldSucceed() {
        String constraintName = "exclude_constraint";
        PostgresTable table = PostgresTable.name("tbn1")
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("clm1", SQLDataType.UUID.notNull())
                .column("clm2", SQLDataType.VARCHAR(255).notNull()))
            .disableRowLevelSecurity()
            .addAdditionalAlterQueries("ALTER TABLE tbn1 ADD CONSTRAINT " + constraintName + " EXCLUDE (clm2 WITH =)")
            .build();
        PostgresModule module = PostgresModule.table(table);
        PostgresTableManager testee = new PostgresTableManager(postgresExtension.getPostgresExecutor(), module, false);

        testee.initializeTables().block();

        boolean constraintExists = postgresExtension.getConnection()
            .flatMapMany(connection -> connection.createStatement("SELECT EXISTS(SELECT 1 FROM pg_catalog.pg_constraint WHERE conname = $1) AS constraint_exists;")
                .bind("$1", constraintName)
                    .execute())
            .flatMap(result -> result.map((row, rowMetaData) -> row.get("constraint_exists", Boolean.class)))
            .single()
            .block();

        assertThat(constraintExists).isTrue();
    }

    @Test
    void additionalAlterQueryToReCreateConstraintShouldNotThrow() {
        String constraintName = "exclude_constraint";
        PostgresTable table = PostgresTable.name("tbn1")
            .createTableStep((dsl, tbn) -> dsl.createTable(tbn)
                .column("clm1", SQLDataType.UUID.notNull())
                .column("clm2", SQLDataType.VARCHAR(255).notNull()))
            .disableRowLevelSecurity()
            .addAdditionalAlterQueries("ALTER TABLE tbn1 ADD CONSTRAINT " + constraintName + " EXCLUDE (clm2 WITH =)")
            .build();
        PostgresModule module = PostgresModule.table(table);
        PostgresTableManager testee = new PostgresTableManager(postgresExtension.getPostgresExecutor(), module, false);

        testee.initializeTables().block();

        assertThatCode(() -> testee.initializeTables().block())
            .doesNotThrowAnyException();
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

    // return list<pair<indexName, tableName>>
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
