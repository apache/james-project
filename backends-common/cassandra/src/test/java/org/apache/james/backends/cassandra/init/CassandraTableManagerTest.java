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

package org.apache.james.backends.cassandra.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;

class CassandraTableManagerTest {
    private static final String TABLE_NAME = "tablename";

    public static final CassandraModule MODULE = CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraModule.table(TABLE_NAME)
                .comment("Testing table")
                .statement(statement -> statement
                        .addPartitionKey("id", DataType.timeuuid())
                        .addClusteringColumn("clustering", DataType.bigint()))
                .build());

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private CassandraCluster cassandra;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        this.cassandra = cassandra;
    }

    @Test
    void describeShouldNotReturnNullNorFailWhenTableIsDefined() {
        ensureTableExistence(TABLE_NAME);
    }

    @Test
    void initializeTableShouldCreateAllTheTables() {
        cassandra.getConf().execute(SchemaBuilder.dropTable(TABLE_NAME));
        cassandra.getConf().execute(SchemaBuilder.dropTable(CassandraSchemaVersionTable.TABLE_NAME));

        assertThat(new CassandraTableManager(MODULE, cassandra.getConf()).initializeTables())
                .isEqualByComparingTo(CassandraTable.InitializationStatus.FULL);

        ensureTableExistence(TABLE_NAME);
    }

    @Test
    void initializeTableShouldCreateAllTheMissingTable() {
        cassandra.getConf().execute(SchemaBuilder.dropTable(TABLE_NAME));

        assertThat(new CassandraTableManager(MODULE, cassandra.getConf()).initializeTables())
                .isEqualByComparingTo(CassandraTable.InitializationStatus.PARTIAL);

        ensureTableExistence(TABLE_NAME);
    }

    @Test
    void initializeTableShouldNotPerformIfCalledASecondTime() {
        assertThat(new CassandraTableManager(MODULE, cassandra.getConf()).initializeTables())
                .isEqualByComparingTo(CassandraTable.InitializationStatus.ALREADY_DONE);
    }

    @Test
    void initializeTableShouldNotFailIfCalledASecondTime() {
        new CassandraTableManager(MODULE, cassandra.getConf()).initializeTables();

        ensureTableExistence(TABLE_NAME);
    }

    private void ensureTableExistence(String tableName) {
        assertThatCode(() -> cassandra.getConf().execute(QueryBuilder.select().from(tableName).limit(1)))
            .doesNotThrowAnyException();
    }
}