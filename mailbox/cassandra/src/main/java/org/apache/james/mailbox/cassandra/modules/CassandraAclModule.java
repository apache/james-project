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

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.set;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timeuuid;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.cassandra.table.CassandraACLV2Table;
import org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraAclModule {
    CassandraModule MODULE = CassandraModule
        .builder()
        .table(CassandraACLTable.TABLE_NAME)
        .comment("Holds mailbox ACLs")
        .options(options -> options
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(CassandraACLTable.ID, timeuuid())
            .addColumn(CassandraACLTable.ACL, text())
            .addColumn(CassandraACLTable.VERSION, bigint()))

        .table(CassandraACLV2Table.TABLE_NAME)
        .comment("Holds mailbox ACLs. This table do not rely on a JSON representation nor on LWT, contrary to the acl table it replaces.")
        .options(options -> options
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(CassandraACLV2Table.ID, timeuuid())
            .addClusteringColumn(CassandraACLV2Table.KEY, text())
            .addColumn(CassandraACLV2Table.RIGHTS, set(text())))

        .table(CassandraUserMailboxRightsTable.TABLE_NAME)
        .comment("Denormalisation table. Allow to retrieve non personal mailboxIds a user has right on")
        .options(options -> options
            .compactionOptions(SchemaBuilder.leveledStrategy())
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(CassandraUserMailboxRightsTable.USER_NAME, text())
            .addClusteringColumn(CassandraUserMailboxRightsTable.MAILBOX_ID, timeuuid())
            .addColumn(CassandraUserMailboxRightsTable.RIGHTS, text()))
        .build();
}
