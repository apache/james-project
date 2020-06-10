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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.Domain;
import org.apache.james.json.DTOConverter;
import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.CassandraRRTModule;
import org.apache.james.rrt.cassandra.CassandraRecipientRewriteTableDAO;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigration;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigrationTaskAdditionalInformationDTO;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.CassandraMappingsService;
import org.apache.james.webadmin.service.CassandraMappingsSolveInconsistenciesTask;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;

class CassandraMappingsRoutesTest {
    private static final String MAPPINGS_ACTION = "SolveInconsistencies";
    private static final MappingSource SOURCE_1 = MappingSource.fromUser("bob", Domain.LOCALHOST);
    private static final MappingSource SOURCE_2 = MappingSource.fromUser("alice", Domain.LOCALHOST);
    private static final Mapping MAPPING = Mapping.alias("bob-alias@domain");

    private WebAdminServer webAdminServer;

    private MappingsSourcesMigration mappingsSourcesMigration;
    private CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO;
    private CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO;
    private MemoryTaskManager taskManager;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraRRTModule.MODULE);

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        cassandraRecipientRewriteTableDAO = new CassandraRecipientRewriteTableDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        cassandraMappingsSourcesDAO = new CassandraMappingsSourcesDAO(cassandra.getConf());
        mappingsSourcesMigration = new MappingsSourcesMigration(cassandraRecipientRewriteTableDAO, cassandraMappingsSourcesDAO);

        CassandraMappingsService cassandraMappingsService = new CassandraMappingsService(mappingsSourcesMigration, cassandraMappingsSourcesDAO);

        JsonTransformer jsonTransformer = new JsonTransformer();
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        webAdminServer = WebAdminUtils.createWebAdminServer(
                new CassandraMappingsRoutes(cassandraMappingsService, taskManager, jsonTransformer),
                new TasksRoutes(taskManager, jsonTransformer,
                    DTOConverter.of(MappingsSourcesMigrationTaskAdditionalInformationDTO.module(CassandraMappingsSolveInconsistenciesTask.TYPE))))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(CassandraMappingsRoutes.ROOT_PATH)
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void postMappingsActionWithSolvedInconsistenciesQueryParamShouldCreateATask() {
        given()
            .queryParam("action", MAPPINGS_ACTION)
        .when()
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", is(notNullValue()))
            .body("taskId", is(notNullValue()));
    }

    @Test
    void postMappingsActionWithSolvedInconsistenciesQueryParamShouldHaveSuccessfulCompletedTask() {
        String taskId = with()
            .queryParam("action", MAPPINGS_ACTION)
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("additionalInformation.successfulMappingsCount", is(0))
            .body("additionalInformation.errorMappingsCount", is(0))
            .body("type", is(CassandraMappingsSolveInconsistenciesTask.TYPE.asString()))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void postMappingsActionShouldRejectInvalidActions() {
        given()
            .queryParam("action", "invalid-action")
        .when()
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid-action. Supported values are [SolveInconsistencies]"));
    }

    @Test
    void postMappingsActionShouldRequireAction() {
        when()
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter is compulsory. Supported values are [SolveInconsistencies]"));
    }

    @Test
    void postMappingsActionShouldResolveRRTInconsistencies() {
        cassandraRecipientRewriteTableDAO.addMapping(SOURCE_1, MAPPING).block();
        cassandraRecipientRewriteTableDAO.addMapping(SOURCE_2, MAPPING).block();

        cassandraMappingsSourcesDAO.addMapping(MAPPING, SOURCE_1).block();

        String taskId = with()
            .queryParam("action", MAPPINGS_ACTION)
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("additionalInformation.successfulMappingsCount", is(2))
            .body("additionalInformation.errorMappingsCount", is(0));

        assertThat(cassandraMappingsSourcesDAO.retrieveSources(MAPPING).collectList().block())
            .containsOnly(SOURCE_1, SOURCE_2);
    }

    @Test
    void postMappingsActionShouldResolveMappingsSourcesInconsistencies() {
        cassandraRecipientRewriteTableDAO.addMapping(SOURCE_1, MAPPING).block();

        cassandraMappingsSourcesDAO.addMapping(MAPPING, SOURCE_1).block();
        cassandraMappingsSourcesDAO.addMapping(MAPPING, SOURCE_2).block();

        String taskId = with()
            .queryParam("action", MAPPINGS_ACTION)
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("additionalInformation.successfulMappingsCount", is(1))
            .body("additionalInformation.errorMappingsCount", is(0));

        assertThat(cassandraMappingsSourcesDAO.retrieveSources(MAPPING).collectList().block())
            .containsOnly(SOURCE_1);
    }
}
