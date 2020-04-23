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

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.HashSet;
import java.util.Set;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;

import io.restassured.RestAssured;
import net.javacrumbs.jsonunit.core.Option;
import reactor.core.publisher.Mono;

public class HealthCheckRoutesTest {

    private static final String NAME_1 = "component-1";
    private static final String NAME_2 = "component-2";
    private static final String NAME_3 = "component 3";
    private static final String NAME_3_ESCAPED = "component%203";

    private static final ComponentName COMPONENT_NAME_1 = new ComponentName(NAME_1);
    private static final ComponentName COMPONENT_NAME_2 = new ComponentName(NAME_2);
    private static final ComponentName COMPONENT_NAME_3 = new ComponentName(NAME_3); // mind the space
    private static final String SAMPLE_CAUSE = "sample cause";

    private static HealthCheck healthCheck(Result result) {
        return new HealthCheck() {
            @Override
            public ComponentName componentName() {
                return result.getComponentName();
            }

            @Override
            public Publisher<Result> checkReactive() {
                return Mono.just(result);
            }
        };
    }

    private WebAdminServer webAdminServer;
    private Set<HealthCheck> healthChecks;

    @Before
    public void setUp() throws Exception {
        healthChecks = new HashSet<>();
        webAdminServer = WebAdminUtils.createWebAdminServer(new HealthCheckRoutes(healthChecks, new JsonTransformer()))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(HealthCheckRoutes.HEALTHCHECK)
            .build();
    }

    @After
    public void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    public void validateHealthChecksShouldReturnOkWhenNoHealthChecks() {
        String healthCheckBody =
            "{\"status\":\"healthy\"," +
            " \"checks\":[]}";

        String retrieveBody = when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .extract()
            .body().asString();

        assertThatJson(retrieveBody)
            .isEqualTo(healthCheckBody);
    }

    @Test
    public void validateHealthChecksShouldReturnOkWhenHealthChecksAreHealthy() {
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_1)));
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_2)));
        String healthCheckBody =
            "{\"status\": \"healthy\"," +
            " \"checks\": [" +
            "  {" +
            "    \"componentName\": \"component-1\"," +
            "    \"escapedComponentName\": \"component-1\"," +
            "    \"status\": \"healthy\"," +
            "    \"cause\": null" +
            "  }," +
            "  {" +
            "    \"componentName\": \"component-2\"," +
            "    \"escapedComponentName\": \"component-2\"," +
            "    \"status\": \"healthy\"," +
            "    \"cause\": null" +
            "}]}";

        String retrieveBody = when()
                .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body().asString();

        assertThatJson(retrieveBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(healthCheckBody);
    }

    @Test
    public void validateHealthChecksShouldReturnInternalErrorWhenOneHealthCheckIsUnhealthy() {
        healthChecks.add(healthCheck(Result.unhealthy(COMPONENT_NAME_1, "cause")));
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_2)));
        String healthCheckBody =
            "{\"status\": \"unhealthy\"," +
            " \"checks\": [" +
            "  {" +
            "    \"componentName\": \"component-1\"," +
            "    \"escapedComponentName\": \"component-1\"," +
            "    \"status\": \"unhealthy\"," +
            "    \"cause\": \"cause\"" +
            "  }," +
            "  {" +
            "    \"componentName\": \"component-2\"," +
            "    \"escapedComponentName\": \"component-2\"," +
            "    \"status\": \"healthy\"," +
            "    \"cause\": null" +
            "}]}";

        String retrieveBody = when()
                .get()
            .then()
                .statusCode(HttpStatus.SERVICE_UNAVAILABLE_503)
                .extract()
                .body().asString();

        assertThatJson(retrieveBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(healthCheckBody);
    }

    @Test
    public void validateHealthChecksShouldReturnInternalErrorWhenAllHealthChecksAreUnhealthy() {
        healthChecks.add(healthCheck(Result.unhealthy(COMPONENT_NAME_1, "cause")));
        healthChecks.add(healthCheck(Result.unhealthy(COMPONENT_NAME_2, SAMPLE_CAUSE)));
        String healthCheckBody =
            "{\"status\": \"unhealthy\"," +
            " \"checks\": [" +
            "  {" +
            "    \"componentName\": \"component-1\"," +
            "    \"escapedComponentName\": \"component-1\"," +
            "    \"status\": \"unhealthy\"," +
            "    \"cause\": \"cause\"" +
            "  }," +
            "  {" +
            "    \"componentName\": \"component-2\"," +
            "    \"escapedComponentName\": \"component-2\"," +
            "    \"status\": \"unhealthy\"," +
            "    \"cause\": \"sample cause\"" +
            "}]}";

        String retrieveBody = when()
                .get()
            .then()
                .statusCode(HttpStatus.SERVICE_UNAVAILABLE_503)
                .extract()
                .body().asString();

        assertThatJson(retrieveBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(healthCheckBody);
    }

    @Test
    public void validateHealthChecksShouldReturnInternalErrorWhenOneHealthCheckIsDegraded() {
        healthChecks.add(healthCheck(Result.degraded(COMPONENT_NAME_1, "cause")));
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_2)));
        String healthCheckBody =
            "{\"status\": \"degraded\"," +
            " \"checks\": [" +
            "  {" +
            "    \"componentName\": \"component-1\"," +
            "    \"escapedComponentName\": \"component-1\"," +
            "    \"status\": \"degraded\"," +
            "    \"cause\": \"cause\"" +
            "  }," +
            "  {" +
            "    \"componentName\": \"component-2\"," +
            "    \"escapedComponentName\": \"component-2\"," +
            "    \"status\": \"healthy\"," +
            "    \"cause\": null" +
            "}]}";

        String retrieveBody = when()
                .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body().asString();

        assertThatJson(retrieveBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(healthCheckBody);
    }

    @Test
    public void validateHealthChecksShouldReturnInternalErrorWhenAllHealthCheckAreDegraded() {
        healthChecks.add(healthCheck(Result.degraded(COMPONENT_NAME_1, "cause")));
        healthChecks.add(healthCheck(Result.degraded(COMPONENT_NAME_2, "cause")));
        String healthCheckBody =
            "{\"status\": \"degraded\"," +
            " \"checks\": [" +
            "  {" +
            "    \"componentName\": \"component-1\"," +
            "    \"escapedComponentName\": \"component-1\"," +
            "    \"status\": \"degraded\"," +
            "    \"cause\": \"cause\"" +
            "  }," +
            "  {" +
            "    \"componentName\": \"component-2\"," +
            "    \"escapedComponentName\": \"component-2\"," +
            "    \"status\": \"degraded\"," +
            "    \"cause\": \"cause\"" +
            "}]}";

        String retrieveBody = when()
                .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body().asString();

        assertThatJson(retrieveBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(healthCheckBody);
    }

    @Test
    public void validateHealthChecksShouldReturnStatusUnHealthyWhenOneIsUnHealthyAndOtherIsDegraded() {
        healthChecks.add(healthCheck(Result.degraded(COMPONENT_NAME_1, "cause")));
        healthChecks.add(healthCheck(Result.unhealthy(COMPONENT_NAME_2, "cause")));
        String healthCheckBody =
            "{\"status\": \"unhealthy\"," +
            " \"checks\": [" +
            "  {" +
            "    \"componentName\": \"component-1\"," +
            "    \"escapedComponentName\": \"component-1\"," +
            "    \"status\": \"degraded\"," +
            "    \"cause\": \"cause\"" +
            "  }," +
            "  {" +
            "    \"componentName\": \"component-2\"," +
            "    \"escapedComponentName\": \"component-2\"," +
            "    \"status\": \"unhealthy\"," +
            "    \"cause\": \"cause\"" +
            "}]}";
        String retrieveBody = when()
                .get()
            .then()
                .statusCode(HttpStatus.SERVICE_UNAVAILABLE_503)
                .extract()
                .body().asString();

        assertThatJson(retrieveBody)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(healthCheckBody);
    }
    
    @Test
    public void performHealthCheckShouldReturnOkWhenHealthCheckIsHealthy() {
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_1)));
        
        given()
            .pathParam("componentName", COMPONENT_NAME_1.getName())
        .when()
            .get("/checks/{componentName}")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("componentName", equalTo(NAME_1))
            .body("escapedComponentName", equalTo(NAME_1))
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()))
            .body("cause", is(nullValue()));
    }
    
    @Test
    public void performHealthCheckShouldReturnNotFoundWhenComponentNameIsUnknown() {
        
        given()
            .pathParam("componentName", "unknown")
        .when()
            .get("/checks/{componentName}")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("details", is(nullValue()))
            .body("type", equalTo("notFound"))
            .body("message", equalTo("Component with name unknown cannot be found"))
            .body("statusCode", is(HttpStatus.NOT_FOUND_404));
    }
    
    @Test
    public void performHealthCheckShouldReturnInternalErrorWhenHealthCheckIsDegraded() {
        healthChecks.add(healthCheck(Result.degraded(COMPONENT_NAME_1, "the cause")));
        
        given()
            .pathParam("componentName", COMPONENT_NAME_1.getName())
        .when()
            .get("/checks/{componentName}")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("componentName", equalTo(NAME_1))
            .body("escapedComponentName", equalTo(NAME_1))
            .body("status", equalTo(ResultStatus.DEGRADED.getValue()))
            .body("cause", equalTo("the cause"));
    }
    
    @Test
    public void performHealthCheckShouldReturnInternalErrorWhenHealthCheckIsUnhealthy() {
        healthChecks.add(healthCheck(Result.unhealthy(COMPONENT_NAME_1, SAMPLE_CAUSE)));
        
        given()
            .pathParam("componentName", COMPONENT_NAME_1.getName())
        .when()
            .get("/checks/{componentName}")
        .then()
            .statusCode(HttpStatus.SERVICE_UNAVAILABLE_503)
            .body("componentName", equalTo(NAME_1))
            .body("escapedComponentName", equalTo(NAME_1))
            .body("status", equalTo(ResultStatus.UNHEALTHY.getValue()))
            .body("cause", is(SAMPLE_CAUSE));
    }
    
    @Test
    public void performHealthCheckShouldReturnProperlyEscapedComponentName() {
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_3)));
        
        given()
            .pathParam("componentName", COMPONENT_NAME_3.getName())
        .when()
            .get("/checks/{componentName}")
        .then()
            .body("componentName", equalTo(NAME_3))
            .body("escapedComponentName", equalTo(NAME_3_ESCAPED))
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()))
            .body("cause", is(nullValue()));
    }
    
    @Test
    public void performHealthCheckShouldWorkWithEscapedPathParam() {
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_3)));
        
        // disable URL encoding
        RestAssured.requestSpecification.urlEncodingEnabled(false);

        given()
            .pathParam("componentName", NAME_3_ESCAPED)
        .when()
            .get("/checks/{componentName}")
        .then()
            .body("componentName", equalTo(NAME_3))
            .body("escapedComponentName", equalTo(NAME_3_ESCAPED))
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()))
            .body("cause", is(nullValue()));
    }

    @Test
    public void getHealthchecksShouldReturnEmptyWhenNoHealthChecks() {
        when()
           .get(HealthCheckRoutes.CHECKS)
        .then()
           .body(is("[]"))
           .body("", hasSize(0))
           .statusCode(HttpStatus.OK_200);
    }

    @Test
    public void getHealthchecksShouldReturnHealthCheckWhenHealthCheckPresent() {
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_3)));
        when()
           .get(HealthCheckRoutes.CHECKS)
        .then()
            .body("", hasSize(1))
            .body("componentName[0]", equalTo(NAME_3))
            .body("escapedComponentName[0]", equalTo(NAME_3_ESCAPED))
            .statusCode(HttpStatus.OK_200);
    }

    @Test
    public void getHealthchecksShouldReturnHealthChecksWhenHealthChecksPresent() {
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_2)));
        healthChecks.add(healthCheck(Result.healthy(COMPONENT_NAME_3)));
        when()
           .get(HealthCheckRoutes.CHECKS)
        .then()
           .body("", hasSize(2))
           .statusCode(HttpStatus.OK_200);
    }

}