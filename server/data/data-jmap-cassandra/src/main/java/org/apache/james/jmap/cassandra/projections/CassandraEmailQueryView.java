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

package org.apache.james.jmap.cassandra.projections;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.desc;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.gte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.DATE_LOOKUP_TABLE;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.MAILBOX_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.MESSAGE_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.RECEIVED_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.SENT_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.TABLE_NAME_RECEIVED_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.TABLE_NAME_SENT_AT;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.util.streams.Limit;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraEmailQueryView implements EmailQueryView {
    private static final String LIMIT_MARKER = "LIMIT_BIND_MARKER";

    private final CassandraMessageId.Factory messageIdFactory;
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement listMailboxContentBySentAt;
    private final PreparedStatement listMailboxContentSinceSentAt;
    private final PreparedStatement listMailboxContentSinceReceivedAt;
    private final PreparedStatement insertInLookupTable;
    private final PreparedStatement insertReceivedAt;
    private final PreparedStatement insertSentAt;
    private final PreparedStatement deleteLookupRecord;
    private final PreparedStatement deleteSentAt;
    private final PreparedStatement deleteReceivedAt;
    private final PreparedStatement deleteAllLookupRecords;
    private final PreparedStatement deleteAllSentAt;
    private final PreparedStatement deleteAllReceivedAt;
    private final PreparedStatement lookupDate;

    @Inject
    public CassandraEmailQueryView(CassandraMessageId.Factory messageIdFactory, Session session) {
        this.messageIdFactory = messageIdFactory;
        this.executor = new CassandraAsyncExecutor(session);

        listMailboxContentBySentAt = session.prepare(select()
            .from(TABLE_NAME_SENT_AT)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .orderBy(desc(SENT_AT))
            .limit(bindMarker(LIMIT_MARKER)));

        listMailboxContentSinceSentAt = session.prepare(select()
            .from(TABLE_NAME_SENT_AT)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(gte(SENT_AT, bindMarker(SENT_AT)))
            .orderBy(desc(SENT_AT))
            .limit(bindMarker(LIMIT_MARKER)));

        listMailboxContentSinceReceivedAt = session.prepare(select()
            .from(TABLE_NAME_RECEIVED_AT)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(gte(RECEIVED_AT, bindMarker(RECEIVED_AT)))
            .orderBy(desc(RECEIVED_AT)));

        insertInLookupTable = session.prepare(insertInto(DATE_LOOKUP_TABLE)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(SENT_AT, bindMarker(SENT_AT))
            .value(RECEIVED_AT, bindMarker(RECEIVED_AT)));

        insertSentAt = session.prepare(insertInto(TABLE_NAME_SENT_AT)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(SENT_AT, bindMarker(SENT_AT)));

        insertReceivedAt = session.prepare(insertInto(TABLE_NAME_RECEIVED_AT)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(RECEIVED_AT, bindMarker(RECEIVED_AT))
            .value(SENT_AT, bindMarker(SENT_AT)));

        deleteLookupRecord = session.prepare(QueryBuilder.delete()
            .from(DATE_LOOKUP_TABLE)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));

        deleteSentAt = session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME_SENT_AT)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(MESSAGE_ID, bindMarker(MESSAGE_ID)))
            .and(eq(SENT_AT, bindMarker(SENT_AT))));

        deleteReceivedAt = session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME_RECEIVED_AT)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(MESSAGE_ID, bindMarker(MESSAGE_ID)))
            .and(eq(RECEIVED_AT, bindMarker(RECEIVED_AT))));

        deleteAllLookupRecords = session.prepare(QueryBuilder.delete()
            .from(DATE_LOOKUP_TABLE)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));

        deleteAllSentAt = session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME_SENT_AT)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));

        deleteAllReceivedAt = session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME_RECEIVED_AT)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));

        lookupDate = session.prepare(select().from(DATE_LOOKUP_TABLE)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    @Override
    public Flux<MessageId> listMailboxContent(MailboxId mailboxId, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        CassandraId cassandraId = (CassandraId) mailboxId;
        return executor.executeRows(listMailboxContentBySentAt.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setInt(LIMIT_MARKER, limit.getLimit().get()))
            .map(row -> messageIdFactory.of(row.getUUID(MESSAGE_ID)));
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceReceivedAt(MailboxId mailboxId, ZonedDateTime since, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        Date sinceDate = Date.from(since.toInstant());
        CassandraId cassandraId = (CassandraId) mailboxId;

        return executor.executeRows(listMailboxContentSinceReceivedAt.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setTimestamp(RECEIVED_AT, sinceDate))
            .map(row -> {
                CassandraMessageId messageId = messageIdFactory.of(row.getUUID(MESSAGE_ID));
                Date receivedAt = row.getTimestamp(RECEIVED_AT);
                Date sentAt = row.getTimestamp(SENT_AT);

                return new Entry(cassandraId, messageId,
                    ZonedDateTime.ofInstant(sentAt.toInstant(), ZoneOffset.UTC),
                    ZonedDateTime.ofInstant(receivedAt.toInstant(), ZoneOffset.UTC));
            })
            .sort(Comparator.comparing(Entry::getSentAt).reversed())
            .map(Entry::getMessageId)
            .take(limit.getLimit().get());
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceSentAt(MailboxId mailboxId, ZonedDateTime since, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        Date sinceDate = Date.from(since.toInstant());
        CassandraId cassandraId = (CassandraId) mailboxId;

        return executor.executeRows(listMailboxContentSinceSentAt.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setInt(LIMIT_MARKER, limit.getLimit().get())
            .setTimestamp(SENT_AT, sinceDate))
            .map(row -> messageIdFactory.of(row.getUUID(MESSAGE_ID)));
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId, MessageId messageId) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        CassandraId cassandraId = (CassandraId) mailboxId;

        return executor.executeSingleRow(lookupDate.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setUUID(MESSAGE_ID, cassandraMessageId.get()))
            .flatMap(row -> doDelete(cassandraMessageId, cassandraId, row));
    }

    public Mono<? extends Void> doDelete(CassandraMessageId cassandraMessageId, CassandraId cassandraId, Row row) {
        Date receivedAt = row.getTimestamp(RECEIVED_AT);
        Date sentAt = row.getTimestamp(SENT_AT);

        BatchStatement batchStatement = new BatchStatement();
        batchStatement.add(deleteLookupRecord.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setUUID(MESSAGE_ID, cassandraMessageId.get()));
        batchStatement.add(deleteSentAt.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setUUID(MESSAGE_ID, cassandraMessageId.get())
            .setTimestamp(SENT_AT, sentAt));
        batchStatement.add(deleteReceivedAt.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setUUID(MESSAGE_ID, cassandraMessageId.get())
            .setTimestamp(RECEIVED_AT, receivedAt));

        return executor.executeVoid(batchStatement);
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        BatchStatement batchStatement = new BatchStatement();
        batchStatement.add(deleteAllLookupRecords.bind()
            .setUUID(MAILBOX_ID, ((CassandraId) mailboxId).asUuid()));
        batchStatement.add(deleteAllReceivedAt.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid()));
        batchStatement.add(deleteAllSentAt.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid()));

        return executor.executeVoid(batchStatement);
    }

    @Override
    public Mono<Void> save(MailboxId mailboxId, ZonedDateTime sentAt, ZonedDateTime receivedAt, MessageId messageId) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        CassandraId cassandraId = (CassandraId) mailboxId;
        Date sentAtDate = Date.from(sentAt.toInstant());
        Date receivedAtDate = Date.from(receivedAt.toInstant());

        BatchStatement batchStatement = new BatchStatement();
        batchStatement.add(insertInLookupTable.bind()
            .setUUID(MESSAGE_ID, cassandraMessageId.get())
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setTimestamp(RECEIVED_AT, receivedAtDate)
            .setTimestamp(SENT_AT, sentAtDate));
        batchStatement.add(insertSentAt.bind()
            .setUUID(MESSAGE_ID, cassandraMessageId.get())
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setTimestamp(SENT_AT, sentAtDate));
        batchStatement.add(insertReceivedAt.bind()
            .setUUID(MESSAGE_ID, cassandraMessageId.get())
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setTimestamp(RECEIVED_AT, receivedAtDate)
            .setTimestamp(SENT_AT, sentAtDate));

        return executor.executeVoid(batchStatement);
    }
}
