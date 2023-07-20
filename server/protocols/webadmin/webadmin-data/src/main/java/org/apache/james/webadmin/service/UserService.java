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
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.util.streams.Iterators;
import org.apache.james.webadmin.dto.UserResponse;

import com.google.common.collect.ImmutableList;

public class UserService {
    private final UsersRepository usersRepository;

    @Inject
    public UserService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    public List<UserResponse> getUsers() throws UsersRepositoryException {
        return  Optional.ofNullable(usersRepository.list())
            .map(Iterators::toStream)
            .orElse(Stream.of())
            .map(Username::asString)
            .map(UserResponse::new)
            .collect(ImmutableList.toImmutableList());
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
