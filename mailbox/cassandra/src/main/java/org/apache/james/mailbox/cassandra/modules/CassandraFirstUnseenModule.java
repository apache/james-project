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

package org.apache.james.mailbox.cassandra.modules;

import static com.datastax.oss.driver.api.core.type.DataTypes.BIGINT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMEUUID;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraFirstUnseenTable;

import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

public interface CassandraFirstUnseenModule {
    CassandraModule MODULE = CassandraModule.table(CassandraFirstUnseenTable.TABLE_NAME)
        .comment("Denormalisation table. Allow to quickly retrieve the first UNSEEN UID of a specific mailbox.")
        .options(options -> options
            .withCompaction(SchemaBuilder.sizeTieredCompactionStrategy())
            .withBloomFilterFpChance(0.01)
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION))
            .withClusteringOrder(CassandraFirstUnseenTable.UID, ClusteringOrder.ASC))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraFirstUnseenTable.MAILBOX_ID, TIMEUUID)
            .withClusteringColumn(CassandraFirstUnseenTable.UID, BIGINT))
        .build();
}
