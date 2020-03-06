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
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.AliasRoutes;
import org.apache.james.webadmin.routes.DomainsRoutes;
import org.apache.james.webadmin.routes.ForwardRoutes;
import org.apache.james.webadmin.routes.GroupsRoutes;
import org.apache.james.webadmin.routes.HealthCheckRoutes;
import org.apache.james.webadmin.routes.MailQueueRoutes;
import org.apache.james.webadmin.routes.MailRepositoriesRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.routes.UserMailboxesRoutes;
import org.apache.james.webadmin.routes.UserRoutes;
import org.apache.james.webadmin.swagger.routes.SwaggerRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

public abstract class WebAdminServerIntegrationTest {

    private static final String DOMAIN = "domain";
    protected static final String USERNAME = "username@" + DOMAIN;
    private static final String USERNAME_2 = "username2@" + DOMAIN;
    private static final String GROUP = "group@" + DOMAIN;
    private static final String SPECIFIC_DOMAIN = DomainsRoutes.DOMAINS + SEPARATOR + DOMAIN;
    private static final String SPECIFIC_USER = UserRoutes.USERS + SEPARATOR + USERNAME;
    private static final String MAILBOX = "mailbox";
    private static final String SPECIFIC_MAILBOX = SPECIFIC_USER + SEPARATOR + UserMailboxesRoutes.MAILBOXES + SEPARATOR + MAILBOX;

    protected static final String ALIAS_1 = "alias1@" + DOMAIN;
    protected static final String ALIAS_2 = "alias2@" + DOMAIN;

    private DataProbe dataProbe;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
    }

    @Test
    void postShouldAddTheGivenDomain() throws Exception {
        when()
            .put(SPECIFIC_DOMAIN)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listDomains()).contains(DOMAIN);
    }

    @Test
    void mailQueueRoutesShouldBeExposed() {
        when()
            .get(MailQueueRoutes.BASE_URL)
        .then()
            .statusCode(HttpStatus.OK_200);
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
    void deleteShouldRemoveTheGivenDomain() throws Exception {
        when()
            .delete(SPECIFIC_DOMAIN)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listDomains()).doesNotContain(DOMAIN);
    }

    @Test
    void postShouldAddTheUser() throws Exception {
        given()
            .body("{\"password\":\"password\"}")
        .when()
            .put(SPECIFIC_USER)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listUsers()).contains(USERNAME);
    }

    @Test
    void deleteShouldRemoveTheUser() throws Exception {
        dataProbe.addUser(USERNAME, "anyPassword");

        given()
            .body("{\"username\":\"" + USERNAME + "\",\"password\":\"password\"}")
        .when()
            .delete(SPECIFIC_USER)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listUsers()).doesNotContain(USERNAME);
    }

    @Test
    void getUsersShouldDisplayUsers() throws Exception {
        dataProbe.addUser(USERNAME, "anyPassword");

        when()
            .get(UserRoutes.USERS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("[{\"username\":\"username@domain\"}]"));
    }

    @Test
    void putMailboxShouldAddAMailbox(GuiceJamesServer guiceJamesServer) throws Exception {
        dataProbe.addUser(USERNAME, "anyPassword");

        when()
            .put(SPECIFIC_MAILBOX)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(guiceJamesServer.getProbe(MailboxProbeImpl.class).listUserMailboxes(USERNAME)).containsExactly(MAILBOX);
    }

    @Test
    void deleteMailboxShouldRemoveAMailbox(GuiceJamesServer guiceJamesServer) throws Exception {
        dataProbe.addUser(USERNAME, "anyPassword");
        guiceJamesServer.getProbe(MailboxProbeImpl.class).createMailbox("#private", USERNAME, MAILBOX);

        when()
            .delete(SPECIFIC_MAILBOX)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(guiceJamesServer.getProbe(MailboxProbeImpl.class).listUserMailboxes(USERNAME)).isEmpty();
    }

    @Test
    void addressGroupsEndpointShouldHandleRequests() throws Exception {
        with()
            .put(GroupsRoutes.ROOT_PATH + SEPARATOR + GROUP + SEPARATOR + USERNAME);
        with()
            .put(GroupsRoutes.ROOT_PATH + SEPARATOR + GROUP + SEPARATOR + USERNAME_2);

        List<String> members = when()
            .get(GroupsRoutes.ROOT_PATH + SEPARATOR + GROUP)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .extract()
            .jsonPath()
            .getList(".");
        assertThat(members).containsOnly(USERNAME, USERNAME_2);
    }

    @Test
    void addressForwardsEndpointShouldListForwardAddresses() throws Exception {
        dataProbe.addUser(USERNAME, "anyPassword");
        dataProbe.addUser(USERNAME_2, "anyPassword");

        with()
            .put(ForwardRoutes.ROOT_PATH + SEPARATOR + USERNAME + "/targets/to1@domain.com");
        with()
            .put(ForwardRoutes.ROOT_PATH + SEPARATOR + USERNAME_2 + "/targets/to2@domain.com");

        List<String> members = when()
            .get(ForwardRoutes.ROOT_PATH)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .extract()
            .jsonPath()
            .getList(".");
        assertThat(members).containsOnly(USERNAME, USERNAME_2);
    }

    @Test
    void addressAliasesEndpointShouldListAliasesAddresses() {
        with()
            .put(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME + "/sources/" + ALIAS_1);
        with()
            .put(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME_2 + "/sources/" + ALIAS_2);

        List<String> members = when()
            .get(AliasRoutes.ROOT_PATH)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .extract()
            .jsonPath()
            .getList(".");
        assertThat(members).containsOnly(USERNAME, USERNAME_2);
    }

    @Test
    void getSwaggerShouldReturnJsonDataForSwagger() {
        when()
            .get(SwaggerRoutes.SWAGGER_ENDPOINT)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body(containsString("\"swagger\":\"2.0\""))
            .body(containsString("\"info\":{\"description\":\"All the web administration API for JAMES\",\"version\":\"V1.0\",\"title\":\"JAMES Web Admin API\"}"))
            .body(containsString("\"tags\":[\"User's Mailbox\"]"))
            .body(containsString("\"tags\":[\"GlobalQuota\"]"))
            .body(containsString("\"tags\":[\"DomainQuota\"]"))
            .body(containsString("\"tags\":[\"UserQuota\"]"))
            .body(containsString("\"tags\":[\"Domains\"]"))
            .body(containsString("\"tags\":[\"Users\"]"))
            .body(containsString("\"tags\":[\"MailRepositories\"]"))
            .body(containsString("\"tags\":[\"MailQueues\"]"))
            .body(containsString("\"tags\":[\"Address Forwards\"]"))
            .body(containsString("\"tags\":[\"Address Aliases\"]"))
            .body(containsString("\"tags\":[\"Address Groups\"]"))
            .body(containsString("{\"name\":\"Mailboxes\"}"));
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
    void jmapUserTasksShouldBeExposed() throws Exception {
        dataProbe.addUser(USERNAME, "anyPassword");

        String taskId = with()
            .queryParam("task", "recomputeFastViewProjectionItems")
            .post("/users/" + USERNAME + "/mailboxes")
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
        dataProbe.addUser(USERNAME, "anyPassword");

        String taskId = with()
            .queryParam("task", "export")
            .post("/users/" + USERNAME + "/mailboxes")
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
}