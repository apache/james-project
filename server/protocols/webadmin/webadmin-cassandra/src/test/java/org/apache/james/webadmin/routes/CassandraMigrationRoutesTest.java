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
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.apache.james.backends.cassandra.migration.CassandraMigrationService;
import org.apache.james.backends.cassandra.migration.CassandraSchemaTransitions;
import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.backends.cassandra.migration.MigrationTask;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.SchemaTransition;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.json.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.dto.WebAdminMigrationTaskAdditionalInformationDTO;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.core.publisher.Mono;

class CassandraMigrationRoutesTest {
    private static final SchemaVersion LATEST_VERSION = new SchemaVersion(3);
    private static final SchemaVersion CURRENT_VERSION = new SchemaVersion(2);
    private static final SchemaVersion OLDER_VERSION = new SchemaVersion(1);
    private static final SchemaTransition FROM_OLDER_TO_CURRENT = SchemaTransition.to(CURRENT_VERSION);
    private static final SchemaTransition FROM_CURRENT_TO_LATEST = SchemaTransition.to(LATEST_VERSION);
    private WebAdminServer webAdminServer;
    private CassandraSchemaVersionDAO schemaVersionDAO;
    private MemoryTaskManager taskManager;

    private void createServer() {
        Migration successfulMigration = () -> { };

        CassandraSchemaTransitions transitions = new CassandraSchemaTransitions(ImmutableMap.of(
            FROM_OLDER_TO_CURRENT, successfulMigration,
            FROM_CURRENT_TO_LATEST, successfulMigration));

        schemaVersionDAO = mock(CassandraSchemaVersionDAO.class);
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.empty()));
        when(schemaVersionDAO.updateVersion(any())).thenReturn(Mono.empty());

        taskManager = new MemoryTaskManager(new Hostname("foo"));
        JsonTransformer jsonTransformer = new JsonTransformer();
        webAdminServer = WebAdminUtils.createWebAdminServer(
                new CassandraMigrationRoutes(new CassandraMigrationService(schemaVersionDAO, transitions, version -> new MigrationTask(schemaVersionDAO, transitions, version), LATEST_VERSION),
                    taskManager, jsonTransformer),
                new TasksRoutes(taskManager, jsonTransformer,
                    DTOConverter.of(WebAdminMigrationTaskAdditionalInformationDTO.module())))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(CassandraMigrationRoutes.VERSION_BASE)
            .build();
    }

    @BeforeEach
    void setUp() {
        createServer();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void getShouldReturnTheCurrentVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(CURRENT_VERSION)));

        Integer version =
            when()
                .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath()
                .getInt("version");

        assertThat(version).isEqualTo(CURRENT_VERSION.getValue());
    }

    @Test
    void getShouldReturnTheLatestVersionWhenSetUpTheLatestVersion() {
        Integer version =
            when()
                .get("/latest")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath()
                .getInt("version");

        assertThat(version).isEqualTo(LATEST_VERSION.getValue());
    }

    @Disabled
    @Test
    void postShouldReturnConflictWhenMigrationOnRunning() {
        when()
            .post("/upgrade")
        .then()
            .statusCode(HttpStatus.CONFLICT_409);
    }

    @Test
    void postShouldReturnErrorCodeWhenInvalidVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        Map<String, Object> errors = given()
            .body("NonInt")
        .with()
            .post("/upgrade")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(ContentType.JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
            .containsEntry("type", "InvalidArgument")
            .containsEntry("message", "Invalid arguments supplied in the user request")
            .containsEntry("details", "Expecting version to be specified as an integer");

        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    void postShouldDoMigrationToNewVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        String taskId = with()
            .body(String.valueOf(CURRENT_VERSION.getValue()))
        .post("/upgrade")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));

        verify(schemaVersionDAO, atLeastOnce()).getCurrentSchemaVersion();
        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    void postShouldCreateTaskWhenCurrentVersionIsNewerThan() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(CURRENT_VERSION)));

        String taskId =  given()
            .body(String.valueOf(OLDER_VERSION.getValue()))
        .with()
            .post("/upgrade")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));
    }

    @Test
    void postShouldNotUpdateVersionWhenCurrentVersionIsNewerThan() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(CURRENT_VERSION)));

        String taskId =  given()
            .body(String.valueOf(OLDER_VERSION.getValue()))
        .with()
            .post("/upgrade")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        verify(schemaVersionDAO, atLeastOnce()).getCurrentSchemaVersion();
        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    void postShouldPositionLocationHeader() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(CURRENT_VERSION)));

        given()
            .body(String.valueOf(OLDER_VERSION.getValue()))
        .when()
            .post("/upgrade")
        .then()
            .header("Location", notNullValue());
    }

    @Test
    void postShouldDoMigrationToLatestVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        String taskId = with()
            .post("/upgrade/latest")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        verify(schemaVersionDAO, atLeastOnce()).getCurrentSchemaVersion();
        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
        verify(schemaVersionDAO, times(1)).updateVersion(eq(LATEST_VERSION));
        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    void postShouldReturnTaskIdAndLocation() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        when()
            .post("/upgrade/latest")
        .then()
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    void createdTaskShouldHaveDetails() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        String taskId = with()
            .post("/upgrade/latest")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(MigrationTask.CASSANDRA_MIGRATION.asString()))
            .body("additionalInformation.toVersion", is(LATEST_VERSION.getValue()))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void postShouldCreateTaskWhenItIsUpToDate() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(LATEST_VERSION)));

        String taskId = with()
            .post("/upgrade/latest")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"));
    }

    @Test
    void postShouldNotUpdateVersionWhenItIsUpToDate() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(LATEST_VERSION)));

        String taskId = with()
            .post("/upgrade/latest")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        verify(schemaVersionDAO, atLeastOnce()).getCurrentSchemaVersion();
        verifyNoMoreInteractions(schemaVersionDAO);
    }
}
