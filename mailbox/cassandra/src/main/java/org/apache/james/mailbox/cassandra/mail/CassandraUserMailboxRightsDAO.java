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
import static org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable.RIGHTS;
import static org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraUserMailboxRightsTable.USER_NAME;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.acl.PositiveUserACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraUserMailboxRightsDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectUser;

    @Inject
    public CassandraUserMailboxRightsDAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.delete = prepareDelete(session);
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);
        this.selectUser = prepareSelectAllForUser(session);
    }

    private PreparedStatement prepareDelete(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(USER_NAME).isEqualTo(bindMarker(USER_NAME)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    private PreparedStatement prepareInsert(CqlSession session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(USER_NAME, bindMarker(USER_NAME))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(RIGHTS, bindMarker(RIGHTS))
            .build());
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(RIGHTS)
            .where(column(USER_NAME).isEqualTo(bindMarker(USER_NAME)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    private PreparedStatement prepareSelectAllForUser(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(MAILBOX_ID, RIGHTS)
            .where(column(USER_NAME).isEqualTo(bindMarker(USER_NAME)))
            .build());
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
                        .setUuid(MAILBOX_ID, cassandraId.asUuid())),
                DEFAULT_CONCURRENCY);
    }

    private Flux<Void> addAll(CassandraId cassandraId, Stream<MailboxACL.Entry> addedEntries) {
        return Flux.fromStream(addedEntries)
            .flatMap(entry -> cassandraAsyncExecutor.executeVoid(
                    insert.bind()
                        .setString(USER_NAME, entry.getKey().getName())
                        .setUuid(MAILBOX_ID, cassandraId.asUuid())
                        .setString(RIGHTS, entry.getValue().serialize())),
                DEFAULT_CONCURRENCY);
    }

    public Mono<Optional<Rfc4314Rights>> retrieve(Username userName, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRowOptional(
                select.bind()
                    .setString(USER_NAME, userName.asString())
                    .setUuid(MAILBOX_ID, mailboxId.asUuid()))
            .map(rowOptional ->
                rowOptional.map(Throwing.function(row -> Rfc4314Rights.fromSerializedRfc4314Rights(row.getString(RIGHTS)))));
    }

    public Flux<Pair<CassandraId, Rfc4314Rights>> listRightsForUser(Username userName) {
        return cassandraAsyncExecutor.executeRows(
                selectUser.bind()
                    .setString(USER_NAME, userName.asString()))
            .map(Throwing.function(this::toPair));
    }

    private Pair<CassandraId, Rfc4314Rights> toPair(Row row) throws UnsupportedRightException {
        return Pair.of(
            CassandraId.of(row.getUuid(MAILBOX_ID)),
            Rfc4314Rights.fromSerializedRfc4314Rights(row.getString(RIGHTS)));
    }
}
