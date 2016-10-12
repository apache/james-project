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
import static com.datastax.driver.core.querybuilder.QueryBuilder.gte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.lte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.IMAP_UID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.cassandra.CassandraMessageId.Factory;
import org.apache.james.mailbox.model.MessageRange;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

public class CassandraMessageIdDAO {

    private static final String IMAP_UID_GTE = IMAP_UID + "_GTE";
    private static final String IMAP_UID_LTE = IMAP_UID + "_LTE";

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final Factory messageIdFactory;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectAllUids;
    private final PreparedStatement selectUidGte;
    private final PreparedStatement selectUidRange;

    @Inject
    public CassandraMessageIdDAO(Session session, CassandraMessageId.Factory messageIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.messageIdFactory = messageIdFactory;
        this.delete = prepareDelete(session);
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);
        this.selectAllUids = prepareSelectAllUids(session);
        this.selectUidGte = prepareSelectUidGte(session);
        this.selectUidRange = prepareSelectUidRange(session);
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
                .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
                .value(IMAP_UID, bindMarker(IMAP_UID))
                .value(MESSAGE_ID, bindMarker(MESSAGE_ID)));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(MESSAGE_ID)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareSelectAllUids(Session session) {
        return session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareSelectUidGte(Session session) {
        return session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(gte(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareSelectUidRange(Session session) {
        return session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(gte(IMAP_UID, bindMarker(IMAP_UID_GTE)))
                .and(lte(IMAP_UID, bindMarker(IMAP_UID_LTE))));
    }

    public CompletableFuture<Void> delete(CassandraId mailboxId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong()));
    }

    public CompletableFuture<Void> insert(CassandraId mailboxId, MessageUid uid, CassandraMessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(insert.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong())
                .setUUID(MESSAGE_ID, messageId.get()));
    }

    public CompletableFuture<Optional<CassandraMessageId>> retrieve(CassandraId mailboxId, MessageUid uid) {
        return selectOneRow(mailboxId, uid).thenApply(this::asOptionalOfCassandraMessageId);
    }

    private Optional<CassandraMessageId> asOptionalOfCassandraMessageId(ResultSet resultSet) {
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        return Optional.of(messageIdFactory.of(resultSet.one().getUUID(MESSAGE_ID)));
    }

    private CompletableFuture<ResultSet> selectOneRow(CassandraId mailboxId, MessageUid uid) {
        return cassandraAsyncExecutor.execute(select.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong()));
    }

    public CompletableFuture<Stream<CassandraMessageId>> retrieveMessageIds(CassandraId mailboxId, MessageRange set) {
        switch (set.getType()) {
        case ALL:
            return toMessageIds(selectAll(mailboxId));
        case FROM:
            return toMessageIds(selectFrom(mailboxId, set.getUidFrom()));
        case RANGE:
            return toMessageIds(selectRange(mailboxId, set.getUidFrom(), set.getUidTo()));
        case ONE:
            return toMessageIds(selectOneRow(mailboxId, set.getUidFrom()));
        }
        throw new UnsupportedOperationException();
    }

    private CompletableFuture<ResultSet> selectAll(CassandraId mailboxId) {
        return cassandraAsyncExecutor.execute(selectAllUids.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    private CompletableFuture<ResultSet> selectFrom(CassandraId mailboxId, MessageUid uid) {
        return cassandraAsyncExecutor.execute(selectUidGte.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong()));
    }

    private CompletableFuture<ResultSet> selectRange(CassandraId mailboxId, MessageUid from, MessageUid to) {
        return cassandraAsyncExecutor.execute(selectUidRange.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID_GTE, from.asLong())
                .setLong(IMAP_UID_LTE, to.asLong()));
    }

    private CompletableFuture<Stream<CassandraMessageId>> toMessageIds(CompletableFuture<ResultSet> completableFuture) {
        return completableFuture
            .thenApply(resultSet ->  { 
                return CassandraUtils.convertToStream(resultSet)
                    .map(row -> messageIdFactory.of(row.getUUID(MESSAGE_ID)));
            });
    }
}
