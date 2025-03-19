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
import static org.apache.james.mailbox.cassandra.table.CassandraDeletedMessageTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraDeletedMessageTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraDeletedMessageTable.UID;

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.utils.CassandraConstants;

import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

public interface CassandraDeletedMessageDataDefinition {
    CassandraDataDefinition MODULE = CassandraDataDefinition.table(TABLE_NAME)
        .comment("Denormalisation table. Allows to retrieve UID marked as DELETED in specific mailboxes.")
        .options(options -> options
            .withCompaction(SchemaBuilder.sizeTieredCompactionStrategy())
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(MAILBOX_ID, TIMEUUID)
            .withClusteringColumn(UID, BIGINT))
        .build();
}
