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

import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.indexer.IndexingDetailInformation;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskNotFoundException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.PreviousReIndexingService;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry.TaskRegistration;
import org.apache.james.webadmin.tasks.TaskIdDto;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Strings;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Route;
import spark.Service;

@Api(tags = "Mailboxes")
@Path("/mailboxes")
@Produces("application/json")
public class MailboxesRoutes implements Routes {
    public static class ReIndexAllMailboxesTaskRegistration extends TaskRegistration {
        @Inject
        public ReIndexAllMailboxesTaskRegistration(ReIndexer reIndexer, PreviousReIndexingService previousReIndexingService, MailboxId.Factory mailboxIdFactory) {
            super(RE_INDEX, wrap(request -> reIndexAll(previousReIndexingService, reIndexer, request)));
        }

        @POST
        @Path("/")
        @ApiOperation(value = "Re-indexes all the mails on this server")
        @ApiImplicitParams({
            @ApiImplicitParam(
                required = true,
                name = "task",
                paramType = "query parameter",
                dataType = "String",
                defaultValue = "none",
                example = "?task=reIndex",
                value = "Compulsory. Only supported value is `reIndex`"),
            @ApiImplicitParam(
                required = false,
                name = "messagesPerSecond",
                paramType = "query parameter",
                dataType = "Integer",
                defaultValue = "none",
                example = "?messagesPerSecond=100",
                value = "If present, determine the number of messages being processed in one second."),
            @ApiImplicitParam(
                name = "reIndexFailedMessagesOf",
                paramType = "query parameter",
                dataType = "String",
                defaultValue = "none",
                example = "?reIndexFailedMessagesOf=3294a976-ce63-491e-bd52-1b6f465ed7a2",
                value = "optional. References a previously run reIndexing task. if present, the messages that this previous " +
                    "task failed to index will be reIndexed.")
        })
        @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - details in the returned error message")
        })
        private static Task reIndexAll(PreviousReIndexingService previousReIndexingService, ReIndexer reIndexer, Request request) throws MailboxException {
            boolean indexingCorrection = !Strings.isNullOrEmpty(request.queryParams(RE_INDEX_FAILED_MESSAGES_QUERY_PARAM));
            if (indexingCorrection) {
                IndexingDetailInformation indexingDetailInformation = retrieveIndexingExecutionDetails(previousReIndexingService, request);
                return reIndexer.reIndex(indexingDetailInformation.failures(), RunningOptionsParser.parse(request));
            }

            return reIndexer.reIndex(RunningOptionsParser.parse(request));
        }

        private static IndexingDetailInformation retrieveIndexingExecutionDetails(PreviousReIndexingService previousReIndexingService, Request request) {
            TaskId taskId = getTaskId(request);
            try {
                return previousReIndexingService.retrieveIndexingExecutionDetails(taskId);
            } catch (PreviousReIndexingService.NotAnIndexingRetriableTask | PreviousReIndexingService.TaskNotYetFinishedException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("Invalid task id")
                    .cause(e)
                    .haltError();
            } catch (TaskNotFoundException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("TaskId %s does not exist", taskId.asString())
                    .cause(e)
                    .haltError();
            }
        }

        private static TaskId getTaskId(Request request) {
            try {
                String id = request.queryParams(RE_INDEX_FAILED_MESSAGES_QUERY_PARAM);
                return TaskId.fromString(id);
            } catch (Exception e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .cause(e)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("Invalid task id")
                    .haltError();
            }
        }
    }

    public static class ReIndexOneMailboxTaskRegistration extends TaskRegistration {
        @Inject
        public ReIndexOneMailboxTaskRegistration(ReIndexer reIndexer, MailboxId.Factory mailboxIdFactory) {
            super(RE_INDEX, toTask(reIndexer, mailboxIdFactory));
        }

        @POST
        @Path("/{mailboxId}")
        @ApiOperation(value = "Re-indexes all the mails in a mailbox")
        @ApiImplicitParams({
            @ApiImplicitParam(
                required = true,
                name = "task",
                paramType = "query parameter",
                dataType = "String",
                defaultValue = "none",
                example = "?task=reIndex",
                value = "Compulsory. Only supported value is `reIndex`"),
            @ApiImplicitParam(
                required = false,
                name = "messagesPerSecond",
                paramType = "query parameter",
                dataType = "Integer",
                defaultValue = "none",
                example = "?messagesPerSecond=100",
                value = "If present, determine the number of messages being processed in one second."),
            @ApiImplicitParam(
                required = true,
                name = "mailboxId",
                paramType = "path parameter",
                dataType = "String",
                defaultValue = "none",
                value = "Compulsory. Needs to be a valid mailbox ID")
        })
        @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - details in the returned error message")
        })
        private static TaskFromRequest toTask(ReIndexer reIndexer, MailboxId.Factory mailboxIdFactory) {
            return wrap(request -> reIndexer.reIndex(extractMailboxId(mailboxIdFactory, request), RunningOptionsParser.parse(request)));
        }
    }

    public static class ReIndexOneMailTaskRegistration extends TaskRegistration {
        @Inject
        public ReIndexOneMailTaskRegistration(ReIndexer reIndexer, MailboxId.Factory mailboxIdFactory) {
            super(RE_INDEX, toTask(reIndexer, mailboxIdFactory));
        }

        @POST
        @Path("/{mailboxId}/mails/{uid}")
        @ApiOperation(value = "Re-indexes a single email")
        @ApiImplicitParams({
            @ApiImplicitParam(
                required = true,
                name = "task",
                paramType = "query parameter",
                dataType = "String",
                defaultValue = "none",
                example = "?task=reIndex",
                value = "Compulsory. Only supported value is `reIndex`"),
            @ApiImplicitParam(
                required = true,
                name = "user",
                paramType = "path parameter",
                dataType = "String",
                defaultValue = "none",
                example = "benoit@apache.org",
                value = "Compulsory. Needs to be a valid username"),
            @ApiImplicitParam(
                required = true,
                name = "mailboxId",
                paramType = "path parameter",
                dataType = "String",
                defaultValue = "none",
                value = "Compulsory. Needs to be a valid mailbox ID"),
            @ApiImplicitParam(
                required = true,
                name = "uid",
                paramType = "path parameter",
                dataType = "Integer",
                defaultValue = "none",
                value = "Compulsory. Needs to be a valid UID")
        })
        public static TaskFromRequest toTask(ReIndexer reIndexer, MailboxId.Factory mailboxIdFactory) {
            return wrap(request -> reIndexer.reIndex(
                extractMailboxId(mailboxIdFactory, request),
                extractUid(request)));
        }

        private static MessageUid extractUid(Request request) {
            try {
                return MessageUid.of(Long.parseLong(request.params(UID_PARAM)));
            } catch (NumberFormatException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("'uid' needs to be a parsable long")
                    .cause(e)
                    .haltError();
            }
        }
    }

    private static TaskFromRequest wrap(TaskFromRequest toBeWrapped) {
        return request -> {
            try {
                return toBeWrapped.fromRequest(request);
            } catch (MailboxNotFoundException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("mailbox not found")
                    .cause(e)
                    .haltError();
            }
        };
    }

    private static MailboxId extractMailboxId(MailboxId.Factory mailboxIdFactory, Request request) {
        try {
            return mailboxIdFactory.fromString(request.params(MAILBOX_PARAM));
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Error while parsing 'mailbox'")
                .cause(e)
                .haltError();
        }
    }

    private static final String BASE_PATH = "/mailboxes";
    private static final String RE_INDEX_FAILED_MESSAGES_QUERY_PARAM = "reIndexFailedMessagesOf";
    private static final String MAILBOX_PARAM = ":mailbox";
    private static final String UID_PARAM = ":uid";
    private static final String MAILBOX_PATH = BASE_PATH + "/" + MAILBOX_PARAM;
    private static final String MESSAGE_PATH = MAILBOX_PATH + "/mails/" + UID_PARAM;
    static final TaskRegistrationKey RE_INDEX = TaskRegistrationKey.of("reIndex");
    static final String TASK_PARAMETER = "task";
    public static final String ALL_MAILBOXES_TASKS = "allMailboxesTasks";
    public static final String ONE_MAILBOX_TASKS = "oneMailboxTasks";
    public static final String ONE_MAIL_TASKS = "oneMailTasks";

    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;
    private final Set<TaskRegistration> allMailboxesTaskRegistration;
    private final Set<TaskRegistration> oneMailboxTaskRegistration;
    private final Set<TaskRegistration> oneMailTaskRegistration;

    @Inject
    MailboxesRoutes(TaskManager taskManager,
                    JsonTransformer jsonTransformer,
                    @Named(ALL_MAILBOXES_TASKS) Set<TaskRegistration> allMailboxesTaskRegistration,
                    @Named(ONE_MAILBOX_TASKS) Set<TaskRegistration> oneMailboxTaskRegistration,
                    @Named(ONE_MAIL_TASKS) Set<TaskRegistration> oneMailTaskRegistration) {
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
        this.allMailboxesTaskRegistration = allMailboxesTaskRegistration;
        this.oneMailboxTaskRegistration = oneMailboxTaskRegistration;
        this.oneMailTaskRegistration = oneMailTaskRegistration;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        allMailboxesOperations()
            .ifPresent(route -> service.post(BASE_PATH, route, jsonTransformer));

        oneMailboxOperations()
            .ifPresent(route -> service.post(MAILBOX_PATH, route, jsonTransformer));

        oneMail()
            .ifPresent(route -> service.post(MESSAGE_PATH, route, jsonTransformer));
    }

    private Optional<Route> allMailboxesOperations() {
        return TaskFromRequestRegistry.builder()
            .parameterName(TASK_PARAMETER)
            .registrations(allMailboxesTaskRegistration)
            .buildAsRouteOptional(taskManager);
    }

    private Optional<Route> oneMailboxOperations() {
        return TaskFromRequestRegistry.builder()
            .parameterName(TASK_PARAMETER)
            .registrations(oneMailboxTaskRegistration)
            .buildAsRouteOptional(taskManager);
    }

    private Optional<Route> oneMail() {
        return TaskFromRequestRegistry.builder()
            .parameterName(TASK_PARAMETER)
            .registrations(oneMailTaskRegistration)
            .buildAsRouteOptional(taskManager);
    }
}
