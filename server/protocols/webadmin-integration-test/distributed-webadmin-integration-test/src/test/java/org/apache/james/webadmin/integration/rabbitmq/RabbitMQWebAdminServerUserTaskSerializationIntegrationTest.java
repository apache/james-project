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

package org.apache.james.webadmin.integration.rabbitmq;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRoutes.MESSAGE_PATH_PARAM;
import static org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRoutes.USERS;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.task.TaskManager;
import org.apache.james.util.ReactorUtils;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.data.jmap.RunRulesOnMailboxTask;
import org.apache.james.webadmin.routes.CassandraMailboxMergingRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.service.ClearMailboxContentTask;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRoutes;
import org.awaitility.Awaitility;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Tasks scoped to the test's own user, mailboxes or messages: the tests share one server
@Tag(BasicFeature.TAG)
class RabbitMQWebAdminServerUserTaskSerializationIntegrationTest {
    private static class MailboxIdPair {
        private final MailboxId origin;
        private final MailboxId destination;

        private MailboxIdPair(MailboxId origin, MailboxId destination) {
            this.origin = origin;
            this.destination = destination;
        }
    }

    private static final int TASK_COUNT = 15;
    private static final int MESSAGES_PER_ORIGIN_MAILBOX = 20;

    @RegisterExtension
    static JamesServerExtension testExtension = RabbitMQWebAdminServerTaskSerializationIntegrationTest.jamesServerBuilder()
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    private static final String DOMAIN = "domain";

    private DataProbe dataProbe;
    private MailboxProbe mailboxProbe;
    private String username;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.fluent().addDomain(DOMAIN);
        username = "username-" + UUID.randomUUID() + "@" + DOMAIN;
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);
        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void subscribeAllShouldComplete(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addUser(username, "secret");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);
        String taskId = with()
            .post("/users/" + username + "/mailboxes?task=subscribeAll")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is("SubscribeAllTask"))
            .body("additionalInformation.username", is(username))
            .body("additionalInformation.subscribedCount", is(1))
            .body("additionalInformation.unsubscribedCount", is(0));
    }

    @Test
    void multipleMailboxMergingTasksShouldCompleteQuicklyDespitePollingInterval(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addUser(username, "secret");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);

        List<MailboxIdPair> mailboxes = Flux.range(0, TASK_COUNT)
            .flatMap(this::provisionMailbox, ReactorUtils.LOW_CONCURRENCY)
            .collectList()
            .block();

        List<String> taskIds = mailboxes.stream()
            .map(pair -> given()
                .contentType(ContentType.JSON)
                .body("{" +
                    "\"mergeOrigin\":\"" + pair.origin.serialize() + "\"," +
                    "\"mergeDestination\":\"" + pair.destination.serialize() + "\"" +
                    "}")
                .post(CassandraMailboxMergingRoutes.BASE)
                .then()
                .statusCode(CREATED_201)
                .extract()
                .jsonPath()
                .getString("taskId"))
            .toList();

        // expect all tasks to complete within 2 minutes
        Awaitility.await()
            .atMost(Duration.ofMinutes(2))
            .untilAsserted(() -> taskIds.forEach(taskId -> given()
                .basePath(TasksRoutes.BASE)
                .get(taskId + "/await")
                .then()
                .statusCode(HttpStatus.OK_200)
                .body("status", is("completed"))));
    }

    @Test
    void singleMailboxReindexingShouldComplete(GuiceJamesServer server) {
        MailboxId mailboxId = server.getProbe(MailboxProbeImpl.class)
            .createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);

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
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is("mailbox-reindexing"))
            .body("additionalInformation.successfullyReprocessedMailCount", is(0))
            .body("additionalInformation.failedReprocessedMailCount", is(0))
            .body("additionalInformation.mailboxId", is(mailboxId.serialize()))
            .body("additionalInformation.messageFailures", is(anEmptyMap()));
    }

    @Test
    void singleMessageReindexingShouldCompleteWhenMail() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);
        ComposedMessageId composedMessageId = mailboxProbe.appendMessage(
                username,
                MailboxPath.inbox(Username.of(username)),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
                new Date(),
                false,
                new Flags());

        String taskId = with()
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
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is("message-reindexing"))
            .body("additionalInformation.mailboxId", is(mailboxId.serialize()))
            .body("additionalInformation.uid", is(Math.toIntExact(composedMessageId.getUid().asLong())));
    }

    @Test
    void messageIdReIndexingShouldCompleteWhenMail() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);
        ComposedMessageId composedMessageId = mailboxProbe.appendMessage(
            username,
            MailboxPath.inbox(Username.of(username)),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        String taskId = with()
            .post("/messages/" + composedMessageId.getMessageId().serialize() + "?task=reIndex")
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is("messageId-reindexing"))
            .body("additionalInformation.messageId", is(composedMessageId.getMessageId().serialize()));
    }

    @Test
    void deletedMessageVaultRestoreShouldComplete() throws Exception {
        dataProbe.addUser(username, "password");
        String query =
            "{" +
                "  \"fieldName\": \"subject\"," +
                "  \"operator\": \"contains\"," +
                "  \"value\": \"subject contains\"" +
                "}";

        String taskId =
            with()
                .basePath(DeletedMessagesVaultRoutes.ROOT_PATH)
                .queryParam("action", "restore")
                .body(query)
            .post(USERS + SEPARATOR + username)
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is("deleted-messages-restore"))
            .body("additionalInformation.username", is(username))
            .body("additionalInformation.successfulRestoreCount", is(0))
            .body("additionalInformation.errorRestoreCount", is(0));
    }

    @Test
    void deletedMessageVaultExportShouldComplete() throws Exception {
        dataProbe.addUser(username, "password");
        String query = "{" +
            "\"combinator\": \"and\"," +
            "\"criteria\": []" +
            "}";

        String exportTo = "exportTo@james.org";
        String taskId = with()
            .basePath(DeletedMessagesVaultRoutes.ROOT_PATH)
            .queryParam("action", "export")
            .queryParam("exportTo", exportTo)
            .body(query)
        .post(USERS + SEPARATOR + username)
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is("deleted-messages-export"))
            .body("additionalInformation.userExportFrom", is(username))
            .body("additionalInformation.exportTo", is(exportTo))
            .body("additionalInformation.totalExportedMessages", is(0));
    }

    @Test
    void mailboxMergingShouldComplete() {
        MailboxId origin = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);
        MailboxId destination = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX + "2");

        String taskId = given()
                .body("{" +
                    "    \"mergeOrigin\":\"" + origin.serialize() + "\"," +
                    "    \"mergeDestination\":\"" + destination.serialize() + "\"" +
                    "}")
            .post(CassandraMailboxMergingRoutes.BASE)
                .jsonPath()
                .getString("taskId");

        with()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is(TaskManager.Status.COMPLETED.getValue()))
            .body("taskId", is(taskId))
            .body("type", is("mailbox-merging"))
            .body("additionalInformation.oldMailboxId", is(origin.serialize()))
            .body("additionalInformation.newMailboxId", is(destination.serialize()))
            .body("additionalInformation.totalMessageCount", is(0))
            .body("additionalInformation.messageMovedCount", is(0))
            .body("additionalInformation.messageFailedCount", is(0));
    }

    @Test
    void deletedMessagesVaultDeleteShouldCompleteEvenNoDeletedMessageExisted() throws Exception {
        dataProbe.addUser(username, "password");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);
        ComposedMessageId composedMessageId = mailboxProbe.appendMessage(
            username,
            MailboxPath.inbox(Username.of(username)),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        String taskId =
            with()
                .basePath(DeletedMessagesVaultRoutes.ROOT_PATH)
            .delete(USERS + SEPARATOR + username + SEPARATOR + MESSAGE_PATH_PARAM + SEPARATOR + composedMessageId.getMessageId().serialize())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("deleted-messages-delete"))
            .body("additionalInformation.username", is(username))
            .body("additionalInformation.deleteMessageId", is(composedMessageId.getMessageId().serialize()));
    }

    @Test
    void deleteMailboxContentShouldComplete(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addUser(username, "secret");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);

        mailboxProbe.appendMessage(
            username,
            MailboxPath.inbox(Username.of(username)),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        String taskId = given()
            .delete("users/" + username + "/mailboxes/" + MailboxConstants.INBOX + "/messages")
            .jsonPath()
            .getString("taskId");

        with()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
            .then()
            .body("status", is(TaskManager.Status.COMPLETED.getValue()))
            .body("taskId", is(taskId))
            .body("type", is(ClearMailboxContentTask.TASK_TYPE.asString()))
            .body("additionalInformation.messagesSuccessCount", is(1))
            .body("additionalInformation.messagesFailCount", is(0))
            .body("additionalInformation.username", is(username))
            .body("additionalInformation.mailboxName", is(MailboxConstants.INBOX));
    }

    @Test
    void runRulesOnMailboxShouldComplete(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addUser(username, "secret");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, MailboxConstants.INBOX);
        MailboxId otherMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "otherMailbox");

        mailboxProbe.appendMessage(
            username,
            MailboxPath.inbox(Username.of(username)),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        String taskId = given()
            .queryParam("action", "triage")
            .body("""
            {
              "id": "1",
              "name": "rule 1",
              "action": {
                "appendIn": {
                  "mailboxIds": ["%s"]
                },
                "important": false,
                "keyworkds": [],
                "reject": false,
                "seen": false
              },
              "conditionGroup": {
                "conditionCombiner": "AND",
                "conditions": [
                  {
                    "comparator": "contains",
                    "field": "subject",
                    "value": "test"
                  }
                ]
              }
            }""".formatted(otherMailboxId.serialize()))
            .post("users/" + username + "/mailboxes/" + MailboxConstants.INBOX + "/messages")
            .jsonPath()
            .getString("taskId");

        with()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is(TaskManager.Status.COMPLETED.getValue()))
            .body("taskId", is(taskId))
            .body("type", is(RunRulesOnMailboxTask.TASK_TYPE.asString()))
            .body("additionalInformation.rulesOnMessagesApplySuccessfully", is(1))
            .body("additionalInformation.rulesOnMessagesApplyFailed", is(0))
            .body("additionalInformation.username", is(username))
            .body("additionalInformation.mailboxPath", is(MailboxPath.forUser(Username.of(username), MailboxConstants.INBOX).asString()));
    }

    private Mono<MailboxIdPair> provisionMailbox(Integer number) {
        return Mono.fromCallable(() -> {
            String originMailboxName = "origin-" + number;
            MailboxId originMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, originMailboxName);
            MailboxId destinationMailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, "destination-" + number);

            try {
                appendMessages(username, originMailboxName, MESSAGES_PER_ORIGIN_MAILBOX);
            } catch (MailboxException e) {
                throw new RuntimeException(e);
            }
            return new MailboxIdPair(originMailboxId, destinationMailboxId);
        });
    }

    private void appendMessages(String username, String mailboxName, int messageCount) throws MailboxException {
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, Username.of(username), mailboxName);
        Flux.range(0, messageCount)
            .flatMap(i -> Mono.fromCallable(() -> mailboxProbe.appendMessage(username, mailboxPath,
                new ByteArrayInputStream(("Subject: test " + i + "\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)),
                new Date(),
                false,
                new Flags())), ReactorUtils.DEFAULT_CONCURRENCY)
            .collectList()
            .block();
    }
}
