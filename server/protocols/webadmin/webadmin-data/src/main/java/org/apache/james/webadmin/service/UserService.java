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

package org.apache.james.webadmin.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.api.model.UsernamePredicate;
import org.apache.james.webadmin.dto.UserResponse;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

public class UserService {
    private final UsersRepository usersRepository;

    @Inject
    public UserService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    /**
     * Users matching all the predicates, sorted alphabetically, strictly after the anchor.
     *
     * The condition can not be applied by the repository: when supplied, users are read without limit then
     * filtered, and the condition is only evaluated until 'limit' users matched.
     */
    public List<UserResponse> getUsers(List<UsernamePredicate> predicates, Optional<Username> anchor, Optional<Integer> limit,
                                       Optional<? extends Predicate<Username>> condition) {
        Flux<Username> users = condition
            .map(c -> applyLimit(Flux.from(usersRepository.searchPaginated(predicates, anchor, Optional.empty())).filter(c), limit))
            .orElseGet(() -> Flux.from(usersRepository.searchPaginated(predicates, anchor, limit)));

        return users.map(Username::asString)
            .map(UserResponse::new)
            .collect(ImmutableList.toImmutableList())
            .block();
    }

    private Flux<Username> applyLimit(Flux<Username> users, Optional<Integer> limit) {
        return limit.map(value -> users.take(value, true))
            .orElse(users);
    }

    public void removeUser(Username username) throws UsersRepositoryException {
        usersRepository.removeUser(username);
    }

    public void upsertUser(Username username, char[] password) throws Exception {
        User user = usersRepository.getUserByName(username);
        if (user == null) {
            usersRepository.addUser(username, new String(password));
        } else {
            user.setPassword(new String(password));
            usersRepository.updateUser(user);
        }
    }

    public boolean verifyUser(Username username, String password) throws UsersRepositoryException {
        return usersRepository.test(username, password)
            .isPresent();
    }

    public boolean userExists(Username username) throws UsersRepositoryException {
        return usersRepository.contains(username);
    }

    public void insertUser(Username username, char[] password) throws Exception {
        usersRepository.addUser(username, new String(password));
    }

}
