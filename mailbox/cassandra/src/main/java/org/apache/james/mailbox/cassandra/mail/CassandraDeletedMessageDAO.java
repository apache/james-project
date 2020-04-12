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

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.MessageRange;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraDeletedMessageDAO {
    private static final String UID_TO = "uid_to";
    private static final String UID_FROM = "uid_from";

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement deleteAllStatement;

    private final PreparedStatement selectAllUidStatement;
    private final PreparedStatement selectOneUidStatement;
    private final PreparedStatement selectBetweenUidStatement;
    private final PreparedStatement selectFromUidStatement;
    private final CassandraUtils cassandraUtils;

    @Inject
    public CassandraDeletedMessageDAO(Session session, CassandraUtils cassandraUtils) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAddStatement(session);
        this.deleteStatement = prepareDeleteStatement(session);
        this.deleteAllStatement = prepareDeleteAllStatement(session);
        this.selectAllUidStatement = prepareAllUidStatement(session);
        this.selectOneUidStatement = prepareOneUidStatement(session);
        this.selectBetweenUidStatement = prepareBetweenUidStatement(session);
        this.selectFromUidStatement = prepareFromUidStatement(session);
        this.cassandraUtils = cassandraUtils;
    }

    @VisibleForTesting
    public CassandraDeletedMessageDAO(Session session) {
        this(session, CassandraUtils.WITH_DEFAULT_CONFIGURATION);
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

    public Mono<Void> removeDeleted(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setLong(UID, uid.asLong()));
    }

    public Mono<Void> removeAll(CassandraId cassandraId) {
        return cassandraAsyncExecutor.executeVoid(deleteAllStatement.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid()));
    }

    public Flux<MessageUid> retrieveDeletedMessage(CassandraId cassandraId, MessageRange range) {
        return retrieveResultSetOfDeletedMessage(cassandraId, range)
            .flatMapMany(this::resultSetToFlux);
    }

    private Mono<ResultSet> retrieveResultSetOfDeletedMessage(CassandraId cassandraId, MessageRange range) {
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

    private Flux<MessageUid> resultSetToFlux(ResultSet resultSet) {
        return cassandraUtils.convertToFlux(resultSet)
            .map(row ->
                MessageUid.of(row.getLong(UID)));
    }

    private Mono<ResultSet> retrieveAllDeleted(CassandraId cassandraId) {
        return cassandraAsyncExecutor.execute(
            selectAllUidStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid()));
    }

    private Mono<ResultSet> retrieveOneDeleted(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.execute(
            selectOneUidStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID, uid.asLong()));
    }

    private Mono<ResultSet> retrieveDeletedBetween(CassandraId cassandraId, MessageUid from, MessageUid to) {
        return cassandraAsyncExecutor.execute(
            selectBetweenUidStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID_FROM, from.asLong())
                .setLong(UID_TO, to.asLong()));
    }

    private Mono<ResultSet> retrieveDeletedAfter(CassandraId cassandraId, MessageUid from) {
        return cassandraAsyncExecutor.execute(
            selectFromUidStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID_FROM, from.asLong()));
    }
}
