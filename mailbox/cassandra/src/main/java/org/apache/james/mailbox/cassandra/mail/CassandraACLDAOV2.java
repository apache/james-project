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

import static com.datastax.driver.core.querybuilder.QueryBuilder.addAll;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.removeAll;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;

import javax.inject.Inject;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.cassandra.table.CassandraACLV2Table;
import org.apache.james.mailbox.model.MailboxACL;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraACLDAOV2 {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertRights;
    private final PreparedStatement removeRights;
    private final PreparedStatement replaceRights;
    private final PreparedStatement delete;
    private final PreparedStatement read;

    @Inject
    public CassandraACLDAOV2(Session session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertRights = prepareInsertRights(session);
        this.removeRights = prepareRemoveRights(session);
        this.replaceRights = prepareReplaceRights(session);
        this.read = prepareRead(session);
        this.delete = prepareDelete(session);
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(
            QueryBuilder.delete().from(CassandraACLV2Table.TABLE_NAME)
                .where(eq(CassandraACLV2Table.ID, bindMarker(CassandraACLV2Table.ID))));
    }

    private PreparedStatement prepareInsertRights(Session session) {
        return session.prepare(
            update(CassandraACLV2Table.TABLE_NAME)
                .with(addAll(CassandraACLV2Table.RIGHTS, bindMarker(CassandraACLV2Table.RIGHTS)))
                .where(eq(CassandraACLV2Table.ID, bindMarker(CassandraACLV2Table.ID)))
                .and(eq(CassandraACLV2Table.KEY, bindMarker(CassandraACLV2Table.KEY))));
    }

    private PreparedStatement prepareReplaceRights(Session session) {
        return session.prepare(
            update(CassandraACLV2Table.TABLE_NAME)
                .with(set(CassandraACLV2Table.RIGHTS, bindMarker(CassandraACLV2Table.RIGHTS)))
                .where(eq(CassandraACLV2Table.ID, bindMarker(CassandraACLV2Table.ID)))
                .and(eq(CassandraACLV2Table.KEY, bindMarker(CassandraACLV2Table.KEY))));
    }

    private PreparedStatement prepareRemoveRights(Session session) {
        return session.prepare(
            update(CassandraACLV2Table.TABLE_NAME)
                .with(removeAll(CassandraACLV2Table.RIGHTS, bindMarker(CassandraACLV2Table.RIGHTS)))
                .where(eq(CassandraACLV2Table.ID, bindMarker(CassandraACLV2Table.ID)))
                .and(eq(CassandraACLV2Table.KEY, bindMarker(CassandraACLV2Table.KEY))));
    }

    private PreparedStatement prepareRead(Session session) {
        return session.prepare(
            select()
                .from(CassandraACLV2Table.TABLE_NAME)
                .where(eq(CassandraACLV2Table.ID, bindMarker(CassandraACLV2Table.ID))));
    }

    public Mono<Void> delete(CassandraId cassandraId) {
        return executor.executeVoid(
            delete.bind()
                .setUUID(CassandraACLTable.ID, cassandraId.asUuid()));
    }

    public Mono<MailboxACL> getACL(CassandraId cassandraId) {
        return executor.executeRows(
            read.bind()
                .setUUID(CassandraACLTable.ID, cassandraId.asUuid()))
            .map(Throwing.function(row -> {
                MailboxACL.EntryKey entryKey = MailboxACL.EntryKey.deserialize(row.getString(CassandraACLV2Table.KEY));
                MailboxACL.Rfc4314Rights rights = row.getSet(CassandraACLV2Table.RIGHTS, String.class)
                    .stream()
                    .map(Throwing.function(MailboxACL.Rfc4314Rights::deserialize))
                    .reduce(MailboxACL.NO_RIGHTS, Throwing.binaryOperator(MailboxACL.Rfc4314Rights::union));
                return new MailboxACL(ImmutableMap.of(entryKey, rights));
            }))
            .reduce(Throwing.biFunction(MailboxACL::union));
    }

    public Mono<Void> setACL(CassandraId cassandraId, MailboxACL mailboxACL) {
        return delete(cassandraId)
                .then(Flux.fromIterable(mailboxACL.getEntries().entrySet())
                    .concatMap(entry -> doSetACL(cassandraId, mailboxACL))
                    .then());
    }

    public Mono<Void> updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command) {
        ImmutableSet<String> rightStrings = asStringSet(command.getRights());
        switch (command.getEditMode()) {
            case ADD:
                return executor.executeVoid(insertRights.bind()
                    .setUUID(CassandraACLV2Table.ID, cassandraId.asUuid())
                    .setString(CassandraACLV2Table.KEY, command.getEntryKey().serialize())
                    .setSet(CassandraACLV2Table.RIGHTS, ImmutableSet.copyOf(rightStrings), String.class));
            case REMOVE:
                return executor.executeVoid(removeRights.bind()
                    .setUUID(CassandraACLV2Table.ID, cassandraId.asUuid())
                    .setString(CassandraACLV2Table.KEY, command.getEntryKey().serialize())
                    .setSet(CassandraACLV2Table.RIGHTS, ImmutableSet.copyOf(rightStrings), String.class));
            case REPLACE:
                return executor.executeVoid(replaceRights.bind()
                    .setUUID(CassandraACLV2Table.ID, cassandraId.asUuid())
                    .setString(CassandraACLV2Table.KEY, command.getEntryKey().serialize())
                    .setSet(CassandraACLV2Table.RIGHTS, rightStrings, String.class));
            default:
                throw new NotImplementedException(command.getEditMode() + "is not supported");
        }
    }

    private ImmutableSet<String> asStringSet(MailboxACL.Rfc4314Rights rights) {
        return rights.list()
            .stream()
            .map(MailboxACL.Right::asCharacter)
            .map(String::valueOf)
            .collect(Guavate.toImmutableSet());
    }

    public Mono<Void> doSetACL(CassandraId cassandraId, MailboxACL mailboxACL) {
        BatchStatement batchStatement = new BatchStatement();
        mailboxACL.getEntries().entrySet()
            .stream().map(entry -> replaceRights.bind()
            .setUUID(CassandraACLV2Table.ID, cassandraId.asUuid())
            .setString(CassandraACLV2Table.KEY, entry.getKey().serialize())
            .setSet(CassandraACLV2Table.RIGHTS, asStringSet(entry.getValue()), String.class))
            .forEach(batchStatement::add);

        return executor.executeVoid(batchStatement);
    }
}
