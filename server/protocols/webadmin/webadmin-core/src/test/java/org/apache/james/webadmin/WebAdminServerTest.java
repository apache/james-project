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

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.is;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.Port;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;
import org.apache.james.webadmin.mdc.LoggingRequestFilter;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import spark.Service;

class WebAdminServerTest {
    @Test
    void getPortShouldThrowWhenNotConfigured() {
        WebAdminServer server = new WebAdminServer(
            WebAdminConfiguration.TEST_CONFIGURATION,
            ImmutableList.of(),
            new NoAuthenticationFilter(),
            new RecordingMetricFactory(),
            LoggingRequestFilter.create());
        assertThatThrownBy(server::getPort)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getPortShouldReturnPortWhenConfigured() {
        WebAdminServer server = WebAdminUtils.createWebAdminServer().start();

        Port port = server.getPort();

        assertThat(port).isNotNull();
    }

    @Test
    void aSecondRouteWithSameEndpointShouldNotOverridePreviouslyDefinedRoutes() {
        String firstAnswer = "1";
        String secondAnswer = "2";
        WebAdminServer server = WebAdminUtils.createWebAdminServer(
                myPrivateRouteWithConstAnswer(firstAnswer),
                myPrivateRouteWithConstAnswer(secondAnswer))
            .start();

        try {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(server)
                .setBasePath("/myRoute")
                .build();

            when()
                .get()
            .then()
                .body(is(firstAnswer));
        } finally {
            server.destroy();
        }
    }

    @Test
    void aSecondRouteWithSameEndpointShouldNotOverridePreviouslyDefinedRoutesWhenPublic() {
        String firstAnswer = "1";
        String secondAnswer = "2";
        WebAdminServer server = WebAdminUtils.createWebAdminServer(
                myPrivateRouteWithConstAnswer(firstAnswer),
                myPublicRouteWithConstAnswer(secondAnswer))
            .start();

        try {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(server)
                .setBasePath("/myRoute")
                .build();

            when()
                .get()
            .then()
                .body(is(firstAnswer));
        } finally {
            server.destroy();
        }
    }

    @Test
    void privateRoutesShouldBePrioritizedOveroutes() {
        String firstAnswer = "1";
        String secondAnswer = "2";
        WebAdminServer server = WebAdminUtils.createWebAdminServer(
                myPublicRouteWithConstAnswer(firstAnswer),
                myPrivateRouteWithConstAnswer(secondAnswer))
            .start();

        try {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(server)
                .setBasePath("/myRoute")
                .build();

            when()
                .get()
            .then()
                .body(is(secondAnswer));
        } finally {
            server.destroy();
        }
    }

    private Routes myPrivateRouteWithConstAnswer(String constAnswer) {
        return new Routes() {
            @Override
            public String getBasePath() {
                return "/myRoute";
            }

            @Override
            public void define(Service service) {
                service.get("/myRoute", (req, res) -> constAnswer);
            }
        };
    }

    private Routes myPublicRouteWithConstAnswer(String constAnswer) {
        return new PublicRoutes() {
            @Override
            public String getBasePath() {
                return "/myRoute";
            }

            @Override
            public void define(Service service) {
                service.get("/myRoute", (req, res) -> constAnswer);
            }
        };
    }
}
