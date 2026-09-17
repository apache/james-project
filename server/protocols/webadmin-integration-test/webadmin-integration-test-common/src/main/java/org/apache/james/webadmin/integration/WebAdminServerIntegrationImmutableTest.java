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
import static org.apache.james.webadmin.Constants.JSON_CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipOutputStream;

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
    private static final String JMAP_USER = "jmap@" + DOMAIN;
    private static final String EXPORT_USER = "export@" + DOMAIN;
    private static final String RESTORE_USER = "restore@" + DOMAIN;

    private static DataProbe dataProbe;

    @BeforeAll
    static void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
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
                "var/mail/address-error",
                "var/mail/rrt-error"));
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
                "var/mail/address-error",
                "var/mail/rrt-error"));
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

    @Test
    void getUserDefaultIdentityShouldReturnNotFoundByDefault() {
        when()
            .get(String.format("/users/%s/identities?default=true", USERNAME))
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(JSON_CONTENT_TYPE)
            .body("message", is("Default identity can not be found"));
    }

    @Test
    void getIdentitiesOfInvalidUserShouldReturnBadRequest() {
        given()
            .urlEncodingEnabled(true)
            .get(String.format("/users/%s/identities?default=true", "John Doe"))
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void createIdentitiesForInvalidUserShouldReturnBadRequest() {
        given()
            .urlEncodingEnabled(true)
            .body("{\n" +
                "  \"name\": \"create name 1\",\n" +
                "  \"email\": \"bob@domain.tld\",\n" +
                "  \"textSignature\": \"create textSignature1\",\n" +
                "  \"htmlSignature\": \"create htmlSignature1\",\n" +
                "  \"sortOrder\": 99,\n" +
                "  \"bcc\": [\n" +
                "    {\n" +
                "      \"name\": \"create bcc 1\",\n" +
                "      \"email\": \"create_boss_bcc_1@domain.tld\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"replyTo\": [\n" +
                "    {\n" +
                "      \"name\": \"create replyTo 1\",\n" +
                "      \"email\": \"create_boss1@domain.tld\"\n" +
                "    }\n" +
                "  ]\n" +
                "}")
            .post(String.format("/users/%s/identities", "John Doe"))
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void updateIdentitiesForInvalidUserShouldReturnBadRequest() {
        given()
            .urlEncodingEnabled(true)
            .body("{\n" +
                "  \"name\": \"create name 1\",\n" +
                "  \"email\": \"bob@domain.tld\",\n" +
                "  \"textSignature\": \"create textSignature1\",\n" +
                "  \"htmlSignature\": \"create htmlSignature1\",\n" +
                "  \"sortOrder\": 99,\n" +
                "  \"bcc\": [\n" +
                "    {\n" +
                "      \"name\": \"create bcc 1\",\n" +
                "      \"email\": \"create_boss_bcc_1@domain.tld\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"replyTo\": [\n" +
                "    {\n" +
                "      \"name\": \"create replyTo 1\",\n" +
                "      \"email\": \"create_boss1@domain.tld\"\n" +
                "    }\n" +
                "  ]\n" +
                "}")
            .put(String.format("/users/%s/identities/b1c924a3-5b86-44fa-a036-77825ec0e3e6", "John Doe"))
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void jmapUserTasksShouldBeExposed() throws Exception {
        dataProbe.addUser(JMAP_USER, "anyPassword");

        String taskId = with()
            .queryParam("task", "recomputeFastViewProjectionItems")
            .post("/users/" + JMAP_USER + "/mailboxes")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("type", is("RecomputeUserFastViewProjectionItemsTask"));
    }

    @Test
    void mailboxesExportTasksShouldBeExposed() throws Exception {
        dataProbe.addUser(EXPORT_USER, "anyPassword");

        String taskId = with()
            .queryParam("task", "export")
            .post("/users/" + EXPORT_USER + "/mailboxes")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("type", is("MailboxesExportTask"));
    }

    @Test
    void mailboxesRestoreTasksShouldBeExposed() throws Exception {
        dataProbe.addUser(RESTORE_USER, "anyPassword");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // empty zip
        }
        byte[] emptyZip = baos.toByteArray();

        String taskId = with()
            .queryParam("task", "restore")
            .body(emptyZip)
            .post("/users/" + RESTORE_USER + "/mailboxes")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("type", is("MailboxesRestoreTask"));
    }

    @Test
    void createMissParentsTasksShouldBeExposed() {
        String taskId = with()
            .queryParam("task", "createMissingParents")
            .post("/mailboxes")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("completed"))
            .body("type", is("CreateMissingParentsTask"))
            .body("additionalInformation.created", hasSize(0))
            .body("additionalInformation.totalCreated", is(0))
            .body("additionalInformation.failures", empty())
            .body("additionalInformation.totalFailure", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }
}
