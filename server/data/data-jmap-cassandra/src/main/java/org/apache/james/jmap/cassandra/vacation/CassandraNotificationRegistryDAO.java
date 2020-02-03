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

package org.apache.james.jmap.cassandra.vacation;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.ttl;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.RecipientId;
import org.apache.james.jmap.cassandra.vacation.tables.CassandraNotificationTable;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;

import reactor.core.publisher.Mono;

public class CassandraNotificationRegistryDAO {

    public static final String TTL = "TTL";
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement registerStatement;
    private final PreparedStatement registerWithTTLStatement;
    private final PreparedStatement isRegisteredStatement;
    private final PreparedStatement flushStatement;

    @Inject
    public CassandraNotificationRegistryDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.registerStatement = session.prepare(createInsert());

        this.registerWithTTLStatement = session.prepare(createInsert().using(ttl(bindMarker(TTL))));

        this.isRegisteredStatement = session.prepare(select()
            .from(CassandraNotificationTable.TABLE_NAME)
            .where(eq(CassandraNotificationTable.ACCOUNT_ID, bindMarker(CassandraNotificationTable.ACCOUNT_ID)))
            .and(eq(CassandraNotificationTable.RECIPIENT_ID, bindMarker(CassandraNotificationTable.RECIPIENT_ID))));

        this.flushStatement = session.prepare(delete()
            .from(CassandraNotificationTable.TABLE_NAME)
            .where(eq(CassandraNotificationTable.ACCOUNT_ID, bindMarker(CassandraNotificationTable.ACCOUNT_ID))));
    }

    private Insert createInsert() {
        return insertInto(CassandraNotificationTable.TABLE_NAME)
            .value(CassandraNotificationTable.ACCOUNT_ID, bindMarker(CassandraNotificationTable.ACCOUNT_ID))
            .value(CassandraNotificationTable.RECIPIENT_ID, bindMarker(CassandraNotificationTable.RECIPIENT_ID));
    }

    public Mono<Void> register(AccountId accountId, RecipientId recipientId, Optional<Integer> ttl) {
        return cassandraAsyncExecutor.executeVoid(
            ttl.map(value -> registerWithTTLStatement.bind().setInt(TTL, value))
                .orElse(registerStatement.bind())
                .setString(CassandraNotificationTable.ACCOUNT_ID, accountId.getIdentifier())
                .setString(CassandraNotificationTable.RECIPIENT_ID, recipientId.getAsString()));
    }

    public Mono<Boolean> isRegistered(AccountId accountId, RecipientId recipientId) {
        return cassandraAsyncExecutor.executeSingleRowOptional(
            isRegisteredStatement.bind()
                .setString(CassandraNotificationTable.ACCOUNT_ID, accountId.getIdentifier())
                .setString(CassandraNotificationTable.RECIPIENT_ID, recipientId.getAsString()))
            .map(Optional::isPresent);
    }

    public Mono<Void> flush(AccountId accountId) {
        return cassandraAsyncExecutor.executeVoid(
            flushStatement.bind()
                .setString(CassandraNotificationTable.ACCOUNT_ID, accountId.getIdentifier()));
    }
}
