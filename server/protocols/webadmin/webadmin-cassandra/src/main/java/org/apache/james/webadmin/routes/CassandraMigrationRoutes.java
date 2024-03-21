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

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.migration.CassandraMigrationService;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.CassandraVersionRequest;
import org.apache.james.webadmin.dto.CassandraVersionResponse;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Service;

public class CassandraMigrationRoutes implements Routes {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMigrationRoutes.class);

    public static final String VERSION_BASE = "/cassandra/version";
    private static final String VERSION_BASE_LATEST = VERSION_BASE + "/latest";
    private static final String VERSION_UPGRADE_BASE = VERSION_BASE + "/upgrade";
    private static final String VERSION_UPGRADE_TO_LATEST_BASE = VERSION_UPGRADE_BASE + "/latest";
    public static final String MIGRATION_REQUEST_CAN_NOT_BE_DONE = "The migration requested can not be performed";

    private final CassandraMigrationService cassandraMigrationService;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;


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

        TaskFromRequest upgradeToVersionTaskFromRequest = this::upgradeToVersion;
        service.post(VERSION_UPGRADE_BASE, upgradeToVersionTaskFromRequest.asRoute(taskManager), jsonTransformer);

        TaskFromRequest upgradeToLatestTaskFromRequest = request -> upgradeToLatest();
        service.post(VERSION_UPGRADE_TO_LATEST_BASE, upgradeToLatestTaskFromRequest.asRoute(taskManager), jsonTransformer);
    }

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

    public Task upgradeToVersion(Request request) {
        LOGGER.debug("Cassandra upgrade launched");
        CassandraVersionRequest cassandraVersionRequest = CassandraVersionRequest.parse(request.body());
        return cassandraMigrationService.upgradeToVersion(cassandraVersionRequest.getValue());
    }

    public CassandraVersionResponse getCassandraLatestVersion() {
        return CassandraVersionResponse.from(cassandraMigrationService.getLatestVersion());
    }

    public CassandraVersionResponse getCassandraCurrentVersion() {
        return CassandraVersionResponse.from(cassandraMigrationService.getCurrentVersion());
    }
}
