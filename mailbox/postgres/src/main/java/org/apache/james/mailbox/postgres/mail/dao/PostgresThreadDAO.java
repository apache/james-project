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

import static org.apache.james.backends.postgres.PostgresCommons.IN_CLAUSE_MAX_SIZE;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.HASH_BASE_SUBJECT;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.HASH_MIME_MESSAGE_ID;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.MESSAGE_ID;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.TABLE_NAME;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.THREAD_ID;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.USERNAME;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.jooq.Record;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresThreadDAO {
    public static class Factory {
        private final PostgresExecutor.Factory executorFactory;

        @Inject
        @Singleton
        public Factory(PostgresExecutor.Factory executorFactory) {
            this.executorFactory = executorFactory;
        }

        public PostgresThreadDAO create(Optional<Domain> domain) {
            return new PostgresThreadDAO(executorFactory.create(domain));
        }
    }

    private final PostgresExecutor postgresExecutor;

    public PostgresThreadDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> insertSome(Username username, Set<Integer> hashMimeMessageIds, PostgresMessageId messageId, ThreadId threadId, Optional<Integer> hashBaseSubject) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.batch(
            hashMimeMessageIds.stream().map(hashMimeMessageId -> dslContext.insertInto(TABLE_NAME)
                .set(USERNAME, username.asString())
                .set(HASH_MIME_MESSAGE_ID, hashMimeMessageId)
                .set(MESSAGE_ID, messageId.asUuid())
                .set(THREAD_ID, ((PostgresMessageId) threadId.getBaseMessageId()).asUuid())
                .set(HASH_BASE_SUBJECT, hashBaseSubject.orElse(null)))
                .collect(ImmutableList.toImmutableList()))));
    }

    public Flux<Pair<Optional<Integer>, ThreadId>> findThreads(Username username, Set<Integer> hashMimeMessageIds) {
        Function<Collection<Integer>, Flux<Pair<Optional<Integer>, ThreadId>>> function = hashMimeMessageIdSubSet ->
            postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(THREAD_ID, HASH_BASE_SUBJECT)
                    .from(TABLE_NAME)
                    .where(USERNAME.eq(username.asString()))
                    .and(HASH_MIME_MESSAGE_ID.in(hashMimeMessageIdSubSet))))
                .map(this::readRecord);

        if (hashMimeMessageIds.size() <= IN_CLAUSE_MAX_SIZE) {
            return function.apply(hashMimeMessageIds);
        } else {
            return Flux.fromIterable(Iterables.partition(hashMimeMessageIds, IN_CLAUSE_MAX_SIZE))
                .flatMap(function);
        }
    }

    public Flux<MessageId> findMessageIds(ThreadId threadId, Username username) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_ID)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString()))
                .and(THREAD_ID.eq(PostgresMessageId.class.cast(threadId.getBaseMessageId()).asUuid()))
                .orderBy(MESSAGE_ID)))
            .map(record -> PostgresMessageId.Factory.of(record.get(MESSAGE_ID)));
    }

    public Pair<Optional<Integer>, ThreadId> readRecord(Record record) {
        return Pair.of(Optional.ofNullable(record.get(HASH_BASE_SUBJECT)),
            ThreadId.fromBaseMessageId(PostgresMessageId.Factory.of(record.get(THREAD_ID))));
    }

    public Mono<Void> deleteSome(Username username, PostgresMessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()))
            .and(MESSAGE_ID.eq(messageId.asUuid()))));
    }
}
