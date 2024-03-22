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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;

import java.util.List;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxRecentsTable;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
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
    private final ProtocolVersion protocolVersion;

    @Inject
    public CassandraMailboxRecentsDAO(CqlSession session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        readStatement = createReadStatement(session);
        deleteStatement = createDeleteStatement(session);
        deleteAllStatement = createDeleteAllStatement(session);
        addStatement = createAddStatement(session);
        protocolVersion = session.getContext().getProtocolVersion();
    }

    private PreparedStatement createReadStatement(CqlSession session) {
        return session.prepare(
            selectFrom(CassandraMailboxRecentsTable.TABLE_NAME)
                .column(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID)
                .where(column(CassandraMailboxRecentsTable.MAILBOX_ID).isEqualTo(bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID)))
                .build());
    }

    private PreparedStatement createDeleteStatement(CqlSession session) {
        return session.prepare(
            deleteFrom(CassandraMailboxRecentsTable.TABLE_NAME)
                .whereColumn(CassandraMailboxRecentsTable.MAILBOX_ID).isEqualTo(bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID))
                .whereColumn(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID).isEqualTo(bindMarker(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID))
                .build());
    }

    private PreparedStatement createDeleteAllStatement(CqlSession session) {
        return session.prepare(
            deleteFrom(CassandraMailboxRecentsTable.TABLE_NAME)
                .where(column(CassandraMailboxRecentsTable.MAILBOX_ID).isEqualTo(bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID)))
                .build());
    }

    private PreparedStatement createAddStatement(CqlSession session) {
        return session.prepare(
            insertInto(CassandraMailboxRecentsTable.TABLE_NAME)
                .value(CassandraMailboxRecentsTable.MAILBOX_ID, bindMarker(CassandraMailboxRecentsTable.MAILBOX_ID))
                .value(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, bindMarker(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID))
                .build());
    }

    public Flux<MessageUid> getRecentMessageUidsInMailbox(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeRows(bindWithMailbox(mailboxId, readStatement))
            .map(row -> TypeCodecs.BIGINT.decodePrimitive(row.getBytesUnsafe(0), protocolVersion))
            .map(MessageUid::of);
    }

    private BoundStatement bindWithMailbox(CassandraId mailboxId, PreparedStatement statement) {
        return statement.bind()
            .set(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID);
    }

    public Mono<Void> removeFromRecent(CassandraId mailboxId, MessageUid messageUid) {
        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUuid(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
            .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, messageUid.asLong()));
    }

    public Mono<Void> removeFromRecent(CassandraId mailboxId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
                .setUuid(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
                .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatementBuilder batch = new BatchStatementBuilder(BatchType.UNLOGGED);
                    uidBatch.forEach(uid -> batch.addStatement(deleteStatement.bind()
                        .setUuid(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
                        .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, uid.asLong())));
                    return batch.build();
                });
            return Flux.fromStream(batches)
                .flatMap(cassandraAsyncExecutor::executeVoid, LOW_CONCURRENCY)
                .then();
        }
    }

    public Mono<Void> delete(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(deleteAllStatement.bind()
            .setUuid(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Void> addToRecent(CassandraId mailboxId, MessageUid messageUid) {
        return cassandraAsyncExecutor.executeVoid(addStatement.bind()
            .setUuid(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
            .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, messageUid.asLong()));
    }

    public Mono<Void> addToRecent(CassandraId mailboxId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(addStatement.bind()
                .setUuid(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
                .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatementBuilder batch = new BatchStatementBuilder(BatchType.UNLOGGED);
                    uidBatch.forEach(uid -> batch.addStatement(addStatement.bind()
                        .setUuid(CassandraMailboxRecentsTable.MAILBOX_ID, mailboxId.asUuid())
                        .setLong(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, uid.asLong())));
                    return batch.build();
                });
            return Flux.fromStream(batches)
                .flatMap(cassandraAsyncExecutor::executeVoid, LOW_CONCURRENCY)
                .then();
        }
    }
}
