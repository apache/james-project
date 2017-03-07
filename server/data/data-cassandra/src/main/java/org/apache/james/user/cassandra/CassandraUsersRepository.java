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

import javax.annotation.Resource;
import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.model.DefaultUser;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

public class CassandraUsersRepository extends AbstractUsersRepository {

    private static final String DEFAULT_ALGO_VALUE = "SHA1";

    private Session session;

    @Inject
    @Resource
    public void setSession(Session session) {
        this.session = session;
    }
    
    @Override
    public User getUserByName(String name){
        ResultSet result = session.execute(
                select(REALNAME, PASSWORD, ALGORITHM)
                .from(TABLE_NAME)
                .where(eq(NAME, name.toLowerCase())));
        return Optional.ofNullable(result.one())
            .map(row -> new DefaultUser(row.getString(REALNAME), row.getString(PASSWORD), row.getString(ALGORITHM)))
            .filter(user -> user.getUserName().equals(name))
            .orElse(null);
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        Preconditions.checkArgument(user instanceof DefaultUser);
        DefaultUser defaultUser = (DefaultUser) user;
        boolean executed = session.execute(
                update(TABLE_NAME)
                    .with(set(REALNAME, defaultUser.getUserName()))
                    .and(set(PASSWORD, defaultUser.getHashedPassword()))
                    .and(set(ALGORITHM, defaultUser.getHashAlgorithm()))
                    .where(eq(NAME, defaultUser.getUserName().toLowerCase()))
                    .ifExists())
                .one()
                .getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED);

        if (!executed) {
            throw new UsersRepositoryException("Unable to update user");
        }
    }

    @Override
    public void removeUser(String name) throws UsersRepositoryException {
        boolean executed = session.execute(
            delete()
                .from(TABLE_NAME)
                .where(eq(NAME, name))
                .ifExists())
            .one()
            .getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED);

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
                .orElse(false);
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        ResultSet result = session.execute(select().countAll().from(TABLE_NAME));
        return Ints.checkedCast(result.one().getLong(0));
    }

    @Override
    public Iterator<String> list() throws UsersRepositoryException {
        ResultSet result = session.execute(
                select(REALNAME)
                .from(TABLE_NAME));
        return CassandraUtils.convertToStream(result)
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
        boolean executed = session.execute(
            insertInto(TABLE_NAME)
                .value(NAME, user.getUserName().toLowerCase())
                .value(REALNAME, user.getUserName())
                .value(PASSWORD, user.getHashedPassword())
                .value(ALGORITHM, user.getHashAlgorithm())
                .ifNotExists())
            .one()
            .getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED);

        if (!executed) {
            throw new AlreadyExistInUsersRepositoryException("User with username " + username + " already exist!");
        }
    }

    @Override
    protected boolean getDefaultVirtualHostingValue() {
        return true;
    }
}
