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

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.ClearMailboxContentTask;
import org.apache.james.webadmin.service.UserMailboxesService;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry.TaskRegistration;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.MailboxHaveChildrenException;
import org.apache.james.webadmin.utils.Responses;
import org.apache.james.webadmin.validation.MailboxName;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Route;
import spark.Service;

public class UserMailboxesRoutes implements Routes {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserMailboxesRoutes.class);
    public static final String USER_MAILBOXES_OPERATIONS_INJECTION_KEY = "userMailboxesOperations";

    public static class UserReIndexingTaskRegistration extends TaskRegistration {
        @Inject
        public UserReIndexingTaskRegistration(ReIndexer reIndexer) {
            super(RE_INDEX, request -> reIndexer.reIndex(getUsernameParam(request), ReindexingRunningOptionsParser.parse(request)));
        }
    }

    private static Username getUsernameParam(Request request) {
        return Username.of(request.params(USER_NAME));
    }

    private static UserMailboxesService.Options getOptions(Request request) {
        if (request.queryParams().contains("force")) {
            return UserMailboxesService.Options.Force;
        }
        return UserMailboxesService.Options.Check;
    }

    public static final String MAILBOX_NAME = ":mailboxName";
    public static final String MAILBOXES = "mailboxes";
    private static final String USER_NAME = ":userName";
    public static final String USERS_BASE = "/users";
    public static final String USER_MAILBOXES_BASE = USERS_BASE + Constants.SEPARATOR + USER_NAME + Constants.SEPARATOR + MAILBOXES;
    public static final String SPECIFIC_MAILBOX = USER_MAILBOXES_BASE + Constants.SEPARATOR + MAILBOX_NAME;
    public static final String MESSAGE_COUNT_PATH = SPECIFIC_MAILBOX + "/messageCount";
    public static final String UNSEEN_MESSAGE_COUNT_PATH = SPECIFIC_MAILBOX + "/unseenMessageCount";
    public static final String MESSAGES_PATH = SPECIFIC_MAILBOX + "/messages";

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

        messageCount();

        reIndexMailboxesRoute()
            .ifPresent(route -> service.post(USER_MAILBOXES_BASE, route, jsonTransformer));

        unseenMessageCount();

        TaskFromRequest clearMailboxContentTaskRequest = this::clearMailboxContent;
        service.delete(MESSAGES_PATH, clearMailboxContentTaskRequest.asRoute(taskManager), jsonTransformer);
    }

    public void defineGetUserMailboxes() {
        service.get(USER_MAILBOXES_BASE, (request, response) -> {
            response.status(HttpStatus.OK_200);
            try {
                return userMailboxesService.listMailboxes(getUsernameParam(request), getOptions(request));
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mailboxes", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message("Invalid get on user mailboxes")
                    .cause(e)
                    .haltError();
            }
        }, jsonTransformer);
    }

    public Optional<Route> reIndexMailboxesRoute() {
        return TaskFromRequestRegistry.builder()
            .parameterName(TASK_PARAMETER)
            .registrations(usersMailboxesTaskRegistration)
            .buildAsRouteOptional(taskManager);
    }

    public void defineDeleteUserMailbox() {
        service.delete(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                userMailboxesService.deleteMailbox(getUsernameParam(request), new MailboxName(request.params(MAILBOX_NAME)), getOptions(request));
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid delete on user mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
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
            } catch (IllegalArgumentException | MailboxNameException e) {
                LOGGER.info("Attempt to delete an invalid mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Attempt to delete an invalid mailbox")
                    .cause(e)
                    .haltError();
            }
        });
    }

    public void defineDeleteUserMailboxes() {
        service.delete(USER_MAILBOXES_BASE, (request, response) -> {
            try {
                userMailboxesService.deleteMailboxes(getUsernameParam(request), getOptions(request));
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid delete on user mailboxes", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message("Invalid delete on user mailboxes")
                    .cause(e)
                    .haltError();
            }
        });
    }

    public void defineMailboxExists() {
        service.get(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                if (userMailboxesService.testMailboxExists(getUsernameParam(request), new MailboxName(request.params(MAILBOX_NAME)), getOptions(request))) {
                    return Responses.returnNoContent(response);
                } else {
                    throw ErrorResponder.builder()
                        .statusCode(HttpStatus.NOT_FOUND_404)
                        .type(ErrorType.NOT_FOUND)
                        .message("Mailbox does not exist")
                        .haltError();
                }
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid get on user mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message("Invalid get on user mailboxes")
                    .cause(e)
                    .haltError();
            } catch (IllegalArgumentException | MailboxNameException e) {
                LOGGER.info("Attempt to test existence of an invalid mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Attempt to test existence of an invalid mailbox")
                    .cause(e)
                    .haltError();
            }
        });
    }

    public void defineCreateUserMailbox() {
        service.put(SPECIFIC_MAILBOX, (request, response) -> {
            try {
                userMailboxesService.createMailbox(getUsernameParam(request), new MailboxName(request.params(MAILBOX_NAME)), getOptions(request));
                return Responses.returnNoContent(response);
            } catch (IllegalStateException e) {
                LOGGER.info("Invalid put on user mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message("Invalid get on user mailboxes")
                    .cause(e)
                    .haltError();
            } catch (IllegalArgumentException | MailboxNameException e) {
                LOGGER.info("Attempt to create an invalid mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Attempt to create an invalid mailbox")
                    .cause(e)
                    .haltError();
            }
        });
    }

    public void messageCount() {
        service.get(MESSAGE_COUNT_PATH, (request, response) -> {
            try {
                return userMailboxesService.messageCount(getUsernameParam(request), new MailboxName(request.params(MAILBOX_NAME)), getOptions(request));
            } catch (IllegalStateException | MailboxNotFoundException e) {
                LOGGER.info("Invalid get on user mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message("Invalid get on user mailboxes")
                    .cause(e)
                    .haltError();
            } catch (IllegalArgumentException | MailboxNameException e) {
                LOGGER.info("Attempt to test existence of an invalid mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Attempt to test existence of an invalid mailbox")
                    .cause(e)
                    .haltError();
            }
        });
    }

    public void unseenMessageCount() {
        service.get(UNSEEN_MESSAGE_COUNT_PATH, (request, response) -> {
            try {
                return userMailboxesService.unseenMessageCount(getUsernameParam(request), new MailboxName(request.params(MAILBOX_NAME)), getOptions(request));
            } catch (IllegalStateException | MailboxNotFoundException e) {
                LOGGER.info("Invalid get on user mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorType.NOT_FOUND)
                    .message("Invalid get on user mailboxes")
                    .cause(e)
                    .haltError();
            } catch (IllegalArgumentException | MailboxNameException e) {
                LOGGER.info("Attempt to test existence of an invalid mailbox", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Attempt to test existence of an invalid mailbox")
                    .cause(e)
                    .haltError();
            }
        });
    }

    public Task clearMailboxContent(Request request) throws UsersRepositoryException, MailboxException {
        Username username = getUsernameParam(request);
        MailboxName mailboxName = new MailboxName(request.params(MAILBOX_NAME));
        try {
            userMailboxesService.usernamePreconditions(username, getOptions(request));
            userMailboxesService.mailboxExistPreconditions(username, mailboxName);
        } catch (IllegalStateException e) {
            LOGGER.info("Invalid put on user mailbox", e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorType.NOT_FOUND)
                .message("Invalid get on user mailboxes")
                .cause(e)
                .haltError();
        }

        return new ClearMailboxContentTask(username, mailboxName, userMailboxesService);
    }
}
