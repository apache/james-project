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
import static com.datastax.oss.driver.api.core.type.DataTypes.BOOLEAN;
import static com.datastax.oss.driver.api.core.type.DataTypes.INT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMESTAMP;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMEUUID;
import static com.datastax.oss.driver.api.core.type.DataTypes.frozenListOf;
import static com.datastax.oss.driver.api.core.type.DataTypes.frozenMapOf;
import static com.datastax.oss.driver.api.core.type.DataTypes.listOf;
import static com.datastax.oss.driver.api.core.type.DataTypes.setOf;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageIds;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table;
import org.apache.james.mailbox.cassandra.table.Flag;
import org.apache.james.mailbox.cassandra.table.MessageIdToImapUid;

import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

public interface CassandraMessageModule {

    int CACHED_MESSAGE_ID_ROWS = 1000;
    int CACHED_IMAP_UID_ROWS = 100;

    CassandraModule MODULE = CassandraModule.builder()
        .table(CassandraMessageIdTable.TABLE_NAME)
        .comment("Holds mailbox and flags for each message, lookup by mailbox ID + UID")
        .options(options -> options
            .withCompaction(SchemaBuilder.sizeTieredCompactionStrategy())
            .withCaching(true, rows(CACHED_MESSAGE_ID_ROWS)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraMessageIds.MAILBOX_ID, TIMEUUID)
            .withClusteringColumn(CassandraMessageIds.IMAP_UID, BIGINT)
            .withColumn(CassandraMessageIds.MESSAGE_ID, TIMEUUID)
            .withColumn(CassandraMessageIdTable.THREAD_ID, TIMEUUID)
            .withColumn(CassandraMessageIdTable.MOD_SEQ, BIGINT)
            .withColumn(Flag.ANSWERED, BOOLEAN)
            .withColumn(Flag.DELETED, BOOLEAN)
            .withColumn(Flag.DRAFT, BOOLEAN)
            .withColumn(Flag.FLAGGED, BOOLEAN)
            .withColumn(Flag.RECENT, BOOLEAN)
            .withColumn(Flag.SEEN, BOOLEAN)
            .withColumn(Flag.USER, BOOLEAN)
            .withColumn(Flag.USER_FLAGS, setOf(TEXT))
            .withColumn(CassandraMessageV3Table.INTERNAL_DATE, TIMESTAMP)
            .withColumn(CassandraMessageIdTable.SAVE_DATE, TIMESTAMP)
            .withColumn(CassandraMessageV3Table.BODY_START_OCTET, INT)
            .withColumn(CassandraMessageV3Table.FULL_CONTENT_OCTETS, BIGINT)
            .withColumn(CassandraMessageV3Table.HEADER_CONTENT, TEXT))
        .table(MessageIdToImapUid.TABLE_NAME)
        .comment("Holds mailbox and flags for each message, lookup by message ID")
        .options(options -> options
            .withCompaction(SchemaBuilder.sizeTieredCompactionStrategy())
            .withLZ4Compression(8, 1)
            .withCaching(true, rows(CACHED_IMAP_UID_ROWS)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraMessageIds.MESSAGE_ID, TIMEUUID)
            .withClusteringColumn(CassandraMessageIds.MAILBOX_ID, TIMEUUID)
            .withClusteringColumn(CassandraMessageIds.IMAP_UID, BIGINT)
            .withColumn(MessageIdToImapUid.THREAD_ID, TIMEUUID)
            .withColumn(MessageIdToImapUid.MOD_SEQ, BIGINT)
            .withColumn(Flag.ANSWERED, BOOLEAN)
            .withColumn(Flag.DELETED, BOOLEAN)
            .withColumn(Flag.DRAFT, BOOLEAN)
            .withColumn(Flag.FLAGGED, BOOLEAN)
            .withColumn(Flag.RECENT, BOOLEAN)
            .withColumn(Flag.SEEN, BOOLEAN)
            .withColumn(Flag.USER, BOOLEAN)
            .withColumn(Flag.USER_FLAGS, setOf(TEXT))
            .withColumn(CassandraMessageV3Table.INTERNAL_DATE, TIMESTAMP)
            .withColumn(CassandraMessageIdTable.SAVE_DATE, TIMESTAMP)
            .withColumn(CassandraMessageV3Table.BODY_START_OCTET, INT)
            .withColumn(CassandraMessageV3Table.FULL_CONTENT_OCTETS, BIGINT)
            .withColumn(CassandraMessageV3Table.HEADER_CONTENT, TEXT))
        .table(CassandraMessageV3Table.TABLE_NAME)
        .comment("Holds message metadata, independently of any mailboxes. Content of messages is stored " +
            "in `blobs` and `blobparts` tables. Optimizes property storage compared to V2.")
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraMessageIds.MESSAGE_ID, TIMEUUID)
            .withColumn(CassandraMessageV3Table.INTERNAL_DATE, TIMESTAMP)
            .withColumn(CassandraMessageV3Table.BODY_START_OCTET, INT)
            .withColumn(CassandraMessageV3Table.BODY_OCTECTS, BIGINT)
            .withColumn(CassandraMessageV3Table.TEXTUAL_LINE_COUNT, BIGINT)
            .withColumn(CassandraMessageV3Table.FULL_CONTENT_OCTETS, BIGINT)
            .withColumn(CassandraMessageV3Table.BODY_CONTENT, TEXT)
            .withColumn(CassandraMessageV3Table.HEADER_CONTENT, TEXT)
            .withColumn(CassandraMessageV3Table.Properties.CONTENT_DESCRIPTION, TEXT)
            .withColumn(CassandraMessageV3Table.Properties.CONTENT_DISPOSITION_TYPE, TEXT)
            .withColumn(CassandraMessageV3Table.Properties.MEDIA_TYPE, TEXT)
            .withColumn(CassandraMessageV3Table.Properties.SUB_TYPE, TEXT)
            .withColumn(CassandraMessageV3Table.Properties.CONTENT_ID, TEXT)
            .withColumn(CassandraMessageV3Table.Properties.CONTENT_MD5, TEXT)
            .withColumn(CassandraMessageV3Table.Properties.CONTENT_TRANSFER_ENCODING, TEXT)
            .withColumn(CassandraMessageV3Table.Properties.CONTENT_LOCATION, TEXT)
            .withColumn(CassandraMessageV3Table.Properties.CONTENT_LANGUAGE, frozenListOf(TEXT))
            .withColumn(CassandraMessageV3Table.Properties.CONTENT_DISPOSITION_PARAMETERS, frozenMapOf(TEXT, TEXT))
            .withColumn(CassandraMessageV3Table.Properties.CONTENT_TYPE_PARAMETERS, frozenMapOf(TEXT, TEXT))
            .withColumn(CassandraMessageV3Table.ATTACHMENTS, listOf(SchemaBuilder.udt(CassandraMessageV3Table.ATTACHMENTS, true))))
        .type(CassandraMessageV3Table.ATTACHMENTS.asCql(true))
        .statement(statement -> statement
            .withField(CassandraMessageV3Table.Attachments.ID, TEXT)
            .withField(CassandraMessageV3Table.Attachments.NAME, TEXT)
            .withField(CassandraMessageV3Table.Attachments.CID, TEXT)
            .withField(CassandraMessageV3Table.Attachments.IS_INLINE, BOOLEAN))
        .build();
}
