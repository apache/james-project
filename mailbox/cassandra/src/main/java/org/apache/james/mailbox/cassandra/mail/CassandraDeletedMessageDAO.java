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
import static org.apache.james.mailbox.cassandra.table.CassandraDeletedMessageTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraDeletedMessageTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraDeletedMessageTable.UID;

import java.util.List;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.MessageRange;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
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
    private final ProtocolVersion protocolVersion;

    @Inject
    public CassandraDeletedMessageDAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAddStatement(session);
        this.deleteStatement = prepareDeleteStatement(session);
        this.deleteAllStatement = prepareDeleteAllStatement(session);
        this.selectAllUidStatement = prepareAllUidStatement(session);
        this.selectOneUidStatement = prepareOneUidStatement(session);
        this.selectBetweenUidStatement = prepareBetweenUidStatement(session);
        this.selectFromUidStatement = prepareFromUidStatement(session);
        this.protocolVersion = session.getContext().getProtocolVersion();
    }

    private PreparedStatement prepareAllUidStatement(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(UID)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    private PreparedStatement prepareOneUidStatement(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(UID)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(UID).isEqualTo(bindMarker(UID)))
            .build());
    }

    private PreparedStatement prepareBetweenUidStatement(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(UID)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(UID).isGreaterThanOrEqualTo(bindMarker(UID_FROM)),
                column(UID).isLessThanOrEqualTo(bindMarker(UID_TO)))
            .build());
    }

    private PreparedStatement prepareFromUidStatement(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(UID)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(UID).isGreaterThanOrEqualTo(bindMarker(UID_FROM)))
            .build());
    }

    private PreparedStatement prepareDeleteStatement(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(UID).isEqualTo(bindMarker(UID)))
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

    public Mono<Void> addDeleted(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(
            addStatement.bind()
                .setUuid(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID, uid.asLong()));
    }

    public Mono<Void> addDeleted(CassandraId cassandraId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(
                addStatement.bind()
                    .setUuid(MAILBOX_ID, cassandraId.asUuid())
                    .setLong(UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatementBuilder batch = new BatchStatementBuilder(BatchType.UNLOGGED);
                    uidBatch.forEach(uid -> batch.addStatement(addStatement.bind()
                        .setUuid(MAILBOX_ID, cassandraId.asUuid())
                        .setLong(UID, uid.asLong())));
                    return batch.build();
                });
            return Flux.fromStream(batches)
                .flatMap(cassandraAsyncExecutor::executeVoid, LOW_CONCURRENCY)
                .then();
        }
    }

    public Mono<Void> removeDeleted(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUuid(MAILBOX_ID, cassandraId.asUuid())
            .setLong(UID, uid.asLong()));
    }

    public Mono<Void> removeDeleted(CassandraId cassandraId, List<MessageUid> uids) {
        if (uids.size() == 1) {
            return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
                .setUuid(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID, uids.iterator().next().asLong()));
        } else {
            Stream<BatchStatement> batches = Lists.partition(uids, BATCH_STATEMENT_WINDOW)
                .stream()
                .map(uidBatch -> {
                    BatchStatementBuilder batch = new BatchStatementBuilder(BatchType.UNLOGGED);
                    uidBatch.forEach(uid -> batch.addStatement(deleteStatement.bind()
                        .setUuid(MAILBOX_ID, cassandraId.asUuid())
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
            .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.TIMEUUID));
    }

    public Flux<MessageUid> retrieveDeletedMessage(CassandraId cassandraId, MessageRange range) {
        return retrieveResultSetOfDeletedMessage(cassandraId, range)
            .map(this::asMessageUid);
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
                .set(MAILBOX_ID, cassandraId.asUuid(), TypeCodecs.TIMEUUID));
    }

    private Flux<Row> retrieveOneDeleted(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeRows(
            selectOneUidStatement.bind()
                .setUuid(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID, uid.asLong()));
    }

    private Flux<Row> retrieveDeletedBetween(CassandraId cassandraId, MessageUid from, MessageUid to) {
        return cassandraAsyncExecutor.executeRows(
            selectBetweenUidStatement.bind()
                .setUuid(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID_FROM, from.asLong())
                .setLong(UID_TO, to.asLong()));
    }

    private Flux<Row> retrieveDeletedAfter(CassandraId cassandraId, MessageUid from) {
        return cassandraAsyncExecutor.executeRows(
            selectFromUidStatement.bind()
                .setUuid(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID_FROM, from.asLong()));
    }

    private MessageUid asMessageUid(Row row) {
        return MessageUid.of(TypeCodecs.BIGINT.decodePrimitive(row.getBytesUnsafe(0), protocolVersion));
    }
}
