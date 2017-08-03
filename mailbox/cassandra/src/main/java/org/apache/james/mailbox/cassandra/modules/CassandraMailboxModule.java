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
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timeuuid;

import java.util.List;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;

public class CassandraMailboxModule implements CassandraModule {

    private final List<CassandraTable> tables;
    private final List<CassandraType> types;

    public CassandraMailboxModule() {
        tables = ImmutableList.of(
            new CassandraTable(CassandraMailboxTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMailboxTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMailboxTable.ID, timeuuid())
                    .addUDTColumn(CassandraMailboxTable.MAILBOX_BASE, SchemaBuilder.frozen(CassandraMailboxTable.MAILBOX_BASE))
                    .addColumn(CassandraMailboxTable.NAME, text())
                    .addColumn(CassandraMailboxTable.UIDVALIDITY, bigint())
                    .withOptions()
                    .comment("Holds the mailboxes information.")
                    .caching(SchemaBuilder.KeyCaching.ALL,
                        SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION))),
            new CassandraTable(CassandraMailboxPathTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMailboxPathTable.TABLE_NAME)
                    .ifNotExists()
                    .addUDTPartitionKey(CassandraMailboxPathTable.NAMESPACE_AND_USER, SchemaBuilder.frozen(CassandraMailboxTable.MAILBOX_BASE))
                    .addClusteringColumn(CassandraMailboxPathTable.MAILBOX_NAME, text())
                    .addColumn(CassandraMailboxPathTable.MAILBOX_ID, timeuuid())
                    .withOptions()
                    .comment("Denormalisation table. Allow to retrieve mailboxes belonging to a certain user. This is a " +
                        "LIST optimisation.")
                    .caching(SchemaBuilder.KeyCaching.ALL,
                        SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION))));
        types = ImmutableList.of(
            new CassandraType(CassandraMailboxTable.MAILBOX_BASE,
                SchemaBuilder.createType(CassandraMailboxTable.MAILBOX_BASE)
                    .ifNotExists()
                    .addColumn(CassandraMailboxTable.MailboxBase.NAMESPACE, text())
                    .addColumn(CassandraMailboxTable.MailboxBase.USER, text())));
    }

    @Override
    public List<CassandraTable> moduleTables() {
        return tables;
    }

    @Override
    public List<CassandraType> moduleTypes() {
        return types;
    }
}
