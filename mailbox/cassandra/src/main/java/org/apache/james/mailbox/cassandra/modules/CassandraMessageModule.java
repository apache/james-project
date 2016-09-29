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
import static com.datastax.driver.core.DataType.blob;
import static com.datastax.driver.core.DataType.cboolean;
import static com.datastax.driver.core.DataType.cint;
import static com.datastax.driver.core.DataType.set;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.DataType.timeuuid;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.james.backends.cassandra.components.CassandraIndex;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageIds;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable;
import org.apache.james.mailbox.cassandra.table.Flag;
import org.apache.james.mailbox.cassandra.table.MessageIdToImapUid;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;

public class CassandraMessageModule implements CassandraModule {

    private final List<CassandraTable> tables;
    private final List<CassandraIndex> index;
    private final List<CassandraType> types;

    public CassandraMessageModule() {
        tables = ImmutableList.of(
            new CassandraTable(MessageIdToImapUid.TABLE_NAME,
                SchemaBuilder.createTable(MessageIdToImapUid.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMessageIds.MESSAGE_ID, timeuuid())
                    .addClusteringColumn(CassandraMessageIds.MAILBOX_ID, timeuuid())
                    .addClusteringColumn(CassandraMessageIds.IMAP_UID, bigint())
                    .addColumn(MessageIdToImapUid.MOD_SEQ, bigint())
                    .addColumn(Flag.ANSWERED, cboolean())
                    .addColumn(Flag.DELETED, cboolean())
                    .addColumn(Flag.DRAFT, cboolean())
                    .addColumn(Flag.FLAGGED, cboolean())
                    .addColumn(Flag.RECENT, cboolean())
                    .addColumn(Flag.SEEN, cboolean())
                    .addColumn(Flag.USER, cboolean())
                    .addColumn(Flag.USER_FLAGS, set(text()))),
            new CassandraTable(CassandraMessageIdTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMessageIdTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMessageIds.MAILBOX_ID, timeuuid())
                    .addClusteringColumn(CassandraMessageIds.IMAP_UID, bigint())
                    .addColumn(CassandraMessageIds.MESSAGE_ID, timeuuid())
                    .addColumn(CassandraMessageIdTable.MOD_SEQ, bigint())
                    .addColumn(Flag.ANSWERED, cboolean())
                    .addColumn(Flag.DELETED, cboolean())
                    .addColumn(Flag.DRAFT, cboolean())
                    .addColumn(Flag.FLAGGED, cboolean())
                    .addColumn(Flag.RECENT, cboolean())
                    .addColumn(Flag.SEEN, cboolean())
                    .addColumn(Flag.USER, cboolean())
                    .addColumn(Flag.USER_FLAGS, set(text()))),

            new CassandraTable(CassandraMessageTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMessageTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMessageIds.MESSAGE_ID, timeuuid())
                    .addColumn(CassandraMessageTable.INTERNAL_DATE, timestamp())
                    .addColumn(CassandraMessageTable.BODY_START_OCTET, cint())
                    .addColumn(CassandraMessageTable.BODY_OCTECTS, bigint())
                    .addColumn(CassandraMessageTable.TEXTUAL_LINE_COUNT, bigint())
                    .addColumn(CassandraMessageTable.FULL_CONTENT_OCTETS, bigint())
                    .addColumn(CassandraMessageTable.BODY_CONTENT, blob())
                    .addColumn(CassandraMessageTable.HEADER_CONTENT, blob())
                    .addUDTListColumn(CassandraMessageTable.ATTACHMENTS, SchemaBuilder.frozen(CassandraMessageTable.ATTACHMENTS))
                    .addUDTListColumn(CassandraMessageTable.PROPERTIES, SchemaBuilder.frozen(CassandraMessageTable.PROPERTIES))));
        index = Collections.emptyList();
        types = Arrays.asList(
            new CassandraType(CassandraMessageTable.PROPERTIES,
                SchemaBuilder.createType(CassandraMessageTable.PROPERTIES)
                    .ifNotExists()
                    .addColumn(CassandraMessageTable.Properties.NAMESPACE, text())
                    .addColumn(CassandraMessageTable.Properties.NAME, text())
                    .addColumn(CassandraMessageTable.Properties.VALUE, text())),
            new CassandraType(CassandraMessageTable.ATTACHMENTS,
                SchemaBuilder.createType(CassandraMessageTable.ATTACHMENTS)
                    .ifNotExists()
                    .addColumn(CassandraMessageTable.Attachments.ID, text())
                    .addColumn(CassandraMessageTable.Attachments.NAME, text())
                    .addColumn(CassandraMessageTable.Attachments.CID, text())
                    .addColumn(CassandraMessageTable.Attachments.IS_INLINE, cboolean())));
    }

    @Override
    public List<CassandraTable> moduleTables() {
        return tables;
    }

    @Override
    public List<CassandraIndex> moduleIndex() {
        return index;
    }

    @Override
    public List<CassandraType> moduleTypes() {
        return types;
    }
}
