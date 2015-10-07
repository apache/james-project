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

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.blob;
import static com.datastax.driver.core.DataType.cboolean;
import static com.datastax.driver.core.DataType.cint;
import static com.datastax.driver.core.DataType.counter;
import static com.datastax.driver.core.DataType.set;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.DataType.timeuuid;

import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.schemabuilder.Create;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.datastax.driver.core.schemabuilder.SchemaStatement;

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

import java.util.Arrays;

public class CassandraTableManager {

    private final static String INDEX_PREFIX = "INDEX_";

    private Session session;

    enum TABLE {
        Mailbox(CassandraMailboxTable.TABLE_NAME,
            SchemaBuilder.createTable(CassandraMailboxTable.TABLE_NAME)
                .ifNotExists()
                .addPartitionKey(CassandraMailboxTable.ID, timeuuid())
                .addUDTColumn(CassandraMailboxTable.MAILBOX_BASE, SchemaBuilder.frozen(CassandraTypesProvider.TYPE.MailboxBase.getName()))
                .addColumn(CassandraMailboxTable.NAME, text())
                .addColumn(CassandraMailboxTable.PATH, text())
                .addColumn(CassandraMailboxTable.UIDVALIDITY, bigint())),
        MailboxCounter(CassandraMailboxCountersTable.TABLE_NAME,
            SchemaBuilder.createTable(CassandraMailboxCountersTable.TABLE_NAME)
                .ifNotExists()
                .addPartitionKey(CassandraMailboxCountersTable.MAILBOX_ID, timeuuid())
                .addColumn(CassandraMailboxCountersTable.COUNT, counter())
                .addColumn(CassandraMailboxCountersTable.UNSEEN, counter())),
        MessageUid(CassandraMessageUidTable.TABLE_NAME,
            SchemaBuilder.createTable(CassandraMessageUidTable.TABLE_NAME)
                .ifNotExists()
                .addPartitionKey(CassandraMessageUidTable.MAILBOX_ID, timeuuid())
                .addColumn(CassandraMessageUidTable.NEXT_UID, bigint())),
        Message(CassandraMessageTable.TABLE_NAME,
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
                .addUDTListColumn(CassandraMessageTable.PROPERTIES, SchemaBuilder.frozen(CassandraTypesProvider.TYPE.Property.getName()))),
        Subscription(CassandraSubscriptionTable.TABLE_NAME,
            SchemaBuilder.createTable(CassandraSubscriptionTable.TABLE_NAME)
                .ifNotExists()
                .addPartitionKey(CassandraSubscriptionTable.MAILBOX, text())
                .addClusteringColumn(CassandraSubscriptionTable.USER, text())
        ),
        Acl(CassandraACLTable.TABLE_NAME,
            SchemaBuilder.createTable(CassandraACLTable.TABLE_NAME)
                .ifNotExists()
                .addPartitionKey(CassandraACLTable.ID, timeuuid())
                .addColumn(CassandraACLTable.ACL, text())
                .addColumn(CassandraACLTable.VERSION, bigint())),
        ModSeq(CassandraMessageModseqTable.TABLE_NAME,
            SchemaBuilder.createTable(CassandraMessageModseqTable.TABLE_NAME)
                .ifNotExists()
                .addPartitionKey(CassandraMessageModseqTable.MAILBOX_ID, timeuuid())
                .addColumn(CassandraMessageModseqTable.NEXT_MODSEQ, bigint())),
        CurrentQuota(CassandraCurrentQuota.TABLE_NAME,
            SchemaBuilder.createTable(CassandraCurrentQuota.TABLE_NAME)
                .ifNotExists()
                .addPartitionKey(CassandraCurrentQuota.QUOTA_ROOT, text())
                .addColumn(CassandraCurrentQuota.MESSAGE_COUNT, counter())
                .addColumn(CassandraCurrentQuota.STORAGE, counter())),
        MaxQuota(CassandraMaxQuota.TABLE_NAME,
            SchemaBuilder.createTable(CassandraMaxQuota.TABLE_NAME)
                .ifNotExists()
                .addPartitionKey(CassandraMaxQuota.QUOTA_ROOT, text())
                .addColumn(CassandraMaxQuota.MESSAGE_COUNT, bigint())
                .addColumn(CassandraMaxQuota.STORAGE, bigint())),
        DefaultMaxQuota(CassandraDefaultMaxQuota.TABLE_NAME,
            SchemaBuilder.createTable(CassandraDefaultMaxQuota.TABLE_NAME)
                .ifNotExists()
                .addPartitionKey(CassandraDefaultMaxQuota.TYPE, text())
                .addColumn(CassandraDefaultMaxQuota.VALUE, bigint()))
        ;

        private Create createStatement;
        private String name;

        TABLE(String name, Create createStatement) {
            this.createStatement = createStatement;
            this.name = name;
        }
    }

    enum INDEX {
        MailboxPath(SchemaBuilder.createIndex(INDEX_PREFIX + CassandraMailboxTable.TABLE_NAME)
            .ifNotExists()
            .onTable(CassandraMailboxTable.TABLE_NAME)
            .andColumn(CassandraMailboxTable.PATH)),
        AggregateNamespaceUser(
            SchemaBuilder.createIndex(INDEX_PREFIX + CassandraMailboxTable.MAILBOX_BASE)
                .ifNotExists()
                .onTable(CassandraMailboxTable.TABLE_NAME)
                .andColumn(CassandraMailboxTable.MAILBOX_BASE)),
        UserSubscription(SchemaBuilder.createIndex(INDEX_PREFIX + CassandraSubscriptionTable.USER)
            .ifNotExists()
            .onTable(CassandraSubscriptionTable.TABLE_NAME)
            .andColumn(CassandraSubscriptionTable.USER)),
        RecentMessages(SchemaBuilder.createIndex(INDEX_PREFIX + CassandraMessageTable.Flag.RECENT)
            .ifNotExists()
            .onTable(CassandraMessageTable.TABLE_NAME)
            .andColumn(CassandraMessageTable.Flag.RECENT)),
        SeenMessages(SchemaBuilder.createIndex(INDEX_PREFIX + CassandraMessageTable.Flag.SEEN)
            .ifNotExists()
            .onTable(CassandraMessageTable.TABLE_NAME)
            .andColumn(CassandraMessageTable.Flag.SEEN)),
        DeletedMessages(SchemaBuilder.createIndex(INDEX_PREFIX + CassandraMessageTable.Flag.DELETED)
            .ifNotExists()
            .onTable(CassandraMessageTable.TABLE_NAME)
            .andColumn(CassandraMessageTable.Flag.DELETED))
        ;
        private SchemaStatement createIndexStatement;

        INDEX(SchemaStatement createIndexStatement) {
            this.createIndexStatement = createIndexStatement;
        }
    }

    public CassandraTableManager(Session session) {
        this.session = session;
    }

    public CassandraTableManager ensureAllTables() {
        Arrays.asList(TABLE.values())
            .forEach(
                (table) -> session.execute(table.createStatement)
            );
        Arrays.asList(INDEX.values())
            .forEach(
                (index) -> session.execute(index.createIndexStatement)
            );
        return this;
    }

    public void clearAllTables() {
        Arrays.asList(TABLE.values())
            .forEach(
                (table) -> clearTable(table.name)
            );
    }

    private void clearTable(String tableName) {
        session.execute(QueryBuilder.truncate(tableName));
    }
}
