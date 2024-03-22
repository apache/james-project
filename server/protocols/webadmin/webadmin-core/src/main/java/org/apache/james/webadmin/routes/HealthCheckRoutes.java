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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.webadmin.PublicRoutes;
import org.apache.james.webadmin.dto.HealthCheckDto;
import org.apache.james.webadmin.dto.HealthCheckExecutionResultDto;
import org.apache.james.webadmin.dto.HeathCheckAggregationExecutionResultDto;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class HealthCheckRoutes implements PublicRoutes {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckRoutes.class);

    public static final String HEALTHCHECK = "/healthcheck";
    public static final String CHECKS = "/checks";
    
    private static final String PARAM_COMPONENT_NAME = "componentName";

    private final JsonTransformer jsonTransformer;
    private final Set<HealthCheck> healthChecks;

    @Inject
    public HealthCheckRoutes(@Named("resolved-checks") Set<HealthCheck> healthChecks, JsonTransformer jsonTransformer) {
        this.healthChecks = healthChecks;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return HEALTHCHECK;
    }

    @Override
    public void define(Service service) {
        service.get(HEALTHCHECK, this::validateHealthChecks, jsonTransformer);
        service.get(HEALTHCHECK + "/checks/:" + PARAM_COMPONENT_NAME, this::performHealthCheckForComponent, jsonTransformer);
        service.get(HEALTHCHECK + CHECKS, this::getHealthChecks, jsonTransformer);
    }

    public Object validateHealthChecks(Request request, Response response) {
        List<Result> results = executeHealthChecks().collectList().block();
        ResultStatus status = retrieveAggregationStatus(results);
        response.status(getCorrespondingStatusCode(status));
        return new HeathCheckAggregationExecutionResultDto(status, mapResultToDto(results));
    }

    public Object performHealthCheckForComponent(Request request, Response response) {
        String componentName = request.params(PARAM_COMPONENT_NAME);
        HealthCheck healthCheck = healthChecks.stream()
            .filter(c -> c.componentName().getName().equals(componentName))
            .findFirst()
            .orElseThrow(() -> throw404(componentName));

        Result result = Mono.from(healthCheck.check()).block();
        logFailedCheck(result);
        response.status(getCorrespondingStatusCode(result.getStatus()));
        return new HealthCheckExecutionResultDto(result);
    }

    public Object getHealthChecks(Request request, Response response) {
        return healthChecks.stream()
                .map(healthCheck -> new HealthCheckDto(healthCheck.componentName()))
                .collect(ImmutableList.toImmutableList());
    }
    
    private int getCorrespondingStatusCode(ResultStatus resultStatus) {
        switch (resultStatus) {
            case HEALTHY:
            case DEGRADED:
                return HttpStatus.OK_200;
            case UNHEALTHY:
                return HttpStatus.SERVICE_UNAVAILABLE_503;
            default:
                throw new NotImplementedException(resultStatus + " is not supported");
        }
    }

    private void logFailedCheck(Result result) {
        switch (result.getStatus()) {
            case UNHEALTHY:
                if (result.getError().isPresent()) {
                    LOGGER.error("HealthCheck failed for {} : {}",
                        result.getComponentName().getName(),
                        result.getCause().orElse(""),
                        result.getError().get());
                } else {
                    LOGGER.error("HealthCheck failed for {} : {}",
                        result.getComponentName().getName(),
                        result.getCause().orElse(""));
                }
                break;
            case DEGRADED:
                if (result.getError().isPresent()) {
                    LOGGER.warn("HealthCheck is unstable for {} : {}",
                        result.getComponentName().getName(),
                        result.getCause().orElse(""),
                        result.getError().get());
                } else {
                    LOGGER.warn("HealthCheck is unstable for {} : {}",
                        result.getComponentName().getName(),
                        result.getCause().orElse(""));
                }
                break;
            case HEALTHY:
                // Here only to fix a warning, such cases are already filtered
                break;
        }
    }

    private Flux<Result> executeHealthChecks() {
        return Flux.fromIterable(healthChecks)
            .flatMap(HealthCheck::check, DEFAULT_CONCURRENCY)
            .doOnNext(this::logFailedCheck);
    }

    private ResultStatus retrieveAggregationStatus(List<Result> results) {
        return results.stream()
            .map(Result::getStatus)
            .reduce(ResultStatus::merge)
            .orElse(ResultStatus.HEALTHY);
    }

    private ImmutableList<HealthCheckExecutionResultDto> mapResultToDto(List<Result> results) {
        return results.stream()
            .map(HealthCheckExecutionResultDto::new)
            .collect(ImmutableList.toImmutableList());
    }
    
    private HaltException throw404(String componentName) {
        return ErrorResponder.builder()
            .message("Component with name %s cannot be found", componentName)
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .haltError();
    }
}
