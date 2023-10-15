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


import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.ALGORITHM;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.AUTHORIZED_USERS;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.DELEGATED_USERS;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.NAME;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.PASSWORD;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.REALNAME;
import static org.apache.james.user.cassandra.tables.CassandraUserTable.TABLE_NAME;

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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
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
    private final PreparedStatement removeAllAuthorizedUsersStatement;
    private final PreparedStatement getAuthorizedUsersStatement;
    private final PreparedStatement addAuthorizedUsersStatement;
    private final PreparedStatement removeAuthorizedUsersStatement;

    private final PreparedStatement getDelegatedToUsersStatement;
    private final PreparedStatement addDelegatedToUsersStatement;
    private final PreparedStatement removeDelegatedToUsersStatement;

    private final Algorithm preferredAlgorithm;
    private final HashingMode fallbackHashingMode;

    @Inject
    public CassandraUsersDAO(CqlSession session, CassandraRepositoryConfiguration configuration) {
        this.executor = new CassandraAsyncExecutor(session);
        this.preferredAlgorithm = configuration.getPreferredAlgorithm();
        this.fallbackHashingMode = configuration.getFallbackHashingMode();

        this.getUserStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(NAME, PASSWORD, ALGORITHM)
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .build());

        this.updateUserStatement = session.prepare(update(TABLE_NAME)
            .setColumn(REALNAME, bindMarker(REALNAME))
            .setColumn(PASSWORD, bindMarker(PASSWORD))
            .setColumn(ALGORITHM, bindMarker(ALGORITHM))
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .ifExists()
            .build());

        this.removeUserStatement = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .ifExists()
            .build());

        this.countUserStatement = session.prepare(selectFrom(TABLE_NAME)
            .countAll()
            .build());

        this.listStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(NAME)
            .build());

        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(NAME, bindMarker(NAME))
            .value(REALNAME, bindMarker(REALNAME))
            .value(PASSWORD, bindMarker(PASSWORD))
            .value(ALGORITHM, bindMarker(ALGORITHM))
            .ifNotExists()
            .build());

        this.removeAllAuthorizedUsersStatement = session.prepare(deleteFrom(TABLE_NAME)
            .column(AUTHORIZED_USERS)
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .build());

        this.getAuthorizedUsersStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(AUTHORIZED_USERS)
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .build());

        this.addAuthorizedUsersStatement = session.prepare(update(TABLE_NAME)
            .append(AUTHORIZED_USERS, bindMarker(AUTHORIZED_USERS))
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .build());

        this.removeAuthorizedUsersStatement = session.prepare(update(TABLE_NAME)
            .remove(AUTHORIZED_USERS, bindMarker(AUTHORIZED_USERS))
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .build());

        this.getDelegatedToUsersStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(DELEGATED_USERS)
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .build());

        this.addDelegatedToUsersStatement = session.prepare(update(TABLE_NAME)
            .append(DELEGATED_USERS, bindMarker(DELEGATED_USERS))
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .build());

        this.removeDelegatedToUsersStatement = session.prepare(update(TABLE_NAME)
            .remove(DELEGATED_USERS, bindMarker(DELEGATED_USERS))
            .whereColumn(NAME).isEqualTo(bindMarker(NAME))
            .build());
    }

    @VisibleForTesting
    public CassandraUsersDAO(CqlSession session) {
        this(session, CassandraRepositoryConfiguration.DEFAULT);
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

    public Mono<Boolean> exist(Username name) {
        return executor.executeReturnExists(getUserStatement.bind()
            .setString(NAME, name.asString()));
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

    public Mono<Void> addAuthorizedUsers(Username baseUser, Username userWithAccess, boolean targetUserExists) {
        BatchStatementBuilder batchBuilder = new BatchStatementBuilder(BatchType.LOGGED);
        batchBuilder.addStatement(addAuthorizedUsersStatement.bind()
            .setString(NAME, baseUser.asString())
            .setSet(AUTHORIZED_USERS, ImmutableSet.of(userWithAccess.asString()), String.class));
        if (targetUserExists) {
            batchBuilder.addStatement(addDelegatedToUsersStatement.bind()
                .setString(NAME, userWithAccess.asString())
                .setSet(DELEGATED_USERS, ImmutableSet.of(baseUser.asString()), String.class));
        }

        return executor.executeVoid(batchBuilder.build());
    }

    public Mono<Void> removeAuthorizedUser(Username baseUser, Username userWithAccess) {
        return executor.executeVoid(new BatchStatementBuilder(BatchType.LOGGED)
            .addStatement(removeAuthorizedUsersStatement.bind()
                .setString(NAME, baseUser.asString())
                .setSet(AUTHORIZED_USERS, ImmutableSet.of(userWithAccess.asString()), String.class))
            .addStatement(removeDelegatedToUsersStatement.bind()
                .setString(NAME, userWithAccess.asString())
                .setSet(DELEGATED_USERS, ImmutableSet.of(baseUser.asString()), String.class))
            .build());
    }

    public Mono<Void> removeAllAuthorizedUsers(Username baseUser) {
        return getAuthorizedUsers(baseUser)
            .collectList()
            .map(authorizedList -> {
                BatchStatementBuilder batch = new BatchStatementBuilder(BatchType.LOGGED);
                authorizedList.forEach(username -> batch.addStatement(
                    removeDelegatedToUsersStatement.bind()
                        .setString(NAME, username.asString())
                        .setSet(DELEGATED_USERS, ImmutableSet.of(baseUser.asString()), String.class)));
                batch.addStatement(removeAllAuthorizedUsersStatement.bind()
                    .setString(NAME, baseUser.asString()));
                return batch.build();
            })
            .flatMap(executor::executeVoid);
    }

    public Flux<Username> getAuthorizedUsers(Username name) {
        return executor.executeSingleRow(
                getAuthorizedUsersStatement.bind()
                    .setString(NAME, name.asString()))
            .mapNotNull(row -> row.getSet(AUTHORIZED_USERS, String.class))
            .flatMapIterable(set -> set)
            .map(Username::of);
    }

    public Mono<Void> removeDelegatedToUser(Username baseUser, Username delegatedToUser) {
        return executor.executeVoid(new BatchStatementBuilder(BatchType.LOGGED)
            .addStatement(removeAuthorizedUsersStatement.bind()
                .setString(NAME, delegatedToUser.asString())
                .setSet(AUTHORIZED_USERS, ImmutableSet.of(baseUser.asString()), String.class))
            .addStatement(removeDelegatedToUsersStatement.bind()
                .setString(NAME, baseUser.asString())
                .setSet(DELEGATED_USERS, ImmutableSet.of(delegatedToUser.asString()), String.class))
            .build());
    }

    public Flux<Username> getDelegatedToUsers(Username name) {
        return executor.executeSingleRow(
                getDelegatedToUsersStatement.bind()
                    .setString(NAME, name.asString()))
            .mapNotNull(row -> row.getSet(DELEGATED_USERS, String.class))
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
        return listReactive()
            .toIterable()
            .iterator();
    }

    @Override
    public Flux<Username> listReactive() {
        return executor.executeRows(listStatement.bind())
            .mapNotNull(row -> row.getString(NAME))
            .map(Username::of);
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
