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

import static com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.DESC;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.DATE_LOOKUP_TABLE;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.MAILBOX_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.MESSAGE_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.RECEIVED_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.SENT_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.TABLE_NAME_RECEIVED_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.TABLE_NAME_SENT_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.THREAD_ID;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.jmap.api.projections.EmailQueryViewUtils;
import org.apache.james.jmap.api.projections.EmailQueryViewUtils.EmailEntry;
import org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.streams.Limit;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraEmailQueryView implements EmailQueryView {
    private static final String LIMIT_MARKER = "LIMIT_BIND_MARKER";

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement listMailboxContentBySentAt;
    private final PreparedStatement listMailboxContentByReceivedAt;
    private final PreparedStatement listMailboxContentSinceSentAt;
    private final PreparedStatement listMailboxContentSinceReceivedAt;
    private final PreparedStatement listMailboxContentBeforeReceivedAt;
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
    public CassandraEmailQueryView(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);

        listMailboxContentBySentAt = session.prepare(selectFrom(TABLE_NAME_SENT_AT)
            .columns(MESSAGE_ID, SENT_AT, THREAD_ID)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .orderBy(SENT_AT, DESC)
            .limit(bindMarker(LIMIT_MARKER))
            .build());

        listMailboxContentByReceivedAt = session.prepare(selectFrom(TABLE_NAME_RECEIVED_AT)
            .columns(MESSAGE_ID, RECEIVED_AT, THREAD_ID)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .orderBy(RECEIVED_AT, DESC)
            .limit(bindMarker(LIMIT_MARKER))
            .build());

        listMailboxContentSinceSentAt = session.prepare(selectFrom(TABLE_NAME_SENT_AT)
            .columns(MESSAGE_ID, SENT_AT, THREAD_ID)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .whereColumn(SENT_AT).isGreaterThanOrEqualTo(bindMarker(SENT_AT))
            .orderBy(SENT_AT, DESC)
            .limit(bindMarker(LIMIT_MARKER))
            .build());

        listMailboxContentSinceReceivedAt = session.prepare(selectFrom(TABLE_NAME_RECEIVED_AT)
            .columns(MESSAGE_ID, SENT_AT, THREAD_ID)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .whereColumn(RECEIVED_AT).isGreaterThanOrEqualTo(bindMarker(RECEIVED_AT))
            .orderBy(RECEIVED_AT, DESC)
            .limit(bindMarker(LIMIT_MARKER))
            .build());

        listMailboxContentBeforeReceivedAt = session.prepare(selectFrom(TABLE_NAME_RECEIVED_AT)
            .columns(MESSAGE_ID, SENT_AT, THREAD_ID)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .whereColumn(RECEIVED_AT).isLessThanOrEqualTo(bindMarker(RECEIVED_AT))
            .orderBy(RECEIVED_AT, DESC)
            .limit(bindMarker(LIMIT_MARKER))
            .build());

        insertInLookupTable = session.prepare(insertInto(DATE_LOOKUP_TABLE)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(SENT_AT, bindMarker(SENT_AT))
            .value(RECEIVED_AT, bindMarker(RECEIVED_AT))
            .build());

        insertSentAt = session.prepare(insertInto(TABLE_NAME_SENT_AT)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(SENT_AT, bindMarker(SENT_AT))
            .value(THREAD_ID, bindMarker(THREAD_ID))
            .build());

        insertReceivedAt = session.prepare(insertInto(TABLE_NAME_RECEIVED_AT)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(RECEIVED_AT, bindMarker(RECEIVED_AT))
            .value(SENT_AT, bindMarker(SENT_AT))
            .value(THREAD_ID, bindMarker(THREAD_ID))
            .build());

        deleteLookupRecord = session.prepare(deleteFrom(DATE_LOOKUP_TABLE)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());

        deleteSentAt = session.prepare(deleteFrom(TABLE_NAME_SENT_AT)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .whereColumn(SENT_AT).isEqualTo(bindMarker(SENT_AT))
            .build());

        deleteReceivedAt = session.prepare(QueryBuilder.deleteFrom(TABLE_NAME_RECEIVED_AT)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .whereColumn(RECEIVED_AT).isEqualTo(bindMarker(RECEIVED_AT))
            .build());

        deleteAllLookupRecords = session.prepare(QueryBuilder.deleteFrom(DATE_LOOKUP_TABLE)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .build());

        deleteAllSentAt = session.prepare(QueryBuilder.deleteFrom(TABLE_NAME_SENT_AT)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .build());

        deleteAllReceivedAt = session.prepare(QueryBuilder.deleteFrom(TABLE_NAME_RECEIVED_AT)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .build());

        lookupDate = session.prepare(selectFrom(DATE_LOOKUP_TABLE)
            .all()
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());
    }

    @Override
    public Flux<MessageId> listMailboxContentSortedBySentAt(MailboxId mailboxId, Limit limit, boolean collapseThreads) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> executor.executeRows(listMailboxContentBySentAt.bind()
                    .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                    .setInt(LIMIT_MARKER, backendFetchLimit.getLimit().get()))
                .map(asEmailEntry(SENT_AT)));
    }

    @Override
    public Flux<MessageId> listMailboxContentSortedByReceivedAt(MailboxId mailboxId, Limit limit, boolean collapseThreads) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> executor.executeRows(listMailboxContentByReceivedAt.bind()
                    .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                    .setInt(LIMIT_MARKER, backendFetchLimit.getLimit().get()))
                .map(asEmailEntry(RECEIVED_AT)));
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceAfterSortedByReceivedAt(MailboxId mailboxId, ZonedDateTime since, Limit limit, boolean collapseThreads) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> executor.executeRows(listMailboxContentSinceReceivedAt.bind()
                    .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                    .setInstant(RECEIVED_AT, since.toInstant())
                    .setInt(LIMIT_MARKER, backendFetchLimit.getLimit().get()))
                .map(asEmailEntry(SENT_AT)));
    }

    @Override
    public Flux<MessageId> listMailboxContentBeforeSortedByReceivedAt(MailboxId mailboxId, ZonedDateTime since, Limit limit, boolean collapseThreads) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> executor.executeRows(listMailboxContentBeforeReceivedAt.bind()
                        .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                        .setInstant(RECEIVED_AT, since.toInstant())
                        .setInt(LIMIT_MARKER, backendFetchLimit.getLimit().get()))
                    .map(asEmailEntry(SENT_AT)));
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceSentAt(MailboxId mailboxId, ZonedDateTime since, Limit limit, boolean collapseThreads) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> executor.executeRows(listMailboxContentSinceSentAt.bind()
                .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                .setInstant(SENT_AT, since.toInstant())
                .setInt(LIMIT_MARKER, backendFetchLimit.getLimit().get()))
            .map(asEmailEntry(SENT_AT)));
    }

    private Function<Row, EmailEntry> asEmailEntry(CqlIdentifier dateField) {
        return (Row row) -> {
            CassandraMessageId messageId = CassandraMessageId.Factory.of(row.getUuid(MESSAGE_ID));
            ThreadId threadId = getThreadIdFromRow(row, messageId);
            Instant messageDate = row.getInstant(dateField);
            return new EmailEntry(messageId, threadId, messageDate);
        };
    }

    private ThreadId getThreadIdFromRow(Row row, MessageId messageId) {
        UUID threadIdUUID = row.get(CassandraEmailQueryViewTable.THREAD_ID, TypeCodecs.TIMEUUID);
        if (threadIdUUID == null) {
            return ThreadId.fromBaseMessageId(messageId);
        }
        return ThreadId.fromBaseMessageId(CassandraMessageId.Factory.of(threadIdUUID));
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId, MessageId messageId) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        CassandraId cassandraId = (CassandraId) mailboxId;

        return executor.executeSingleRow(lookupDate.bind()
            .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
            .setUuid(MESSAGE_ID, cassandraMessageId.get()))
            .flatMap(row -> doDelete(cassandraMessageId, cassandraId, row));
    }

    public Mono<? extends Void> doDelete(CassandraMessageId cassandraMessageId, CassandraId cassandraId, Row row) {
        Instant receivedAt = row.getInstant(RECEIVED_AT);
        Instant sentAt = row.getInstant(SENT_AT);

        return Flux.concat(
            executor.executeVoid(deleteSentAt.bind()
                .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                .setUuid(MESSAGE_ID, cassandraMessageId.get())
                .setInstant(SENT_AT, sentAt)),
            executor.executeVoid(deleteReceivedAt.bind()
                .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                .setUuid(MESSAGE_ID, cassandraMessageId.get())
                .setInstant(RECEIVED_AT, receivedAt)))
            .then(executor.executeVoid(deleteLookupRecord.bind()
                .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                .setUuid(MESSAGE_ID, cassandraMessageId.get())));
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        return Flux.concat(
            executor.executeVoid(deleteAllSentAt.bind()
                .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)),
            executor.executeVoid(deleteAllReceivedAt.bind()
                .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)))
            .then(executor.executeVoid(deleteAllLookupRecords.bind()
                .setUuid(MAILBOX_ID, ((CassandraId) mailboxId).asUuid())));
    }

    @Override
    public Mono<Void> save(MailboxId mailboxId, ZonedDateTime sentAt, ZonedDateTime receivedAt, MessageId messageId, ThreadId threadId) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        CassandraId cassandraId = (CassandraId) mailboxId;

        return executor.executeVoid(insertInLookupTable.bind()
            .setUuid(MESSAGE_ID, cassandraMessageId.get())
            .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
            .setInstant(RECEIVED_AT, receivedAt.toInstant())
            .setInstant(SENT_AT, sentAt.toInstant()))
            .then(Flux.concat(
                executor.executeVoid(insertSentAt.bind()
                    .setUuid(MESSAGE_ID, cassandraMessageId.get())
                    .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                    .setInstant(SENT_AT, sentAt.toInstant())
                    .setUuid(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())),
                executor.executeVoid(insertReceivedAt.bind()
                    .setUuid(MESSAGE_ID, cassandraMessageId.get())
                    .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.UUID)
                    .setInstant(RECEIVED_AT, receivedAt.toInstant())
                    .setInstant(SENT_AT, sentAt.toInstant())
                    .setUuid(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())))
                .then());
    }
}
