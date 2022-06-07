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
import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMEUUID;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;

public interface CassandraMailboxModule {
    CassandraModule MODULE = CassandraModule.builder()

        .type(CassandraMailboxTable.MAILBOX_BASE)
        .statement(statement -> statement
            .withField(CassandraMailboxTable.MailboxBase.NAMESPACE, TEXT)
            .withField(CassandraMailboxTable.MailboxBase.USER, TEXT))

        .table(CassandraMailboxTable.TABLE_NAME)
        .comment("Holds the mailboxes information.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION))
            .withLZ4Compression(8, 1))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraMailboxTable.ID, TIMEUUID)
            .withColumn(CassandraMailboxTable.MAILBOX_BASE, types.getDefinedUserType(CassandraMailboxTable.MAILBOX_BASE))
            .withColumn(CassandraMailboxTable.NAME, TEXT)
            .withColumn(CassandraMailboxTable.UIDVALIDITY, BIGINT))

        .table(CassandraMailboxPathV3Table.TABLE_NAME)
        .comment("Denormalisation table. Allow to retrieve mailboxes belonging to a certain user. This is a " +
            "LIST optimisation.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraMailboxPathV3Table.NAMESPACE, TEXT)
            .withPartitionKey(CassandraMailboxPathV3Table.USER, TEXT)
            .withClusteringColumn(CassandraMailboxPathV3Table.MAILBOX_NAME, TEXT)
            .withColumn(CassandraMailboxPathV3Table.MAILBOX_ID, TIMEUUID)
            .withColumn(CassandraMailboxPathV3Table.UIDVALIDITY, BIGINT))

        .build();
}
