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
import static org.apache.james.mailbox.cassandra.table.CassandraFirstUnseenTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraFirstUnseenTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraFirstUnseenTable.UID;

import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.google.common.collect.Lists;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraFirstUnseenDAO {
    private static final int BATCH_STATEMENT_WINDOW = 1024;
    private static final int LOW_CONCURRENCY = 2;

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement deleteAllStatement;
    private final PreparedStatement readStatement;
    private final PreparedStatement listStatement;

    @Inject
    public CassandraFirstUnseenDAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAddStatement(session);
        this.deleteStatement = prepareDeleteStatement(session);
        this.deleteAllStatement = prepareDeleteAllStatement(session);
        this.readStatement = prepareReadStatement(session);
        this.listStatement = prepareListStatement(session);
    }

    private PreparedStatement prepareReadStatement(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(UID)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .orderBy(UID, ClusteringOrder.ASC)
            .limit(1)
            .build());
    }

    private PreparedStatement prepareListStatement(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(UID)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .orderBy(UID, ClusteringOrder.ASC)
            .build());
    }

    private PreparedStatement prepareDeleteStatement(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))
            .whereColumn(UID).isEqualTo(bindMarker(UID))
            .build());
    }

    private PreparedStatement prepareDeleteAllStatement(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    private PreparedStatement prepareAddStatement(CqlSession session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(UID, bindMarker(UID))
            .build());
    }

    public Mono<Void> addUnread(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(
            addStatement.bind()
                .setUuid(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID, uid.asLong()));
    }

    public Mono<Void> addUnread(CassandraId mailboxId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(
                addStatement.bind()
                    .setUuid(MAILBOX_ID, mailboxId.asUuid())
                    .setLong(UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatementBuilder batch = new BatchStatementBuilder(BatchType.UNLOGGED);
                    uidBatch.forEach(uid -> batch.addStatement(addStatement.bind()
                        .setUuid(MAILBOX_ID, mailboxId.asUuid())
                        .setLong(UID, uid.asLong())));
                    return batch.build();
                });
            return Flux.fromStream(batches)
                .flatMap(cassandraAsyncExecutor::executeVoid, LOW_CONCURRENCY)
                .then();
        }
    }

    public Mono<Void> removeUnread(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUuid(MAILBOX_ID, cassandraId.asUuid())
            .setLong(UID, uid.asLong()));
    }

    public Mono<Void> removeUnread(CassandraId mailboxId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
                .setUuid(MAILBOX_ID, mailboxId.asUuid())
                .setLong(UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatementBuilder batch = new BatchStatementBuilder(BatchType.UNLOGGED);
                    uidBatch.forEach(uid -> batch.addStatement(deleteStatement.bind()
                        .setUuid(MAILBOX_ID, mailboxId.asUuid())
                        .setLong(UID, uid.asLong())));
                    return batch.build();
                });
            return Flux.fromStream(batches)
                .flatMap(cassandraAsyncExecutor::executeVoid, LOW_CONCURRENCY)
                .then();
        }
    }

    public Mono<Void> removeAll(CassandraId cassandraId) {
        return cassandraAsyncExecutor.executeVoid(deleteAllStatement.bind()
            .setUuid(MAILBOX_ID, cassandraId.asUuid()));
    }

    public Mono<MessageUid> retrieveFirstUnread(CassandraId cassandraId) {
        return cassandraAsyncExecutor.executeSingleRow(
                readStatement.bind()
                    .setUuid(MAILBOX_ID, cassandraId.asUuid()))
            .map(row -> MessageUid.of(row.getLong(UID)));
    }

    public Flux<MessageUid> listUnseen(CassandraId cassandraId) {
        return cassandraAsyncExecutor.executeRows(
                listStatement.bind()
                    .setUuid(MAILBOX_ID, cassandraId.asUuid()))
            .map(row -> MessageUid.of(row.getLong(UID)));
    }

}
