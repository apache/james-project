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

package org.apache.james.jmap.cassandra.access;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.jmap.api.access.AccessTokenRepository.TOKEN_EXPIRATION_IN_MS;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.cassandra.access.table.CassandraAccessTokenTable;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.google.common.primitives.Ints;

import reactor.core.publisher.Mono;

public class CassandraAccessTokenDAO {

    private static final String TTL = "ttl";

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement removeStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final int durationInSeconds;

    @Inject
    public CassandraAccessTokenDAO(CqlSession session, @Named(TOKEN_EXPIRATION_IN_MS) long durationInMilliseconds) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.durationInSeconds = Ints.checkedCast(TimeUnit.MILLISECONDS.toSeconds(durationInMilliseconds));

        this.removeStatement = session.prepare(deleteFrom(CassandraAccessTokenTable.TABLE_NAME)
            .whereColumn(CassandraAccessTokenTable.TOKEN).isEqualTo(bindMarker(CassandraAccessTokenTable.TOKEN))
            .build());

        this.insertStatement = session.prepare(insertInto(CassandraAccessTokenTable.TABLE_NAME)
            .value(CassandraAccessTokenTable.TOKEN, bindMarker(CassandraAccessTokenTable.TOKEN))
            .value(CassandraAccessTokenTable.USERNAME, bindMarker(CassandraAccessTokenTable.USERNAME))
            .usingTtl(bindMarker(TTL))
            .build());

        this.selectStatement = session.prepare(selectFrom(CassandraAccessTokenTable.TABLE_NAME)
            .column(CassandraAccessTokenTable.USERNAME)
            .whereColumn(CassandraAccessTokenTable.TOKEN)
            .isEqualTo(bindMarker(CassandraAccessTokenTable.TOKEN))
            .build());
    }

    public Mono<Void> addToken(Username username, AccessToken accessToken) {
        return cassandraAsyncExecutor.executeVoid(insertStatement.bind()
            .setUuid(CassandraAccessTokenTable.TOKEN, accessToken.asUUID())
            .setString(CassandraAccessTokenTable.USERNAME, username.asString())
            .setInt(TTL, durationInSeconds));
    }

    public Mono<Void> removeToken(AccessToken accessToken) {
        return cassandraAsyncExecutor.executeVoid(removeStatement.bind()
            .setUuid(CassandraAccessTokenTable.TOKEN, accessToken.asUUID()));
    }

    public Mono<Username> getUsernameFromToken(AccessToken accessToken) {
        return cassandraAsyncExecutor.executeSingleRow(selectStatement.bind()
                .set(CassandraAccessTokenTable.TOKEN, accessToken.asUUID(), TypeCodecs.UUID))
            .map(row -> row.get(0, TypeCodecs.TEXT))
            .map(Username::of);
    }
}
