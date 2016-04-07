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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.ttl;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.access.exceptions.AccessTokenAlreadyStored;
import org.apache.james.jmap.api.access.exceptions.InvalidAccessToken;
import org.apache.james.jmap.cassandra.access.table.CassandraAccessTokenTable;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;

public class CassandraAccessTokenRepository implements AccessTokenRepository {

    private static final String TTL = "ttl";

    private final Session session;
    private final PreparedStatement removeStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final int durationInSeconds;

    @Inject
    public CassandraAccessTokenRepository(Session session, @Named(TOKEN_EXPIRATION_IN_MS) long durationInMilliseconds) {
        this.session = session;
        this.durationInSeconds = (int) (durationInMilliseconds / 1000);

        this.removeStatement = this.session.prepare(delete()
            .from(CassandraAccessTokenTable.TABLE_NAME)
            .where(eq(CassandraAccessTokenTable.TOKEN, bindMarker(CassandraAccessTokenTable.TOKEN))));

        this.insertStatement = this.session.prepare(insertInto(CassandraAccessTokenTable.TABLE_NAME)
            .ifNotExists()
            .value(CassandraAccessTokenTable.TOKEN, bindMarker(CassandraAccessTokenTable.TOKEN))
            .value(CassandraAccessTokenTable.USERNAME, bindMarker(CassandraAccessTokenTable.USERNAME))
            .using(ttl(bindMarker(TTL))));

        this.selectStatement = this.session.prepare(select()
            .from(CassandraAccessTokenTable.TABLE_NAME)
            .where(eq(CassandraAccessTokenTable.TOKEN, bindMarker(CassandraAccessTokenTable.TOKEN))));
    }

    @Override
    public void addToken(String username, AccessToken accessToken) throws AccessTokenAlreadyStored {
        Preconditions.checkNotNull(username);
        Preconditions.checkArgument(! username.isEmpty(), "Username should not be empty");
        Preconditions.checkNotNull(accessToken);

        boolean applied = session.execute(insertStatement.bind()
            .setUUID(CassandraAccessTokenTable.TOKEN, accessToken.getToken())
            .setString(CassandraAccessTokenTable.USERNAME, username)
            .setInt(TTL, durationInSeconds))
            .one()
            .getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED);

        if (!applied) {
            throw new AccessTokenAlreadyStored(accessToken);
        }
    }

    @Override
    public void removeToken(AccessToken accessToken) {
        Preconditions.checkNotNull(accessToken);

        session.execute(removeStatement.bind()
            .setUUID(CassandraAccessTokenTable.TOKEN, accessToken.getToken()));
    }

    @Override
    public String getUsernameFromToken(AccessToken accessToken) throws InvalidAccessToken {
        Preconditions.checkNotNull(accessToken);

        return Optional.ofNullable(
            session.execute(
                selectStatement.bind()
                    .setUUID(CassandraAccessTokenTable.TOKEN, accessToken.getToken()))
                .one())
            .map(row -> row.getString(CassandraAccessTokenTable.USERNAME))
            .orElseThrow(() -> new InvalidAccessToken(accessToken));
    }
}
