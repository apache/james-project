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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesService;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesTaskAdditionalInformationDTO;
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

public class SolveMailboxInconsistenciesRequestToTaskTest {
    private final class JMAPRoutes implements Routes {
        private final SolveMailboxInconsistenciesService service;
        private final TaskManager taskManager;

        private JMAPRoutes(SolveMailboxInconsistenciesService service, TaskManager taskManager) {
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
                    .registrations(new SolveMailboxInconsistenciesRequestToTask(this.service))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    static final String BASE_PATH = "/mailboxes";

    private WebAdminServer webAdminServer;
    private SolveMailboxInconsistenciesService service;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void setUp() {
        JsonTransformer jsonTransformer = new JsonTransformer();
        taskManager = new MemoryTaskManager(new Hostname("foo"));

        service = mock(SolveMailboxInconsistenciesService.class);
        Mockito.when(service.fixMailboxInconsistencies(any())).thenReturn(Mono.just(Task.Result.COMPLETED));

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of(SolveMessageInconsistenciesTaskAdditionalInformationDTO.module())),
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
    void postShouldFailUponEmptyTask() {
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
    void postShouldFailWhenIKnowWhatImDoingHeaderIsMissing() {
        given()
            .queryParam("action", "SolveInconsistencies")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Due to concurrency risks, a `I-KNOW-WHAT-I-M-DOING` header should be positioned to " +
                "`ALL-SERVICES-ARE-OFFLINE` in order to prevent accidental calls. " +
                "Check the documentation for details."));
    }

    @Test
    void postShouldFailWhenIKnowWhatImDoingHeaderIsInvalid() {
        given()
            .queryParam("action", "SolveInconsistencies")
            .header("I-KNOW-WHAT-I-M-DOING", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Due to concurrency risks, a `I-KNOW-WHAT-I-M-DOING` header should be positioned to " +
                "`ALL-SERVICES-ARE-OFFLINE` in order to prevent accidental calls. " +
                "Check the documentation for details."));
    }

    @Test
    void postShouldFailWhenIKnowWhatImDoingHeaderIsEmpty() {
        given()
            .queryParam("action", "SolveInconsistencies")
            .header("I-KNOW-WHAT-I-M-DOING", "")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Due to concurrency risks, a `I-KNOW-WHAT-I-M-DOING` header should be positioned to " +
                "`ALL-SERVICES-ARE-OFFLINE` in order to prevent accidental calls. " +
                "Check the documentation for details."));
    }

    @Test
    void postShouldCreateANewTask() {
        given()
            .queryParam("action", "SolveInconsistencies")
            .header("I-KNOW-WHAT-I-M-DOING", "ALL-SERVICES-ARE-OFFLINE")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }
}
