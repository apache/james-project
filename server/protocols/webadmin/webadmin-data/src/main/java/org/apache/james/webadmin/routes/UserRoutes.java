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
import static spark.Spark.halt;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.user.api.InvalidUsernameException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.AddUserRequest;
import org.apache.james.webadmin.dto.UserResponse;
import org.apache.james.webadmin.service.UserService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.HaltException;
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
    private final CanSendFrom canSendFrom;
    private final JsonExtractor<AddUserRequest> jsonExtractor;

    private Service service;

    @Inject
    public UserRoutes(UserService userService, CanSendFrom canSendFrom, JsonTransformer jsonTransformer) {
        this.userService = userService;
        this.jsonTransformer = jsonTransformer;
        this.canSendFrom = canSendFrom;
        this.jsonExtractor = new JsonExtractor<>(AddUserRequest.class);
    }

    @Override
    public String getBasePath() {
        return USERS;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        defineGetUsers();

        defineCreateUser();

        defineDeleteUser();

        defineAllowedFromHeaders();

        defineUserExist();
    }

    @DELETE
    @Path("/{username}")
    @ApiOperation(value = "Deleting an user")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. User is removed."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid input user."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteUser() {
        service.delete(USERS + SEPARATOR + USER_NAME, this::removeUser);
    }

    @HEAD
    @Path("/{username}")
    @ApiOperation(value = "Testing an user existence")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK. User exists."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid input user."),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "User does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUserExist() {
        service.head(USERS + SEPARATOR + USER_NAME, this::userExist);
    }

    @PUT
    @Path("/{username}")
    @ApiOperation(value = "Creating an user")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataTypeClass = AddUserRequest.class, paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. New user is added."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid input user."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                message = "Internal server error - Something went bad on the server side.")
    })
    public void defineCreateUser() {
        service.put(USERS + SEPARATOR + USER_NAME, this::upsertUser);
    }

    @GET
    @ApiOperation(value = "Getting all users")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK.", response = UserResponse.class),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetUsers() {
        service.get(USERS,
            (request, response) -> userService.getUsers(),
            jsonTransformer);
    }

    @GET
    @Path("/{username}/allowedFromHeaders")
    @ApiOperation(value = "List all possible From header value for an existing user")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK.", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "user is not valid."),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "user does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public void defineAllowedFromHeaders() {
        service.get(USERS + SEPARATOR + USER_NAME + SEPARATOR + "allowedFromHeaders",
            this::allowedFromHeaders,
            jsonTransformer);
    }

    private String removeUser(Request request, Response response) {
        Username username = extractUsername(request);
        try {
            userService.removeUser(username);
            return Responses.returnNoContent(response);
        } catch (UsersRepositoryException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NO_CONTENT_204)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("The user " + username + " does not exists")
                .cause(e)
                .haltError();
        }
    }

    private String userExist(Request request, Response response) throws UsersRepositoryException {
        Username username = extractUsername(request);
        if (userService.userExists(username)) {
            response.status(HttpStatus.OK_200);
        } else {
            response.status(HttpStatus.NOT_FOUND_404);
        }
        return Constants.EMPTY_BODY;
    }

    private HaltException upsertUser(Request request, Response response) throws JsonExtractException {
        Username username = extractUsername(request);
        try {
            userService.upsertUser(username,
                jsonExtractor.parse(request.body()).getPassword());

            return halt(HttpStatus.NO_CONTENT_204);
        } catch (InvalidUsernameException e) {
            LOGGER.info("Invalid username", e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Username supplied is invalid")
                .cause(e)
                .haltError();
        } catch (UsersRepositoryException e) {
            String errorMessage = String.format("Error while upserting user '%s'", username);
            LOGGER.info(errorMessage, e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorType.SERVER_ERROR)
                .message(errorMessage)
                .cause(e)
                .haltError();
        }
    }

    private List<String> allowedFromHeaders(Request request, Response response) {
        Username username = extractUsername(request);

        try {
            if (!userService.userExists(username)) {
                LOGGER.info("Allowed FROM headers requested for an unknown user: '{}", username.asString());
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message(String.format("user '%s' does not exist", username.asString()))
                    .haltError();
            }

            return canSendFrom
                .allValidFromAddressesForUser(username)
                .map(MailAddress::asString)
                .collect(Guavate.toImmutableList());
        } catch (RecipientRewriteTable.ErrorMappingException | RecipientRewriteTableException | UsersRepositoryException e) {
            String errorMessage = String.format("Error while listing allowed From headers for user '%s'", username);
            LOGGER.info(errorMessage, e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorType.SERVER_ERROR)
                .message(errorMessage)
                .cause(e)
                .haltError();
        }
    }

    private Username extractUsername(Request request) {
        return Username.of(request.params(USER_NAME));
    }
}