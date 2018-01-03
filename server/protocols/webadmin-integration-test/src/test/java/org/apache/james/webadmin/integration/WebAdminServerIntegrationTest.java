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

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.webadmin.Constants.JSON_CONTENT_TYPE;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.apache.james.CassandraJmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.task.TaskManager;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.routes.DomainsRoutes;
import org.apache.james.webadmin.routes.UserMailboxesRoutes;
import org.apache.james.webadmin.routes.UserRoutes;
import org.apache.james.webadmin.swagger.routes.SwaggerRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public class WebAdminServerIntegrationTest {

    public static final String DOMAIN = "domain";
    public static final String USERNAME = "username@" + DOMAIN;
    public static final String SPECIFIC_DOMAIN = DomainsRoutes.DOMAINS + SEPARATOR + DOMAIN;
    public static final String SPECIFIC_USER = UserRoutes.USERS + SEPARATOR + USERNAME;
    public static final String MAILBOX = "mailbox";
    public static final String SPECIFIC_MAILBOX = SPECIFIC_USER + SEPARATOR + UserMailboxesRoutes.MAILBOXES + SEPARATOR + MAILBOX;
    public static final String VERSION = "/cassandra/version";
    public static final String VERSION_LATEST = VERSION + "/latest";
    public static final String UPGRADE_VERSION = VERSION + "/upgrade";
    public static final String UPGRADE_TO_LATEST_VERSION = UPGRADE_VERSION + "/latest";

    @ClassRule
    public static DockerCassandraRule cassandra = new DockerCassandraRule();
    
    @Rule
    public CassandraJmapTestRule cassandraJmapTestRule = CassandraJmapTestRule.defaultTestRule();

    private GuiceJamesServer guiceJamesServer;
    private DataProbe dataProbe;
    private WebAdminGuiceProbe webAdminGuiceProbe;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = cassandraJmapTestRule.jmapServer(cassandra.getModule())
                .overrideWith(new WebAdminConfigurationModule());
        guiceJamesServer.start();
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
        		.setContentType(ContentType.JSON)
        		.setAccept(ContentType.JSON)
        		.setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
        		.build();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void postShouldAddTheGivenDomain() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .put(SPECIFIC_DOMAIN)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listDomains()).contains(DOMAIN);
    }

    @Test
    public void deleteShouldRemoveTheGivenDomain() throws Exception {
        dataProbe.addDomain(DOMAIN);

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .delete(SPECIFIC_DOMAIN)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listDomains()).doesNotContain(DOMAIN);
    }

    @Test
    public void postShouldAddTheUser() throws Exception {
        dataProbe.addDomain(DOMAIN);

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .body("{\"password\":\"password\"}")
        .when()
            .put(SPECIFIC_USER)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listUsers()).contains(USERNAME);
    }

    @Test
    public void deleteShouldRemoveTheUser() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, "anyPassword");

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .body("{\"username\":\"" + USERNAME + "\",\"password\":\"password\"}")
        .when()
            .delete(SPECIFIC_USER)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listUsers()).doesNotContain(USERNAME);
    }

    @Test
    public void getUsersShouldDisplayUsers() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, "anyPassword");

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(UserRoutes.USERS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("[{\"username\":\"username@domain\"}]"));
    }

    @Test
    public void putMailboxShouldAddAMailbox() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, "anyPassword");

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .put(SPECIFIC_MAILBOX)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(guiceJamesServer.getProbe(MailboxProbeImpl.class).listUserMailboxes(USERNAME)).containsExactly(MAILBOX);
    }

    @Test
    public void deleteMailboxShouldRemoveAMailbox() throws Exception {
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, "anyPassword");
        guiceJamesServer.getProbe(MailboxProbeImpl.class).createMailbox("#private", USERNAME, MAILBOX);

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .delete(SPECIFIC_MAILBOX)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(guiceJamesServer.getProbe(MailboxProbeImpl.class).listUserMailboxes(USERNAME)).isEmpty();
    }

    @Test
    public void getCurrentVersionShouldReturnNullForCurrentVersionAsBeginning() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(VERSION)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("{\"version\":null}"));
    }

    @Test
    public void getLatestVersionShouldReturnTheConfiguredLatestVersion() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(VERSION_LATEST)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION.getValue() + "}"));
    }

    @Test
    public void postShouldDoMigrationAndUpdateCurrentVersion() throws Exception {
        String taskId = with()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .body(String.valueOf(CassandraSchemaVersionManager.MAX_VERSION.getValue()))
        .post(UPGRADE_VERSION)
            .jsonPath()
            .get("taskId");

        with()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .get("/task/" + taskId + "/await");

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(VERSION)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION.getValue() + "}"));
    }

    @Test
    public void postShouldDoMigrationAndUpdateToTheLatestVersion() throws Exception {
        String taskId = with()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .post(UPGRADE_TO_LATEST_VERSION)
            .jsonPath()
            .get("taskId");

        with()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .get("/task/" + taskId + "/await");

        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(VERSION)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION.getValue() + "}"));
    }

    @Test
    public void concurrentMigrationIsNotAllowed() throws Exception {
        ConcurrentLinkedQueue<String> taskIds = new ConcurrentLinkedQueue<>();
        int threadCount = 2;
        int operationCount = 1;
        new ConcurrentTestRunner(threadCount, operationCount, (a, b) -> {
            String migrationId = with()
                .port(webAdminGuiceProbe.getWebAdminPort())
                .post(UPGRADE_TO_LATEST_VERSION)
                .jsonPath()
                .get("taskId");
            taskIds.add(migrationId);
        }).run()
            .awaitTermination(1, TimeUnit.MINUTES);

        String id1 = taskIds.poll();
        String id2 = taskIds.poll();
        String status1 = with()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .get("/tasks/" + id1 + "/await")
            .jsonPath()
            .get("status");
        String status2 = with()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .get("/tasks/" + id2 + "/await")
            .jsonPath()
            .get("status");

        assertThat(ImmutableList.of(status1, status2))
            .containsOnly(
                TaskManager.Status.COMPLETED.getValue(),
                TaskManager.Status.FAILED.getValue());
    }

    @Test
    public void addressGroupsEndpointShouldHandleRequests() throws Exception {
        dataProbe.addAddressMapping("group", "domain.com", "user1@domain.com");
        dataProbe.addAddressMapping("group", "domain.com", "user2@domain.com");

        List<String> members = given()
            .port(webAdminGuiceProbe.getWebAdminPort())
            .when()
            .get("/address/groups/group@domain.com")
            .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .extract()
            .jsonPath()
            .getList(".");
        assertThat(members).containsOnly("user1@domain.com", "user2@domain.com");
    }

    @Test
    public void getSwaggerShouldReturnJsonDataForSwagger() throws Exception {
        given()
            .port(webAdminGuiceProbe.getWebAdminPort())
        .when()
            .get(SwaggerRoutes.SWAGGER_ENDPOINT)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body(containsString("\"swagger\":\"2.0\""))
            .body(containsString("\"info\":{\"description\":\"All the web administration API for JAMES\",\"version\":\"V1.0\",\"title\":\"JAMES Web Admin API\"}"))
            .body(containsString("\"tags\":[\"User's Mailbox\"]"))
            .body(containsString("\"tags\":[\"GlobalQuota\"]"))
            .body(containsString("\"tags\":[\"Domains\"]"))
            .body(containsString("\"tags\":[\"Users\"]"))
            .body(containsString("\"tags\":[\"Address Groups\"]"));
    }

}
