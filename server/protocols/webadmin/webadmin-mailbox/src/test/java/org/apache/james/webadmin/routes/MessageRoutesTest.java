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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.mailbox.tools.indexer.MessageIdReIndexerImpl;
import org.apache.mailbox.tools.indexer.MessageIdReIndexingTask;
import org.apache.mailbox.tools.indexer.ReIndexerPerformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.restassured.RestAssured;

class MessageRoutesTest {
    private static final Username USERNAME = Username.of("benwa@apache.org");
    private static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);

    private WebAdminServer webAdminServer;
    private ListeningMessageSearchIndex searchIndex;
    private InMemoryMailboxManager mailboxManager;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void beforeEach() {
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        searchIndex = mock(ListeningMessageSearchIndex.class);
        ReIndexerPerformer reIndexerPerformer = new ReIndexerPerformer(
            mailboxManager,
            searchIndex,
            mailboxManager.getMapperFactory());
        JsonTransformer jsonTransformer = new JsonTransformer();

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new TasksRoutes(taskManager, jsonTransformer),
                new MessagesRoutes(taskManager,
                    new InMemoryMessageId.Factory(),
                    new MessageIdReIndexerImpl(reIndexerPerformer),
                    jsonTransformer))
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
    class MessageReprocessing {
        @Nested
        class Validation {
            @Test
            void messageIdReprocessingShouldFailWithNoTask() {
                when()
                    .post("/messages/7")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("'task' query parameter is compulsory. Supported values are [reIndex]"));
            }

            @Test
            void messageIdReprocessingShouldFailWithBadTask() {
                when()
                    .post("/messages/7?task=bad")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Invalid arguments supplied in the user request"))
                    .body("details", is("Invalid value supplied for query parameter 'task': bad. Supported values are [reIndex]"));
            }

            @Test
            void messageIdReprocessingShouldFailWithBadMessageId() {
                when()
                    .post("/messages/bad?task=reIndex")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .body("statusCode", is(400))
                    .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                    .body("message", is("Error while parsing 'messageId'"));
            }
        }

        @Nested
        class TaskDetails {
            @Test
            void messageIdReprocessingShouldNotFailWhenUidNotFound() {
                String taskId = when()
                    .post("/messages/1?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(MessageIdReIndexingTask.TYPE.asString()))
                    .body("additionalInformation.messageId", is("1"))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            void messageIdReprocessingShouldReturnTaskDetailsWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId composedMessageId = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession).getId();

                String taskId = when()
                    .post("/messages/" + composedMessageId.getMessageId().serialize() + "?task=reIndex")
                    .jsonPath()
                    .get("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(MessageIdReIndexingTask.TYPE.asString()))
                    .body("additionalInformation.messageId", is(composedMessageId.getMessageId().serialize()))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }
        }

        @Nested
        class SideEffects {
            @Test
            void messageIdReprocessingShouldPerformReprocessingWhenMail() throws Exception {
                MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
                mailboxManager.createMailbox(INBOX, systemSession).get();
                ComposedMessageId composedMessageId = mailboxManager.getMailbox(INBOX, systemSession)
                    .appendMessage(
                        MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                        systemSession).getId();

                String taskId = when()
                    .post("/messages/" + composedMessageId.getMessageId().serialize() + "?task=reIndex")
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

                assertThat(mailboxCaptor.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(composedMessageId.getMailboxId()));
                assertThat(messageCaptor.getValue()).matches(message -> message.getComposedMessageIdWithMetaData()
                    .getComposedMessageId()
                    .getMessageId()
                    .equals(composedMessageId.getMessageId()));
            }
        }
    }
}