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

import static org.apache.james.webadmin.routes.MailboxesRoutes.TASK_PARAMETER;

import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.mailbox.indexer.MessageIdReIndexer;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.ExpireMailboxService;
import org.apache.james.webadmin.service.ExpireMailboxTask;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Route;
import spark.Service;

public class MessagesRoutes implements Routes {
    private static final String MESSAGE_ID_PARAM = ":messageId";
    private static final String BASE_PATH = "/messages";
    private static final String MESSAGE_PATH = BASE_PATH + "/" + MESSAGE_ID_PARAM;

    private final TaskManager taskManager;
    private final MessageId.Factory messageIdFactory;
    private final MessageIdReIndexer reIndexer;
    private final ExpireMailboxService expireMailboxService;
    private final JsonTransformer jsonTransformer;
    private final Set<TaskFromRequestRegistry.TaskRegistration> allMessagesTaskRegistration;

    public static final String ALL_MESSAGES_TASKS = "allMessagesTasks";

    @Inject
    MessagesRoutes(TaskManager taskManager, MessageId.Factory messageIdFactory, MessageIdReIndexer reIndexer,
                   ExpireMailboxService expireMailboxService, JsonTransformer jsonTransformer,
                   @Named(ALL_MESSAGES_TASKS) Set<TaskFromRequestRegistry.TaskRegistration> allMessagesTaskRegistration) {
        this.taskManager = taskManager;
        this.messageIdFactory = messageIdFactory;
        this.reIndexer = reIndexer;
        this.expireMailboxService = expireMailboxService;
        this.jsonTransformer = jsonTransformer;
        this.allMessagesTaskRegistration = allMessagesTaskRegistration;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest expireMailboxTaskRequest = this::expireMailbox;
        service.delete(BASE_PATH, expireMailboxTaskRequest.asRoute(taskManager), jsonTransformer);
        service.post(MESSAGE_PATH, reIndexMessage(), jsonTransformer);
        allMessagesOperations()
            .ifPresent(route -> service.post(BASE_PATH, route, jsonTransformer));
    }

    private Task expireMailbox(Request request) {
        try {
            ExpireMailboxService.RunningOptions runningOptions = ExpireMailboxService.RunningOptions.fromParams(
                Optional.ofNullable(request.queryParams("byExpiresHeader")),
                Optional.ofNullable(request.queryParams("olderThan")),
                Optional.ofNullable(request.queryParams("usersPerSecond")),
                Optional.ofNullable(request.queryParams("mailbox")));
            return new ExpireMailboxTask(expireMailboxService, runningOptions);
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid arguments supplied in the user request")
                .cause(e)
                .haltError();
        }    
    }

    private Route reIndexMessage() {
        return TaskFromRequestRegistry.builder()
            .parameterName(TASK_PARAMETER)
            .register(MailboxesRoutes.RE_INDEX, request -> reIndexer.reIndex(extractMessageId(request)))
            .buildAsRoute(taskManager);
    }

    private MessageId extractMessageId(Request request) {
        try {
            return messageIdFactory.fromString(request.params(MESSAGE_ID_PARAM));
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Error while parsing 'messageId'")
                .cause(e)
                .haltError();
        }
    }

    private Optional<Route> allMessagesOperations() {
        return TaskFromRequestRegistry.builder()
            .parameterName(TASK_PARAMETER)
            .registrations(allMessagesTaskRegistration)
            .buildAsRouteOptional(taskManager);
    }
}
