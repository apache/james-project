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

package org.apache.james.webadmin;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;


import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;
import org.apache.james.webadmin.authentication.PasswordFilter;
import org.apache.james.webadmin.mdc.LoggingRequestFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import spark.Service;

class WebAdminServerCorsTest {
    private static final String PATH = "/myRoute";
    private static final String ORIGIN = "https://admin.example";

    private WebAdminServer server;

    @AfterEach
    void tearDown() {
        server.destroy();
    }

    private void start(WebAdminConfiguration configuration) {
        start(configuration, new NoAuthenticationFilter());
    }

    private void start(WebAdminConfiguration configuration, AuthenticationFilter authenticationFilter) {
        Routes routes = new Routes() {
            @Override
            public String getBasePath() {
                return PATH;
            }

            @Override
            public void define(Service service) {
                service.get(PATH, (req, res) -> "get");
            }
        };
        server = new WebAdminServer(configuration, ImmutableList.of(routes), authenticationFilter,
            new RecordingMetricFactory(), LoggingRequestFilter.create())
            .start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(server).build();
    }

    private static WebAdminConfiguration.Builder configuration() {
        return WebAdminConfiguration.builder()
            .enabled()
            .host("127.0.0.1")
            .port(new RandomPortSupplier());
    }

    private static Response preflight() {
        return given()
            .header("Origin", "https://attacker.example")
            .header("Access-Control-Request-Method", "TRACE")
            .header("Access-Control-Request-Headers", "x-http-method-override")
        .when()
            .options(PATH);
    }

    @Test
    void corsHeadersShouldNotBeSentWhenCorsIsDisabled() {
        start(configuration().corsDisabled().build());

        Response response = given().header("Origin", "https://attacker.example").when().get(PATH);
        Response preflight = preflight();

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(preflight.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(preflight.getHeader("Access-Control-Allow-Methods")).isNull();
        assertThat(preflight.getHeader("Access-Control-Allow-Headers")).isNull();
    }

    @Test
    void corsShouldOnlyGrantTheConfiguredOriginMethodsAndHeaders() {
        start(configuration().corsEnabled().urlCORSOrigin(ORIGIN).build());

        Response response = given().header("Origin", ORIGIN).when().get(PATH);
        Response preflight = preflight();

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
        assertThat(preflight.getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGIN);
        assertThat(preflight.getHeader("Access-Control-Allow-Methods")).doesNotContainIgnoringCase("TRACE");
        assertThat(preflight.getHeaders().getValues("Access-Control-Allow-Headers"))
            .noneMatch(value -> value.toLowerCase().contains("x-http-method-override"));
    }

    @Test
    void preflightShouldNotRequireAuthentication() {
        start(configuration().corsEnabled().urlCORSOrigin(ORIGIN).build(), new PasswordFilter("secret"));

        Response preflight = given()
            .header("Origin", ORIGIN)
            .header("Access-Control-Request-Method", "PUT")
        .when()
            .options(PATH);

        assertThat(preflight.getStatusCode()).isEqualTo(200);
        assertThat(preflight.getHeader("Access-Control-Allow-Methods")).isEqualTo("GET, POST, PUT, PATCH, DELETE");
        assertThat(given().when().get(PATH).getStatusCode()).isEqualTo(401);
    }
}
