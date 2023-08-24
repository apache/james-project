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

package org.apache.james.backends.cassandra.components;

import static com.datastax.oss.driver.api.core.type.DataTypes.BIGINT;
import static com.datastax.oss.driver.api.core.type.DataTypes.COUNTER;
import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;

import org.apache.james.backends.cassandra.utils.CassandraConstants;

public interface CassandraMutualizedQuotaModule {
    CassandraModule MODULE = CassandraModule.builder()
        .table(CassandraQuotaLimitTable.TABLE_NAME)
        .comment("Holds quota limits.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraQuotaLimitTable.QUOTA_SCOPE, TEXT)
            .withPartitionKey(CassandraQuotaLimitTable.IDENTIFIER, TEXT)
            .withClusteringColumn(CassandraQuotaLimitTable.QUOTA_COMPONENT, TEXT)
            .withClusteringColumn(CassandraQuotaLimitTable.QUOTA_TYPE, TEXT)
            .withColumn(CassandraQuotaLimitTable.QUOTA_LIMIT, BIGINT))

        .table(CassandraQuotaCurrentValueTable.TABLE_NAME)
        .comment("Holds quota current values.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraQuotaCurrentValueTable.IDENTIFIER, TEXT)
            .withClusteringColumn(CassandraQuotaCurrentValueTable.QUOTA_COMPONENT, TEXT)
            .withClusteringColumn(CassandraQuotaCurrentValueTable.QUOTA_TYPE, TEXT)
            .withColumn(CassandraQuotaCurrentValueTable.CURRENT_VALUE, COUNTER))
        .build();
}
