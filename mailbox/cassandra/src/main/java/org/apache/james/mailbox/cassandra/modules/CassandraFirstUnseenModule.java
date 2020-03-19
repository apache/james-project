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

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraFirstUnseenTable;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraFirstUnseenModule {
    CassandraModule MODULE = CassandraModule.table(CassandraFirstUnseenTable.TABLE_NAME)
        .comment("Denormalisation table. Allow to quickly retrieve the first UNSEEN UID of a specific mailbox.")
        .options(options -> options
            .compactionOptions(SchemaBuilder.sizedTieredStategy())
            .bloomFilterFPChance(0.01)
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION))
            .clusteringOrder(CassandraFirstUnseenTable.UID, SchemaBuilder.Direction.ASC))
        .statement(statement -> statement
            .addPartitionKey(CassandraFirstUnseenTable.MAILBOX_ID, DataType.timeuuid())
            .addClusteringColumn(CassandraFirstUnseenTable.UID, DataType.bigint()))
        .build();
}
