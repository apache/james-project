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

package org.apache.james.webadmin.routes;

import static org.apache.james.webadmin.Constants.SEPARATOR;

import javax.inject.Inject;

import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.model.AddUserRequest;
import org.apache.james.webadmin.service.UserService;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Service;

public class UserRoutes implements Routes {

    private static final String USER_NAME = ":userName";
    private static final String EMPTY_BODY = "";
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRoutes.class);

    public static final String USERS = "/users";

    private final UserService userService;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<AddUserRequest> jsonExtractor;

    @Inject
    public UserRoutes(UserService userService, JsonTransformer jsonTransformer) {
        this.userService = userService;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(AddUserRequest.class);
    }

    @Override
    public void define(Service service) {
        service.get(USERS,
            (request, response) -> userService.getUsers(),
            jsonTransformer);

        service.put(USERS + SEPARATOR + USER_NAME, this::upsertUser);

        service.delete(USERS + SEPARATOR + USER_NAME, this::removeUser);
    }

    private String removeUser(Request request, Response response) {
        String username = request.params(USER_NAME);
        try {
            userService.removeUser(username);
            response.status(204);
            return EMPTY_BODY;
        } catch (UsersRepositoryException e) {
            response.status(204);
            return "The user " + username + " does not exists";
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid user path", e);
            response.status(400);
            return EMPTY_BODY;
        }
    }

    private String upsertUser(Request request, Response response) throws UsersRepositoryException {
        try {
            return userService.upsertUser(request.params(USER_NAME),
                jsonExtractor.parse(request.body()).getPassword(),
                response);
        } catch (JsonExtractException e) {
            LOGGER.info("Error while deserializing addUser request", e);
            response.status(400);
            return EMPTY_BODY;
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid user path", e);
            response.status(400);
            return EMPTY_BODY;
        }
    }



}
