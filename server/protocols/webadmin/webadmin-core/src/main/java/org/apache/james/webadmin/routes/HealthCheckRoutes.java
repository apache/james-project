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

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.webadmin.Routes;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Service;

@Api(tags = "Healthchecks")
@Path(HealthCheckRoutes.HEALTHCHECK)
public class HealthCheckRoutes implements Routes {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckRoutes.class);

    public static final String HEALTHCHECK = "/healthcheck";


    private final Set<HealthCheck> healthChecks;
    private Service service;

    @Inject
    public HealthCheckRoutes(Set<HealthCheck> healthChecks) {
        this.healthChecks = healthChecks;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        validateHealthchecks();
    }

    @GET
    @ApiOperation(value = "Validate all health checks")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK"),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                message = "Internal server error - When one check has failed.")
    })
    public void validateHealthchecks() {
        service.get(HEALTHCHECK,
            (request, response) -> {
                List<Result> anyUnhealthyOrDegraded = retrieveUnhealthyOrDegradedHealthChecks();

                anyUnhealthyOrDegraded.forEach(this::logFailedCheck);
                response.status(getCorrespondingStatusCode(anyUnhealthyOrDegraded));
                return response;
            });
    }

    private int getCorrespondingStatusCode(List<Result> anyUnhealthy) {
        if (anyUnhealthy.isEmpty()) {
            return HttpStatus.OK_200;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR_500;
        }
    }

    private void logFailedCheck(Result result) {
        switch (result.getStatus()) {
        case UNHEALTHY:
            LOGGER.error("HealthCheck failed for {} : {}",
                    result.getComponentName().getName(),
                    result.getCause().orElse(""));
            break;
        case DEGRADED:
            LOGGER.warn("HealthCheck is unstable for {} : {}",
                    result.getComponentName().getName(),
                    result.getCause().orElse(""));
            break;
        case HEALTHY:
            // Here only to fix a warning, such cases are already filtered
            break;
        }
    }

    private List<Result> retrieveUnhealthyOrDegradedHealthChecks() {
        return healthChecks.stream()
            .map(HealthCheck::check)
            .filter(result -> result.isUnHealthy() || result.isDegraded())
            .collect(Guavate.toImmutableList());
    }
}
