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

import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.util.streams.Iterators;
import org.apache.james.webadmin.dto.UserResponse;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import spark.Response;

public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private static final String EMPTY_BODY = "";
    public static final int MAXIMUM_MAIL_ADDRESS_LENGTH = 255;

    private final UsersRepository usersRepository;

    @Inject
    public UserService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    public List<UserResponse> getUsers() throws UsersRepositoryException {
        return  Optional.ofNullable(usersRepository.list())
            .map(Iterators::toStream)
            .orElse(Stream.of())
            .map(UserResponse::new)
            .collect(Guavate.toImmutableList());
    }

    public void removeUser(String username) throws UsersRepositoryException {
        usernamePreconditions(username);
        usersRepository.removeUser(username);
    }

    public String upsertUser(String username, char[] password, Response response) throws UsersRepositoryException {
        usernamePreconditions(username);
        User user = usersRepository.getUserByName(username);
        try {
            upsert(user, username, password);
            return Responses.returnNoContent(response);
        } catch (UsersRepositoryException e) {
            LOGGER.info("Error creating or updating user : {}", e.getMessage());
            response.status(HttpStatus.CONFLICT_409);
        }
        return EMPTY_BODY;
    }

    private void usernamePreconditions(String username) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(username));
        Preconditions.checkArgument(username.length() < MAXIMUM_MAIL_ADDRESS_LENGTH);
    }

    private void upsert(User user, String username, char[] password) throws UsersRepositoryException {
        if (user == null) {
            usersRepository.addUser(username, new String(password));
        } else {
            user.setPassword(new String(password));
            usersRepository.updateUser(user);
        }
    }
}
