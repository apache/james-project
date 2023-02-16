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

import org.apache.james.core.Username;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.UsernameChangeService;
import org.apache.james.webadmin.service.UsernameChangeTask;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.base.Preconditions;

import spark.Route;
import spark.Service;

public class UsernameChangeRoutes implements Routes {
    private static final String OLD_USER_PARAM = "oldUser";
    private static final String NEW_USER_PARAM = "newUser";
    private static final String ROOT_PATH = "/users/:" + OLD_USER_PARAM + "/rename/:" + NEW_USER_PARAM;
    private static final TaskRegistrationKey RENAME = TaskRegistrationKey.of("rename");

    private final UsersRepository usersRepository;
    private final UsernameChangeService service;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    UsernameChangeRoutes(UsersRepository usersRepository, UsernameChangeService service, TaskManager taskManager, JsonTransformer jsonTransformer) {
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
        service.post(ROOT_PATH, changeUsername(), jsonTransformer);
    }

    public Route changeUsername() {
        return TaskFromRequestRegistry.of(RENAME, request -> {
            Username oldUser = Username.of(request.params(OLD_USER_PARAM));
            Username newUser = Username.of(request.params(NEW_USER_PARAM));

            Preconditions.checkArgument(usersRepository.contains(oldUser), "'oldUser' parameter should be an existing user");
            Preconditions.checkArgument(usersRepository.contains(newUser), "'newUser' parameter should be an existing user");

            return new UsernameChangeTask(service, oldUser, newUser);
        }).asRoute(taskManager);
    }
}
