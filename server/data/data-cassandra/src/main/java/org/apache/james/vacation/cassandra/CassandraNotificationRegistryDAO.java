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

package org.apache.james.vacation.cassandra;


import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.RecipientId;
import org.apache.james.vacation.cassandra.tables.CassandraNotificationTable;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;

import reactor.core.publisher.Mono;

public class CassandraNotificationRegistryDAO {

    public static final String TTL = "TTL";
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement registerStatement;
    private final PreparedStatement registerWithTTLStatement;
    private final PreparedStatement isRegisteredStatement;
    private final PreparedStatement flushStatement;

    @Inject
    public CassandraNotificationRegistryDAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.registerStatement = session.prepare(createInsert().build());

        this.registerWithTTLStatement = session.prepare(createInsert().usingTtl(bindMarker(TTL)).build());

        this.isRegisteredStatement = session.prepare(selectFrom(CassandraNotificationTable.TABLE_NAME)
            .all()
            .whereColumn(CassandraNotificationTable.ACCOUNT_ID).isEqualTo(bindMarker(CassandraNotificationTable.ACCOUNT_ID))
            .whereColumn(CassandraNotificationTable.RECIPIENT_ID).isEqualTo(bindMarker(CassandraNotificationTable.RECIPIENT_ID))
            .build());

        this.flushStatement = session.prepare(deleteFrom(CassandraNotificationTable.TABLE_NAME)
            .whereColumn(CassandraNotificationTable.ACCOUNT_ID).isEqualTo(bindMarker(CassandraNotificationTable.ACCOUNT_ID))
            .build());
    }

    private RegularInsert createInsert() {
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
