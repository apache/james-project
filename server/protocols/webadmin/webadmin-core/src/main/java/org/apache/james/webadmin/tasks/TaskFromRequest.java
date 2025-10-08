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

package org.apache.james.webadmin.tasks;

import static org.eclipse.jetty.http.HttpHeader.LOCATION;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.tasks.TaskHandler.MultiTaskHandler;
import org.apache.james.webadmin.tasks.TaskHandler.SingleTaskHandler;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Response;
import spark.Route;

public interface TaskFromRequest {
    class TaskRoute implements Route {
        private final TaskFromRequest taskFromRequest;
        private final TaskManager taskManager;

        TaskRoute(TaskFromRequest taskFromRequest, TaskManager taskManager) {
            this.taskFromRequest = taskFromRequest;
            this.taskManager = taskManager;
        }

        @Override
        public TaskIdDto handle(Request request, Response response) throws Exception {
            SingleTaskHandler taskHandler = (SingleTaskHandler) taskFromRequest.fromRequest(request);
            TaskId taskId = taskManager.submit(taskHandler.task());
            response.status(HttpStatus.CREATED_201);
            response.header(LOCATION.asString(), TasksRoutes.BASE + "/" + taskId.asString());
            return new TaskIdDto(taskId.getValue());
        }
    }

    class MultiTaskRoute implements Route {
        private final TaskFromRequest taskFromRequest;
        private final TaskManager taskManager;

        MultiTaskRoute(TaskFromRequest taskFromRequest, TaskManager taskManager) {
            this.taskFromRequest = taskFromRequest;
            this.taskManager = taskManager;
        }

        @Override
        public Map<String, TaskIdDto> handle(Request request, Response response) throws Exception {
            MultiTaskHandler task = (MultiTaskHandler) taskFromRequest.fromRequest(request);
            response.status(HttpStatus.CREATED_201);
            return task.userTasks()
                .stream()
                .collect(Collectors.toMap(userTask -> userTask.username().asString(), userTask -> new TaskIdDto(taskManager.submit(userTask.task()).getValue())));
        }
    }

    TaskHandler fromRequest(Request request) throws Exception;

    default Route asRoute(TaskManager taskManager) {
        return new TaskRoute(this, taskManager);
    }
}
