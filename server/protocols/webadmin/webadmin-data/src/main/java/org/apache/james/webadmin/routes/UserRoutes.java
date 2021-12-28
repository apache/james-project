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

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.InvalidUsernameException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.AddUserRequest;
import org.apache.james.webadmin.dto.VerifyUserRequest;
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

import com.google.common.collect.ImmutableList;

import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class UserRoutes implements Routes {

    private static final String USER_NAME = ":userName";
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRoutes.class);

    public static final String USERS = "/users";
    private static final String FORCE_PARAM = "force";
    private static final String VERIFY = "verify";

    private final UserService userService;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<VerifyUserRequest> jsonExtractorVerify;
    private final CanSendFrom canSendFrom;
    private final JsonExtractor<AddUserRequest> jsonExtractor;

    private Service service;

    @Inject
    public UserRoutes(UserService userService, CanSendFrom canSendFrom, JsonTransformer jsonTransformer) {
        this.userService = userService;
        this.jsonTransformer = jsonTransformer;
        this.canSendFrom = canSendFrom;
        this.jsonExtractor = new JsonExtractor<>(AddUserRequest.class);
        this.jsonExtractorVerify = new JsonExtractor<>(VerifyUserRequest.class);
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

        defineVerifyUsers();
    }

    public void defineVerifyUsers() {
        service.post(USERS + SEPARATOR + USER_NAME + SEPARATOR + VERIFY, this::verifyUser);
    }

    public void defineDeleteUser() {
        service.delete(USERS + SEPARATOR + USER_NAME, this::removeUser);
    }

    public void defineUserExist() {
        service.head(USERS + SEPARATOR + USER_NAME, this::userExist);
    }

    public void defineCreateUser() {
        service.put(USERS + SEPARATOR + USER_NAME, this::upsertUser);
    }

    public void defineGetUsers() {
        service.get(USERS,
            (request, response) -> userService.getUsers(),
            jsonTransformer);
    }

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

    private HaltException upsertUser(Request request, Response response) throws Exception {
        Username username = extractUsername(request);
        try {
            boolean isForced = request.queryParams().contains(FORCE_PARAM);
            if (isForced) {
                userService.upsertUser(username, jsonExtractor.parse(request.body()).getPassword());
            } else {
                userService.insertUser(username, jsonExtractor.parse(request.body()).getPassword());
            }
            return halt(HttpStatus.NO_CONTENT_204);
        } catch (InvalidUsernameException e) {
            LOGGER.info("Invalid username", e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Username supplied is invalid")
                .cause(e)
                .haltError();
        } catch (AlreadyExistInUsersRepositoryException e) {
            LOGGER.info(e.getMessage());
            throw ErrorResponder.builder()
                    .statusCode(HttpStatus.CONFLICT_409)
                    .type(ErrorType.WRONG_STATE)
                    .message(e.getMessage())
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

    private String verifyUser(Request request, Response response) throws UsersRepositoryException {
        Username username = extractUsername(request);
        try {
            if (userService.verifyUser(username,
                    jsonExtractorVerify.parse(request.body()).getPassword())) {
                response.status(HttpStatus.NO_CONTENT_204);
            } else {
                response.status(HttpStatus.UNAUTHORIZED_401);
            }
            return Constants.EMPTY_BODY;
        } catch (JsonExtractException e) {
            LOGGER.info("Error while deserializing verifyUser request", e);
            throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Error while deserializing verifyUser request")
                    .cause(e)
                    .haltError();
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid user path", e);
            throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Invalid user path")
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
                .collect(ImmutableList.toImmutableList());
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