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

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.sieve.cassandra.tables.CassandraSieveClusterQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveSpaceTable;

import com.datastax.oss.driver.api.core.type.DataTypes;

@Deprecated() // To be removed after release 3.9.0
public interface CassandraSieveQuotaModule {
    CassandraModule MODULE = CassandraModule.builder()

        .table(CassandraSieveSpaceTable.TABLE_NAME)
        .comment("Holds per user current space occupied by SIEVE scripts.")
        .options(options -> options)
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraSieveSpaceTable.USER_NAME, DataTypes.TEXT)
            .withColumn(CassandraSieveSpaceTable.SPACE_USED, DataTypes.COUNTER))

        .table(CassandraSieveQuotaTable.TABLE_NAME)
        .comment("Holds per user size limitations for SIEVE script storage.")
        .options(options -> options)
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraSieveQuotaTable.USER_NAME, DataTypes.TEXT)
            .withColumn(CassandraSieveQuotaTable.QUOTA, DataTypes.BIGINT))

        .table(CassandraSieveClusterQuotaTable.TABLE_NAME)
        .comment("Holds default size limitations for SIEVE script storage.")
        .options(options -> options)
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraSieveClusterQuotaTable.NAME, DataTypes.TEXT)
            .withColumn(CassandraSieveClusterQuotaTable.VALUE, DataTypes.BIGINT))

        .build();

}
