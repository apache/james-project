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
import static org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable.RIGHTS;
import static org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable.USER_NAME;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.acl.PositiveUserACLChanged;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.util.FluentFutureStream;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.github.fge.lambdas.Throwing;

public class CassandraUserMailboxRightsDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectUser;

    @Inject
    public CassandraUserMailboxRightsDAO(Session session, CassandraUtils cassandraUtils) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;
        this.delete = prepareDelete(session);
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);
        this.selectUser = prepareSelectAllForUser(session);
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(USER_NAME, bindMarker(USER_NAME)))
            .and(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(USER_NAME, bindMarker(USER_NAME))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(RIGHTS, bindMarker(RIGHTS)));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(RIGHTS)
            .from(TABLE_NAME)
            .where(eq(USER_NAME, bindMarker(USER_NAME)))
            .and(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareSelectAllForUser(Session session) {
        return session.prepare(select(MAILBOX_ID, RIGHTS)
            .from(TABLE_NAME)
            .where(eq(USER_NAME, bindMarker(USER_NAME))));
    }

    public CompletableFuture<Void> update(CassandraId cassandraId, PositiveUserACLChanged positiveUserAclChanged) {
        ACLDiff aclDiff = ACLDiff.from(positiveUserAclChanged.getOldACL(), positiveUserAclChanged.getNewACL());
        return CompletableFuture.allOf(
            addAll(cassandraId, aclDiff.addedEntries()),
            removeAll(cassandraId, aclDiff.removedEntries()),
            addAll(cassandraId, aclDiff.changedEntries()));
    }

    private CompletableFuture<Stream<Void>> removeAll(CassandraId cassandraId, Stream<MailboxACL.Entry> removedEntries) {
        return FluentFutureStream.of(removedEntries
            .map(entry -> cassandraAsyncExecutor.executeVoid(
                delete.bind()
                    .setString(USER_NAME, entry.getKey().getName())
                    .setUUID(MAILBOX_ID, cassandraId.asUuid()))))
        .completableFuture();
    }

    private CompletableFuture<Stream<Void>> addAll(CassandraId cassandraId, Stream<MailboxACL.Entry> addedEntries) {
        return FluentFutureStream.of(addedEntries
            .map(entry -> cassandraAsyncExecutor.executeVoid(
                insert.bind()
                    .setString(USER_NAME, entry.getKey().getName())
                    .setUUID(MAILBOX_ID, cassandraId.asUuid())
                    .setString(RIGHTS, entry.getValue().serialize()))))
        .completableFuture();
    }

    public CompletableFuture<Optional<Rfc4314Rights>> retrieve(String userName, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRow(
            select.bind()
                .setString(USER_NAME, userName)
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .thenApply(rowOptional ->
                rowOptional.map(Throwing.function(row -> Rfc4314Rights.fromSerializedRfc4314Rights(row.getString(RIGHTS)))));
    }

    public CompletableFuture<Map<CassandraId, Rfc4314Rights>> listRightsForUser(String userName) {
        return cassandraAsyncExecutor.execute(
            selectUser.bind()
                .setString(USER_NAME, userName))
            .thenApply(cassandraUtils::convertToStream)
            .thenApply(row ->
                row.map(Throwing.function(this::toPair))
                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight)));
    }

    private Pair<CassandraId, Rfc4314Rights> toPair(Row row) throws UnsupportedRightException {
        return Pair.of(
            CassandraId.of(row.getUUID(MAILBOX_ID)),
            Rfc4314Rights.fromSerializedRfc4314Rights(row.getString(RIGHTS)));
    }
}
