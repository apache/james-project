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

import static com.datastax.driver.core.querybuilder.QueryBuilder.add;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.Flag.USER_FLAGS;

import java.util.Set;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Update;
import com.datastax.driver.core.querybuilder.Update.Assignments;

import reactor.core.publisher.Mono;

public class CassandraApplicableFlagDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement select;
    private final PreparedStatement delete;

    @Inject
    public CassandraApplicableFlagDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.select = prepareSelect(session);
        this.delete = prepareDelete(session);
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    public Mono<Void> delete(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(
            delete.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Flags> retrieveApplicableFlag(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRow(
            select.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .map(row -> new FlagsExtractor(row).getApplicableFlags());
    }

    public Mono<Void> updateApplicableFlags(CassandraId cassandraId, Set<String> toBeAdded) {
        if (toBeAdded.isEmpty()) {
            return Mono.empty();
        }
        return cassandraAsyncExecutor.executeVoid(updateQuery(cassandraId, toBeAdded));
    }

    private Update.Where updateQuery(CassandraId cassandraId, Set<String> userFlags) {
        return addUserFlagsToQuery(userFlags,
                update(TABLE_NAME).with())
            .where(eq(MAILBOX_ID, cassandraId.asUuid()));
    }

    private Assignments addUserFlagsToQuery(Set<String> userFlags, Assignments updateQuery) {
        userFlags.forEach(userFlag -> updateQuery.and(add(USER_FLAGS, userFlag)));
        return updateQuery;
    }

}
