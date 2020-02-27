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

import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.acl.PositiveUserACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    public Mono<Void> update(CassandraId cassandraId, ACLDiff aclDiff) {
        PositiveUserACLDiff userACLDiff = new PositiveUserACLDiff(aclDiff);
        return Flux.merge(
                addAll(cassandraId, userACLDiff.addedEntries()),
                removeAll(cassandraId, userACLDiff.removedEntries()),
                addAll(cassandraId, userACLDiff.changedEntries()))
            .then();
    }

    private Flux<Void> removeAll(CassandraId cassandraId, Stream<MailboxACL.Entry> removedEntries) {
        return Flux.fromStream(removedEntries)
            .flatMap(entry -> cassandraAsyncExecutor.executeVoid(
                delete.bind()
                    .setString(USER_NAME, entry.getKey().getName())
                    .setUUID(MAILBOX_ID, cassandraId.asUuid())));
    }

    private Flux<Void> addAll(CassandraId cassandraId, Stream<MailboxACL.Entry> addedEntries) {
        return Flux.fromStream(addedEntries)
            .flatMap(entry -> cassandraAsyncExecutor.executeVoid(
                insert.bind()
                    .setString(USER_NAME, entry.getKey().getName())
                    .setUUID(MAILBOX_ID, cassandraId.asUuid())
                    .setString(RIGHTS, entry.getValue().serialize())));
    }

    public Mono<Optional<Rfc4314Rights>> retrieve(Username userName, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRowOptional(
            select.bind()
                .setString(USER_NAME, userName.asString())
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .map(rowOptional ->
                rowOptional.map(Throwing.function(row -> Rfc4314Rights.fromSerializedRfc4314Rights(row.getString(RIGHTS)))));
    }

    public Flux<Pair<CassandraId, Rfc4314Rights>> listRightsForUser(Username userName) {
        return cassandraAsyncExecutor.execute(
            selectUser.bind()
                .setString(USER_NAME, userName.asString()))
            .flatMapMany(cassandraUtils::convertToFlux)
            .map(Throwing.function(this::toPair));
    }

    private Pair<CassandraId, Rfc4314Rights> toPair(Row row) throws UnsupportedRightException {
        return Pair.of(
            CassandraId.of(row.getUUID(MAILBOX_ID)),
            Rfc4314Rights.fromSerializedRfc4314Rights(row.getString(RIGHTS)));
    }
}
