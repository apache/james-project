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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Date;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.ExpireMailboxAdditionalInformationDTO;
import org.apache.james.webadmin.service.ExpireMailboxService;
import org.apache.james.webadmin.service.ExpireMailboxService.RunningOptions;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;

class MessageRoutesExpireTest {
    private static final DomainList NO_DOMAIN_LIST = null;
    private static final Username USERNAME = Username.of("benwa");
    private static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);

    private WebAdminServer webAdminServer;
    private InMemoryMailboxManager mailboxManager;
    private MemoryTaskManager taskManager;
    private MemoryUsersRepository usersRepository;

    @BeforeEach
    void beforeEach() {
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        JsonTransformer jsonTransformer = new JsonTransformer();

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new TasksRoutes(taskManager, jsonTransformer,
                    DTOConverter.of(
                        ExpireMailboxAdditionalInformationDTO.module())),
                new MessagesRoutes(taskManager,
                    new InMemoryMessageId.Factory(),
                    null,
                    new ExpireMailboxService(usersRepository, mailboxManager),
                    jsonTransformer,
                    ImmutableSet.of()))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Nested
    class ExpireMailbox {
        @Nested
        class Validation {
            @Test
            void expireMailboxShouldFailWithNoOption() {
                when()
                    .delete("/messages")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("Must specify either 'olderThan' or 'byExpiresHeader' parameter"));
            }

            @Test
            void expireMailboxShouldFailWithBothOptions() {
                when()
                    .delete("/messages?byExpiresHeader&olderThan=30d")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("Must specify either 'olderThan' or 'byExpiresHeader' parameter"));
            }

            @Test
            void expireMailboxShouldFailWithBadOlderThan() {
                when()
                    .delete("/messages?olderThan=bad")
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("'usersPerSecond' must be numeric"));
            }

            @Test
            void expireMailboxShouldFailWithNegativeOlderThan() {
                when()
                    .delete("/messages?olderThan=-30d")
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("Duration amount should be positive"));
            }

            @Test
            void expireMailboxShouldFailWithBadUsersPerSeconds() {
                when()
                    .delete("/messages?byExpiresHeader&usersPerSecond=bad")
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("'usersPerSecond' must be numeric"));
            }

            @Test
            void expireMailboxShouldFailWithNegativeUsersPerSeconds() {
                when()
                    .delete("/messages?byExpiresHeader&usersPerSecond=-10")
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("'usersPerSecond' must be strictly positive"));
            }

        }

        @Nested
        class TaskDetails {
            @Test
            void expireMailboxShouldNotFailWhenNoMailsFound() {
                String taskId = when()
                    .delete("/messages?byExpiresHeader")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("type", Matchers.is("ExpireMailboxTask"))
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("additionalInformation.type", is("ExpireMailboxTask"))
                    .body("additionalInformation.runningOptions.usersPerSecond", is(RunningOptions.DEFAULT.getUsersPerSecond()))
                    .body("additionalInformation.runningOptions.byExpiresHeader", is(true))
                    .body("additionalInformation.runningOptions.olderThan", is(nullValue()))
                    .body("additionalInformation.mailboxesExpired", is(0))
                    .body("additionalInformation.mailboxesFailed", is(0))
                    .body("additionalInformation.mailboxesProcessed", is(0))
                    .body("additionalInformation.messagesDeleted", is(0));
            }

            @Test
            void expireMailboxShouldReturnTaskDetailsWhenMail() throws Exception {
                usersRepository.addUser(USERNAME, "secret");
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                mailboxManager.getMailbox(INBOX, systemSession).appendMessage(
                    MessageManager.AppendCommand.builder()
                        .withInternalDate(new Date(System.currentTimeMillis() - 5000))
                        .build("header: value\r\n\r\nbody"),
                    systemSession).getId();

                String taskId = when()
                    .delete("/messages?olderThan=1s")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("type", Matchers.is("ExpireMailboxTask"))
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("additionalInformation.type", is("ExpireMailboxTask"))
                    .body("additionalInformation.runningOptions.usersPerSecond", is(RunningOptions.DEFAULT.getUsersPerSecond()))
                    .body("additionalInformation.runningOptions.byExpiresHeader", is(false))
                    .body("additionalInformation.runningOptions.olderThan", is("1s"))
                    .body("additionalInformation.mailboxesExpired", is(1))
                    .body("additionalInformation.mailboxesFailed", is(0))
                    .body("additionalInformation.mailboxesProcessed", is(1))
                    .body("additionalInformation.messagesDeleted", is(1));
            }
        }

        @Nested
        class SideEffects {
            @Test
            void expireMailboxShouldExpireOldMail() throws Exception {
                usersRepository.addUser(USERNAME, "secret");
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                mailboxManager.getMailbox(INBOX, systemSession).appendMessage(
                    MessageManager.AppendCommand.builder()
                        .withInternalDate(new Date(System.currentTimeMillis() - 5000))
                        .build("header: value\r\n\r\nbody"),
                    systemSession).getId();

                String taskId = when()
                    .delete("/messages?olderThan=1s")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                    .when()
                    .get(taskId + "/await")
                    .then()
                    .body("status", is("completed"))
                    .body("additionalInformation.messagesDeleted", is(1));

                MessageManager mailbox = mailboxManager.getMailbox(INBOX, systemSession);
                assertThat(mailbox.getMessageCount(systemSession)).isEqualTo(0);
            }

            @Test
            void expireMailboxShouldNotExpireNewMail() throws Exception {
                usersRepository.addUser(USERNAME, "secret");
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                mailboxManager.getMailbox(INBOX, systemSession).appendMessage(
                    MessageManager.AppendCommand.builder()
                        .withInternalDate(new Date(System.currentTimeMillis()))
                        .build("header: value\r\n\r\nbody"),
                    systemSession).getId();

                String taskId = when()
                    .delete("/messages?olderThan=7d")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                    .when()
                    .get(taskId + "/await")
                    .then()
                    .body("status", is("completed"))
                    .body("additionalInformation.messagesDeleted", is(0));

                MessageManager mailbox = mailboxManager.getMailbox(INBOX, systemSession);
                assertThat(mailbox.getMessageCount(systemSession)).isEqualTo(1);
            }
        }
    }
}