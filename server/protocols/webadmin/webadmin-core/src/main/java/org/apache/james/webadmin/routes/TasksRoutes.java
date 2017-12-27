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
import java.util.UUID;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskNotFoundException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.ExecutionDetailsDto;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Response;
import spark.Service;

public class TasksRoutes implements Routes {
    public static final String BASE = "/tasks";
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    public TasksRoutes(TaskManager taskManager, JsonTransformer jsonTransformer) {
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public void define(Service service) {
        service.get(BASE + "/:id", this::getStatus, jsonTransformer);

        service.get(BASE + "/:id/await", this::await, jsonTransformer);

        service.delete(BASE + "/:id", this::cancel, jsonTransformer);

        service.get(BASE, this::list, jsonTransformer);
    }

    private Object list(Request req, Response response) {
        try {
            return ExecutionDetailsDto.from(
                Optional.ofNullable(req.queryParams("status"))
                .map(TaskManager.Status::fromString)
                .map(taskManager::list)
                .orElse(taskManager.list()));
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .cause(e)
                .message("Invalid status query parameter")
                .haltError();
        }
    }

    private Object getStatus(Request req, Response response) {
        TaskId taskId = getTaskId(req);
        return respondStatus(taskId,
            () -> taskManager.getExecutionDetails(getTaskId(req)));
    }

    private Object await(Request req, Response response) {
        TaskId taskId = getTaskId(req);
        return respondStatus(taskId,
            () -> taskManager.await(getTaskId(req)));
    }

    private Object respondStatus(TaskId taskId, Supplier<TaskExecutionDetails> executionDetailsSupplier) {
        try {
            TaskExecutionDetails executionDetails = executionDetailsSupplier.get();
            return ExecutionDetailsDto.from(executionDetails);
        } catch (TaskNotFoundException e) {
            throw ErrorResponder.builder()
                .message(String.format("%s can not be found", taskId.getValue()))
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .haltError();
        }
    }

    private Object cancel(Request req, Response response) {
        TaskId taskId = getTaskId(req);
        taskManager.cancel(taskId);
        response.status(HttpStatus.NO_CONTENT_204);
        return Constants.EMPTY_BODY;
    }

    private TaskId getTaskId(Request req) {
        try {
            String id = req.params("id");
            return new TaskId(UUID.fromString(id));
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
