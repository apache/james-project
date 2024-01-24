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

package org.apache.james.mailbox.postgres.mail.dao;

import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.HASH_BASE_SUBJECT;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.HASH_MIME_MESSAGE_ID;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.MESSAGE_ID;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.TABLE_NAME;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.THREAD_ID;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.USERNAME;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.jooq.Record;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresThreadDAO {
    private final PostgresExecutor postgresExecutor;

    public PostgresThreadDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> insertSome(Username username, Set<Integer> hashMimeMessageIds, MessageId messageId, ThreadId threadId, Optional<Integer> hashBaseSubject) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.batch(
            hashMimeMessageIds.stream().map(hashMimeMessageId -> dslContext.insertInto(TABLE_NAME)
                .set(USERNAME, username.asString())
                .set(HASH_MIME_MESSAGE_ID, hashMimeMessageId)
                .set(MESSAGE_ID, ((PostgresMessageId) messageId).asUuid())
                .set(THREAD_ID, ((PostgresMessageId) threadId.getBaseMessageId()).asUuid())
                .set(HASH_BASE_SUBJECT, hashBaseSubject.orElse(null)))
                .collect(ImmutableList.toImmutableList()))));
    }

    public Flux<Pair<Optional<Integer>, ThreadId>> findThreads(Username username, Set<Integer> hashMimeMessageIds) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(THREAD_ID, HASH_BASE_SUBJECT)
            .from(TABLE_NAME)
            .where(USERNAME.eq(username.asString()))
            .and(HASH_MIME_MESSAGE_ID.in(hashMimeMessageIds))))
            .map(this::readRecord);
    }

    public Pair<Optional<Integer>, ThreadId> readRecord(Record record) {
        return Pair.of(Optional.ofNullable(record.get(HASH_BASE_SUBJECT)),
            ThreadId.fromBaseMessageId(PostgresMessageId.Factory.of(record.get(THREAD_ID))));
    }

    public Mono<Void> deleteSome(PostgresMessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MESSAGE_ID.eq(messageId.asUuid()))));
    }
}
