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


import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.webadmin.authentication.PasswordFilter;
import org.apache.james.webadmin.mdc.LoggingRequestFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import spark.Service;

class WebAdminServerAuthenticationTest {
    private static final String PATH = "/myRoute";

    private WebAdminServer server;

    @BeforeEach
    void setUp() {
        Routes routes = new Routes() {
            @Override
            public String getBasePath() {
                return PATH;
            }

            @Override
            public void define(Service service) {
                service.get(PATH, (req, res) -> "get");
                service.put(PATH, (req, res) -> "put");
                service.delete(PATH, (req, res) -> "delete");
                service.get(PATH + "/typed", "message/rfc822", (req, res) -> "typed");
            }
        };
        server = new WebAdminServer(
            WebAdminConfiguration.TEST_CONFIGURATION,
            ImmutableList.of(routes),
            new PasswordFilter("secret"),
            new RecordingMetricFactory(),
            LoggingRequestFilter.create())
            .start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(server).build();
    }

    @AfterEach
    void tearDown() {
        server.destroy();
    }

    @Test
    void methodOverrideShouldBeRejectedWithoutPassword() {
        given()
            .header("X-HTTP-Method-Override", "PUT")
        .when()
            .options(PATH)
        .then()
            .statusCode(400);
    }

    @Test
    void methodOverrideShouldBeRejectedWithPassword() {
        given()
            .header("Password", "secret")
            .header("X-HTTP-Method-Override", "DELETE")
        .when()
            .get(PATH)
        .then()
            .statusCode(400);
    }

    @Test
    void typedRouteShouldRequireAPassword() {
        given()
            .accept("message/rfc822")
        .when()
            .get(PATH + "/typed")
        .then()
            .statusCode(401);
    }

    @Test
    void acceptNegotiationShouldNotSkipAuthentication() {
        given()
            .accept("message/rfc822, */*;q=0")
        .when()
            .get(PATH + "/typed")
        .then()
            .statusCode(401);
    }

    @Test
    void emptyPathSegmentsShouldNotSkipAuthentication() {
        given()
            .urlEncodingEnabled(false)
        .when()
            .get("/" + PATH)
        .then()
            .statusCode(401);
    }

    @Test
    void typedRouteShouldBeServedWithPassword() {
        given()
            .header("Password", "secret")
            .accept("message/rfc822, */*;q=0")
        .when()
            .get(PATH + "/typed")
        .then()
            .statusCode(200);
    }
}
