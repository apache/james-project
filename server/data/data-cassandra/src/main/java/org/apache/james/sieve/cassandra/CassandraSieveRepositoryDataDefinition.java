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

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.sieve.cassandra.tables.CassandraSieveActiveTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveTable;

import com.datastax.oss.driver.api.core.type.DataTypes;

public interface CassandraSieveRepositoryDataDefinition {

    CassandraDataDefinition MODULE = CassandraDataDefinition.builder()

        .table(CassandraSieveTable.TABLE_NAME)
        .comment("Holds SIEVE scripts.")
        .options(options -> options)
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraSieveTable.USER_NAME, DataTypes.TEXT)
            .withClusteringColumn(CassandraSieveTable.SCRIPT_NAME, DataTypes.TEXT)
            .withColumn(CassandraSieveTable.SCRIPT_CONTENT, DataTypes.TEXT)
            .withColumn(CassandraSieveTable.IS_ACTIVE, DataTypes.BOOLEAN)
            .withColumn(CassandraSieveTable.SIZE, DataTypes.BIGINT))

        .table(CassandraSieveActiveTable.TABLE_NAME)
        .comment("Denormalisation table. Allows per user direct active SIEVE script retrieval.")
        .options(options -> options)
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraSieveActiveTable.USER_NAME, DataTypes.TEXT)
            .withColumn(CassandraSieveActiveTable.SCRIPT_NAME, DataTypes.TEXT)
            .withColumn(CassandraSieveActiveTable.DATE, DataTypes.TIMESTAMP))

        .build();

}
