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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;

import javax.inject.Inject;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.cassandra.table.CassandraACLV2Table;
import org.apache.james.mailbox.model.MailboxACL;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class CassandraACLDAOV2 {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertRights;
    private final PreparedStatement removeRights;
    private final PreparedStatement replaceRights;
    private final PreparedStatement read;

    @Inject
    public CassandraACLDAOV2(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertRights = prepareInsertRights(session);
        this.removeRights = prepareRemoveRights(session);
        this.replaceRights = prepareReplaceRights(session);
        this.read = prepareRead(session);
    }

    private PreparedStatement prepareInsertRights(CqlSession session) {
        return session.prepare(update(CassandraACLV2Table.TABLE_NAME)
            .append(CassandraACLV2Table.RIGHTS, bindMarker(CassandraACLV2Table.RIGHTS))
            .where(column(CassandraACLV2Table.ID).isEqualTo(bindMarker(CassandraACLV2Table.ID)),
                column(CassandraACLV2Table.KEY).isEqualTo(bindMarker(CassandraACLV2Table.KEY)))
            .build());
    }

    private PreparedStatement prepareReplaceRights(CqlSession session) {
        return session.prepare(update(CassandraACLV2Table.TABLE_NAME)
            .setColumn(CassandraACLV2Table.RIGHTS, bindMarker(CassandraACLV2Table.RIGHTS))
            .where(column(CassandraACLV2Table.ID).isEqualTo(bindMarker(CassandraACLV2Table.ID)),
                column(CassandraACLV2Table.KEY).isEqualTo(bindMarker(CassandraACLV2Table.KEY)))
            .build());
    }

    private PreparedStatement prepareRemoveRights(CqlSession session) {
        return session.prepare(update(CassandraACLV2Table.TABLE_NAME)
            .remove(CassandraACLV2Table.RIGHTS, bindMarker(CassandraACLV2Table.RIGHTS))
            .where(column(CassandraACLV2Table.ID).isEqualTo(bindMarker(CassandraACLV2Table.ID)),
                column(CassandraACLV2Table.KEY).isEqualTo(bindMarker(CassandraACLV2Table.KEY)))
            .build());
    }

    private PreparedStatement prepareRead(CqlSession session) {
        return session.prepare(selectFrom(CassandraACLV2Table.TABLE_NAME)
                .all()
                .where(column(CassandraACLV2Table.ID).isEqualTo(bindMarker(CassandraACLV2Table.ID)))
                .build());
    }

    public Mono<MailboxACL> getACL(CassandraId cassandraId) {
        return executor.executeRows(
                read.bind()
                    .set(CassandraACLTable.ID, cassandraId.asUuid(), TypeCodecs.TIMEUUID))
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

    public Mono<Void> updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command) {
        ImmutableSet<String> rightStrings = asStringSet(command.getRights());
        switch (command.getEditMode()) {
            case ADD:
                return executor.executeVoid(insertRights.bind()
                    .setUuid(CassandraACLV2Table.ID, cassandraId.asUuid())
                    .setString(CassandraACLV2Table.KEY, command.getEntryKey().serialize())
                    .setSet(CassandraACLV2Table.RIGHTS, ImmutableSet.copyOf(rightStrings), String.class));
            case REMOVE:
                return executor.executeVoid(removeRights.bind()
                    .setUuid(CassandraACLV2Table.ID, cassandraId.asUuid())
                    .setString(CassandraACLV2Table.KEY, command.getEntryKey().serialize())
                    .setSet(CassandraACLV2Table.RIGHTS, ImmutableSet.copyOf(rightStrings), String.class));
            case REPLACE:
                return executor.executeVoid(replaceRights.bind()
                    .setUuid(CassandraACLV2Table.ID, cassandraId.asUuid())
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
            .collect(ImmutableSet.toImmutableSet());
    }
}
