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
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxRecentsTable;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.collect.Lists;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxRecentsDAO {
    private static final int BATCH_STATEMENT_WINDOW = 1024;
    private static final int LOW_CONCURRENCY = 2;

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement readStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement deleteAllStatement;
    private final PreparedStatement addStatement;

    @Inject
    public CassandraMailboxRecentsDAO(Session session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        readStatement = createReadStatement(session);
        deleteStatement = createDeleteStatement(session);
        deleteAllStatement = createDeleteAllStatement(session);
        addStatement = createAddStatement(session);
    }

    private PreparedStatement createReadStatement(Session session) {
        return session.prepare(
            select(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID)
                .from(CassandraMailboxRecentsTable.TABLE_NAME)
                .where(eq(CassandraMailboxRecentsTable.MAILBOX_ID, bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID))));
    }

    private PreparedStatement createDeleteStatement(Session session) {
        return session.prepare(
            QueryBuilder.delete()
                .from(CassandraMailboxRecentsTable.TABLE_NAME)
                .where(eq(CassandraMailboxRecentsTable.MAILBOX_ID, bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID)))
                .and(eq(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, bindMarker(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID))));
    }

    private PreparedStatement createDeleteAllStatement(Session session) {
        return session.prepare(
            QueryBuilder.delete()
                .from(CassandraMailboxRecentsTable.TABLE_NAME)
                .where(eq(CassandraMailboxRecentsTable.MAILBOX_ID, bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID))));
    }

    private PreparedStatement createAddStatement(Session session) {
        return session.prepare(
            insertInto(CassandraMailboxRecentsTable.TABLE_NAME)
                .value(CassandraMailboxRecentsTable.MAILBOX_ID, bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID))
                .value(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, bindMarker(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID)));
    }

    public Flux<MessageUid> getRecentMessageUidsInMailbox(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeRows(bindWithMailbox(mailboxId, readStatement))
            .map(row -> row.getLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID))
            .map(MessageUid::of);
    }

    private BoundStatement bindWithMailbox(CassandraId mailboxId, PreparedStatement statement) {
        return statement.bind()
            .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid());
    }

    public Mono<Void> removeFromRecent(CassandraId mailboxId, MessageUid messageUid) {
        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
            .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, messageUid.asLong()));
    }

    public Mono<Void> removeFromRecent(CassandraId mailboxId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
                .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
                .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
                    uidBatch.forEach(uid -> batch.add(deleteStatement.bind()
                        .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
                        .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, uid.asLong())));
                    return batch;
                });
            return Flux.fromStream(batches)
                .flatMap(cassandraAsyncExecutor::executeVoid, LOW_CONCURRENCY)
                .then();
        }
    }

    public Mono<Void> delete(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(deleteAllStatement.bind()
            .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Void> addToRecent(CassandraId mailboxId, MessageUid messageUid) {
        return cassandraAsyncExecutor.executeVoid(addStatement.bind()
            .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
            .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, messageUid.asLong()));
    }

    public Mono<Void> addToRecent(CassandraId mailboxId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(addStatement.bind()
                .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
                .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
                    uidBatch.forEach(uid -> batch.add(addStatement.bind()
                        .setUUID(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
                        .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, uid.asLong())));
                    return batch;
                });
            return Flux.fromStream(batches)
                .flatMap(cassandraAsyncExecutor::executeVoid, LOW_CONCURRENCY)
                .then();
        }
    }
}
