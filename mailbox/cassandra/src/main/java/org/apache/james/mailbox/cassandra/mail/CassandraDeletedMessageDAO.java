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
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.gte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.lte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraDeletedMessageTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraDeletedMessageTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraDeletedMessageTable.UID;

import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.MessageRange;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.collect.Lists;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraDeletedMessageDAO {
    private static final String UID_TO = "uid_to";
    private static final String UID_FROM = "uid_from";
    private static final int BATCH_STATEMENT_WINDOW = 1024;
    private static final int LOW_CONCURRENCY = 2;

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement deleteAllStatement;
    private final PreparedStatement selectAllUidStatement;
    private final PreparedStatement selectOneUidStatement;
    private final PreparedStatement selectBetweenUidStatement;
    private final PreparedStatement selectFromUidStatement;

    @Inject
    public CassandraDeletedMessageDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAddStatement(session);
        this.deleteStatement = prepareDeleteStatement(session);
        this.deleteAllStatement = prepareDeleteAllStatement(session);
        this.selectAllUidStatement = prepareAllUidStatement(session);
        this.selectOneUidStatement = prepareOneUidStatement(session);
        this.selectBetweenUidStatement = prepareBetweenUidStatement(session);
        this.selectFromUidStatement = prepareFromUidStatement(session);
    }

    private PreparedStatement prepareAllUidStatement(Session session) {
        return session.prepare(select(UID)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareOneUidStatement(Session session) {
        return session.prepare(select(UID)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(UID, bindMarker(UID))));
    }

    private PreparedStatement prepareBetweenUidStatement(Session session) {
        return session.prepare(select(UID)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(gte(UID, bindMarker(UID_FROM)))
            .and(lte(UID, bindMarker(UID_TO))));
    }

    private PreparedStatement prepareFromUidStatement(Session session) {
        return session.prepare(select(UID)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(gte(UID, bindMarker(UID_FROM))));
    }

    private PreparedStatement prepareDeleteStatement(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(UID, bindMarker(UID))));
    }

    private PreparedStatement prepareDeleteAllStatement(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareAddStatement(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(UID, bindMarker(UID)));
    }

    public Mono<Void> addDeleted(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(
            addStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID, uid.asLong()));
    }

    public Mono<Void> addDeleted(CassandraId cassandraId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(
                addStatement.bind()
                    .setUUID(MAILBOX_ID, cassandraId.asUuid())
                    .setLong(UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
                    uidBatch.forEach(uid -> batch.add(addStatement.bind()
                        .setUUID(MAILBOX_ID, cassandraId.asUuid())
                        .setLong(UID, uid.asLong())));
                    return batch;
                });
            return Flux.fromStream(batches)
                .flatMap(cassandraAsyncExecutor::executeVoid, LOW_CONCURRENCY)
                .then();
        }
    }

    public Mono<Void> removeDeleted(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setLong(UID, uid.asLong()));
    }

    public Mono<Void> removeDeleted(CassandraId cassandraId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
                    uidBatch.forEach(uid -> batch.add(deleteStatement.bind()
                        .setUUID(MAILBOX_ID, cassandraId.asUuid())
                        .setLong(UID, uid.asLong())));
                    return batch;
                });
            return Flux.fromStream(batches)
                .flatMap(cassandraAsyncExecutor::executeVoid, LOW_CONCURRENCY)
                .then();
        }
    }

    public Mono<Void> removeAll(CassandraId cassandraId) {
        return cassandraAsyncExecutor.executeVoid(deleteAllStatement.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid()));
    }

    public Flux<MessageUid> retrieveDeletedMessage(CassandraId cassandraId, MessageRange range) {
        return retrieveResultSetOfDeletedMessage(cassandraId, range)
            .map(row -> MessageUid.of(row.getLong(UID)));
    }

    private Flux<Row> retrieveResultSetOfDeletedMessage(CassandraId cassandraId, MessageRange range) {
        switch (range.getType()) {
            case ALL:
                return retrieveAllDeleted(cassandraId);
            case FROM:
                return retrieveDeletedAfter(cassandraId, range.getUidFrom());
            case RANGE:
                return retrieveDeletedBetween(cassandraId, range.getUidFrom(), range.getUidTo());
            case ONE:
                return retrieveOneDeleted(cassandraId, range.getUidFrom());
        }

        throw new UnsupportedOperationException();
    }

    private Flux<Row> retrieveAllDeleted(CassandraId cassandraId) {
        return cassandraAsyncExecutor.executeRows(
            selectAllUidStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid()));
    }

    private Flux<Row> retrieveOneDeleted(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeRows(
            selectOneUidStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID, uid.asLong()));
    }

    private Flux<Row> retrieveDeletedBetween(CassandraId cassandraId, MessageUid from, MessageUid to) {
        return cassandraAsyncExecutor.executeRows(
            selectBetweenUidStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID_FROM, from.asLong())
                .setLong(UID_TO, to.asLong()));
    }

    private Flux<Row> retrieveDeletedAfter(CassandraId cassandraId, MessageUid from) {
        return cassandraAsyncExecutor.executeRows(
            selectFromUidStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID_FROM, from.asLong()));
    }
}
