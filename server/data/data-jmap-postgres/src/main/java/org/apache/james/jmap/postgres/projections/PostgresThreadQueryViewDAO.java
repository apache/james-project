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

import static org.apache.james.backends.postgres.utils.PostgresExecutor.EAGER_FETCH;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.MAILBOX_ID;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.MESSAGE_ID;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.PK_CONSTRAINT_NAME;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.RECEIVED_AT;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.SENT_AT;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.TABLE_NAME;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.THREAD_ID;

import java.time.ZonedDateTime;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.PostgresMessageModule;
import org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule;
import org.apache.james.util.streams.Limit;
import org.jooq.impl.DSL;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresThreadQueryViewDAO {
    private PostgresExecutor postgresExecutor;

    @Inject
    public PostgresThreadQueryViewDAO(@Named(PostgresExecutor.BY_PASS_RLS_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Flux<ThreadId> listLatestThreadIdsSortedByReceivedAt(PostgresMailboxId mailboxId, Limit limit) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext
                .select(THREAD_ID, DSL.max(PostgresMessageModule.MessageToMailboxTable.INTERNAL_DATE).as("latest_date")) // Select thread_id and max internal_date
                .from(PostgresMessageModule.MessageToMailboxTable.TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid())) // Filter by mailboxId
                .groupBy(THREAD_ID) // Group by thread_id
                .orderBy(DSL.field("latest_date").desc()) // Order by latest_date in descending order
                .limit(limit.getLimit().get())
        ), EAGER_FETCH).map(record ->
                ThreadId.fromBaseMessageId(PostgresMessageId.Factory.of(record.get(PostgresThreadModule.PostgresThreadTable.THREAD_ID))));
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
