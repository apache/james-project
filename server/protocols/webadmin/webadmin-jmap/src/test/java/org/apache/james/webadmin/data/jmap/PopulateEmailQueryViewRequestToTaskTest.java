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

package org.apache.james.webadmin.data.jmap;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static javax.mail.Flags.Flag.DELETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.jmap.memory.projections.MemoryEmailQueryView;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.streams.Limit;
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

import io.restassured.RestAssured;
import spark.Service;

class PopulateEmailQueryViewRequestToTaskTest {
    private static final class JMAPRoutes implements Routes {
        private final EmailQueryViewPopulator populator;
        private final TaskManager taskManager;

        private JMAPRoutes(EmailQueryViewPopulator populator, TaskManager taskManager) {
            this.populator = populator;
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
                    .registrations(new PopulateEmailQueryViewRequestToTask(populator))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    static final String BASE_PATH = "/:username/mailboxes";

    static final DomainList NO_DOMAIN_LIST = null;
    static final Username BOB = Username.of("bob");

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private InMemoryMailboxManager mailboxManager;
    private MailboxId bobInboxboxId;
    private MailboxSession bobSession;
    private MemoryEmailQueryView view;

    @BeforeEach
    void setUp() throws Exception {
        JsonTransformer jsonTransformer = new JsonTransformer();
        taskManager = new MemoryTaskManager(new Hostname("foo"));

        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        usersRepository.addUser(BOB, "pass");
        bobSession = mailboxManager.createSystemSession(BOB);
        bobInboxboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession)
            .get();

        view = new MemoryEmailQueryView();
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer,
                DTOConverter.of(PopulateEmailQueryViewTaskAdditionalInformationDTO.module())),
            new JMAPRoutes(
                new EmailQueryViewPopulator(usersRepository, mailboxManager, view),
                taskManager))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("/bob/mailboxes")
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
            .body("details", is("'action' query parameter is compulsory. Supported values are [populateEmailQueryView]"));
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
            .body("details", is("'action' query parameter cannot be empty or blank. Supported values are [populateEmailQueryView]"));
    }

    @Test
    void postShouldFailWhenMessagesPerSecondIsNotAnInt() {
        given()
            .queryParam("action", "populateEmailQueryView")
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
            .queryParam("action", "populateEmailQueryView")
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
            .queryParam("action", "populateEmailQueryView")
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
    void postShouldFailUponInvalidAction() {
        given()
            .queryParam("action", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [populateEmailQueryView]"));
    }

    @Test
    void postShouldCreateANewTask() {
        given()
            .queryParam("action", "populateEmailQueryView")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void postShouldCreateANewTaskWhenConcurrencyParametersSpecified() {
        given()
            .queryParam("messagesPerSecond", "1")
            .queryParam("action", "populateEmailQueryView")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void runningOptionsShouldBePartOfTaskDetails() {
        String taskId = with()
            .queryParam("action", "populateEmailQueryView")
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
            .body("type", is("PopulateEmailQueryViewTask"))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(20));
    }

    @Test
    void populateShouldUpdateProjection() throws Exception {
        ComposedMessageId messageId = mailboxManager.getMailbox(bobInboxboxId, bobSession).appendMessage(
            MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
            bobSession).getId();

        String taskId = with()
            .queryParam("action", "populateEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(view.listMailboxContentSortedBySentAt(messageId.getMailboxId(), Limit.from(12)).collectList().block())
            .containsOnly(messageId.getMessageId());
    }

    @Test
    void populateShouldNotUpdateProjectionForDeletedMessages() throws Exception {
        ComposedMessageId messageId = mailboxManager.getMailbox(bobInboxboxId, bobSession).appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(new Flags(DELETED))
                .build("header: value\r\n\r\nbody"),
            bobSession).getId();

        String taskId = with()
            .queryParam("action", "populateEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(view.listMailboxContentSortedBySentAt(messageId.getMailboxId(), Limit.from(12)).collectList().block())
            .isEmpty();
    }

    @Test
    void populateShouldBeIdempotent() throws Exception {
        ComposedMessageId messageId = mailboxManager.getMailbox(bobInboxboxId, bobSession).appendMessage(
            MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
            bobSession).getId();

        String taskId1 = with()
            .queryParam("action", "populateEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");
        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId1 + "/await");

        String taskId2 = with()
            .queryParam("action", "populateEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");
        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId2 + "/await");

        assertThat(view.listMailboxContentSortedBySentAt(messageId.getMailboxId(), Limit.from(12)).collectList().block())
            .containsOnly(messageId.getMessageId());
    }
}