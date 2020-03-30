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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.decr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.incr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable.COUNT;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable.UNSEEN;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Assignment;

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

    @Inject
    public CassandraMailboxCounterDAO(Session session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        readStatement = createReadStatement(session);
        incrementMessageCountStatement = updateMailboxStatement(session, incr(COUNT));
        incrementUnseenCountStatement = updateMailboxStatement(session, incr(UNSEEN));
        addToCounters = session.prepare(update(TABLE_NAME)
            .with(incr(COUNT, bindMarker(COUNT)))
            .and(incr(UNSEEN, bindMarker(UNSEEN)))
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
        removeToCounters = session.prepare(update(TABLE_NAME)
            .with(decr(COUNT, bindMarker(COUNT)))
            .and(decr(UNSEEN, bindMarker(UNSEEN)))
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
        decrementMessageCountStatement = updateMailboxStatement(session, decr(COUNT));
        decrementUnseenCountStatement = updateMailboxStatement(session, decr(UNSEEN));
        incrementUnseenAndCountStatement =  session.prepare(update(TABLE_NAME)
            .with(incr(COUNT))
            .and(incr(UNSEEN))
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
        decrementUnseenAndCountStatement = session.prepare(update(TABLE_NAME)
            .with(decr(COUNT))
            .and(decr(UNSEEN))
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement createReadStatement(Session session) {
        return session.prepare(
            select(UNSEEN, COUNT)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement updateMailboxStatement(Session session, Assignment operation) {
        return session.prepare(
            update(TABLE_NAME)
                .with(operation)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    public Mono<MailboxCounters> retrieveMailboxCounters(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRow(bindWithMailbox(mailboxId, readStatement))
            .map(row ->  MailboxCounters.builder()
                .mailboxId(mailboxId)
                .count(row.getLong(COUNT))
                .unseen(row.getLong(UNSEEN))
                .build());
    }

    public Mono<Void> resetCounters(MailboxCounters counters) {
        CassandraId mailboxId = (CassandraId) counters.getMailboxId();

        return retrieveMailboxCounters(mailboxId)
            .defaultIfEmpty(emptyCounters(mailboxId))
            .flatMap(storedCounters -> {
                if (storedCounters.equals(counters)) {
                    return Mono.empty();
                }
                return remove(storedCounters)
                    .then(add(counters));
            });
    }

    private MailboxCounters emptyCounters(CassandraId mailboxId) {
        return MailboxCounters.builder()
            .count(0)
            .unseen(0)
            .mailboxId(mailboxId)
            .build();
    }

    private Mono<Void> add(MailboxCounters counters) {
        CassandraId mailboxId = (CassandraId) counters.getMailboxId();
        return cassandraAsyncExecutor.executeVoid(
            bindWithMailbox(mailboxId, addToCounters)
                .setLong(COUNT, counters.getCount())
                .setLong(UNSEEN, counters.getUnseen()));
    }

    private Mono<Void> remove(MailboxCounters counters) {
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

    private BoundStatement bindWithMailbox(CassandraId mailboxId, PreparedStatement statement) {
        return statement.bind()
            .setUUID(MAILBOX_ID, mailboxId.asUuid());
    }
}
