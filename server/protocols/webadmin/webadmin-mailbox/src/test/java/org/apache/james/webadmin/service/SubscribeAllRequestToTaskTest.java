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
import static org.apache.james.webadmin.service.ExportServiceTestSystem.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.restassured.RestAssured;
import spark.Service;

class SubscribeAllRequestToTaskTest {
    private static class SubscribeAllRoutes implements Routes {
        private final MailboxManager mailboxManager;
        private final SubscriptionManager subscriptionManager;
        private final TaskManager taskManager;
        private final UsersRepository usersRepository;

        public SubscribeAllRoutes(MailboxManager mailboxManager, SubscriptionManager subscriptionManager, TaskManager taskManager, UsersRepository usersRepository) {
            this.mailboxManager = mailboxManager;
            this.subscriptionManager = subscriptionManager;
            this.taskManager = taskManager;
            this.usersRepository = usersRepository;
        }

        @Override
        public String getBasePath() {
            return BASE_PATH;
        }

        @Override
        public void define(Service service) {
            service.post(BASE_PATH,
                TaskFromRequestRegistry.builder()
                    .registrations(new SubscribeAllRequestToTask(mailboxManager, subscriptionManager, usersRepository))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    private static final String BASE_PATH = "users/:username/mailboxes";

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private MailboxManager mailboxManager;
    private SubscriptionManager subscriptionManager;
    private MailboxSession session;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources inMemoryIntegrationResources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = inMemoryIntegrationResources.getMailboxManager();
        subscriptionManager = new StoreSubscriptionManager(inMemoryIntegrationResources.getMailboxManager().getMapperFactory());
        DomainList domainList = mock(DomainList.class);
        Mockito.when(domainList.containsDomain(any())).thenReturn(true);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "anyPassord");
        session = mailboxManager.createSystemSession(BOB);

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        JsonTransformer jsonTransformer = new JsonTransformer();
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of(SubscribeAllTaskAdditionalInformationDTO.SERIALIZATION_MODULE)),
            new SubscribeAllRoutes(mailboxManager, subscriptionManager, taskManager, usersRepository))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("users/" + BOB.asString() + "/mailboxes")
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
            .body("details", is("'action' query parameter is compulsory. Supported values are [subscribeAll]"));
    }

    @Test
    void subscribeAllMailboxesShouldFailUponEmptyAction() {
        given()
            .queryParam("action", "")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter cannot be empty or blank. Supported values are [subscribeAll]"));
    }

    @Test
    void subscribeAllMailboxesShouldFailUponInvalidAction() {
        given()
            .queryParam("action", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [subscribeAll]"));
    }

    @Test
    void subscribeAllMailboxesShouldFailUponBadUsername() {
        given()
            .basePath("users/bad@bad@bad/mailboxes")
            .queryParam("action", "subscribeAll")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("The username should not contain multiple domain delimiter. Value: bad@bad@bad"));
    }

    @Test
    void exportMailboxesShouldFailUponUnknownUser() {
        given()
            .basePath("users/notFound/mailboxes")
            .queryParam("action", "subscribeAll")
            .post()
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("User 'notfound' does not exist"));
    }

    @Test
    void postShouldCreateANewTask() {
        given()
            .queryParam("action", "subscribeAll")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void subscribeAllMailboxesShouldCompleteWhenUserHasNoMailbox() {
        String taskId = with()
            .queryParam("action", "subscribeAll")
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
            .body("type", is("SubscribeAllTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.subscribedCount", is(0))
            .body("additionalInformation.unsubscribedCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void subscribeAllMailboxesShouldRegisterMissingMailbox() throws Exception {
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), session);

        String taskId = with()
            .queryParam("action", "subscribeAll")
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
            .body("type", is("SubscribeAllTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.subscribedCount", is(1))
            .body("additionalInformation.unsubscribedCount", is(0))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));

        assertThat(subscriptionManager.subscriptions(session)).containsOnly("INBOX");
    }

    @Test
    void subscribeAllMailboxesShouldUnregisterAdditionalMailbox() throws Exception {
        subscriptionManager.subscribe(session, "any");

        String taskId = with()
            .queryParam("action", "subscribeAll")
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
            .body("type", is("SubscribeAllTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.subscribedCount", is(0))
            .body("additionalInformation.unsubscribedCount", is(1))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));

        assertThat(subscriptionManager.subscriptions(session)).isEmpty();
    }
}