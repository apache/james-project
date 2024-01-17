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

import static org.apache.james.jmap.postgres.change.PostgresEmailChangeModule.PostgresEmailChangeTable.ACCOUNT_ID;
import static org.apache.james.jmap.postgres.change.PostgresEmailChangeModule.PostgresEmailChangeTable.CREATED;
import static org.apache.james.jmap.postgres.change.PostgresEmailChangeModule.PostgresEmailChangeTable.DATE;
import static org.apache.james.jmap.postgres.change.PostgresEmailChangeModule.PostgresEmailChangeTable.DESTROYED;
import static org.apache.james.jmap.postgres.change.PostgresEmailChangeModule.PostgresEmailChangeTable.IS_SHARED;
import static org.apache.james.jmap.postgres.change.PostgresEmailChangeModule.PostgresEmailChangeTable.STATE;
import static org.apache.james.jmap.postgres.change.PostgresEmailChangeModule.PostgresEmailChangeTable.TABLE_NAME;
import static org.apache.james.jmap.postgres.change.PostgresEmailChangeModule.PostgresEmailChangeTable.UPDATED;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.jmap.api.change.EmailChange;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.jooq.Record;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresEmailChangeDAO {
    private final PostgresExecutor postgresExecutor;

    public PostgresEmailChangeDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> insert(EmailChange change) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(ACCOUNT_ID, change.getAccountId().getIdentifier())
            .set(STATE, change.getState().getValue())
            .set(IS_SHARED, change.isShared())
            .set(CREATED, convertToUUIDArray(change.getCreated()))
            .set(UPDATED, convertToUUIDArray(change.getUpdated()))
            .set(DESTROYED, convertToUUIDArray(change.getDestroyed()))
            .set(DATE, change.getDate().toOffsetDateTime())));
    }

    public Flux<EmailChange> getAllChanges(AccountId accountId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
            .where(ACCOUNT_ID.eq(accountId.getIdentifier()))))
            .map(record -> readRecord(record, accountId));
    }

    public Flux<EmailChange> getChangesSince(AccountId accountId, State state) {
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

    private UUID[] convertToUUIDArray(List<MessageId> messageIds) {
        return messageIds.stream().map(PostgresMessageId.class::cast).map(PostgresMessageId::asUuid).toArray(UUID[]::new);
    }

    private EmailChange readRecord(Record record, AccountId accountId) {
        return EmailChange.builder()
            .accountId(accountId)
            .state(State.of(record.get(STATE)))
            .date(record.get(DATE).toZonedDateTime())
            .isShared(record.get(IS_SHARED))
            .created(convertToMessageIdList(record.get(CREATED)))
            .updated(convertToMessageIdList(record.get(UPDATED)))
            .destroyed(convertToMessageIdList(record.get(DESTROYED)))
            .build();
    }

    private List<MessageId> convertToMessageIdList(UUID[] uuids) {
        return Arrays.stream(uuids).map(PostgresMessageId.Factory::of).collect(ImmutableList.toImmutableList());
    }
}
