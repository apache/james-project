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

import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.dto.TaskIdDto;

import spark.Request;
import spark.Response;
import spark.Route;

public interface TaskGenerator {
    class TaskRoute implements Route {
        private final TaskGenerator taskGenerator;
        private final TaskManager taskManager;

        TaskRoute(TaskGenerator taskGenerator, TaskManager taskManager) {
            this.taskGenerator = taskGenerator;
            this.taskManager = taskManager;
        }

        @Override
        public Object handle(Request request, Response response) throws Exception {
            Task task = taskGenerator.generate(request);
            TaskId taskId = taskManager.submit(task);
            return TaskIdDto.respond(response, taskId);
        }
    }

    Task generate(Request request) throws Exception;

    default Route asRoute(TaskManager taskManager) {
        return new TaskRoute(this, taskManager);
    }
}
