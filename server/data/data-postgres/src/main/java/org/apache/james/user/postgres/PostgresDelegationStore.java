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

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.UsersRepository;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class PostgresDelegationStore implements DelegationStore {
    public interface UserExistencePredicate {
        Mono<Boolean> exists(Username username);
    }

    public static class UserExistencePredicateImplementation implements UserExistencePredicate {
        private final UsersRepository usersRepository;

        @Inject
        UserExistencePredicateImplementation(UsersRepository usersRepository) {
            this.usersRepository = usersRepository;
        }

        @Override
        public Mono<Boolean> exists(Username username) {
            return Mono.from(usersRepository.containsReactive(username));
        }
    }

    private final PostgresUsersDAO postgresUsersDAO;
    private final UserExistencePredicate userExistencePredicate;

    @Inject
    public PostgresDelegationStore(PostgresUsersDAO postgresUsersDAO, UserExistencePredicate userExistencePredicate) {
        this.postgresUsersDAO = postgresUsersDAO;
        this.userExistencePredicate = userExistencePredicate;
    }

    @Override
    public Publisher<Username> authorizedUsers(Username baseUser) {
        return postgresUsersDAO.getAuthorizedUsers(baseUser);
    }

    @Override
    public Publisher<Void> clear(Username baseUser) {
        return postgresUsersDAO.removeAllAuthorizedUsers(baseUser);
    }

    @Override
    public Publisher<Void> addAuthorizedUser(Username baseUser, Username userWithAccess) {
        return userExistencePredicate.exists(userWithAccess)
            .flatMap(targetUserExists -> postgresUsersDAO.addAuthorizedUser(baseUser, userWithAccess, targetUserExists));
    }

    @Override
    public Publisher<Void> removeAuthorizedUser(Username baseUser, Username userWithAccess) {
        return postgresUsersDAO.removeAuthorizedUser(baseUser, userWithAccess);
    }

    @Override
    public Publisher<Username> delegatedUsers(Username baseUser) {
        return postgresUsersDAO.getDelegatedToUsers(baseUser);
    }

    @Override
    public Publisher<Void> removeDelegatedUser(Username baseUser, Username delegatedToUser) {
        return postgresUsersDAO.removeDelegatedToUser(baseUser, delegatedToUser);
    }
}
