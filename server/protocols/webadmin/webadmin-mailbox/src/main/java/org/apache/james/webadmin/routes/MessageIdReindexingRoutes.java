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

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.mailbox.indexer.MessageIdReIndexer;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.github.fge.lambdas.supplier.ThrowingSupplier;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Response;
import spark.Service;

@Api(tags = "MessageIdReIndexing")
@Path("/messages")
@Produces("application/json")
public class MessageIdReindexingRoutes implements Routes {
    private static final String MESSAGE_ID_PARAM = ":messageId";
    private static final String BASE_PATH = "/messages";
    private static final String MESSAGE_PATH = BASE_PATH + "/" + MESSAGE_ID_PARAM;

    private final TaskManager taskManager;
    private final MessageId.Factory messageIdFactory;
    private final MessageIdReIndexer reIndexer;
    private final JsonTransformer jsonTransformer;

    @Inject
    MessageIdReindexingRoutes(TaskManager taskManager, MessageId.Factory messageIdFactory, MessageIdReIndexer reIndexer, JsonTransformer jsonTransformer) {
        this.taskManager = taskManager;
        this.messageIdFactory = messageIdFactory;
        this.reIndexer = reIndexer;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(MESSAGE_PATH, this::reIndexMessage, jsonTransformer);
    }

    @POST
    @Path("/{messageId}")
    @ApiOperation(value = "Re-indexes one email in the different mailboxes containing it")
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
            name = "messageId",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory. Needs to be a valid messageId (format depends on the mailbox implementation)")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - details in the returned error message")
    })
    private TaskIdDto reIndexMessage(Request request, Response response) {
        return wrap(request, response, () -> reIndexer.reIndex(extractMessageId(request)));
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

    private TaskIdDto wrap(Request request, Response response, ThrowingSupplier<Task> taskGenerator) {
        ReIndexingRoutesUtil.enforceTaskParameter(request);

        Task task = taskGenerator.get();
        TaskId taskId = taskManager.submit(task);
        return TaskIdDto.respond(response, taskId);
    }
}
