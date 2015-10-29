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

package org.apache.james.mailbox.cassandra;

import java.util.Arrays;
import java.util.List;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import org.apache.james.backends.cassandra.components.CassandraIndex;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota;
import org.apache.james.mailbox.cassandra.table.CassandraDefaultMaxQuota;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.cassandra.table.CassandraMaxQuota;
import org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageUidTable;
import org.apache.james.mailbox.cassandra.table.CassandraSubscriptionTable;
import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.blob;
import static com.datastax.driver.core.DataType.cboolean;
import static com.datastax.driver.core.DataType.cint;
import static com.datastax.driver.core.DataType.counter;
import static com.datastax.driver.core.DataType.set;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.DataType.timeuuid;

public class CassandraMailboxModule implements CassandraModule {

    private final List<CassandraTable> tables;
    private final List<CassandraIndex> index;
    private final List<CassandraType> types;

    public CassandraMailboxModule() {
        tables = Arrays.asList(
            new CassandraTable(CassandraMailboxTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMailboxTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMailboxTable.ID, timeuuid())
                    .addUDTColumn(CassandraMailboxTable.MAILBOX_BASE, SchemaBuilder.frozen(CassandraMailboxTable.MAILBOX_BASE))
                    .addColumn(CassandraMailboxTable.NAME, text())
                    .addColumn(CassandraMailboxTable.PATH, text())
                    .addColumn(CassandraMailboxTable.UIDVALIDITY, bigint())),
            new CassandraTable(CassandraMailboxCountersTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMailboxCountersTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMailboxCountersTable.MAILBOX_ID, timeuuid())
                    .addColumn(CassandraMailboxCountersTable.COUNT, counter())
                    .addColumn(CassandraMailboxCountersTable.UNSEEN, counter())),
            new CassandraTable(CassandraMessageUidTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMessageUidTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMessageUidTable.MAILBOX_ID, timeuuid())
                    .addColumn(CassandraMessageUidTable.NEXT_UID, bigint())),
            new CassandraTable(CassandraMessageTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMessageTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMessageTable.MAILBOX_ID, timeuuid())
                    .addClusteringColumn(CassandraMessageTable.IMAP_UID, bigint())
                    .addColumn(CassandraMessageTable.INTERNAL_DATE, timestamp())
                    .addColumn(CassandraMessageTable.BODY_START_OCTET, cint())
                    .addColumn(CassandraMessageTable.BODY_OCTECTS, cint())
                    .addColumn(CassandraMessageTable.TEXTUAL_LINE_COUNT, bigint())
                    .addColumn(CassandraMessageTable.MOD_SEQ, bigint())
                    .addColumn(CassandraMessageTable.FULL_CONTENT_OCTETS, cint())
                    .addColumn(CassandraMessageTable.BODY_CONTENT, blob())
                    .addColumn(CassandraMessageTable.HEADER_CONTENT, blob())
                    .addColumn(CassandraMessageTable.Flag.ANSWERED, cboolean())
                    .addColumn(CassandraMessageTable.Flag.DELETED, cboolean())
                    .addColumn(CassandraMessageTable.Flag.DRAFT, cboolean())
                    .addColumn(CassandraMessageTable.Flag.FLAGGED, cboolean())
                    .addColumn(CassandraMessageTable.Flag.RECENT, cboolean())
                    .addColumn(CassandraMessageTable.Flag.SEEN, cboolean())
                    .addColumn(CassandraMessageTable.Flag.USER, cboolean())
                    .addColumn(CassandraMessageTable.Flag.USER_FLAGS, set(text()))
                    .addUDTListColumn(CassandraMessageTable.PROPERTIES, SchemaBuilder.frozen(CassandraMessageTable.PROPERTIES))),
            new CassandraTable(CassandraSubscriptionTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraSubscriptionTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraSubscriptionTable.MAILBOX, text())
                    .addClusteringColumn(CassandraSubscriptionTable.USER, text())),
            new CassandraTable(CassandraACLTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraACLTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraACLTable.ID, timeuuid())
                    .addColumn(CassandraACLTable.ACL, text())
                    .addColumn(CassandraACLTable.VERSION, bigint())),
            new CassandraTable(CassandraMessageModseqTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMessageModseqTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMessageModseqTable.MAILBOX_ID, timeuuid())
                    .addColumn(CassandraMessageModseqTable.NEXT_MODSEQ, bigint())),
            new CassandraTable(CassandraCurrentQuota.TABLE_NAME,
                    SchemaBuilder.createTable(CassandraCurrentQuota.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraCurrentQuota.QUOTA_ROOT, text())
                    .addColumn(CassandraCurrentQuota.MESSAGE_COUNT, counter())
                    .addColumn(CassandraCurrentQuota.STORAGE, counter())),
            new CassandraTable(CassandraMaxQuota.TABLE_NAME,
                    SchemaBuilder.createTable(CassandraMaxQuota.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMaxQuota.QUOTA_ROOT, text())
                    .addColumn(CassandraMaxQuota.MESSAGE_COUNT, bigint())
                    .addColumn(CassandraMaxQuota.STORAGE, bigint())),
            new CassandraTable(CassandraDefaultMaxQuota.TABLE_NAME,
                    SchemaBuilder.createTable(CassandraDefaultMaxQuota.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraDefaultMaxQuota.TYPE, text())
                    .addColumn(CassandraDefaultMaxQuota.VALUE, bigint())));
        index = Arrays.asList(
            new CassandraIndex(
                SchemaBuilder.createIndex(CassandraIndex.INDEX_PREFIX + CassandraMailboxTable.TABLE_NAME)
                    .ifNotExists()
                    .onTable(CassandraMailboxTable.TABLE_NAME)
                    .andColumn(CassandraMailboxTable.PATH)),
            new CassandraIndex(
                SchemaBuilder.createIndex(CassandraIndex.INDEX_PREFIX + CassandraMailboxTable.MAILBOX_BASE)
                    .ifNotExists()
                    .onTable(CassandraMailboxTable.TABLE_NAME)
                    .andColumn(CassandraMailboxTable.MAILBOX_BASE)),
            new CassandraIndex(
                SchemaBuilder.createIndex(CassandraIndex.INDEX_PREFIX + CassandraSubscriptionTable.USER)
                    .ifNotExists()
                    .onTable(CassandraSubscriptionTable.TABLE_NAME)
                    .andColumn(CassandraSubscriptionTable.USER)),
            new CassandraIndex(
                SchemaBuilder.createIndex(CassandraIndex.INDEX_PREFIX + CassandraMessageTable.Flag.RECENT)
                    .ifNotExists()
                    .onTable(CassandraMessageTable.TABLE_NAME)
                    .andColumn(CassandraMessageTable.Flag.RECENT)),
            new CassandraIndex(
                SchemaBuilder.createIndex(CassandraIndex.INDEX_PREFIX + CassandraMessageTable.Flag.SEEN)
                    .ifNotExists()
                    .onTable(CassandraMessageTable.TABLE_NAME)
                    .andColumn(CassandraMessageTable.Flag.SEEN)),
            new CassandraIndex(
                SchemaBuilder.createIndex(CassandraIndex.INDEX_PREFIX + CassandraMessageTable.Flag.DELETED)
                    .ifNotExists()
                    .onTable(CassandraMessageTable.TABLE_NAME)
                    .andColumn(CassandraMessageTable.Flag.DELETED)));
        types = Arrays.asList(
            new CassandraType(CassandraMailboxTable.MAILBOX_BASE,
                SchemaBuilder.createType(CassandraMailboxTable.MAILBOX_BASE)
                    .ifNotExists()
                    .addColumn(CassandraMailboxTable.MailboxBase.NAMESPACE, text())
                    .addColumn(CassandraMailboxTable.MailboxBase.USER, text())),
            new CassandraType(CassandraMessageTable.PROPERTIES,
                SchemaBuilder.createType(CassandraMessageTable.PROPERTIES)
                    .ifNotExists()
                    .addColumn(CassandraMessageTable.Properties.NAMESPACE, text())
                    .addColumn(CassandraMessageTable.Properties.NAME, text())
                    .addColumn(CassandraMessageTable.Properties.VALUE, text())));
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
