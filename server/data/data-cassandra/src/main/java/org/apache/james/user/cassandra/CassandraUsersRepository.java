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
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
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
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.model.DefaultUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

public class CassandraUsersRepository extends AbstractUsersRepository {

    private static final String DEFAULT_ALGO_VALUE = "SHA1";
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraUsersRepository.class);

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement getUserStatement;
    private final PreparedStatement updateUserStatement;
    private final PreparedStatement removeUserStatement;
    private final PreparedStatement countUserStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement insertStatement;

    @Inject
    public CassandraUsersRepository(DomainList domainList, Session session) {
        super(domainList);
        this.executor = new CassandraAsyncExecutor(session);
        this.getUserStatement = prepareGetUserStatement(session);
        this.updateUserStatement = prepareUpdateUserStatement(session);
        this.removeUserStatement = prepareRemoveUserStatement(session);
        this.countUserStatement = prepareCountStatement(session);
        this.listStatement = prepareListStatement(session);
        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(NAME, bindMarker(NAME))
            .value(REALNAME, bindMarker(REALNAME))
            .value(PASSWORD, bindMarker(PASSWORD))
            .value(ALGORITHM, bindMarker(ALGORITHM))
            .ifNotExists());
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
            .where(eq(NAME, bindMarker(NAME)))
            .ifExists());
    }

    private PreparedStatement prepareUpdateUserStatement(Session session) {
        return session.prepare(update(TABLE_NAME)
            .with(set(REALNAME, bindMarker(REALNAME)))
            .and(set(PASSWORD, bindMarker(PASSWORD)))
            .and(set(ALGORITHM, bindMarker(ALGORITHM)))
            .where(eq(NAME, bindMarker(NAME)))
            .ifExists());
    }

    private PreparedStatement prepareGetUserStatement(Session session) {
        return session.prepare(select(NAME, PASSWORD, ALGORITHM)
            .from(TABLE_NAME)
            .where(eq(NAME, bindMarker(NAME))));
    }

    @Override
    public User getUserByName(Username name) {
        return executor.executeSingleRow(
                getUserStatement.bind()
                    .setString(NAME, name.asString()))
            .map(row -> new DefaultUser(Username.of(row.getString(NAME)), row.getString(PASSWORD), row.getString(ALGORITHM)))
            .blockOptional()
            .orElse(null);
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        Preconditions.checkArgument(user instanceof DefaultUser);
        DefaultUser defaultUser = (DefaultUser) user;
        boolean executed = executor.executeReturnApplied(
                updateUserStatement.bind()
                    .setString(REALNAME, defaultUser.getUserName().asString())
                    .setString(PASSWORD, defaultUser.getHashedPassword())
                    .setString(ALGORITHM, defaultUser.getHashAlgorithm())
                    .setString(NAME, defaultUser.getUserName().asString()))
            .block();

        if (!executed) {
            throw new UsersRepositoryException("Unable to update user");
        }
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        boolean executed = executor.executeReturnApplied(
            removeUserStatement.bind()
                .setString(NAME, name.asString()))
            .block();

        if (!executed) {
            throw new UsersRepositoryException("unable to remove unknown user " + name.asString());
        }
    }

    @Override
    public boolean contains(Username name) {
        return getUserByName(name) != null;
    }

    @Override
    public boolean test(Username name, String password) throws UsersRepositoryException {
        return Optional.ofNullable(getUserByName(name))
                .map(x -> x.verifyPassword(password))
            .orElseGet(() -> {
                LOGGER.info("Could not retrieve user {}. Password is unverified.", name);
                return false;
            });
    }

    @Override
    public int countUsers() {
        return executor.executeSingleRow(countUserStatement.bind())
            .map(row -> Ints.checkedCast(row.getLong(0)))
            .block();
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        return executor.executeRows(listStatement.bind())
            .map(row -> row.getString(NAME))
            .map(Username::of)
            .toIterable()
            .iterator();
    }

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        assertValid(username);
        doAddUser(username, password);
    }

    @Override
    protected void doAddUser(Username username, String password) throws UsersRepositoryException {
        DefaultUser user = new DefaultUser(username, DEFAULT_ALGO_VALUE);
        user.setPassword(password);
        boolean executed = executor.executeReturnApplied(
            insertStatement.bind()
                .setString(NAME, user.getUserName().asString())
                .setString(REALNAME, user.getUserName().asString())
                .setString(PASSWORD, user.getHashedPassword())
                .setString(ALGORITHM, user.getHashAlgorithm()))
            .block();

        if (!executed) {
            throw new AlreadyExistInUsersRepositoryException("User with username " + username + " already exist!");
        }
    }

    @Override
    protected boolean getDefaultVirtualHostingValue() {
        return true;
    }
}
