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

package org.apache.james.webadmin.integration.rabbitmq;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.apache.james.webadmin.Constants.JSON_CONTENT_TYPE;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.webadmin.integration.WebAdminServerIntegrationTest;
import org.apache.james.webadmin.integration.WebadminIntegrationTestModule;
import org.apache.james.webadmin.routes.AliasRoutes;
import org.apache.james.webadmin.routes.CassandraMappingsRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.swagger.routes.SwaggerRoutes;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.http.ContentType;

@Tag(BasicFeature.TAG)
class RabbitMQWebAdminServerIntegrationTest extends WebAdminServerIntegrationTest {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new RabbitMQExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
            .overrideWith(new WebadminIntegrationTestModule()))
        .build();

    private static final String VERSION = "/cassandra/version";
    private static final String VERSION_LATEST = VERSION + "/latest";
    private static final String UPGRADE_VERSION = VERSION + "/upgrade";
    private static final String UPGRADE_TO_LATEST_VERSION = UPGRADE_VERSION + "/latest";

    @Test
    void getCurrentVersionShouldReturnNullForCurrentVersionAsBeginning() {
        when()
            .get(VERSION)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("{\"version\":null}"));
    }

    @Test
    void getLatestVersionShouldReturnTheConfiguredLatestVersion() {
        when()
            .get(VERSION_LATEST)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION.getValue() + "}"));
    }

    @Test
    void postShouldDoMigrationAndUpdateCurrentVersion() {
        String taskId = with()
            .body(String.valueOf(CassandraSchemaVersionManager.MAX_VERSION.getValue()))
        .post(UPGRADE_VERSION)
            .jsonPath()
            .get("taskId");

        with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));

        Awaitility.await()
            .atMost(Duration.TEN_SECONDS)
            .await()
            .untilAsserted(() ->
                when()
                    .get(VERSION)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(JSON_CONTENT_TYPE)
                    .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION.getValue() + "}")));
    }

    @Test
    void postShouldDoMigrationAndUpdateToTheLatestVersion() {
        String taskId = with().post(UPGRADE_TO_LATEST_VERSION)
            .jsonPath()
            .get("taskId");

        with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));

        when()
            .get(VERSION)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION.getValue() + "}"));
    }

    @Test
    void cassandraMappingsEndpointShouldKeepDataConsistencyWhenDataValid() {
        with()
            .put(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME + "/sources/" + ALIAS_1);
        with()
            .put(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME + "/sources/" + ALIAS_2);

        String taskId = with()
            .queryParam("action", "SolveInconsistencies")
        .post(CassandraMappingsRoutes.ROOT_PATH)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));

        when()
            .get(AliasRoutes.ROOT_PATH + SEPARATOR + USERNAME)
        .then()
            .contentType(ContentType.JSON)
        .statusCode(HttpStatus.OK_200)
            .body("source", hasItems(ALIAS_1, ALIAS_2));
    }


    @Test
    void getSwaggerShouldContainDistributedEndpoints() {
        when()
            .get(SwaggerRoutes.SWAGGER_ENDPOINT)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body(containsString("\"tags\":[\"Cassandra Mappings Operations\"]"))
            .body(containsString("{\"name\":\"MessageIdReIndexing\"}"));
    }
}