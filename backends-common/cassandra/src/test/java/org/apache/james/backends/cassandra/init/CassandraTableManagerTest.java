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

import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

class CassandraTableManagerTest {
    private static final String TABLE_NAME = "tablename";

    public static final CassandraModule MODULE = CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraModule.table(TABLE_NAME)
                .comment("Testing table")
                .statement(statement -> types -> statement
                    .withPartitionKey("id", DataTypes.TIMEUUID)
                    .withClusteringColumn("clustering", DataTypes.BIGINT))
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
        cassandra.getConf().execute(SchemaBuilder.dropTable(TABLE_NAME).build());
        cassandra.getConf().execute(SchemaBuilder.dropTable(CassandraSchemaVersionTable.TABLE_NAME).build());

        assertThat(new CassandraTableManager(MODULE, cassandra.getConf()).initializeTables(new CassandraTypesProvider(cassandra.getConf())))
                .isEqualByComparingTo(CassandraTable.InitializationStatus.FULL);

        ensureTableExistence(TABLE_NAME);
    }

    @Test
    void initializeTableShouldCreateAllTheMissingTable() {
        cassandra.getConf().execute(SchemaBuilder.dropTable(TABLE_NAME).build());

        assertThat(new CassandraTableManager(MODULE, cassandra.getConf()).initializeTables(new CassandraTypesProvider(cassandra.getConf())))
                .isEqualByComparingTo(CassandraTable.InitializationStatus.PARTIAL);

        ensureTableExistence(TABLE_NAME);
    }

    @Test
    void initializeTableShouldNotPerformIfCalledASecondTime() {
        assertThat(new CassandraTableManager(MODULE, cassandra.getConf()).initializeTables(new CassandraTypesProvider(cassandra.getConf())))
                .isEqualByComparingTo(CassandraTable.InitializationStatus.ALREADY_DONE);
    }

    @Test
    void initializeTableShouldNotFailIfCalledASecondTime() {
        new CassandraTableManager(MODULE, cassandra.getConf()).initializeTables(new CassandraTypesProvider(cassandra.getConf()));

        ensureTableExistence(TABLE_NAME);
    }

    private void ensureTableExistence(String tableName) {
        assertThatCode(() -> cassandra.getConf().execute(QueryBuilder.selectFrom(tableName).all().limit(1).build()))
            .doesNotThrowAnyException();
    }
}