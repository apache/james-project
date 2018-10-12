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
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.mailbox.tools.indexer.FullReindexingTask;
import org.apache.mailbox.tools.indexer.ReIndexerImpl;
import org.apache.mailbox.tools.indexer.ReIndexerPerformer;
import org.apache.mailbox.tools.indexer.SingleMailboxReindexingTask;
import org.apache.mailbox.tools.indexer.SingleMessageReindexingTask;
import org.apache.mailbox.tools.indexer.UserReindexingTask;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.restassured.RestAssured;

class ReindexingRoutesTest {
    private static final String USERNAME = "benwa@apache.org";
    private static final MailboxPath INBOX = MailboxPath.forUser(USERNAME, "INBOX");

    private WebAdminServer webAdminServer;
    private ListeningMessageSearchIndex searchIndex;
    private InMemoryMailboxManager mailboxManager;

    @BeforeEach
    void beforeEach() throws Exception {
        mailboxManager = new InMemoryIntegrationResources().createMailboxManager(new SimpleGroupMembershipResolver());
        MemoryTaskManager taskManager = new MemoryTaskManager();
        InMemoryId.Factory mailboxIdFactory = new InMemoryId.Factory();
        searchIndex = mock(ListeningMessageSearchIndex.class);
        ReIndexer reIndexer = new ReIndexerImpl(
            new ReIndexerPerformer(
                mailboxManager,
                searchIndex,
                mailboxManager.getMapperFactory()));
        JsonTransformer jsonTransformer = new JsonTransformer();

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new TasksRoutes(taskManager, jsonTransformer),
            new ReindexingRoutes(
                taskManager,
                mailboxManager,
                mailboxIdFactory,
                reIndexer,
                jsonTransformer));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class FullReprocessing {
        @Nested
        class Validation {
            @Test
            void fullReprocessingShouldFailWithNoTask() {
                when()
                    .post("/mailboxIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("task query parameter is mandatory. The only supported value is `reIndex`"));
            }

            @Test
            void fullReprocessingShouldFailWithBadTask() {
                when()
                    .post("/mailboxIndex?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("task query parameter is mandatory. The only supported value is `reIndex`"));
            }
        }

        @Nested
        class TaskDetails {
            @Test
            void fullReprocessingShouldNotFailWhenNoMail() {
                String taskId = with()
                    .post("/mailboxIndex?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(FullReindexingTask.FULL_RE_INDEXING))
                    .body("additionalInformation.successfullyReprocessMailCount", is(0))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            void fullReprocessingShouldReturnTaskDetailsWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = with()
                    .post("/mailboxIndex?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(FullReindexingTask.FULL_RE_INDEXING))
                    .body("additionalInformation.successfullyReprocessMailCount", is(1))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }
        }

        @Nested
        class SideEffects {
            @Test
            void fullReprocessingShouldPerformReprocessingWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = with()
                    .post("/mailboxIndex?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                with()
                    .basePath(TasksRoutes.BASE)
                    .get(taskId + "/await")
                    .then()
                    .body("status", is("completed"));


                ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
                ArgumentCaptor<Mailbox> mailboxCaptor1 = ArgumentCaptor.forClass(Mailbox.class);
                ArgumentCaptor<Mailbox> mailboxCaptor2 = ArgumentCaptor.forClass(Mailbox.class);

                verify(searchIndex).deleteAll(any(MailboxSession.class), mailboxCaptor1.capture());
                verify(searchIndex).add(any(MailboxSession.class), mailboxCaptor2.capture(), messageCaptor.capture());
                verifyNoMoreInteractions(searchIndex);

                assertThat(mailboxCaptor1.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(mailboxId));
                assertThat(mailboxCaptor2.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(mailboxId));
                assertThat(messageCaptor.getValue()).matches(message -> message.getMailboxId().equals(mailboxId)
                    && message.getUid().equals(createdMessage.getUid()));
            }
        }
    }

    @Nested
    class UserReprocessing {
        @Nested
        class Validation {
            @Test
            void userReprocessingShouldFailWithNoTask() {
                when()
                    .post("/mailboxIndex/users/" + USERNAME)
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("task query parameter is mandatory. The only supported value is `reIndex`"));
            }

            @Test
            void userReprocessingShouldFailWithBadTask() {
                when()
                    .post("/mailboxIndex/users/" + USERNAME + "?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("task query parameter is mandatory. The only supported value is `reIndex`"));
            }

            @Test
            void userReprocessingShouldFailWithBadUser() {
                when()
                    .post("/mailboxIndex/users/bad@bad@bad?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Error while parsing 'user'"));
            }
        }

        @Nested
        class TaskDetails {
            @Test
            void userReprocessingShouldNotFailWhenNoMail() {
                String taskId = with()
                    .post("/mailboxIndex/users/" + USERNAME + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(UserReindexingTask.USER_RE_INDEXING))
                    .body("additionalInformation.user", is("benwa@apache.org"))
                    .body("additionalInformation.successfullyReprocessMailCount", is(0))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            void userReprocessingShouldReturnTaskDetailsWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = with()
                    .post("/mailboxIndex/users/" + USERNAME + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(UserReindexingTask.USER_RE_INDEXING))
                    .body("additionalInformation.user", is("benwa@apache.org"))
                    .body("additionalInformation.successfullyReprocessMailCount", is(1))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }
        }

        @Nested
        class SideEffects {
            @Test
            void userReprocessingShouldPerformReprocessingWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = with()
                    .post("/mailboxIndex/users/" + USERNAME + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));


                ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
                ArgumentCaptor<Mailbox> mailboxCaptor1 = ArgumentCaptor.forClass(Mailbox.class);
                ArgumentCaptor<Mailbox> mailboxCaptor2 = ArgumentCaptor.forClass(Mailbox.class);

                verify(searchIndex).deleteAll(any(MailboxSession.class), mailboxCaptor1.capture());
                verify(searchIndex).add(any(MailboxSession.class), mailboxCaptor2.capture(), messageCaptor.capture());
                verifyNoMoreInteractions(searchIndex);

                assertThat(mailboxCaptor1.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(mailboxId));
                assertThat(mailboxCaptor2.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(mailboxId));
                assertThat(messageCaptor.getValue()).matches(message -> message.getMailboxId().equals(mailboxId)
                    && message.getUid().equals(createdMessage.getUid()));
            }
        }
    }

    @Nested
    class MailboxReprocessing {
        @Nested
        class Validation {
            @Test
            void mailboxReprocessingShouldFailWithNoTask() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize())
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("task query parameter is mandatory. The only supported value is `reIndex`"));
            }

            @Test
            void mailboxReprocessingShouldFailWithBadTask() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize() + "?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("task query parameter is mandatory. The only supported value is `reIndex`"));
            }

            @Test
            void mailboxReprocessingShouldFailWithBadUser() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                when()
                    .post("/mailboxIndex/users/bad@bad@bad/mailboxes/" + mailboxId.serialize() + "?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Error while parsing 'user'"));
            }

            @Test
            void mailboxReprocessingShouldFailWithBadMailboxId() {
                when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/bad?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Error while parsing 'mailbox'"));
            }

            @Test
            void mailboxReprocessingShouldFailWithNonExistentMailboxId() {
                when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/36?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("mailbox not found"));
            }

            @Test
            void mailboxReprocessingShouldFailWithNonExistentUser() {
                when()
                    .post("/mailboxIndex/users/notFound@domain.tld/mailboxes/36?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("mailbox not found"));
            }
        }

        @Nested
        class TaskDetails {
            @Test
            void mailboxReprocessingShouldNotFailWhenNoMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                String taskId = when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize() + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(SingleMailboxReindexingTask.MAILBOX_RE_INDEXING))
                    .body("additionalInformation.mailboxPath", is("#private:benwa@apache.org:INBOX"))
                    .body("additionalInformation.successfullyReprocessMailCount", is(0))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            void mailboxReprocessingShouldReturnTaskDetailsWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize() + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(SingleMailboxReindexingTask.MAILBOX_RE_INDEXING))
                    .body("additionalInformation.mailboxPath", is("#private:benwa@apache.org:INBOX"))
                    .body("additionalInformation.successfullyReprocessMailCount", is(1))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }
        }

        @Nested
        class SideEffects {
            @Test
            void mailboxReprocessingShouldPerformReprocessingWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize() + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));


                ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
                ArgumentCaptor<Mailbox> mailboxCaptor1 = ArgumentCaptor.forClass(Mailbox.class);
                ArgumentCaptor<Mailbox> mailboxCaptor2 = ArgumentCaptor.forClass(Mailbox.class);

                verify(searchIndex).deleteAll(any(MailboxSession.class), mailboxCaptor1.capture());
                verify(searchIndex).add(any(MailboxSession.class), mailboxCaptor2.capture(), messageCaptor.capture());
                verifyNoMoreInteractions(searchIndex);

                assertThat(mailboxCaptor1.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(mailboxId));
                assertThat(mailboxCaptor2.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(mailboxId));
                assertThat(messageCaptor.getValue()).matches(message -> message.getMailboxId().equals(mailboxId)
                    && message.getUid().equals(createdMessage.getUid()));
            }
        }
    }

    @Nested
    class MessageReprocessing {
        @Nested
        class Validation {
            @Test
            void messageReprocessingShouldFailWithNoTask() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize() + "/mails/7")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("task query parameter is mandatory. The only supported value is `reIndex`"));
            }

            @Test
            void messageReprocessingShouldFailWithBadTask() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize() + "/mails/7?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("task query parameter is mandatory. The only supported value is `reIndex`"));
            }

            @Test
            void messageReprocessingShouldFailWithBadUser() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                when()
                    .post("/mailboxIndex/users/bad@bad@bad/mailboxes/" + mailboxId.serialize() + "/mails/7?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Error while parsing 'user'"));
            }

            @Test
            void messageReprocessingShouldFailWithBadMailboxId() {
                when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/bad/mails/7?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Error while parsing 'mailbox'"));
            }

            @Test
            void messageReprocessingShouldFailWithNonExistentMailboxId() {
                when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/36/mails/7?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("mailbox not found"));
            }

            @Test
            void messageReprocessingShouldFailWithBadUid() {
                when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/36/mails/bad?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("mailbox not found"));
            }
        }

        @Nested
        class TaskDetails {
            @Test
            void messageReprocessingShouldNotFailWhenUidNotFound() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                String taskId = when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize() + "/mails/1?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(SingleMessageReindexingTask.MESSAGE_RE_INDEXING))
                    .body("additionalInformation.mailboxPath", is("#private:benwa@apache.org:INBOX"))
                    .body("additionalInformation.uid", is(1))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            void messageReprocessingShouldReturnTaskDetailsWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId composedMessageId = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize() + "/mails/"
                        + composedMessageId.getUid().asLong() + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(SingleMessageReindexingTask.MESSAGE_RE_INDEXING))
                    .body("additionalInformation.mailboxPath", is("#private:benwa@apache.org:INBOX"))
                    .body("additionalInformation.uid", is((int) composedMessageId.getUid().asLong()))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }
        }

        @Nested
        class SideEffects {
            @Test
            void mailboxReprocessingShouldPerformReprocessingWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                String taskId = when()
                    .post("/mailboxIndex/users/" + USERNAME + "/mailboxes/" + mailboxId.serialize() + "/mails/"
                        + createdMessage.getUid().asLong() + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));


                ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
                ArgumentCaptor<Mailbox> mailboxCaptor = ArgumentCaptor.forClass(Mailbox.class);

                verify(searchIndex).add(any(MailboxSession.class), mailboxCaptor.capture(), messageCaptor.capture());
                verifyNoMoreInteractions(searchIndex);

                assertThat(mailboxCaptor.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(mailboxId));
                assertThat(messageCaptor.getValue()).matches(message -> message.getMailboxId().equals(mailboxId)
                    && message.getUid().equals(createdMessage.getUid()));
            }
        }
    }

}