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

import java.util.UUID;

import org.apache.james.task.TaskId;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.eclipse.jetty.http.HttpStatus;

import spark.Response;

public class TaskIdDto {
    static TaskIdDto respond(Response response, TaskId taskId) {
        response.status(HttpStatus.CREATED_201);
        response.header(LOCATION.asString(), TasksRoutes.BASE + "/" + taskId.asString());
        return new TaskIdDto(taskId.getValue());
    }

    private final UUID uuid;

    private TaskIdDto(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getTaskId() {
        return uuid;
    }
}
