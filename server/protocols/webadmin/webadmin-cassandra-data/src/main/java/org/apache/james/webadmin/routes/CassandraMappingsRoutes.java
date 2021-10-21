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

import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.CassandraMappingsService;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.JsonTransformer;

import spark.Route;
import spark.Service;

public class CassandraMappingsRoutes implements Routes {
    public static final String ROOT_PATH = "cassandra/mappings";
    private static final TaskRegistrationKey SOLVE_INCONSISTENCIES = TaskRegistrationKey.of("SolveInconsistencies");

    private final CassandraMappingsService cassandraMappingsService;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    private static final String INVALID_ACTION_ARGUMENT_REQUEST = "Invalid action argument for performing operation on mappings data";
    private static final String ACTION_REQUEST_CAN_NOT_BE_DONE = "The action requested for performing operation on mappings data cannot be performed";

    @Inject
    CassandraMappingsRoutes(CassandraMappingsService cassandraMappingsService, TaskManager taskManager, JsonTransformer jsonTransformer) {
        this.cassandraMappingsService = cassandraMappingsService;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(ROOT_PATH, performActionOnMappings(), jsonTransformer);
    }

    public Route performActionOnMappings() {
        return TaskFromRequestRegistry.of(SOLVE_INCONSISTENCIES, request -> cassandraMappingsService.solveMappingsSourcesInconsistencies())
            .asRoute(taskManager);
    }
}
