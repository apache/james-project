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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.Flag.USER_FLAGS;

import java.util.Set;

import javax.inject.Inject;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.table.Flag;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;

import reactor.core.publisher.Mono;

public class CassandraApplicableFlagDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement select;
    private final PreparedStatement update;
    private final PreparedStatement delete;

    @Inject
    public CassandraApplicableFlagDAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.select = prepareSelect(session);
        this.delete = prepareDelete(session);
        this.update = prepareUpdate(session);
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(Flag.USER_FLAGS)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    private PreparedStatement prepareDelete(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    private PreparedStatement prepareUpdate(CqlSession session) {
        return session.prepare(QueryBuilder.update(TABLE_NAME)
            .append(USER_FLAGS, bindMarker(USER_FLAGS))
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    public Mono<Void> delete(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(
            delete.bind()
                .setUuid(MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Flags> retrieveApplicableFlag(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRow(
                select.bind()
                    .setUuid(MAILBOX_ID, mailboxId.asUuid()))
            .map(FlagsExtractor::getApplicableFlags);
    }

    public Mono<Void> updateApplicableFlags(CassandraId cassandraId, Set<String> toBeAdded) {
        if (toBeAdded.isEmpty()) {
            return Mono.empty();
        }
        return cassandraAsyncExecutor.executeVoid(update.bind()
            .setUuid(MAILBOX_ID, cassandraId.asUuid())
            .setSet(USER_FLAGS, toBeAdded, String.class));
    }
}
