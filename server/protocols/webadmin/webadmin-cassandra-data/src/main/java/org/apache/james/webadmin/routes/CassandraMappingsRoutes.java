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

import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.CassandraMappingsService;
import org.apache.james.webadmin.tasks.TaskFactory;
import org.apache.james.webadmin.tasks.TaskIdDto;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ResponseHeader;
import spark.Route;
import spark.Service;

@Api(tags = "Cassandra Mappings Operations")
@Path(CassandraMappingsRoutes.ROOT_PATH)
@Produces(Constants.JSON_CONTENT_TYPE)
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

    @POST
    @Path(ROOT_PATH)
    @ApiOperation(value = "Performing operations on cassandra data mappings")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            dataType = "String",
            name = "action",
            paramType = "query",
            example = "?action=SolveInconsistencies",
            value = "Specify the action to perform on mappings. For now only 'SolveInconsistencies' is supported as an action, "
                + "and its purpose is to clean 'mappings_sources' projection table and repopulate it."),
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "The taskId of the given scheduled task", response = TaskIdDto.class,
            responseHeaders = {
                @ResponseHeader(name = "Location", description = "URL of the resource associated with the scheduled task")
            }),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = INVALID_ACTION_ARGUMENT_REQUEST),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = ACTION_REQUEST_CAN_NOT_BE_DONE)
    })
    public Route performActionOnMappings() {
        return TaskFactory.of(SOLVE_INCONSISTENCIES, request -> cassandraMappingsService.solveMappingsSourcesInconsistencies())
            .asRoute(taskManager);
    }
}
