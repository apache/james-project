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
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.backends.cassandra.migration.CassandraMigrationService;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.CassandraVersionRequest;
import org.apache.james.webadmin.dto.CassandraVersionResponse;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.tasks.TaskGenerator;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ResponseHeader;
import spark.Request;
import spark.Service;

@Api(tags = "Cassandra migration")
@Path(":cassandra/version")
@Produces("application/json")
public class CassandraMigrationRoutes implements Routes {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMigrationRoutes.class);

    public static final String VERSION_BASE = "/cassandra/version";
    private static final String VERSION_BASE_LATEST = VERSION_BASE + "/latest";
    private static final String VERSION_UPGRADE_BASE = VERSION_BASE + "/upgrade";
    private static final String VERSION_UPGRADE_TO_LATEST_BASE = VERSION_UPGRADE_BASE + "/latest";

    private final CassandraMigrationService cassandraMigrationService;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    public static String INVALID_VERSION_UPGRADE_REQUEST = "Invalid request for version upgrade";
    public static String MIGRATION_REQUEST_CAN_NOT_BE_DONE = "The migration requested can not be performed";
    public static String PARTIAL_MIGRATION_PROCESS = "An error lead to partial migration process";

    @Inject
    public CassandraMigrationRoutes(CassandraMigrationService cassandraMigrationService,
                                    TaskManager taskManager, JsonTransformer jsonTransformer) {
        this.cassandraMigrationService = cassandraMigrationService;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return VERSION_BASE;
    }

    @Override
    public void define(Service service) {
        service.get(VERSION_BASE, (request, response) -> getCassandraCurrentVersion(), jsonTransformer);

        service.get(VERSION_BASE_LATEST, (request, response) -> getCassandraLatestVersion(), jsonTransformer);

        TaskGenerator upgradeToVersionTaskGenerator = this::upgradeToVersion;
        service.post(VERSION_UPGRADE_BASE, upgradeToVersionTaskGenerator.asRoute(taskManager), jsonTransformer);

        TaskGenerator upgradeToLatestTaskGenerator = request -> upgradeToLatest();
        service.post(VERSION_UPGRADE_TO_LATEST_BASE, upgradeToLatestTaskGenerator.asRoute(taskManager), jsonTransformer);
    }

    @POST
    @Path("upgrade/latest")
    @ApiOperation("Triggers a migration of Cassandra schema to the latest available")
    @ApiResponses({
        @ApiResponse(code = HttpStatus.CREATED_201, message = "The taskId of the given scheduled task", response = TaskIdDto.class,
            responseHeaders = {
                @ResponseHeader(name = "Location", description = "URL of the resource associated with the scheduled task")
            }),
        @ApiResponse(code = HttpStatus.CONFLICT_409, message = "Migration can not be done")
    })
    public Task upgradeToLatest() {
        try {
            return cassandraMigrationService.upgradeToLastVersion();
        } catch (IllegalStateException e) {
            LOGGER.info(MIGRATION_REQUEST_CAN_NOT_BE_DONE, e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.CONFLICT_409)
                .type(ErrorType.WRONG_STATE)
                .message(MIGRATION_REQUEST_CAN_NOT_BE_DONE)
                .cause(e)
                .haltError();
        }
    }

    @POST
    @Path("upgrade")
    @ApiOperation("Triggers a migration of Cassandra schema to a specific version")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            paramType = "body",
            dataType = "Integer",
            example = "3",
            value = "The schema version to upgrade to.")
    })
    @ApiResponses({
        @ApiResponse(code = HttpStatus.CREATED_201, message = "The taskId of the given scheduled task", response = TaskIdDto.class,
            responseHeaders = {
            @ResponseHeader(name = "Location", description = "URL of the resource associated with the scheduled task")
        }),
        @ApiResponse(code = HttpStatus.CONFLICT_409, message = "Migration can not be done")
    })
    public Task upgradeToVersion(Request request) {
        LOGGER.debug("Cassandra upgrade launched");
        CassandraVersionRequest cassandraVersionRequest = CassandraVersionRequest.parse(request.body());
        return cassandraMigrationService.upgradeToVersion(cassandraVersionRequest.getValue());
    }

    @GET
    @Path("latest")
    @ApiOperation(value = "Getting the latest version available for Cassandra schema")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "The latest version of the schema", response = CassandraVersionResponse.class)
    })
    public CassandraVersionResponse getCassandraLatestVersion() {
        return CassandraVersionResponse.from(cassandraMigrationService.getLatestVersion());
    }

    @GET
    @ApiOperation(value = "Getting the current version used by Cassandra schema")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "The current version of the schema", response = CassandraVersionResponse.class)
    })
    public CassandraVersionResponse getCassandraCurrentVersion() {
        return CassandraVersionResponse.from(cassandraMigrationService.getCurrentVersion());
    }
}
