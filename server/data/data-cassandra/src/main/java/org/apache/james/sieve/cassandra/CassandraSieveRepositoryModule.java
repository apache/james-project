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

package org.apache.james.sieve.cassandra;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.cboolean;
import static com.datastax.driver.core.DataType.counter;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timestamp;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.sieve.cassandra.tables.CassandraSieveActiveTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveClusterQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveSpaceTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveTable;

public interface CassandraSieveRepositoryModule {

    CassandraModule MODULE = CassandraModule.builder()
        .table(CassandraSieveTable.TABLE_NAME)
        .comment("Holds SIEVE scripts.")
        .options(options -> options)
        .statement(statement -> statement
            .addPartitionKey(CassandraSieveTable.USER_NAME, text())
            .addClusteringColumn(CassandraSieveTable.SCRIPT_NAME, text())
            .addColumn(CassandraSieveTable.SCRIPT_CONTENT, text())
            .addColumn(CassandraSieveTable.IS_ACTIVE, cboolean())
            .addColumn(CassandraSieveTable.SIZE, bigint()))
        .table(CassandraSieveSpaceTable.TABLE_NAME)
        .comment("Holds per user current space occupied by SIEVE scripts.")
        .options(options -> options)
        .statement(statement -> statement
            .addPartitionKey(CassandraSieveSpaceTable.USER_NAME, text())
            .addColumn(CassandraSieveSpaceTable.SPACE_USED, counter()))
        .table(CassandraSieveQuotaTable.TABLE_NAME)
        .comment("Holds per user size limitations for SIEVE script storage.")
        .options(options -> options)
        .statement(statement -> statement
            .addPartitionKey(CassandraSieveQuotaTable.USER_NAME, text())
            .addColumn(CassandraSieveQuotaTable.QUOTA, bigint()))
        .table(CassandraSieveClusterQuotaTable.TABLE_NAME)
        .comment("Holds default size limitations for SIEVE script storage.")
        .options(options -> options)
        .statement(statement -> statement
            .addPartitionKey(CassandraSieveClusterQuotaTable.NAME, text())
            .addColumn(CassandraSieveClusterQuotaTable.VALUE, bigint()))
        .table(CassandraSieveActiveTable.TABLE_NAME)
        .comment("Denormalisation table. Allows per user direct active SIEVE script retrieval.")
        .options(options -> options)
        .statement(statement -> statement
            .addPartitionKey(CassandraSieveActiveTable.USER_NAME, text())
            .addColumn(CassandraSieveActiveTable.SCRIPT_NAME, text())
            .addColumn(CassandraSieveActiveTable.DATE, timestamp()))
        .build();

}
