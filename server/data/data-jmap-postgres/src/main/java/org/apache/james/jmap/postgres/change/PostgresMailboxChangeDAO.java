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

package org.apache.james.jmap.postgres.change;

import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.ACCOUNT_ID;
import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.CREATED;
import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.DATE;
import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.DESTROYED;
import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.IS_COUNT_CHANGE;
import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.IS_SHARED;
import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.STATE;
import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.TABLE_NAME;
import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.UPDATED;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.jmap.api.change.MailboxChange;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.jooq.Record;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailboxChangeDAO {
    private final PostgresExecutor postgresExecutor;

    public PostgresMailboxChangeDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> insert(MailboxChange change) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(ACCOUNT_ID, change.getAccountId().getIdentifier())
            .set(STATE, change.getState().getValue())
            .set(IS_SHARED, change.isShared())
            .set(IS_COUNT_CHANGE, change.isCountChange())
            .set(CREATED, toUUIDArray(change.getCreated()))
            .set(UPDATED, toUUIDArray(change.getUpdated()))
            .set(DESTROYED, toUUIDArray(change.getDestroyed()))
            .set(DATE, change.getDate().toOffsetDateTime())));
    }

    public Flux<MailboxChange> getAllChanges(AccountId accountId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
            .where(ACCOUNT_ID.eq(accountId.getIdentifier()))))
            .map(record -> readRecord(record, accountId));
    }

    public Flux<MailboxChange> getChangesSince(AccountId accountId, State state) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
                .where(ACCOUNT_ID.eq(accountId.getIdentifier()))
                .and(STATE.greaterOrEqual(state.getValue()))
                .orderBy(STATE)))
            .map(record -> readRecord(record, accountId));
    }

    public Mono<State> latestState(AccountId accountId) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(STATE)
                .from(TABLE_NAME)
                .where(ACCOUNT_ID.eq(accountId.getIdentifier()))
                .orderBy(STATE.desc())
                .limit(1)))
            .map(record -> State.of(record.get(STATE)));
    }

    public Mono<State> latestStateNotDelegated(AccountId accountId) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(STATE)
                .from(TABLE_NAME)
                .where(ACCOUNT_ID.eq(accountId.getIdentifier()))
                .and(IS_SHARED.eq(false))
                .orderBy(STATE.desc())
                .limit(1)))
            .map(record -> State.of(record.get(STATE)));
    }

    private UUID[] toUUIDArray(List<MailboxId> mailboxIds) {
        return mailboxIds.stream()
            .map(PostgresMailboxId.class::cast)
            .map(PostgresMailboxId::asUuid)
            .toArray(UUID[]::new);
    }

    private MailboxChange readRecord(Record record, AccountId accountId) {
        return MailboxChange.builder()
            .accountId(accountId)
            .state(State.of(record.get(STATE)))
            .date(record.get(DATE).toZonedDateTime())
            .isCountChange(record.get(IS_COUNT_CHANGE))
            .shared(record.get(IS_SHARED))
            .created(toMailboxIds(record.get(CREATED)))
            .updated(toMailboxIds(record.get(UPDATED)))
            .destroyed(toMailboxIds(record.get(DESTROYED)))
            .build();
    }

    private List<MailboxId> toMailboxIds(UUID[] uuids) {
        return Arrays.stream(uuids)
            .map(PostgresMailboxId::of)
            .collect(ImmutableList.toImmutableList());
    }
}
