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
import java.util.Locale;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.model.DefaultUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

public class CassandraUsersRepository extends AbstractUsersRepository {

    private static final String DEFAULT_ALGO_VALUE = "SHA1";
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraUsersRepository.class);

    private final CassandraAsyncExecutor executor;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement getUserStatement;
    private final PreparedStatement updateUserStatement;
    private final PreparedStatement removeUserStatement;
    private final PreparedStatement countUserStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement insertStatement;

    @Inject
    public CassandraUsersRepository(Session session, CassandraUtils cassandraUtils) {
        this.executor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;
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
        return session.prepare(select(REALNAME)
            .from(TABLE_NAME));
    }

    private PreparedStatement prepareCountStatement(Session session) {
        return session.prepare(select().countAll().from(TABLE_NAME));
    }

    private PreparedStatement prepareRemoveUserStatement(Session session) {
        return session.prepare( delete()
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
        return session.prepare(select(REALNAME, PASSWORD, ALGORITHM)
            .from(TABLE_NAME)
            .where(eq(NAME, bindMarker(NAME))));
    }

    @Override
    public User getUserByName(String name){
        ResultSet result = executor.execute(getUserStatement.bind()
            .setString(NAME, name.toLowerCase(Locale.US)))
            .join();
        return Optional.ofNullable(result.one())
            .map(row -> new DefaultUser(row.getString(REALNAME), row.getString(PASSWORD), row.getString(ALGORITHM)))
            .filter(user -> user.getUserName().equals(name))
            .orElse(null);
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        Preconditions.checkArgument(user instanceof DefaultUser);
        DefaultUser defaultUser = (DefaultUser) user;
        boolean executed = executor.executeReturnApplied(
                updateUserStatement.bind()
                    .setString(REALNAME, defaultUser.getUserName())
                    .setString(PASSWORD, defaultUser.getHashedPassword())
                    .setString(ALGORITHM, defaultUser.getHashAlgorithm())
                    .setString(NAME, defaultUser.getUserName().toLowerCase(Locale.US)))
            .join();

        if (!executed) {
            throw new UsersRepositoryException("Unable to update user");
        }
    }

    @Override
    public void removeUser(String name) throws UsersRepositoryException {
        boolean executed = executor.executeReturnApplied(
            removeUserStatement.bind()
                .setString(NAME, name))
            .join();

        if (!executed) {
            throw new UsersRepositoryException("unable to remove unknown user " + name);
        }
    }

    @Override
    public boolean contains(String name) {
        return getUserByName(name) != null;
    }

    @Override
    public boolean test(String name, String password) throws UsersRepositoryException {
        return Optional.ofNullable(getUserByName(name))
                .map(x -> x.verifyPassword(password))
            .orElseGet(() -> {
                LOGGER.info("Could not retrieve user {}. Password is unverified.");
                return false;
            });
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        ResultSet result = executor.execute(countUserStatement.bind()).join();
        return Ints.checkedCast(result.one().getLong(0));
    }

    @Override
    public Iterator<String> list() throws UsersRepositoryException {
        ResultSet result = executor.execute(listStatement.bind())
            .join();
        return cassandraUtils.convertToStream(result)
            .map(row -> row.getString(REALNAME))
            .iterator();
    }

    @Override
    public void addUser(String username, String password) throws UsersRepositoryException {
        isValidUsername(username);
        doAddUser(username, password);
    }

    @Override
    protected void doAddUser(String username, String password) throws UsersRepositoryException {
        DefaultUser user = new DefaultUser(username, DEFAULT_ALGO_VALUE);
        user.setPassword(password);
        boolean executed = executor.executeReturnApplied(
            insertStatement.bind()
                .setString(NAME, user.getUserName().toLowerCase(Locale.US))
                .setString(REALNAME, user.getUserName())
                .setString(PASSWORD, user.getHashedPassword())
                .setString(ALGORITHM, user.getHashAlgorithm()))
            .join();

        if (!executed) {
            throw new AlreadyExistInUsersRepositoryException("User with username " + username + " already exist!");
        }
    }

    @Override
    protected boolean getDefaultVirtualHostingValue() {
        return true;
    }
}
