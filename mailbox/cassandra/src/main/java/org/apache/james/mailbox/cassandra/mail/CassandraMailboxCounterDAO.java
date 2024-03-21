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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.decrement;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.increment;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable.COUNT;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable.UNSEEN;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.querybuilder.update.Assignment;

import reactor.core.publisher.Mono;

public class CassandraMailboxCounterDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement readStatement;
    private final PreparedStatement incrementUnseenCountStatement;
    private final PreparedStatement incrementMessageCountStatement;
    private final PreparedStatement addToCounters;
    private final PreparedStatement removeToCounters;
    private final PreparedStatement decrementUnseenCountStatement;
    private final PreparedStatement decrementMessageCountStatement;
    private final PreparedStatement incrementUnseenAndCountStatement;
    private final PreparedStatement decrementUnseenAndCountStatement;
    private final PreparedStatement deleteStatement;

    @Inject
    public CassandraMailboxCounterDAO(CqlSession session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        readStatement = createReadStatement(session);
        incrementMessageCountStatement = updateMailboxStatement(session, increment(COUNT));
        incrementUnseenCountStatement = updateMailboxStatement(session, increment(UNSEEN));

        addToCounters = session.prepare(update(TABLE_NAME)
            .increment(COUNT, bindMarker(COUNT))
            .increment(UNSEEN, bindMarker(UNSEEN))
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());

        removeToCounters = session.prepare(update(TABLE_NAME)
            .decrement(COUNT, bindMarker(COUNT))
            .decrement(UNSEEN, bindMarker(UNSEEN))
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());

        decrementMessageCountStatement = updateMailboxStatement(session, decrement(COUNT));
        decrementUnseenCountStatement = updateMailboxStatement(session, decrement(UNSEEN));

        incrementUnseenAndCountStatement = session.prepare(update(TABLE_NAME)
            .increment(COUNT)
            .increment(UNSEEN)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());

        decrementUnseenAndCountStatement = session.prepare(update(TABLE_NAME)
            .decrement(COUNT)
            .decrement(UNSEEN)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());

        deleteStatement = session.prepare(deleteFrom(TABLE_NAME)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    private PreparedStatement createReadStatement(CqlSession session) {
        return session.prepare(
            selectFrom(TABLE_NAME)
                .columns(UNSEEN, COUNT)
                .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
                .build());
    }

    private PreparedStatement updateMailboxStatement(CqlSession session, Assignment operation) {
        return session.prepare(
            update(TABLE_NAME)
                .set(operation)
                .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
                .build());
    }

    public Mono<Void> delete(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, deleteStatement));
    }

    public Mono<MailboxCounters> retrieveMailboxCounters(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRow(bindWithMailbox(mailboxId, readStatement))
            .map(row -> MailboxCounters.builder()
                .mailboxId(mailboxId)
                .count(row.getLong(COUNT))
                .unseen(row.getLong(UNSEEN))
                .build());
    }

    public Mono<Void> resetCounters(MailboxCounters counters) {
        CassandraId mailboxId = (CassandraId) counters.getMailboxId();

        return retrieveMailboxCounters(mailboxId)
            .defaultIfEmpty(MailboxCounters.empty(mailboxId))
            .flatMap(storedCounters -> {
                if (storedCounters.equals(counters)) {
                    return Mono.empty();
                }
                return remove(storedCounters)
                    .then(add(counters));
            });
    }

    public Mono<Void> add(MailboxCounters counters) {
        CassandraId mailboxId = (CassandraId) counters.getMailboxId();
        return cassandraAsyncExecutor.executeVoid(
            bindWithMailbox(mailboxId, addToCounters)
                .setLong(COUNT, counters.getCount())
                .setLong(UNSEEN, counters.getUnseen()));
    }

    public Mono<Void> remove(MailboxCounters counters) {
        CassandraId mailboxId = (CassandraId) counters.getMailboxId();
        return cassandraAsyncExecutor.executeVoid(
            bindWithMailbox(mailboxId, removeToCounters)
                .setLong(COUNT, counters.getCount())
                .setLong(UNSEEN, counters.getUnseen()));
    }

    public Mono<Long> countMessagesInMailbox(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        return countMessagesInMailbox(mailboxId);
    }

    public Mono<Long> countMessagesInMailbox(CassandraId cassandraId) {
        return cassandraAsyncExecutor.executeSingleRow(bindWithMailbox(cassandraId, readStatement))
            .map(row -> row.getLong(COUNT));
    }

    public Mono<Long> countUnseenMessagesInMailbox(Mailbox mailbox) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        return cassandraAsyncExecutor.executeSingleRow(bindWithMailbox(mailboxId, readStatement))
            .map(row -> row.getLong(UNSEEN));
    }

    public Mono<Void> decrementCount(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, decrementMessageCountStatement));
    }

    public Mono<Void> incrementCount(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, incrementMessageCountStatement));
    }

    public Mono<Void> decrementUnseen(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, decrementUnseenCountStatement));
    }

    public Mono<Void> incrementUnseen(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, incrementUnseenCountStatement));
    }

    public Mono<Void> decrementUnseenAndCount(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, decrementUnseenAndCountStatement));
    }

    public Mono<Void> incrementUnseenAndCount(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, incrementUnseenAndCountStatement));
    }

    public Mono<Void> incrementUnseenAndCount(CassandraId mailboxId, long count, long unseen) {
        return cassandraAsyncExecutor.executeVoid(
            bindWithMailbox(mailboxId, addToCounters)
                .setLong(COUNT, count)
                .setLong(UNSEEN, unseen));
    }

    private BoundStatement bindWithMailbox(CassandraId mailboxId, PreparedStatement statement) {
        return statement.bind()
            .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID);
    }
}
