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

package org.apache.james.rrt.cassandra;

import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.rrt.cassandra.tables.CassandraMappingsSourcesTable;
import org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable;

import com.datastax.oss.driver.api.core.type.DataTypes;

public interface CassandraRRTDataDefinition {
    CassandraDataDefinition MODULE = CassandraDataDefinition.builder()
        .table(CassandraRecipientRewriteTableTable.TABLE_NAME)
        .comment("Holds address re-writing rules.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraRecipientRewriteTableTable.USER, DataTypes.TEXT)
            .withClusteringColumn(CassandraRecipientRewriteTableTable.DOMAIN, DataTypes.TEXT)
            .withClusteringColumn(CassandraRecipientRewriteTableTable.MAPPING, DataTypes.TEXT))
        .table(CassandraMappingsSourcesTable.TABLE_NAME)
        .comment("Projection table for retrieving sources associated with given mappings.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraMappingsSourcesTable.MAPPING_TYPE, DataTypes.TEXT)
            .withPartitionKey(CassandraMappingsSourcesTable.MAPPING_VALUE, DataTypes.TEXT)
            .withClusteringColumn(CassandraMappingsSourcesTable.SOURCE, DataTypes.TEXT))
        .build();
}
