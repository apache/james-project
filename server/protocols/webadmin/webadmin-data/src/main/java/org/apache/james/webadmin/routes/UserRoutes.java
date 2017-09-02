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
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.AddUserRequest;
import org.apache.james.webadmin.dto.UserResponse;
import org.apache.james.webadmin.service.UserService;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Service;

@Api(tags = "Users")
@Path(UserRoutes.USERS)
@Produces("application/json")
public class UserRoutes implements Routes {

    private static final String USER_NAME = ":userName";
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRoutes.class);

    public static final String USERS = "/users";

    private final UserService userService;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<AddUserRequest> jsonExtractor;

    private Service service;

    @Inject
    public UserRoutes(UserService userService, JsonTransformer jsonTransformer) {
        this.userService = userService;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(AddUserRequest.class);
    }

    @Override
    public void define(Service service) {
        this.service = service;

        defineGetUsers();

        defineCreateUser();

        defineDeleteUser();
    }

    @DELETE
    @Path("/{username}")
    @ApiOperation(value = "Deleting an user")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "OK. User is removed."),
            @ApiResponse(code = 400, message = "Invalid input user."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteUser() {
        service.delete(USERS + SEPARATOR + USER_NAME, this::removeUser);
    }

    @PUT
    @Path("/{username}")
    @ApiOperation(value = "Creating an user")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "org.apache.james.webadmin.dto.AddUserRequest", paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "OK. New user is added."),
            @ApiResponse(code = 400, message = "Invalid input user."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineCreateUser() {
        service.put(USERS + SEPARATOR + USER_NAME, this::upsertUser);
    }

    @GET
    @ApiOperation(value = "Getting all users")
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "OK.", response = UserResponse.class),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetUsers() {
        service.get(USERS,
            (request, response) -> userService.getUsers(),
            jsonTransformer);
    }

    private String removeUser(Request request, Response response) {
        String username = request.params(USER_NAME);
        try {
            userService.removeUser(username);
            response.status(204);
            return Constants.EMPTY_BODY;
        } catch (UsersRepositoryException e) {
            response.status(204);
            return "The user " + username + " does not exists";
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid user path", e);
            response.status(400);
            return Constants.EMPTY_BODY;
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
            return Constants.EMPTY_BODY;
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid user path", e);
            response.status(400);
            return Constants.EMPTY_BODY;
        }
    }



}
