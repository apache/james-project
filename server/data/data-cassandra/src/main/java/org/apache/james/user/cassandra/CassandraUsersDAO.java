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

package org.apache.james.user.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.ALGORITHM;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.NAME;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.PASSWORD;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.REALNAME;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.TABLE_NAME;

import java.util.Iterator;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.user.lib.model.DefaultUser;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

public class CassandraUsersDAO implements UsersDAO {
    private static final String DEFAULT_ALGO_VALUE = "SHA-512";

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement getUserStatement;
    private final PreparedStatement removeUserStatement;
    private final PreparedStatement countUserStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement insertStatement;

    @Inject
    public CassandraUsersDAO(Session session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.getUserStatement = prepareGetUserStatement(session);
        this.removeUserStatement = prepareRemoveUserStatement(session);
        this.countUserStatement = prepareCountStatement(session);
        this.listStatement = prepareListStatement(session);
        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(NAME, bindMarker(NAME))
            .value(REALNAME, bindMarker(REALNAME))
            .value(PASSWORD, bindMarker(PASSWORD))
            .value(ALGORITHM, bindMarker(ALGORITHM)));
    }

    private PreparedStatement prepareListStatement(Session session) {
        return session.prepare(select(NAME)
            .from(TABLE_NAME));
    }

    private PreparedStatement prepareCountStatement(Session session) {
        return session.prepare(select().countAll().from(TABLE_NAME));
    }

    private PreparedStatement prepareRemoveUserStatement(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(NAME, bindMarker(NAME))));
    }

    private PreparedStatement prepareGetUserStatement(Session session) {
        return session.prepare(select(NAME, PASSWORD, ALGORITHM)
            .from(TABLE_NAME)
            .where(eq(NAME, bindMarker(NAME))));
    }

    @Override
    public Optional<DefaultUser> getUserByName(Username name) {
        return executor.executeSingleRow(
                getUserStatement.bind()
                    .setString(NAME, name.asString()))
            .map(row -> new DefaultUser(Username.of(row.getString(NAME)), row.getString(PASSWORD), row.getString(ALGORITHM)))
            .blockOptional();
    }

    @Override
    public void updateUser(User user) {
        Preconditions.checkArgument(user instanceof DefaultUser);
        DefaultUser defaultUser = (DefaultUser) user;
        executor.executeVoid(
                insertStatement.bind()
                    .setString(REALNAME, defaultUser.getUserName().asString())
                    .setString(PASSWORD, defaultUser.getHashedPassword())
                    .setString(ALGORITHM, defaultUser.getHashAlgorithm())
                    .setString(NAME, defaultUser.getUserName().asString()))
            .block();
    }

    @Override
    public void removeUser(Username name) {
        executor.executeVoid(
            removeUserStatement.bind()
                .setString(NAME, name.asString()))
            .block();
    }

    @Override
    public boolean contains(Username name) {
        return getUserByName(name).isPresent();
    }

    @Override
    public int countUsers() {
        return executor.executeSingleRow(countUserStatement.bind())
            .map(row -> Ints.checkedCast(row.getLong(0)))
            .block();
    }

    @Override
    public Iterator<Username> list() {
        return executor.executeRows(listStatement.bind())
            .map(row -> row.getString(NAME))
            .map(Username::of)
            .toIterable()
            .iterator();
    }

    @Override
    public void addUser(Username username, String password) {
        DefaultUser user = new DefaultUser(username, DEFAULT_ALGO_VALUE);
        user.setPassword(password);
        executor.executeReturnApplied(
            insertStatement.bind()
                .setString(NAME, user.getUserName().asString())
                .setString(REALNAME, user.getUserName().asString())
                .setString(PASSWORD, user.getHashedPassword())
                .setString(ALGORITHM, user.getHashAlgorithm()))
            .block();
    }

    @Override
    public boolean getDefaultVirtualHostingValue() {
        return true;
    }
}
