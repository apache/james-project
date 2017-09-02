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

import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.CassandraVersionRequest;
import org.apache.james.webadmin.service.CassandraMigrationService;
import org.apache.james.webadmin.service.MigrationException;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Service;

public class CassandraMigrationRoutes implements Routes {


    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMigrationRoutes.class);

    public static final String VERSION_BASE = "/cassandra/version";
    private static final String VERSION_BASE_LATEST = VERSION_BASE + "/latest";
    private static final String VERSION_UPGRADE_BASE = VERSION_BASE + "/upgrade";
    private static final String VERSION_UPGRADE_TO_LATEST_BASE = VERSION_UPGRADE_BASE + "/latest";
    private static final int NO_CONTENT = 204;
    private static final int INVALID_VERSION = 400;
    private static final int MIGRATION_CAN_NOT_BE_PERFORMED = 410;
    private static final int INTERNAL_ERROR = 500;

    private final CassandraMigrationService cassandraMigrationService;
    private final JsonTransformer jsonTransformer;

    @Inject
    public CassandraMigrationRoutes(CassandraMigrationService cassandraMigrationService, JsonTransformer jsonTransformer) {
        this.cassandraMigrationService = cassandraMigrationService;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public void define(Service service) {
        service.get(VERSION_BASE,
            (request, response) -> cassandraMigrationService.getCurrentVersion(),
            jsonTransformer);

        service.get(VERSION_BASE_LATEST,
            (request, response) -> cassandraMigrationService.getLatestVersion(),
            jsonTransformer);

        service.post(VERSION_UPGRADE_BASE, (request, response) -> {
            LOGGER.debug("Cassandra upgrade launched");
            try {
                CassandraVersionRequest cassandraVersionRequest = CassandraVersionRequest.parse(request.body());
                cassandraMigrationService.upgradeToVersion(cassandraVersionRequest.getValue());
                response.status(NO_CONTENT);
            } catch (NullPointerException | IllegalArgumentException e) {
                LOGGER.info("Invalid request for version upgrade");
                response.status(INVALID_VERSION);
            } catch (IllegalStateException e) {
                LOGGER.info("The migration requested can not be performed.", e);
                response.status(MIGRATION_CAN_NOT_BE_PERFORMED);
                response.body(e.getMessage());
            } catch (MigrationException e) {
                LOGGER.error("An error lead to partial migration process", e);
                response.status(INTERNAL_ERROR);
                response.body(e.getMessage());
            }
            return Constants.EMPTY_BODY;
        });

        service.post(VERSION_UPGRADE_TO_LATEST_BASE, (request, response) -> {
            try {
                cassandraMigrationService.upgradeToLastVersion();
            } catch (IllegalStateException e) {
                LOGGER.info("The migration requested can not be performed.", e);
                response.status(MIGRATION_CAN_NOT_BE_PERFORMED);
                response.body(e.getMessage());
            } catch (MigrationException e) {
                LOGGER.error("An error lead to partial migration process", e);
                response.status(INTERNAL_ERROR);
                response.body(e.getMessage());
            }

            return Constants.EMPTY_BODY;
        });
    }
}
