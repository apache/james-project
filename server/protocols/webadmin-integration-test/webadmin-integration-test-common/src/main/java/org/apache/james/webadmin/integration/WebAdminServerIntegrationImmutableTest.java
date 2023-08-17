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

package org.apache.james.webadmin.integration;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import org.apache.james.GuiceJamesServer;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.HealthCheckRoutes;
import org.apache.james.webadmin.routes.MailQueueRoutes;
import org.apache.james.webadmin.routes.MailRepositoriesRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

public abstract class WebAdminServerIntegrationImmutableTest {
    private static final String DOMAIN = "domain";
    protected static final String USERNAME = "username@" + DOMAIN;

    @BeforeAll
    static void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        DataProbe dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
    }

    @Test
    void mailQueueRoutesShouldBeExposed() {
        when()
            .get(MailQueueRoutes.BASE_URL)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", containsInAnyOrder("spool", "outgoing"));
    }

    @Test
    void metricsRoutesShouldBeExposed() {
        String body = when()
                .get("/metrics")
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body()
                .asString();

        assertThat(body).contains("outgoingMails_total 0.0");
    }

    @Test
    void healthCheckShouldReturn200WhenCalledRepeatedly() {
        given().get(HealthCheckRoutes.HEALTHCHECK);
        given().get(HealthCheckRoutes.HEALTHCHECK);
        given().get(HealthCheckRoutes.HEALTHCHECK);
        given().get(HealthCheckRoutes.HEALTHCHECK);
        given().get(HealthCheckRoutes.HEALTHCHECK);

        when()
            .get(HealthCheckRoutes.HEALTHCHECK)
        .then()
            .statusCode(HttpStatus.OK_200);
    }

    @Test
    void mailRepositoriesRoutesShouldBeExposed() {
        when()
            .get(MailRepositoriesRoutes.MAIL_REPOSITORIES)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("repository", containsInAnyOrder(
                "var/mail/error",
                "var/mail/relay-denied",
                "var/mail/address-error"));
    }

    @Test
    void gettingANonExistingMailRepositoryShouldNotCreateIt() {
        given()
            .get(MailRepositoriesRoutes.MAIL_REPOSITORIES + "file%3A%2F%2Fvar%2Fmail%2Fcustom");

        when()
            .get(MailRepositoriesRoutes.MAIL_REPOSITORIES)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("repository", containsInAnyOrder(
                "var/mail/error",
                "var/mail/relay-denied",
                "var/mail/address-error"));
    }

    @Test
    void validateHealthChecksShouldReturnOk() {
        when()
            .get(HealthCheckRoutes.HEALTHCHECK)
        .then()
            .statusCode(HttpStatus.OK_200);
    }

    @Test
    void jmapTasksShouldBeExposed() {
        String taskId = with()
            .queryParam("task", "recomputeFastViewProjectionItems")
            .post("/mailboxes")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("type", is("RecomputeAllFastViewProjectionItemsTask"));
    }

    @Test
    void jmapFilteringProjectionTasksShouldBeExposed() {
        String taskId = with()
            .queryParam("task", "populateFilteringProjection")
            .post("/mailboxes")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("type", is("PopulateFilteringProjectionTask"));
    }
}