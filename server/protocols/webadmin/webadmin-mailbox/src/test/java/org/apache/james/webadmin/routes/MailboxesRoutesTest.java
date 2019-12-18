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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.PreviousReIndexingService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.mailbox.tools.indexer.FullReindexingTask;
import org.apache.mailbox.tools.indexer.ReIndexerImpl;
import org.apache.mailbox.tools.indexer.ReIndexerPerformer;
import org.apache.mailbox.tools.indexer.SingleMailboxReindexingTask;
import org.apache.mailbox.tools.indexer.SingleMessageReindexingTask;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;

class MailboxesRoutesTest {
    private static final Username USERNAME = Username.of("benwa@apache.org");
    private static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);

    private WebAdminServer webAdminServer;
    private ListeningMessageSearchIndex searchIndex;
    private InMemoryMailboxManager mailboxManager;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void beforeEach() {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        InMemoryId.Factory mailboxIdFactory = new InMemoryId.Factory();
        searchIndex = mock(ListeningMessageSearchIndex.class);
        ReIndexerPerformer reIndexerPerformer = new ReIndexerPerformer(
            mailboxManager,
            searchIndex,
            mailboxManager.getMapperFactory());
        ReIndexer reIndexer = new ReIndexerImpl(
            reIndexerPerformer,
            mailboxManager,
            mailboxManager.getMapperFactory());
        JsonTransformer jsonTransformer = new JsonTransformer();

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new TasksRoutes(taskManager, jsonTransformer),
                new MailboxesRoutes(taskManager,
                    jsonTransformer,
                    ImmutableSet.of(
                        new MailboxesRoutes.ReIndexAllMailboxesTaskRegistration(
                            reIndexer, new PreviousReIndexingService(taskManager), mailboxIdFactory)),
                    ImmutableSet.of(
                        new MailboxesRoutes.ReIndexOneMailboxTaskRegistration(
                            reIndexer, mailboxIdFactory)),
                    ImmutableSet.of(
                        new MailboxesRoutes.ReIndexOneMailTaskRegistration(
                            reIndexer, mailboxIdFactory))))
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
    class FullReprocessing {
        @Nested
        class Validation {
            @Test
            void fullReprocessingShouldFailWithNoTask() {
                when()
                    .post("/mailboxes")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("'task' query parameter is compulsory. Supported values are [reIndex]"));
            }

            @Test
            void fullReprocessingShouldFailWithBadTask() {
                when()
                    .post("/mailboxes?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("Invalid value supplied for query parameter 'task': bad. Supported values are [reIndex]"));
            }
        }

        @Nested
        class TaskDetails {
            @Test
            void fullReprocessingShouldNotFailWhenNoMail() {
                String taskId = with()
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(FullReindexingTask.FULL_RE_INDEXING.asString()))
                    .body("additionalInformation.successfullyReprocessedMailCount", is(0))
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
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(FullReindexingTask.FULL_RE_INDEXING.asString()))
                    .body("additionalInformation.successfullyReprocessedMailCount", is(1))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            void fullReprocessingShouldReturnTaskDetailsWhenFailing() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId composedMessageId = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                doThrow(new RuntimeException())
                    .when(searchIndex)
                    .add(any(MailboxSession.class), any(Mailbox.class), any(MailboxMessage.class));

                String taskId = with()
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                long uidAsLong = composedMessageId.getUid().asLong();
                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("failed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(FullReindexingTask.FULL_RE_INDEXING.asString()))
                    .body("additionalInformation.successfullyReprocessedMailCount", is(0))
                    .body("additionalInformation.failedReprocessedMailCount", is(1))
                    .body("additionalInformation.failures.\"" + mailboxId.serialize() + "\"[0].uid", is(Long.valueOf(uidAsLong).intValue()))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()));
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
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                with()
                    .basePath(TasksRoutes.BASE)
                    .get(taskId + "/await")
                    .then()
                    .body("status", is("completed"));


                ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
                ArgumentCaptor<MailboxId> mailboxIdCaptor = ArgumentCaptor.forClass(MailboxId.class);
                ArgumentCaptor<Mailbox> mailboxCaptor2 = ArgumentCaptor.forClass(Mailbox.class);

                verify(searchIndex).deleteAll(any(MailboxSession.class), mailboxIdCaptor.capture());
                verify(searchIndex).add(any(MailboxSession.class), mailboxCaptor2.capture(), messageCaptor.capture());
                verifyNoMoreInteractions(searchIndex);

                assertThat(mailboxIdCaptor.getValue()).matches(capturedMailboxId -> capturedMailboxId.equals(mailboxId));
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
                    .post("/mailboxes/" + mailboxId.serialize())
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("'task' query parameter is compulsory. Supported values are [reIndex]"));
            }

            @Test
            void mailboxReprocessingShouldFailWithBadTask() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                when()
                    .post("/mailboxes/" + mailboxId.serialize() + "?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("Invalid value supplied for query parameter 'task': bad. Supported values are [reIndex]"));
            }

            @Test
            void mailboxReprocessingShouldFailWithBadMailboxId() {
                when()
                    .post("/mailboxes/bad?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Error while parsing 'mailbox'"));
            }

            @Test
            void mailboxReprocessingShouldFailWithNonExistentMailboxId() {
                when()
                    .post("/mailboxes/36?task=reIndex")
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .body("statusCode", is(404))
                    .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
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
                    .post("/mailboxes/" + mailboxId.serialize() + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(SingleMailboxReindexingTask.MAILBOX_RE_INDEXING.asString()))
                    .body("additionalInformation.mailboxId", is(mailboxId.serialize()))
                    .body("additionalInformation.successfullyReprocessedMailCount", is(0))
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
                    .post("/mailboxes/" + mailboxId.serialize() + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(SingleMailboxReindexingTask.MAILBOX_RE_INDEXING.asString()))
                    .body("additionalInformation.mailboxId", is(mailboxId.serialize()))
                    .body("additionalInformation.successfullyReprocessedMailCount", is(1))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()));
            }

            @Test
            void mailboxReprocessingShouldReturnTaskDetailsWhenFailing() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId composedMessageId = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                doThrow(new RuntimeException())
                    .when(searchIndex)
                    .add(any(MailboxSession.class), any(Mailbox.class), any(MailboxMessage.class));

                String taskId = with()
                    .queryParam("task", "reIndex")
                    .post("/mailboxes/" + mailboxId.serialize())
                    .jsonPath()
                    .get("taskId");

                long uidAsLong = composedMessageId.getUid().asLong();
                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("failed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(SingleMailboxReindexingTask.MAILBOX_RE_INDEXING.asString()))
                    .body("additionalInformation.successfullyReprocessedMailCount", is(0))
                    .body("additionalInformation.failedReprocessedMailCount", is(1))
                    .body("additionalInformation.failures.\"" + mailboxId.serialize() + "\"[0].uid", is(Long.valueOf(uidAsLong).intValue()))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()));
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
                    .post("/mailboxes/" + mailboxId.serialize() + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));


                ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
                ArgumentCaptor<MailboxId> mailboxIdCaptor = ArgumentCaptor.forClass(MailboxId.class);
                ArgumentCaptor<Mailbox> mailboxCaptor2 = ArgumentCaptor.forClass(Mailbox.class);

                verify(searchIndex).deleteAll(any(MailboxSession.class), mailboxIdCaptor.capture());
                verify(searchIndex).add(any(MailboxSession.class), mailboxCaptor2.capture(), messageCaptor.capture());
                verifyNoMoreInteractions(searchIndex);

                assertThat(mailboxIdCaptor.getValue()).matches(capturedMailboxId -> capturedMailboxId.equals(mailboxId));
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
                    .post("/mailboxes/" + mailboxId.serialize() + "/mails/7")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("'task' query parameter is compulsory. Supported values are [reIndex]"));
            }

            @Test
            void messageReprocessingShouldFailWithBadTask() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                when()
                    .post("/mailboxes/" + mailboxId.serialize() + "/mails/7?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("Invalid value supplied for query parameter 'task': bad. Supported values are [reIndex]"));
            }

            @Test
            void messageReprocessingShouldFailWithBadMailboxId() {
                when()
                    .post("/mailboxes/bad/mails/7?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Error while parsing 'mailbox'"));
            }

            @Test
            void messageReprocessingShouldFailWithNonExistentMailboxId() {
                when()
                    .post("/mailboxes/36/mails/7?task=reIndex")
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .body("statusCode", is(404))
                    .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
                    .body("message", is("mailbox not found"));
            }

            @Test
            void messageReprocessingShouldFailWithBadUid() {
                when()
                    .post("/mailboxes/36/mails/bad?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("'uid' needs to be a parsable long"));
            }
        }

        @Nested
        class TaskDetails {
            @Test
            void messageReprocessingShouldNotFailWhenUidNotFound() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

                String taskId = when()
                    .post("/mailboxes/" + mailboxId.serialize() + "/mails/1?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(SingleMessageReindexingTask.MESSAGE_RE_INDEXING.asString()))
                    .body("additionalInformation.mailboxId", is(mailboxId.serialize()))
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
                    .post("/mailboxes/" + mailboxId.serialize() + "/mails/"
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
                    .body("type", is(SingleMessageReindexingTask.MESSAGE_RE_INDEXING.asString()))
                    .body("additionalInformation.mailboxId", is(mailboxId.serialize()))
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
                    .post("/mailboxes/" + mailboxId.serialize() + "/mails/"
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

    @Nested
    class FixingReIndexing {
        @Nested
        class Validation {
            @Test
            void fixingReIndexingShouldThrowOnMissingTaskQueryParameter() {
                String taskId = with()
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                with()
                    .basePath(TasksRoutes.BASE)
                    .get(taskId + "/await");

                given()
                    .queryParam("reIndexFailedMessagesOf", taskId)
                .when()
                    .post("/mailboxes")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("'task' query parameter is compulsory. Supported values are [reIndex]"));
            }

            @Test
            void fixingReIndexingShouldFailWithBadTask() {
                String taskId = with()
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                with()
                    .basePath(TasksRoutes.BASE)
                    .get(taskId + "/await");

                given()
                    .queryParam("reIndexFailedMessagesOf", taskId)
                .when()
                    .post("/mailboxes?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("Invalid value supplied for query parameter 'task': bad. Supported values are [reIndex]"));
            }

            @Test
            void fixingReIndexingShouldRejectNotExistingTask() {
                String taskId = "bbdb69c9-082a-44b0-a85a-6e33e74287a5";

                given()
                    .queryParam("reIndexFailedMessagesOf", taskId)
                .when()
                    .post("/mailboxes?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("Invalid value supplied for query parameter 'task': bad. Supported values are [reIndex]"));
            }
        }

        @Nested
        class TaskDetails {
            @Test
            void fixingReIndexingShouldNotFailWhenNoMail() {
                String taskId = with()
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                with()
                    .basePath(TasksRoutes.BASE)
                    .get(taskId + "/await");

                String fixingTaskId = with()
                    .queryParam("reIndexFailedMessagesOf", taskId)
                    .queryParam("task", "reIndex")
                    .post("/mailboxes")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(fixingTaskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is("error-recovery-indexation"))
                    .body("additionalInformation.successfullyReprocessedMailCount", is(0))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            void fixingReIndexingShouldReturnTaskDetailsWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                doThrow(new RuntimeException()).when(searchIndex).add(any(MailboxSession.class), any(Mailbox.class), any(MailboxMessage.class));

                String taskId = with()
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                with()
                    .basePath(TasksRoutes.BASE)
                    .get(taskId + "/await");

                doNothing().when(searchIndex).add(any(MailboxSession.class), any(Mailbox.class), any(MailboxMessage.class));

                String fixingTaskId = with()
                    .queryParam("reIndexFailedMessagesOf", taskId)
                    .queryParam("task", "reIndex")
                    .post("/mailboxes")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(fixingTaskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is("error-recovery-indexation"))
                    .body("additionalInformation.successfullyReprocessedMailCount", is(1))
                    .body("additionalInformation.failedReprocessedMailCount", is(0))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            void mailboxReprocessingShouldReturnTaskDetailsWhenFailing() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId composedMessageId = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                doThrow(new RuntimeException())
                    .when(searchIndex)
                    .add(any(MailboxSession.class), any(Mailbox.class), any(MailboxMessage.class));

                String taskId = with()
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                with()
                    .basePath(TasksRoutes.BASE)
                    .get(taskId + "/await");

                String fixingTaskId = with()
                    .queryParam("reIndexFailedMessagesOf", taskId)
                    .queryParam("task", "reIndex")
                    .post("/mailboxes")
                    .jsonPath()
                    .get("taskId");

                long uidAsLong = composedMessageId.getUid().asLong();
                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(fixingTaskId + "/await")
                .then()
                    .body("status", is("failed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is("error-recovery-indexation"))
                    .body("additionalInformation.successfullyReprocessedMailCount", is(0))
                    .body("additionalInformation.failedReprocessedMailCount", is(1))
                    .body("additionalInformation.failures.\"" + mailboxId.serialize() + "\"[0].uid", is(Long.valueOf(uidAsLong).intValue()))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()));
            }
        }

        @Nested
        class SideEffects {
            @Test
            void fixingReprocessingShouldPerformReprocessingWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession);

                doThrow(new RuntimeException()).when(searchIndex).add(any(MailboxSession.class), any(Mailbox.class), any(MailboxMessage.class));

                String taskId = with()
                    .post("/mailboxes?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                with()
                    .basePath(TasksRoutes.BASE)
                    .get(taskId + "/await");

                reset(searchIndex);

                String fixingTaskId = with()
                    .queryParam("reIndexFailedMessagesOf", taskId)
                    .queryParam("task", "reIndex")
                    .post("/mailboxes")
                    .jsonPath()
                    .get("taskId");

                with()
                    .basePath(TasksRoutes.BASE)
                    .get(fixingTaskId + "/await")
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