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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Assignment;

public class CassandraMailboxCounterDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement getMessageCountStatement;
    private final PreparedStatement getUnseenCountStatement;
    private final PreparedStatement incrementUnseenCountStatement;
    private final PreparedStatement incrementMessageCountStatement;
    private final PreparedStatement decrementUnseenCountStatement;
    private final PreparedStatement decrementMessageCountStatement;

    public CassandraMailboxCounterDAO(Session session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        getMessageCountStatement = session.prepare(select(CassandraMailboxCountersTable.COUNT)
            .from(CassandraMailboxCountersTable.TABLE_NAME)
            .where(eq(CassandraMailboxCountersTable.MAILBOX_ID, bindMarker(CassandraMailboxCountersTable.MAILBOX_ID))));
        getUnseenCountStatement = session.prepare(select(CassandraMailboxCountersTable.UNSEEN)
            .from(CassandraMailboxCountersTable.TABLE_NAME)
            .where(eq(CassandraMailboxCountersTable.MAILBOX_ID, bindMarker(CassandraMailboxCountersTable.MAILBOX_ID))));
        incrementMessageCountStatement = updateMailboxStatement(session, incr(CassandraMailboxCountersTable.COUNT));
        incrementUnseenCountStatement = updateMailboxStatement(session, incr(CassandraMailboxCountersTable.UNSEEN));
        decrementMessageCountStatement = updateMailboxStatement(session, decr(CassandraMailboxCountersTable.COUNT));
        decrementUnseenCountStatement = updateMailboxStatement(session, decr(CassandraMailboxCountersTable.UNSEEN));
    }

    private PreparedStatement updateMailboxStatement(Session session, Assignment operation) {
        return session.prepare(
            update(CassandraMailboxCountersTable.TABLE_NAME)
                .with(operation)
                .where(eq(CassandraMailboxCountersTable.MAILBOX_ID, bindMarker(CassandraMailboxCountersTable.MAILBOX_ID))));
    }

    public CompletableFuture<Optional<Long>> countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        return cassandraAsyncExecutor.executeSingleRow(bindWithMailbox(mailboxId, getMessageCountStatement))
            .thenApply(optional -> optional.map(row -> row.getLong(CassandraMailboxCountersTable.COUNT)));
    }

    public CompletableFuture<Optional<Long>> countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();

        return cassandraAsyncExecutor.executeSingleRow(bindWithMailbox(mailboxId, getUnseenCountStatement))
            .thenApply(optional -> optional.map(row -> row.getLong(CassandraMailboxCountersTable.UNSEEN)));
    }

    public CompletableFuture<Void> decrementCount(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, decrementMessageCountStatement));
    }

    public CompletableFuture<Void> incrementCount(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, incrementMessageCountStatement));
    }

    public CompletableFuture<Void> decrementUnseen(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, decrementUnseenCountStatement));
    }

    public CompletableFuture<Void> incrementUnseen(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(bindWithMailbox(mailboxId, incrementUnseenCountStatement));
    }

    private BoundStatement bindWithMailbox(CassandraId mailboxId, PreparedStatement statement) {
        return statement.bind()
            .setUUID(CassandraMailboxCountersTable.MAILBOX_ID, mailboxId.asUuid());
    }
}
