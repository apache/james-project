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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.InvalidUsernameException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.UserService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class UserRoutes implements Routes {

    private static final String USER_NAME = ":userName";
    private static final String DELEGATED_USER_NAME = ":delegatedUserName";
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRoutes.class);

    public static final String USERS = "/users";
    private static final String FORCE_PARAM = "force";
    private static final String VERIFY = "verify";
    private static final String AUTHORIZED_USERS = "authorizedUsers";

    private final UserService userService;
    private final JsonTransformer jsonTransformer;
    private final CanSendFrom canSendFrom;
    private final DelegationStore delegationStore;

    private Service service;

    private final String dummyUser = "fc8f9dc08044a0c0ff9528fe997@fc8f9dc08044a0c0a8c23c68";

    @Inject
    public UserRoutes(UserService userService, CanSendFrom canSendFrom, JsonTransformer jsonTransformer, DelegationStore delegationStore) {
        this.userService = userService;
        this.jsonTransformer = jsonTransformer;
        this.canSendFrom = canSendFrom;
        this.delegationStore = delegationStore;
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

        getDelegatedUsers();

        clearAllDelegatedUsers();

        addDelegatedUser();

        removeDelegatedUser();
    }

    public void getDelegatedUsers() {
        service.get(USERS + SEPARATOR + USER_NAME + SEPARATOR + AUTHORIZED_USERS, this::getAuthorizedUsers, jsonTransformer);
    }

    public void clearAllDelegatedUsers() {
        service.delete(USERS + SEPARATOR + USER_NAME + SEPARATOR + AUTHORIZED_USERS, this::clearAllAuthorizedUsers);
    }

    public void addDelegatedUser() {
        service.put(USERS + SEPARATOR + USER_NAME + SEPARATOR + AUTHORIZED_USERS + SEPARATOR + DELEGATED_USER_NAME, this::addAuthorizedUser);
    }

    public void removeDelegatedUser() {
        service.delete(USERS + SEPARATOR + USER_NAME + SEPARATOR + AUTHORIZED_USERS + SEPARATOR + DELEGATED_USER_NAME, this::removeAuthorizedUser);
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
        if (dummyUser.equals(username.asString())) {
            LOGGER.info("Invalid username");
            throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Username supplied is invalid")
                    .haltError();
        }

        try {
            userService.upsertUser(username, "".toCharArray());
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

    private String verifyUser(Request request, Response response) {
        response.status(HttpStatus.FORBIDDEN_403);
        return Constants.BASIC_AUTH_DISABLED;
    }

    private List<String> getAuthorizedUsers(Request request, Response response) throws UsersRepositoryException {
        try {
            Username baseUser = extractUsername(request);

            if (userService.userExists(baseUser)) {
                return Flux.from(delegationStore.authorizedUsers(baseUser))
                    .map(Username::asString)
                    .collectList()
                    .block();
            } else {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message(String.format("User '%s' does not exist", baseUser.asString()))
                    .haltError();
            }
        } catch (NotImplementedException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.METHOD_NOT_ALLOWED_405)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("This version of James does not support delegation.")
                .cause(e)
                .haltError();
        }
    }

    private String clearAllAuthorizedUsers(Request request, Response response) throws UsersRepositoryException {
        try {
            Username baseUser = extractUsername(request);

            if (userService.userExists(baseUser)) {
                Mono.from(delegationStore.clear(baseUser)).block();
                return Constants.EMPTY_BODY;
            } else {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message(String.format("User '%s' does not exist", baseUser.asString()))
                    .haltError();
            }
        } catch (NotImplementedException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.METHOD_NOT_ALLOWED_405)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("This version of James does not support delegation.")
                .cause(e)
                .haltError();
        }
    }

    private String addAuthorizedUser(Request request, Response response) throws UsersRepositoryException {
        try {
            Username baseUser = extractUsername(request);
            Username delegatedUser = extractDelegatedUsername(request);

            if (!userService.userExists(baseUser)) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message(String.format("User '%s' does not exist", baseUser.asString()))
                    .haltError();
            } else {
                Mono.from(delegationStore
                    .addAuthorizedUser(delegatedUser)
                    .forUser(baseUser))
                    .block();
                return Constants.EMPTY_BODY;
            }
        } catch (NotImplementedException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.METHOD_NOT_ALLOWED_405)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("This version of James does not support delegation.")
                .cause(e)
                .haltError();
        }
    }

    private String removeAuthorizedUser(Request request, Response response) throws UsersRepositoryException {
        try {
            Username baseUser = extractUsername(request);
            Username delegatedUser = extractDelegatedUsername(request);

            if (!userService.userExists(baseUser)) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message(String.format("User '%s' does not exist", baseUser.asString()))
                    .haltError();
            } else {
                Mono.from(delegationStore.removeAuthorizedUser(delegatedUser)
                    .forUser(baseUser))
                    .block();
                return Constants.EMPTY_BODY;
            }
        } catch (NotImplementedException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.METHOD_NOT_ALLOWED_405)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("This version of James does not support delegation.")
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

    private Username extractDelegatedUsername(Request request) {
        return Username.of(request.params(DELEGATED_USER_NAME));
    }

}