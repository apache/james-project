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

package org.apache.james.jmap.postgres.projections;

import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewDataDefinition.PostgresEmailQueryViewTable.MAILBOX_ID;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewDataDefinition.PostgresEmailQueryViewTable.MESSAGE_ID;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewDataDefinition.PostgresEmailQueryViewTable.PK_CONSTRAINT_NAME;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewDataDefinition.PostgresEmailQueryViewTable.RECEIVED_AT;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewDataDefinition.PostgresEmailQueryViewTable.SENT_AT;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewDataDefinition.PostgresEmailQueryViewTable.TABLE_NAME;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewDataDefinition.PostgresEmailQueryViewTable.THREAD_ID;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.jmap.api.projections.EmailQueryViewUtils;
import org.apache.james.jmap.api.projections.EmailQueryViewUtils.EmailEntry;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.util.streams.Limit;
import org.jooq.Field;
import org.jooq.Record;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresEmailQueryViewDAO {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresEmailQueryViewDAO(@Named(PostgresExecutor.BY_PASS_RLS_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Flux<MessageId> listMailboxContentSortedBySentAt(PostgresMailboxId mailboxId, Limit limit, boolean collapseThreads) {
        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID, SENT_AT, THREAD_ID)
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                    .orderBy(SENT_AT.desc())
                    .limit(backendFetchLimit.getLimit().get())))
                .map(asEmailEntry(SENT_AT)));
    }

    public Flux<MessageId> listMailboxContentSortedByReceivedAt(PostgresMailboxId mailboxId, Limit limit, boolean collapseThreads) {
        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID, RECEIVED_AT, THREAD_ID)
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                    .orderBy(RECEIVED_AT.desc())
                    .limit(backendFetchLimit.getLimit().get())))
                .map(asEmailEntry(RECEIVED_AT)));
    }

    public Flux<MessageId> listMailboxContentSinceAfterSortedBySentAt(PostgresMailboxId mailboxId, ZonedDateTime since, Limit limit, boolean collapseThreads) {
        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID, SENT_AT, THREAD_ID)
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                    .and(RECEIVED_AT.greaterOrEqual(since.toOffsetDateTime()))
                    .orderBy(SENT_AT.desc())
                    .limit(backendFetchLimit.getLimit().get())))
                .map(asEmailEntry(SENT_AT)));
    }

    public Flux<MessageId> listMailboxContentSinceAfterSortedByReceivedAt(PostgresMailboxId mailboxId, ZonedDateTime since, Limit limit, boolean collapseThreads) {
        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID, RECEIVED_AT, THREAD_ID)
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                    .and(RECEIVED_AT.greaterOrEqual(since.toOffsetDateTime()))
                    .orderBy(RECEIVED_AT.desc())
                    .limit(backendFetchLimit.getLimit().get())))
                .map(asEmailEntry(RECEIVED_AT)));
    }

    public Flux<MessageId> listMailboxContentBeforeSortedByReceivedAt(PostgresMailboxId mailboxId, ZonedDateTime since, Limit limit, boolean collapseThreads) {
        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID, RECEIVED_AT, THREAD_ID)
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                    .and(RECEIVED_AT.lessOrEqual(since.toOffsetDateTime()))
                    .orderBy(RECEIVED_AT.desc())
                    .limit(backendFetchLimit.getLimit().get())))
                .map(asEmailEntry(RECEIVED_AT)));
    }

    public Flux<MessageId> listMailboxContentSinceSentAt(PostgresMailboxId mailboxId, ZonedDateTime since, Limit limit, boolean collapseThreads) {
        return EmailQueryViewUtils.QueryViewExtender.of(limit, collapseThreads)
            .resolve(backendFetchLimit -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID, SENT_AT, THREAD_ID)
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                    .and(SENT_AT.greaterOrEqual(since.toOffsetDateTime()))
                    .orderBy(SENT_AT.desc())
                    .limit(backendFetchLimit.getLimit().get())))
                .map(asEmailEntry(SENT_AT)));
    }

    private Function<Record, EmailEntry> asEmailEntry(Field<OffsetDateTime> dateField) {
        return (Record record) -> {
            PostgresMessageId messageId = PostgresMessageId.Factory.of(record.get(MESSAGE_ID));
            ThreadId threadId = getThreadIdFromRecord(record, messageId);
            Instant messageDate = record.get(dateField).toInstant();
            return new EmailEntry(messageId, threadId, messageDate);
        };
    }

    private ThreadId getThreadIdFromRecord(Record record, MessageId messageId) {
        UUID threadIdUUID = record.get(THREAD_ID);
        if (threadIdUUID == null) {
            return ThreadId.fromBaseMessageId(messageId);
        }
        return ThreadId.fromBaseMessageId(PostgresMessageId.Factory.of(threadIdUUID));
    }

    public Mono<Void> delete(PostgresMailboxId mailboxId, PostgresMessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))
            .and(MESSAGE_ID.eq(messageId.asUuid()))));
    }

    public Mono<Void> delete(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))));
    }

    public Mono<Void> save(PostgresMailboxId mailboxId, ZonedDateTime sentAt, ZonedDateTime receivedAt, PostgresMessageId messageId, ThreadId threadId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(MAILBOX_ID, mailboxId.asUuid())
            .set(MESSAGE_ID, messageId.asUuid())
            .set(SENT_AT, sentAt.toOffsetDateTime())
            .set(RECEIVED_AT, receivedAt.toOffsetDateTime())
            .set(THREAD_ID, ((PostgresMessageId) threadId.getBaseMessageId()).asUuid())
            .onConflictOnConstraint(PK_CONSTRAINT_NAME)
            .doNothing()));
    }
}
