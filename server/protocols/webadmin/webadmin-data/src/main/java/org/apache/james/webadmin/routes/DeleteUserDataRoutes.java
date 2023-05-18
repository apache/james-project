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

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.DeleteUserDataTaskStep.StepName;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.DeleteUserDataService;
import org.apache.james.webadmin.service.DeleteUserDataTask;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.base.Preconditions;

import spark.Route;
import spark.Service;

public class DeleteUserDataRoutes implements Routes {
    private static final String USER_PATH_PARAM = ":username";
    private static final String ROOT_PATH = "/users/" + USER_PATH_PARAM;
    private static final TaskRegistrationKey DELETE_USER_DATA = TaskRegistrationKey.of("deleteData");

    private final UsersRepository usersRepository;
    private final DeleteUserDataService service;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    DeleteUserDataRoutes(UsersRepository usersRepository, DeleteUserDataService service, TaskManager taskManager, JsonTransformer jsonTransformer) {
        this.usersRepository = usersRepository;
        this.service = service;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(ROOT_PATH, deleteUserData(), jsonTransformer);
    }

    public Route deleteUserData() {
        return TaskFromRequestRegistry.builder()
            .parameterName("action")
            .register(DELETE_USER_DATA, request -> {
                Username username = Username.of(request.params(USER_PATH_PARAM));

                Preconditions.checkArgument(usersRepository.contains(username), "'username' parameter should be an existing user");

                Optional<StepName> fromStep = Optional.ofNullable(request.queryParams("fromStep")).map(StepName::new);

                return new DeleteUserDataTask(service, username, fromStep);
            })
            .buildAsRoute(taskManager);
    }
}
