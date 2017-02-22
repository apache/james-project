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
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.Flag.FLAG_TO_STRING_MAP;
import static org.apache.james.mailbox.cassandra.table.Flag.USER_FLAGS;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.cassandra.CassandraId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Update;
import com.datastax.driver.core.querybuilder.Update.Assignments;

public class CassandraApplicableFlagDAO {
    private static final Flags.Flag[] ALL_APPLICABLE_FLAGS = {Flags.Flag.ANSWERED, Flags.Flag.DELETED, Flags.Flag.DRAFT, Flags.Flag.SEEN, Flags.Flag.FLAGGED };
    private static final Flags EMPTY_FLAGS = new Flags();

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement select;

    @Inject
    public CassandraApplicableFlagDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.select = prepareSelect(session);
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    public CompletableFuture<Optional<Flags>> retrieveApplicableFlag(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRow(
            select.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .thenApply(rowOptional ->
                rowOptional.map(row -> new FlagsExtractor(row).getApplicableFlags()));
    }

    public CompletableFuture<Void> updateApplicableFlags(CassandraId cassandraId, Flags flags) {
        Flags newFlags = new FlagsBuilder().add(flags).build();
        newFlags.remove(Flag.RECENT);
        newFlags.remove(Flag.USER);

        if (newFlags.equals(EMPTY_FLAGS)) {
            return CompletableFuture.completedFuture(null);
        }
        return cassandraAsyncExecutor.executeVoid(updateQuery(cassandraId, newFlags));
    }

    private Update.Where updateQuery(CassandraId cassandraId, Flags flags) {
        return addSystemFlagsToQuery(flags,
            addUserFlagsToQuery(flags,
                update(TABLE_NAME).with()))
            .where(eq(MAILBOX_ID, cassandraId.asUuid()));
    }

    private Assignments addUserFlagsToQuery(Flags flags, Assignments updateQuery) {
        if (flags.getUserFlags() != null && flags.getUserFlags().length > 0) {
            Arrays.stream(flags.getUserFlags())
                .forEach(userFlag -> updateQuery.and(add(USER_FLAGS, userFlag)));
        }
        return updateQuery;
    }

    private Assignments addSystemFlagsToQuery(Flags flags, Assignments updateQuery) {
        Arrays.stream(ALL_APPLICABLE_FLAGS)
            .filter(flags::contains)
            .forEach(flag -> updateQuery.and(set(FLAG_TO_STRING_MAP.get(flag), true)));
        return updateQuery;
    }
}
