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

import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.MAILBOX_ID;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.MESSAGE_ID;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.PK_CONSTRAINT_NAME;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.RECEIVED_AT;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.SENT_AT;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.TABLE_NAME;

import java.time.ZonedDateTime;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.util.streams.Limit;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresEmailQueryViewDAO {
    private PostgresExecutor postgresExecutor;

    @Inject
    public PostgresEmailQueryViewDAO(@Named(PostgresExecutor.NON_RLS_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Flux<MessageId> listMailboxContentSortedBySentAt(PostgresMailboxId mailboxId, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .orderBy(SENT_AT.desc())
                .limit(limit.getLimit().get())))
            .map(record -> PostgresMessageId.Factory.of(record.get(MESSAGE_ID)));
    }

    public Flux<MessageId> listMailboxContentSortedByReceivedAt(PostgresMailboxId mailboxId, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .orderBy(RECEIVED_AT.desc())
                .limit(limit.getLimit().get())))
            .map(record -> PostgresMessageId.Factory.of(record.get(MESSAGE_ID)));
    }

    public Flux<MessageId> listMailboxContentSinceAfterSortedBySentAt(PostgresMailboxId mailboxId, ZonedDateTime since, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(RECEIVED_AT.greaterOrEqual(since.toOffsetDateTime()))
                .orderBy(SENT_AT.desc())
                .limit(limit.getLimit().get())))
            .map(record -> PostgresMessageId.Factory.of(record.get(MESSAGE_ID)));
    }

    public Flux<MessageId> listMailboxContentSinceAfterSortedByReceivedAt(PostgresMailboxId mailboxId, ZonedDateTime since, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(RECEIVED_AT.greaterOrEqual(since.toOffsetDateTime()))
                .orderBy(RECEIVED_AT.desc())
                .limit(limit.getLimit().get())))
            .map(record -> PostgresMessageId.Factory.of(record.get(MESSAGE_ID)));
    }

    public Flux<MessageId> listMailboxContentBeforeSortedByReceivedAt(PostgresMailboxId mailboxId, ZonedDateTime since, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(RECEIVED_AT.lessOrEqual(since.toOffsetDateTime()))
                .orderBy(RECEIVED_AT.desc())
                .limit(limit.getLimit().get())))
            .map(record -> PostgresMessageId.Factory.of(record.get(MESSAGE_ID)));
    }

    public Flux<MessageId> listMailboxContentSinceSentAt(PostgresMailboxId mailboxId, ZonedDateTime since, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(SENT_AT.greaterOrEqual(since.toOffsetDateTime()))
                .orderBy(SENT_AT.desc())
                .limit(limit.getLimit().get())))
            .map(record -> PostgresMessageId.Factory.of(record.get(MESSAGE_ID)));
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

    public Mono<Void> save(PostgresMailboxId mailboxId, ZonedDateTime sentAt, ZonedDateTime receivedAt, PostgresMessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(MAILBOX_ID, mailboxId.asUuid())
            .set(MESSAGE_ID, messageId.asUuid())
            .set(SENT_AT, sentAt.toOffsetDateTime())
            .set(RECEIVED_AT, receivedAt.toOffsetDateTime())
            .onConflictOnConstraint(PK_CONSTRAINT_NAME)
            .doNothing()));
    }
}
