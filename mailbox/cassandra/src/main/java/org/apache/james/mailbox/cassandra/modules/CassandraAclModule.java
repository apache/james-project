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

import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMEUUID;
import static com.datastax.oss.driver.api.core.type.DataTypes.setOf;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraACLV2Table;
import org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable;

import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

public interface CassandraAclModule {
    CassandraModule MODULE = CassandraModule
        .builder()

        .table(CassandraACLV2Table.TABLE_NAME)
        .comment("Holds mailbox ACLs. This table do not rely on a JSON representation nor on LWT, contrary to the acl table it replaces.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraACLV2Table.ID, TIMEUUID)
            .withClusteringColumn(CassandraACLV2Table.KEY, TEXT)
            .withColumn(CassandraACLV2Table.RIGHTS, setOf(TEXT)))

        .table(CassandraUserMailboxRightsTable.TABLE_NAME)
        .comment("Denormalisation table. Allow to retrieve non personal mailboxIds a user has right on")
        .options(options -> options
            .withCompaction(SchemaBuilder.leveledCompactionStrategy())
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraUserMailboxRightsTable.USER_NAME, TEXT)
            .withClusteringColumn(CassandraUserMailboxRightsTable.MAILBOX_ID, TIMEUUID)
            .withColumn(CassandraUserMailboxRightsTable.RIGHTS, TEXT))
        .build();
}
