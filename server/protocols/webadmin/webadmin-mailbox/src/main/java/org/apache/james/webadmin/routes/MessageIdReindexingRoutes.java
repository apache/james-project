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

import static org.apache.james.webadmin.routes.ReindexingRoutes.BASE_PATH;

import javax.inject.Inject;

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

import spark.Request;
import spark.Response;
import spark.Service;

public class MessageIdReindexingRoutes implements Routes {
    private static final String MESSAGE_ID_PARAM = ":messageId";
    private static final String MESSAGE_PATH = BASE_PATH + "/messages/" + MESSAGE_ID_PARAM;

    private final TaskManager taskManager;
    private final MessageId.Factory messageIdFactory;
    private final MessageIdReIndexer reIndexer;
    private final JsonTransformer jsonTransformer;

    @Inject
    public MessageIdReindexingRoutes(TaskManager taskManager, MessageId.Factory messageIdFactory, MessageIdReIndexer reIndexer, JsonTransformer jsonTransformer) {
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
