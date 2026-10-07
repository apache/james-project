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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
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
     * Users sorted alphabetically, strictly after the anchor, optionally matching the query.
     *
     * The condition is applied after the repository pagination: repository pages are read until 'limit' users
     * matching the condition are found, or the repository is exhausted.
     */
    public List<UserResponse> getUsers(Optional<String> query, Optional<Username> anchor, Optional<Integer> limit, Predicate<Username> condition) {
        List<UserResponse> result = new ArrayList<>();
        Optional<Username> pageAnchor = anchor;
        while (true) {
            List<Username> page = readPage(query, pageAnchor, limit);
            page.stream()
                .filter(condition)
                .limit(limit.map(value -> value - result.size()).orElse(Integer.MAX_VALUE))
                .map(Username::asString)
                .map(UserResponse::new)
                .forEach(result::add);

            boolean exhausted = limit.map(value -> page.size() < value).orElse(true);
            boolean full = limit.map(value -> result.size() >= value).orElse(false);
            if (exhausted || full) {
                return ImmutableList.copyOf(result);
            }
            pageAnchor = Optional.of(page.get(page.size() - 1));
        }
    }

    private List<Username> readPage(Optional<String> query, Optional<Username> anchor, Optional<Integer> limit) {
        return Flux.from(query
                .map(q -> usersRepository.searchPaginated(q, anchor, limit))
                .orElseGet(() -> usersRepository.listPaginated(anchor, limit)))
            .collectList()
            .block();
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
