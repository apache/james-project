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

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.james.backends.cassandra.migration.CassandraMigrationService;
import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.backends.cassandra.migration.MigrationTask;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public class CassandraMigrationRoutesTest {
    private static final SchemaVersion LATEST_VERSION = new SchemaVersion(3);
    private static final SchemaVersion CURRENT_VERSION = new SchemaVersion(2);
    private static final SchemaVersion OLDER_VERSION = new SchemaVersion(1);
    private WebAdminServer webAdminServer;
    private CassandraSchemaVersionDAO schemaVersionDAO;
    private MemoryTaskManager taskManager;

    private void createServer() throws Exception {
        Migration successfulMigration = mock(Migration.class);
        when(successfulMigration.run()).thenReturn(Migration.Result.COMPLETED);

        Map<SchemaVersion, Migration> allMigrationClazz = ImmutableMap.<SchemaVersion, Migration>builder()
            .put(OLDER_VERSION, successfulMigration)
            .put(CURRENT_VERSION, successfulMigration)
            .put(LATEST_VERSION, successfulMigration)
            .build();
        schemaVersionDAO = mock(CassandraSchemaVersionDAO.class);

        taskManager = new MemoryTaskManager();
        JsonTransformer jsonTransformer = new JsonTransformer();
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new CassandraMigrationRoutes(new CassandraMigrationService(schemaVersionDAO, allMigrationClazz, LATEST_VERSION),
                taskManager, jsonTransformer),
            new TasksRoutes(taskManager, jsonTransformer));

        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setBasePath(CassandraMigrationRoutes.VERSION_BASE)
            .setPort(webAdminServer.getPort().get().getValue())
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .build();
    }

    @Before
    public void setUp() throws Exception {
        createServer();
    }

    @After
    public void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    public void getShouldReturnTheCurrentVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(CURRENT_VERSION)));

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
    public void getShouldReturnTheLatestVersionWhenSetUpTheLatestVersion() throws Exception {

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

    @Ignore
    @Test
    public void postShouldReturnConflictWhenMigrationOnRunning() throws Exception {
        when()
            .post("/upgrade")
        .then()
            .statusCode(HttpStatus.CONFLICT_409);
    }

    @Test
    public void postShouldReturnErrorCodeWhenInvalidVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

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
            .containsEntry("message", "Invalid request for version upgrade")
            .containsEntry("cause", "For input string: \"NonInt\"");

        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    public void postShouldDoMigrationToNewVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

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

        verify(schemaVersionDAO, times(2)).getCurrentSchemaVersion();
        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    public void postShouldCreateTaskWhenCurrentVersionIsNewerThan() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(CURRENT_VERSION)));

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
    public void postShouldNotUpdateVersionWhenCurrentVersionIsNewerThan() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(CURRENT_VERSION)));

        String taskId =  given()
            .body(String.valueOf(OLDER_VERSION.getValue()))
        .with()
            .post("/upgrade")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        verify(schemaVersionDAO, times(1)).getCurrentSchemaVersion();
        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    public void postShouldDoMigrationToLatestVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

        String taskId = with()
            .post("/upgrade/latest")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        verify(schemaVersionDAO, times(3)).getCurrentSchemaVersion();
        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
        verify(schemaVersionDAO, times(1)).updateVersion(eq(LATEST_VERSION));
        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    public void postShouldReturnTaskIdAndLocation() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

        when()
            .post("/upgrade/latest")
        .then()
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    public void createdTaskShouldHaveDetails() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

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
            .body("type", is(MigrationTask.CASSANDRA_MIGRATION))
            .body("additionalInformation.toVersion", is(LATEST_VERSION.getValue()))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    public void postShouldCreateTaskWhenItIsUpToDate() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(LATEST_VERSION)));

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
    public void postShouldNotUpdateVersionWhenItIsUpToDate() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(LATEST_VERSION)));

        String taskId = with()
            .post("/upgrade/latest")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        verify(schemaVersionDAO, times(1)).getCurrentSchemaVersion();
        verifyNoMoreInteractions(schemaVersionDAO);
    }
}
