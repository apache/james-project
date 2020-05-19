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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import reactor.core.publisher.Mono;
import spark.Service;

public class SolveMessageInconsistenciesRequestToTaskTest {
    private final class JMAPRoutes implements Routes {
        private final SolveMessageInconsistenciesService service;
        private final TaskManager taskManager;

        private JMAPRoutes(SolveMessageInconsistenciesService service, TaskManager taskManager) {
            this.service = service;
            this.taskManager = taskManager;
        }

        @Override
        public String getBasePath() {
            return BASE_PATH;
        }

        @Override
        public void define(Service service) {
            service.post(BASE_PATH,
                TaskFromRequestRegistry.builder()
                    .registrations(new SolveMessageInconsistenciesRequestToTask(this.service))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    static final String BASE_PATH = "/messages";

    private WebAdminServer webAdminServer;
    private SolveMessageInconsistenciesService service;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void setUp() {
        JsonTransformer jsonTransformer = new JsonTransformer();
        taskManager = new MemoryTaskManager(new Hostname("foo"));

        service = mock(SolveMessageInconsistenciesService.class);
        Mockito.when(service.fixMessageInconsistencies(any(), any())).thenReturn(Mono.just(Task.Result.COMPLETED));

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer),
            new JMAPRoutes(
                service,
                taskManager))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(BASE_PATH)
            .log(LogDetail.URI)
            .build();
    }

    @AfterEach
    void afterEach() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void actionRequestParameterShouldBeCompulsory() {
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
    void postShouldFailUponEmptyAction() {
        given()
            .queryParam("action", "")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter cannot be empty or blank. Supported values are [SolveInconsistencies]"));
    }

    @Test
    void postShouldFailUponInvalidAction() {
        given()
            .queryParam("action", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [SolveInconsistencies]"));
    }

    @Test
    void postShouldFailWhenMessagesPerSecondIsNotAnInt() {
        given()
            .queryParam("action", "SolveInconsistencies")
            .queryParam("messagesPerSecond", "abc")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Illegal value supplied for query parameter 'messagesPerSecond', expecting a strictly positive optional integer"));
    }

    @Test
    void postShouldFailWhenMessagesPerSecondIsNegative() {
        given()
            .queryParam("action", "SolveInconsistencies")
            .queryParam("messagesPerSecond", "-1")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'messagesPerSecond' must be strictly positive"));
    }

    @Test
    void postShouldFailWhenMessagesPerSecondIsZero() {
        given()
            .queryParam("action", "SolveInconsistencies")
            .queryParam("messagesPerSecond", "0")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'messagesPerSecond' must be strictly positive"));
    }

    @Test
    void postShouldCreateANewTask() {
        given()
            .queryParam("action", "SolveInconsistencies")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void postShouldCreateANewTaskWhenConcurrencyParametersSpecified() {
        given()
            .queryParam("messagesPerSecond", "1")
            .queryParam("action", "SolveInconsistencies")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void runningOptionsShouldBePartOfTaskDetails() {
        String taskId = with()
            .queryParam("action", "SolveInconsistencies")
            .queryParam("messagesPerSecond", "20")
        .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("taskId", is(taskId))
            .body("type", is("solve-message-inconsistencies"))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(20));
    }
}
