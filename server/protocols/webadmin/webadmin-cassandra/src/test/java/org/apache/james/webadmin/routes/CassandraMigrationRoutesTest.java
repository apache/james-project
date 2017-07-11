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
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.mailbox.cassandra.mail.migration.Migration;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.service.CassandraMigrationService;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class CassandraMigrationRoutesTest {

    public static final boolean MIGRATED = true;
    private static final Integer LATEST_VERSION = 3;
    private static final Integer CURRENT_VERSION = 2;
    private static final Integer OLDER_VERSION = 1;
    private WebAdminServer webAdminServer;
    private CassandraSchemaVersionDAO schemaVersionDAO;
    private Migration successfulMigration;

    private void createServer() throws Exception {
        successfulMigration = mock(Migration.class);
        when(successfulMigration.run()).thenReturn(MIGRATED);
        Map<Integer, Migration> allMigrationClazz = ImmutableMap.<Integer, Migration>builder()
            .put(OLDER_VERSION, successfulMigration)
            .put(CURRENT_VERSION, successfulMigration)
            .put(LATEST_VERSION, successfulMigration)
            .build();
        schemaVersionDAO = mock(CassandraSchemaVersionDAO.class);

        webAdminServer = new WebAdminServer(
            new DefaultMetricFactory(),
            new CassandraMigrationRoutes(new CassandraMigrationService(schemaVersionDAO, allMigrationClazz, LATEST_VERSION), new JsonTransformer()));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setBasePath(CassandraMigrationRoutes.VERSION_BASE)
            .setPort(webAdminServer.getPort().toInt())
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8)))
            .build();
    }

    @Before
    public void setUp() throws Exception {
        createServer();
    }

    @After
    public void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    public void getShouldReturnTheCurrentVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(CURRENT_VERSION)));

        when()
            .get()
        .then()
            .statusCode(200)
            .body(is("{\"version\":2}"));
    }

    @Test
    public void getShouldReturnTheLatestVersionWhenSetUpTheLatestVersion() throws Exception {
        when()
            .get("/latest")
        .then()
            .statusCode(200)
            .body(is("{\"version\":" + LATEST_VERSION + "}"));
    }

    @Ignore
    @Test
    public void postShouldReturnConflictWhenMigrationOnRunning() throws Exception {
        when()
            .post("/upgrade")
        .then()
            .statusCode(409);
    }

    @Test
    public void postShouldReturnErrorCodeWhenInvalidVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

        given()
            .body(String.valueOf("NonInt"))
        .with()
            .post("/upgrade")
        .then()
            .statusCode(400);

        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    public void postShouldDoMigrationToNewVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

        given()
            .body(String.valueOf(CURRENT_VERSION))
        .with()
            .post("/upgrade")
        .then()
            .statusCode(204);

        verify(schemaVersionDAO, times(1)).getCurrentSchemaVersion();
        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    public void postShouldNotDoMigrationWhenCurrentVersionIsNewerThan() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(CURRENT_VERSION)));

        given()
            .body(String.valueOf(OLDER_VERSION))
        .with()
            .post("/upgrade")
        .then()
            .statusCode(410);

        verify(schemaVersionDAO, times(1)).getCurrentSchemaVersion();
        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    public void postShouldDoMigrationToLatestVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

        when()
            .post("/upgrade/latest")
        .then()
            .statusCode(200);

        verify(schemaVersionDAO, times(1)).getCurrentSchemaVersion();
        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
        verify(schemaVersionDAO, times(1)).updateVersion(eq(LATEST_VERSION));
        verifyNoMoreInteractions(schemaVersionDAO);
    }

    @Test
    public void postShouldNotDoMigrationToLatestVersionWhenItIsUpToDate() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(LATEST_VERSION)));

        when()
            .post("/upgrade/latest")
        .then()
            .statusCode(410);

        verify(schemaVersionDAO, times(1)).getCurrentSchemaVersion();
        verifyNoMoreInteractions(schemaVersionDAO);
    }
}
