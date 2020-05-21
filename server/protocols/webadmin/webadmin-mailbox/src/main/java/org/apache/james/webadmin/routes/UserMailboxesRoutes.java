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

import static org.apache.james.webadmin.routes.MailboxesRoutes.RE_INDEX;
import static org.apache.james.webadmin.routes.MailboxesRoutes.TASK_PARAMETER;

import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.Username;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry.TaskRegistration;
import org.apache.james.webadmin.tasks.TaskIdDto;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.MailboxHaveChildrenException;
import org.apache.james.webadmin.utils.Responses;
import org.apache.james.webadmin.validation.MailboxName;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Route;
import spark.Service;

@Api(tags = "User's Mailbox")
@Path("/users/{username}/mailboxes")
@Produces("application/json")
public class UserMailboxesRoutes implements Routes {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserMailboxesRoutes.class);
    public static final String USER_MAILBOXES_OPERATIONS_INJECTION_KEY = "userMailboxesOperations";

    public static class UserReIndexingTaskRegistration extends TaskRegistration {
        @Inject
        public UserReIndexingTaskRegistration(ReIndexer reIndexer) {
            super(RE_INDEX, request -> reIndexer.reIndex(getUsernameParam(request), RunningOptionsParser.parse(request)));
        }
    }

    private static Username getUsernameParam(Request request) {
        return Username.of(request.params(USER_NAME));
    }

    public static final String MAILBOX_NAME = ":mailboxName";
    public static final String MAILBOXES = "mailboxes";
    private static final String USER_NAME = ":userName";
    public static final String USERS_BASE = "/users";
    public static final String USER_MAILBOXES_BASE = USERS_BASE + Constants.SEPARATOR + USER_NAME + Constants.SEPARATOR + MAILBOXES;
    public static final String SPECIFIC_MAILBOX = USER_MAILBOXES_BASE + Constants.SEPARATOR + MAILBOX_NAME;

    private final UserMailboxesService userMailboxesService;
    private final JsonTransformer jsonTransformer;
    private final TaskManager taskManager;
    private final Set<TaskRegistration> usersMailboxesTaskRegistration;
    private Service service;

    @Inject
    UserMailboxesRoutes(UserMailboxesService userMailboxesService,
                        JsonTransformer jsonTransformer,
                        TaskManager taskManager,
                        @Named(USER_MAILBOXES_OPERATIONS_INJECTION_KEY) Set<TaskRegistration> usersMailboxesTaskRegistration) {
        this.userMailboxesService = userMailboxesService;
        this.jsonTransformer = jsonTransformer;
        this.taskManager = taskManager;
        this.usersMailboxesTaskRegistration = usersMailboxesTaskRegistration;
    }

    @Override
    public String getBasePath() {
        return USER_MAILBOXES_BASE;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        defineMailboxExists();

        defineGetUserMailboxes();

        defineCreateUserMailbox();

        defineDeleteUserMailbox();

        defineDeleteUserMailboxes();

        reIndexMailboxesRoute()
            .ifPresent(route -> service.post(USER_MAILBOXES_BASE, route, jsonTransformer));
    }

    @GET
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path")
    })
    @ApiOperation(value = "Listing all mailboxes of user.")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "The list of mailboxes", response = String.class),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform", response = String.class),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetUserMailboxes() {
        service.get(USER_MAILBOXES_BASE, (request, response) -> {
            response.status(HttpStatus.OK_200);
            try {
                return userMailboxesService.listMailboxes(getUsernameParam(request));
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mailboxes", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Invalid get on user mailboxes")
                    .cause(e)
                    .haltError();
            }
        }, jsonTransformer);
    }

    @POST
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
        @ApiImplicitParam(
            required = true,
            name = "task",
            paramType = "query parameter",
            dataType = "String",
            defaultValue = "none",
            example = "?task=reIndex",
            value = "Compulsory. Only supported value is `reIndex`")
    })
    @ApiOperation(value = "Perform an action on a user mailbox")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - details in the returned error message")
    })
    public Optional<Route> reIndexMailboxesRoute() {
        return TaskFromRequestRegistry.builder()
            .parameterName(TASK_PARAMETER)
            .registrations(usersMailboxesTaskRegistration)
            .buildAsRouteOptional(taskManager);
    }

    @DELETE
    @Path("/{mailboxName}")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailboxName", paramType = "path")
    })
    @ApiOperation(value = "Deleting a mailbox and its children")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The mailbox now does not exist on the server", response = String.class),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteUserMailbox() {
        service.delete(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                userMailboxesService.deleteMailbox(getUsernameParam(request), new MailboxName(request.params(MAILBOX_NAME)));
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid delete on user mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Invalid delete on user mailboxes")
                    .cause(e)
                    .haltError();
            } catch (MailboxHaveChildrenException e) {
                LOGGER.info("Attempt to delete a mailbox with children");
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.CONFLICT_409)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Attempt to delete a mailbox with children")
                    .cause(e)
                    .haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to create an invalid mailbox");
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Attempt to create an invalid mailbox")
                    .cause(e)
                    .haltError();
            }
        });
    }

    @DELETE
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path")
    })
    @ApiOperation(value = "Deleting user mailboxes.")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The user does not have any mailbox", response = String.class),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteUserMailboxes() {
        service.delete(USER_MAILBOXES_BASE, (request, response) -> {
            try {
                userMailboxesService.deleteMailboxes(getUsernameParam(request));
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid delete on user mailboxes", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Invalid delete on user mailboxes")
                    .cause(e)
                    .haltError();
            }
        });
    }

    @GET
    @Path("/{mailboxName}")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailboxName", paramType = "path")
    })
    @ApiOperation(value = "Testing existence of a mailbox.")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The mailbox exists", response = String.class),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineMailboxExists() {
        service.get(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                if (userMailboxesService.testMailboxExists(getUsernameParam(request), new MailboxName(request.params(MAILBOX_NAME)))) {
                    return Responses.returnNoContent(response);
                } else {
                    throw ErrorResponder.builder()
                        .statusCode(HttpStatus.NOT_FOUND_404)
                        .type(ErrorType.INVALID_ARGUMENT)
                        .message("Invalid get on user mailboxes")
                        .haltError();
                }
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Invalid get on user mailboxes")
                    .cause(e)
                    .haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to create an invalid mailbox");
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Attempt to create an invalid mailbox")
                    .cause(e)
                    .haltError();
            }
        });
    }

    @PUT
    @Path("/{mailboxName}")
    @ApiOperation(value = "Create a mailbox of the selected user.", nickname = "CreateUserMailbox")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "string", name = "mailboxName", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The mailbox now exists on the server.", response = String.class),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid mailbox name"),
            @ApiResponse(code = HttpStatus.UNAUTHORIZED_401, message = "Unauthorized. The user is not authenticated on the platform"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineCreateUserMailbox() {
        service.put(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                userMailboxesService.createMailbox(getUsernameParam(request), new MailboxName(request.params(MAILBOX_NAME)));
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid put on user mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Invalid get on user mailboxes")
                    .cause(e)
                    .haltError();
            } catch (IllegalArgumentException e) {
                LOGGER.info("Attempt to create an invalid mailbox");
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Attempt to create an invalid mailbox")
                    .cause(e)
                    .haltError();
            }
        });
    }
}
