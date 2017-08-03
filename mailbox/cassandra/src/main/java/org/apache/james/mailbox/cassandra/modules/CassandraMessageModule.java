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

import java.util.List;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageIds;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table;
import org.apache.james.mailbox.cassandra.table.Flag;
import org.apache.james.mailbox.cassandra.table.MessageIdToImapUid;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;

public class CassandraMessageModule implements CassandraModule {

    public static final int CACHED_MESSAGE_ID_ROWS = 1000;
    public static final int CACHED_IMAP_UID_ROWS = 100;
    private final List<CassandraTable> tables;
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
                    .addColumn(Flag.USER_FLAGS, set(text()))
                    .withOptions()
                    .comment("Holds mailbox and flags for each message, lookup by message ID")
                    .compactionOptions(SchemaBuilder.leveledStrategy())
                    .caching(SchemaBuilder.KeyCaching.ALL,
                        SchemaBuilder.rows(CACHED_IMAP_UID_ROWS))),
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
                    .addColumn(Flag.USER_FLAGS, set(text()))
                    .withOptions()
                    .comment("Holds mailbox and flags for each message, lookup by mailbox ID + UID")
                    .compactionOptions(SchemaBuilder.leveledStrategy())
                    .caching(SchemaBuilder.KeyCaching.ALL,
                        SchemaBuilder.rows(CACHED_MESSAGE_ID_ROWS))),
            new CassandraTable(CassandraMessageV1Table.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMessageV1Table.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMessageIds.MESSAGE_ID, timeuuid())
                    .addColumn(CassandraMessageV1Table.INTERNAL_DATE, timestamp())
                    .addColumn(CassandraMessageV1Table.BODY_START_OCTET, cint())
                    .addColumn(CassandraMessageV1Table.BODY_OCTECTS, bigint())
                    .addColumn(CassandraMessageV1Table.TEXTUAL_LINE_COUNT, bigint())
                    .addColumn(CassandraMessageV1Table.FULL_CONTENT_OCTETS, bigint())
                    .addColumn(CassandraMessageV1Table.BODY_CONTENT, blob())
                    .addColumn(CassandraMessageV1Table.HEADER_CONTENT, blob())
                    .addUDTListColumn(CassandraMessageV1Table.ATTACHMENTS, SchemaBuilder.frozen(CassandraMessageV1Table.ATTACHMENTS))
                    .addUDTListColumn(CassandraMessageV1Table.PROPERTIES, SchemaBuilder.frozen(CassandraMessageV1Table.PROPERTIES))
                    .withOptions()
                    .comment("Deprecated table. Was used to store messages. See `messagev2` instead.")),
            new CassandraTable(CassandraMessageV2Table.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMessageV2Table.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMessageIds.MESSAGE_ID, timeuuid())
                    .addColumn(CassandraMessageV2Table.INTERNAL_DATE, timestamp())
                    .addColumn(CassandraMessageV2Table.BODY_START_OCTET, cint())
                    .addColumn(CassandraMessageV2Table.BODY_OCTECTS, bigint())
                    .addColumn(CassandraMessageV2Table.TEXTUAL_LINE_COUNT, bigint())
                    .addColumn(CassandraMessageV2Table.FULL_CONTENT_OCTETS, bigint())
                    .addColumn(CassandraMessageV2Table.BODY_CONTENT, text())
                    .addColumn(CassandraMessageV2Table.HEADER_CONTENT, text())
                    .addUDTListColumn(CassandraMessageV2Table.ATTACHMENTS, SchemaBuilder.frozen(CassandraMessageV2Table.ATTACHMENTS))
                    .addUDTListColumn(CassandraMessageV2Table.PROPERTIES, SchemaBuilder.frozen(CassandraMessageV2Table.PROPERTIES))
                    .withOptions()
                    .comment("Holds message metadata, independently of any mailboxes. Content of messages is stored " +
                        "in `blobs` and `blobparts` tables.")));
        types = ImmutableList.of(
            new CassandraType(CassandraMessageV1Table.PROPERTIES,
                SchemaBuilder.createType(CassandraMessageV1Table.PROPERTIES)
                    .ifNotExists()
                    .addColumn(CassandraMessageV1Table.Properties.NAMESPACE, text())
                    .addColumn(CassandraMessageV1Table.Properties.NAME, text())
                    .addColumn(CassandraMessageV1Table.Properties.VALUE, text())),
            new CassandraType(CassandraMessageV1Table.ATTACHMENTS,
                SchemaBuilder.createType(CassandraMessageV1Table.ATTACHMENTS)
                    .ifNotExists()
                    .addColumn(CassandraMessageV1Table.Attachments.ID, text())
                    .addColumn(CassandraMessageV1Table.Attachments.NAME, text())
                    .addColumn(CassandraMessageV1Table.Attachments.CID, text())
                    .addColumn(CassandraMessageV1Table.Attachments.IS_INLINE, cboolean())));
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
