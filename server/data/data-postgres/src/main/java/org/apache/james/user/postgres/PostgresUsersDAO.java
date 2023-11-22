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

package org.apache.james.user.postgres;

import static org.apache.james.backends.postgres.utils.PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE;
import static org.apache.james.user.postgres.PostgresUserModule.PostgresUserTable.ALGORITHM;
import static org.apache.james.user.postgres.PostgresUserModule.PostgresUserTable.HASHED_PASSWORD;
import static org.apache.james.user.postgres.PostgresUserModule.PostgresUserTable.TABLE_NAME;
import static org.apache.james.user.postgres.PostgresUserModule.PostgresUserTable.USERNAME;
import static org.jooq.impl.DSL.count;

import java.util.Iterator;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.user.lib.model.Algorithm;
import org.apache.james.user.lib.model.DefaultUser;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresUsersDAO implements UsersDAO {
    private final PostgresExecutor postgresExecutor;
    private final Algorithm algorithm;
    private final Algorithm.HashingMode fallbackHashingMode;

    @Inject
    public PostgresUsersDAO(JamesPostgresConnectionFactory jamesPostgresConnectionFactory,
                            PostgresRepositoryConfiguration postgresRepositoryConfiguration) {
        this.postgresExecutor = new PostgresExecutor(jamesPostgresConnectionFactory.getConnection(Optional.empty()));
        this.algorithm = postgresRepositoryConfiguration.getPreferredAlgorithm();
        this.fallbackHashingMode = postgresRepositoryConfiguration.getFallbackHashingMode();
    }

    @Override
    public Optional<? extends User> getUserByName(Username name) {
        return getUserByNameReactive(name).blockOptional();
    }

    private Mono<DefaultUser> getUserByNameReactive(Username name) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.selectFrom(TABLE_NAME)
                .where(USERNAME.eq(name.asString()))))
            .map(record -> new DefaultUser(name, record.get(HASHED_PASSWORD),
                Algorithm.of(record.get(ALGORITHM), fallbackHashingMode), algorithm));
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        Preconditions.checkArgument(user instanceof DefaultUser);
        DefaultUser defaultUser = (DefaultUser) user;

        boolean executed = postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
                .set(HASHED_PASSWORD, defaultUser.getHashedPassword())
                .set(ALGORITHM, defaultUser.getHashAlgorithm().asString())
                .where(USERNAME.eq(user.getUserName().asString()))
                .returning(USERNAME)))
            .map(record -> record.get(USERNAME))
            .blockOptional()
            .isPresent();

        if (!executed) {
            throw new UsersRepositoryException("Unable to update user");
        }
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        boolean executed = postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
                .where(USERNAME.eq(name.asString()))
                .returning(USERNAME)))
            .map(record -> record.get(USERNAME))
            .blockOptional()
            .isPresent();

        if (!executed) {
            throw new UsersRepositoryException("Unable to update user");
        }
    }

    @Override
    public boolean contains(Username name) {
        return getUserByName(name).isPresent();
    }

    @Override
    public int countUsers() {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.select(count()).from(TABLE_NAME)))
            .map(record -> record.get(0, Integer.class))
            .block();
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        return listReactive()
            .toIterable()
            .iterator();
    }

    @Override
    public Flux<Username> listReactive() {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)))
            .map(record -> Username.of(record.get(USERNAME)));
    }

    @Override
    public void addUser(Username username, String password) {
        DefaultUser user = new DefaultUser(username, algorithm, algorithm);
        user.setPassword(password);

        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME, USERNAME, HASHED_PASSWORD, ALGORITHM)
                .values(user.getUserName().asString(), user.getHashedPassword(), user.getHashAlgorithm().asString())))
            .onErrorMap(UNIQUE_CONSTRAINT_VIOLATION_PREDICATE,
                e -> new AlreadyExistInUsersRepositoryException("User with username " + username + " already exist!"))
            .block();
    }
}
