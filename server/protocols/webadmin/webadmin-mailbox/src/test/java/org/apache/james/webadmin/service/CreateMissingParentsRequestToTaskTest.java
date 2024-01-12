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

package org.apache.james.webadmin.service;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import org.apache.james.core.Username;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;
import spark.Service;

@Disabled("Fails on the CI with Java-21. Not locally. Disabled temporarily to move on.")
class CreateMissingParentsRequestToTaskTest {

    private final class CreateMissingParentsRoutes implements Routes {
        private final MailboxManager mailboxManager;
        private final TaskManager taskManager;

        private CreateMissingParentsRoutes(MailboxManager mailboxManager, TaskManager taskManager) {
            this.mailboxManager = mailboxManager;
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
                    .registrations(new CreateMissingParentsRequestToTask(this.mailboxManager))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    private static final String BASE_PATH = "mailboxes";
    private static final Username USERNAME = Username.of("bob");

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private MailboxManager mailboxManager;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        mailboxManager = Mockito.spy(resources.getMailboxManager());

        JsonTransformer jsonTransformer = new JsonTransformer();
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer,
                DTOConverter.of(CreateMissingParentsTaskAdditionalInformationDTO.SERIALIZATION_MODULE)),
            new CreateMissingParentsRoutes(mailboxManager, taskManager))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("mailboxes")
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
            .body("details", is("'action' query parameter is compulsory. Supported values are [createMissingParents]"));
    }

    @Test
    void createMissingParentsShouldFailUponEmptyAction() {
        given()
            .queryParam("action", "")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter cannot be empty or blank. Supported values are [createMissingParents]"));
    }

    @Test
    void createMissingParentsShouldFailUponInvalidAction() {
        given()
            .queryParam("action", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [createMissingParents]"));
    }

    @Test
    void postShouldCreateANewTask() {
        given()
            .queryParam("action", "createMissingParents")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void createMissingParentsShouldCompleteWhenNoMailbox() {
        String taskId = with()
            .queryParam("action", "createMissingParents")
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
            .body("type", is("CreateMissingParentsTask"))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void createMissingParentsShouldCompleteWhenMissingOneParent() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(USERNAME);
        MailboxPath path = MailboxPath.forUser(USERNAME, "INBOX.main");
        mailboxManager.createMailbox(path, session);
        mailboxManager.deleteMailbox(MailboxPath.forUser(USERNAME, "INBOX"), session);

        String taskId = with()
            .queryParam("action", "createMissingParents")
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
            .body("type", is("CreateMissingParentsTask"))
            .body("additionalInformation.created", hasSize(1))
            .body("additionalInformation.totalCreated", is(1))
            .body("additionalInformation.failures", empty())
            .body("additionalInformation.totalFailure", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void createMissingParentsShouldCompleteWhenMissingMultipleParents() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(USERNAME);
        MailboxPath path = MailboxPath.forUser(USERNAME, "INBOX.main.sub");
        mailboxManager.createMailbox(path, session);
        mailboxManager.deleteMailbox(MailboxPath.forUser(USERNAME, "INBOX"), session);
        mailboxManager.deleteMailbox(MailboxPath.forUser(USERNAME, "INBOX.main"), session);

        String taskId = with()
            .queryParam("action", "createMissingParents")
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
            .body("type", is("CreateMissingParentsTask"))
            .body("additionalInformation.created", hasSize(2))
            .body("additionalInformation.totalCreated", is(2))
            .body("additionalInformation.failures", empty())
            .body("additionalInformation.totalFailure", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void createMissingParentsShouldRecordFailure() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(USERNAME);
        MailboxPath path = MailboxPath.forUser(USERNAME, "INBOX.main");
        mailboxManager.createMailbox(path, session);
        mailboxManager.deleteMailbox(MailboxPath.forUser(USERNAME, "INBOX"), session);

        doReturn(Mono.error(new RuntimeException()))
            .when(mailboxManager)
            .createMailboxReactive(any(), any());

        String taskId = with()
            .queryParam("action", "createMissingParents")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("failed"))
            .body("taskId", is(taskId))
            .body("type", is("CreateMissingParentsTask"))
            .body("additionalInformation.created", hasSize(0))
            .body("additionalInformation.totalCreated", is(0))
            .body("additionalInformation.failures", hasSize(1))
            .body("additionalInformation.totalFailure", is(1))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()));
    }
}