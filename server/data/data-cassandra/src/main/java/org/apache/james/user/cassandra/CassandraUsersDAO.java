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

import static com.datastax.driver.core.querybuilder.QueryBuilder.addAll;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.removeAll;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.ALGORITHM;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.AUTHORIZED_USERS;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.NAME;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.PASSWORD;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.REALNAME;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.TABLE_NAME;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.user.lib.model.Algorithm;
import org.apache.james.user.lib.model.Algorithm.HashingMode;
import org.apache.james.user.lib.model.DefaultUser;
import org.reactivestreams.Publisher;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraUsersDAO implements UsersDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement getUserStatement;
    private final PreparedStatement updateUserStatement;
    private final PreparedStatement removeUserStatement;
    private final PreparedStatement countUserStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement addAuthorizedUserStatement;
    private final PreparedStatement removeAuthorizedUserStatement;
    private final PreparedStatement removeAllAuthorizedUsersStatement;
    private final PreparedStatement getAuthorizedUsersStatement;

    private final Algorithm preferredAlgorithm;
    private final HashingMode fallbackHashingMode;

    @Inject
    public CassandraUsersDAO(Session session, CassandraRepositoryConfiguration configuration) {
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
        this.addAuthorizedUserStatement = prepareAddAuthorizedUserStatement(session);
        this.removeAuthorizedUserStatement = prepareRemoveAuthorizedUserStatement(session);
        this.removeAllAuthorizedUsersStatement = prepareRemoveAllAuthorizedUsersStatement(session);
        this.getAuthorizedUsersStatement = prepareGetAuthorizedUsersStatement(session);
        this.preferredAlgorithm = configuration.getPreferredAlgorithm();
        this.fallbackHashingMode = configuration.getFallbackHashingMode();
    }

    @VisibleForTesting
    public CassandraUsersDAO(Session session) {
        this(session, CassandraRepositoryConfiguration.DEFAULT);
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

    private PreparedStatement prepareAddAuthorizedUserStatement(Session session) {
        return session.prepare(update(TABLE_NAME)
            .with(addAll(AUTHORIZED_USERS, bindMarker(AUTHORIZED_USERS)))
            .where(eq(NAME, bindMarker(NAME))));
    }

    private PreparedStatement prepareRemoveAuthorizedUserStatement(Session session) {
        return session.prepare(update(TABLE_NAME)
            .with(removeAll(AUTHORIZED_USERS, bindMarker(AUTHORIZED_USERS)))
            .where(eq(NAME, bindMarker(NAME))));
    }

    private PreparedStatement prepareRemoveAllAuthorizedUsersStatement(Session session) {
        return session.prepare(delete(AUTHORIZED_USERS)
            .from(TABLE_NAME)
            .where(eq(NAME, bindMarker(NAME))));
    }

    private PreparedStatement prepareGetAuthorizedUsersStatement(Session session) {
        return session.prepare(select(AUTHORIZED_USERS)
            .from(TABLE_NAME)
            .where(eq(NAME, bindMarker(NAME))));
    }

    @Override
    public Optional<DefaultUser> getUserByName(Username name) {
        return getUserByNameReactive(name)
            .blockOptional();
    }

    private Mono<DefaultUser> getUserByNameReactive(Username name) {
        return executor.executeSingleRow(
            getUserStatement.bind()
                .setString(NAME, name.asString()))
            .map(row -> new DefaultUser(Username.of(row.getString(NAME)), row.getString(PASSWORD),
                Algorithm.of(row.getString(ALGORITHM), fallbackHashingMode), preferredAlgorithm));
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        Preconditions.checkArgument(user instanceof DefaultUser);
        DefaultUser defaultUser = (DefaultUser) user;
        boolean executed = executor.executeReturnApplied(
                updateUserStatement.bind()
                    .setString(REALNAME, defaultUser.getUserName().asString())
                    .setString(PASSWORD, defaultUser.getHashedPassword())
                    .setString(ALGORITHM, defaultUser.getHashAlgorithm().asString())
                    .setString(NAME, defaultUser.getUserName().asString()))
            .block();

        if (!executed) {
            throw new UsersRepositoryException("Unable to update user");
        }
    }

    public Mono<Void> addAuthorizedUsers(Username baseUser, Username userWithAccess) {
        return executor.executeVoid(
                addAuthorizedUserStatement.bind()
                    .setSet(AUTHORIZED_USERS, Collections.singleton(userWithAccess.asString()))
                    .setString(NAME, baseUser.asString()));
    }

    public Mono<Void> removeAuthorizedUser(Username baseUser, Username userWithAccess) {
        return executor.executeVoid(
            removeAuthorizedUserStatement.bind()
                .setSet(AUTHORIZED_USERS, Collections.singleton(userWithAccess.asString()))
                .setString(NAME, baseUser.asString()));
    }

    public Mono<Void> removeAllAuthorizedUsers(Username baseUser) {
        return executor.executeVoid(
            removeAllAuthorizedUsersStatement.bind()
                .setString(NAME, baseUser.asString()));
    }

    public Flux<Username> getAuthorizedUsers(Username name) {
        return executor.executeSingleRow(
                getAuthorizedUsersStatement.bind()
                    .setString(NAME, name.asString()))
            .map(row -> row.getSet(AUTHORIZED_USERS, String.class))
            .flatMapIterable(set -> set)
            .map(Username::of);
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
        return getUserByName(name).isPresent();
    }

    @Override
    public Publisher<Boolean> containsReactive(Username name) {
        return getUserByNameReactive(name).hasElement();
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
    public void addUser(Username username, String password) throws UsersRepositoryException {
        DefaultUser user = new DefaultUser(username, preferredAlgorithm, preferredAlgorithm);
        user.setPassword(password);
        boolean executed = executor.executeReturnApplied(
            insertStatement.bind()
                .setString(NAME, user.getUserName().asString())
                .setString(REALNAME, user.getUserName().asString())
                .setString(PASSWORD, user.getHashedPassword())
                .setString(ALGORITHM, user.getHashAlgorithm().asString()))
            .block();

        if (!executed) {
            throw new AlreadyExistInUsersRepositoryException("User with username " + username + " already exist!");
        }
    }

    @Override
    public boolean getDefaultVirtualHostingValue() {
        return true;
    }
}
