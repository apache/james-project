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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.inject.Inject;

import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.services.TasksCleanupService;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.tasks.TasksCleanupTask;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.ParametersExtractor;

import spark.Request;
import spark.Service;

public class TasksCleanupRoutes implements Routes {
    public static final String BASE_PATH = "/tasks";

    private final TaskManager taskManager;
    private final Clock clock;
    private final TasksCleanupService tasksCleanupService;

    private final JsonTransformer jsonTransformer;

    @Inject
    public TasksCleanupRoutes(TaskManager taskManager, Clock clock,
                              TasksCleanupService tasksCleanupService, JsonTransformer jsonTransformer) {
        this.taskManager = taskManager;
        this.clock = clock;
        this.tasksCleanupService = tasksCleanupService;
        this.jsonTransformer = jsonTransformer;
    }


    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest tasksCleanupFromRequest = this::tasksCleanupTask;
        service.delete(BASE_PATH, tasksCleanupFromRequest.asRoute(taskManager),
            jsonTransformer);
    }

    public Task tasksCleanupTask(Request request) {
        Duration olderThanDuration = ParametersExtractor.extractDuration(request, "olderThan")
            .orElseThrow(() -> new IllegalArgumentException("missing or invalid `olderThan` parameter"));
        Instant olderThan = clock.instant().minusSeconds(olderThanDuration.toSeconds());
        return new TasksCleanupTask(tasksCleanupService, olderThan);
    }

}
