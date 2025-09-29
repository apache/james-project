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
import static io.restassured.http.ContentType.JSON;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.routes.UserMailboxesRoutes.USERS_BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

import org.apache.james.core.Username;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.routes.UserMailboxesRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.assertj.core.api.SoftAssertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

import io.restassured.RestAssured;

public class RunRulesOnMailboxRoutesTest {
    private static final Username USERNAME = Username.of("username");
    private static final String MAILBOX_NAME = "myMailboxName";
    private static final String OTHER_MAILBOX_NAME = "myOtherMailboxName";
    private static final String ERROR_TYPE_NOTFOUND = "notFound";
    private static final String ERROR_TYPE_INVALIDARGUMENT = "InvalidArgument";
    private static final String RULE_PAYLOAD = """
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
            "conditionCombiner": "OR",
            "conditions": [
              {
                "comparator": "contains",
                "field": "subject",
                "value": "plop"
              },
              {
                "comparator": "exactly-equals",
                "field": "from",
                "value": "bob@example.com"
              }
            ]
          }
        }""";

    private WebAdminServer webAdminServer;
    private UsersRepository usersRepository;
    private MemoryTaskManager taskManager;
    private InMemoryMailboxManager mailboxManager;
    MessageIdManager messageIdManager;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        mailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();

        usersRepository = mock(UsersRepository.class);
        when(usersRepository.contains(USERNAME)).thenReturn(true);

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new RunRulesOnMailboxRoutes(usersRepository, mailboxManager, taskManager, new JsonTransformer(),
                    new RunRulesOnMailboxService(mailboxManager, new InMemoryId.Factory(), messageIdManager)),
                new TasksRoutes(taskManager, new JsonTransformer(),
                    DTOConverter.of(RunRulesOnMailboxTaskAdditionalInformationDTO.SERIALIZATION_MODULE)))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(USERS_BASE + SEPARATOR + USERNAME.asString() + SEPARATOR + UserMailboxesRoutes.MAILBOXES)
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void runRulesOnMailboxShouldReturnErrorWhenUserIsNotFound() throws UsersRepositoryException {
        when(usersRepository.contains(USERNAME)).thenReturn(false);

        Map<String, Object> errors = given()
            .queryParam("action", "triage")
            .body(RULE_PAYLOAD.formatted("2"))
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(NOT_FOUND_404)
            .contentType(JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("statusCode", NOT_FOUND_404)
            .containsEntry("type", ERROR_TYPE_NOTFOUND)
            .containsEntry("message", "Invalid argument on user mailboxes")
            .containsEntry("details", "User does not exist");
    }

    @Test
    void runRulesOnMailboxShouldReturnErrorWhenMailboxDoesNotExist() throws UsersRepositoryException {
        Map<String, Object> errors = given()
            .queryParam("action", "triage")
            .body(RULE_PAYLOAD.formatted("2"))
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(NOT_FOUND_404)
            .contentType(JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("statusCode", NOT_FOUND_404)
            .containsEntry("type", ERROR_TYPE_NOTFOUND)
            .containsEntry("message", "Invalid argument on user mailboxes")
            .containsEntry("details", String.format("Mailbox does not exist. #private:%s:%s", USERNAME.asString(), MAILBOX_NAME));
    }

    @Test
    void runRulesOnMailboxShouldReturnErrorWhenNoPayload() throws MailboxException {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
        mailboxManager.createMailbox(mailboxPath, systemSession);

        Map<String, Object> errors = given()
            .queryParam("action", "triage")
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("statusCode", BAD_REQUEST_400)
            .containsEntry("type", ERROR_TYPE_INVALIDARGUMENT)
            .containsEntry("message", "JSON payload of the request is not valid");
    }

    @Test
    void runRulesOnMailboxShouldReturnErrorWhenBadPayload() throws MailboxException {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
        mailboxManager.createMailbox(mailboxPath, systemSession);

        Map<String, Object> errors = given()
            .queryParam("action", "triage")
            .body("""
                    {
                      "id": "1",
                      "name": "rule 1",
                      "condition": bad condition",
                      "action": "bad action"
                    }""")
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("statusCode", BAD_REQUEST_400)
            .containsEntry("type", ERROR_TYPE_INVALIDARGUMENT)
            .containsEntry("message", "JSON payload of the request is not valid");
    }

    @Test
    void runRulesOnMailboxShouldReturnTaskId() throws MailboxException {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
        mailboxManager.createMailbox(mailboxPath, systemSession);

        String taskId = given()
            .queryParam("action", "triage")
            .body(RULE_PAYLOAD.formatted("2"))
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        assertThat(taskId)
            .isNotEmpty();
    }

    @Test
    void runRulesOnMailboxShouldMoveMatchingMessage() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
        MailboxPath otherMailboxPath = MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME);
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

        mailboxManager.createMailbox(mailboxPath, systemSession);
        mailboxManager.createMailbox(otherMailboxPath, systemSession);

        mailboxManager.getMailbox(mailboxPath, systemSession)
            .appendMessage(MessageManager.AppendCommand.builder()
                    .build(Message.Builder.of()
                        .setSubject("plop")
                        .setFrom("alice@example.com")
                        .setBody("body", StandardCharsets.UTF_8)),
                systemSession);

        MailboxId otherMailboxId = mailboxManager.getMailbox(otherMailboxPath, systemSession).getId();

        String taskId = given()
            .queryParam("action", "triage")
            .body(RULE_PAYLOAD.formatted(otherMailboxId.serialize()))
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        SoftAssertions.assertSoftly(
            softly -> {
                softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(mailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                    .isEqualTo(0);
                softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(otherMailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                    .isEqualTo(1);
            }
        );
    }

    @Test
    void runRulesShouldApplyDateCrieria() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
        MailboxPath otherMailboxPath = MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME);
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

        mailboxManager.createMailbox(mailboxPath, systemSession);
        mailboxManager.createMailbox(otherMailboxPath, systemSession);

        mailboxManager.getMailbox(mailboxPath, systemSession)
            .appendMessage(MessageManager.AppendCommand.builder()
                    .withInternalDate(Date.from(Clock.systemUTC().instant().minus(Duration.ofDays(2))))
                    .build(Message.Builder.of()
                        .setSubject("plop")
                        .setFrom("alice@example.com")
                        .setBody("body", StandardCharsets.UTF_8)),
                systemSession);

        mailboxManager.getMailbox(mailboxPath, systemSession)
            .appendMessage(MessageManager.AppendCommand.builder()
                    .withInternalDate(Date.from(Clock.systemUTC().instant().minus(Duration.ofDays(20))))
                    .build(Message.Builder.of()
                        .setSubject("plop")
                        .setFrom("alice@example.com")
                        .setBody("body", StandardCharsets.UTF_8)),
                systemSession);

        MailboxId otherMailboxId = mailboxManager.getMailbox(otherMailboxPath, systemSession).getId();

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
                "value": "plop"
              },
              {
                "comparator": "isOlderThan",
                "field": "internalDate",
                "value": "10d"
              }
            ]
          }
        }""".formatted(otherMailboxId.serialize()))
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        SoftAssertions.assertSoftly(
            softly -> {
                softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(mailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                    .isEqualTo(1);
                softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(otherMailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                    .isEqualTo(1);
            }
        );
    }

    @Test
    void runRulesOnMailboxShouldNotMoveNonMatchingMessage() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
        MailboxPath otherMailboxPath = MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME);
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

        mailboxManager.createMailbox(mailboxPath, systemSession);
        mailboxManager.createMailbox(otherMailboxPath, systemSession);

        mailboxManager.getMailbox(mailboxPath, systemSession)
            .appendMessage(MessageManager.AppendCommand.builder()
                    .build(Message.Builder.of()
                        .setSubject("hello")
                        .setFrom("alice@example.com")
                        .setBody("body", StandardCharsets.UTF_8)),
                systemSession);

        MailboxId otherMailboxId = mailboxManager.getMailbox(otherMailboxPath, systemSession).getId();

        String taskId = given()
            .queryParam("action", "triage")
            .body(RULE_PAYLOAD.formatted(otherMailboxId.serialize()))
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        SoftAssertions.assertSoftly(
            softly -> {
                softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(mailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                    .isEqualTo(1);
                softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(otherMailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                    .isEqualTo(0);
            }
        );
    }

    @Test
    void runRulesOnMailboxShouldManageMixedCase() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
        MailboxPath otherMailboxPath = MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME);
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

        mailboxManager.createMailbox(mailboxPath, systemSession);
        mailboxManager.createMailbox(otherMailboxPath, systemSession);

        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, systemSession);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setSubject("plop")
                    .setFrom("alice@example.com")
                    .setBody("body", StandardCharsets.UTF_8)),
            systemSession);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setSubject("hello")
                    .setFrom("alice@example.com")
                    .setBody("body", StandardCharsets.UTF_8)),
            systemSession);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setSubject("hello")
                    .setFrom("bob@example.com")
                    .setBody("body", StandardCharsets.UTF_8)),
            systemSession);

        MailboxId otherMailboxId = mailboxManager.getMailbox(otherMailboxPath, systemSession).getId();

        String taskId = given()
            .queryParam("action", "triage")
            .body(RULE_PAYLOAD.formatted(otherMailboxId.serialize()))
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        SoftAssertions.assertSoftly(
            softly -> {
                softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(mailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                    .isEqualTo(1);
                softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(otherMailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                    .isEqualTo(2);
            }
        );
    }

    @Test
    void runRulesOnMailboxShouldReturnTaskDetails() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
        MailboxPath otherMailboxPath = MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME);
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

        mailboxManager.createMailbox(mailboxPath, systemSession);
        mailboxManager.createMailbox(otherMailboxPath, systemSession);

        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, systemSession);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setSubject("plop")
                    .setFrom("alice@example.com")
                    .setBody("body", StandardCharsets.UTF_8)),
            systemSession);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setSubject("hello")
                    .setFrom("alice@example.com")
                    .setBody("body", StandardCharsets.UTF_8)),
            systemSession);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setSubject("hello")
                    .setFrom("bob@example.com")
                    .setBody("body", StandardCharsets.UTF_8)),
            systemSession);

        MailboxId otherMailboxId = mailboxManager.getMailbox(otherMailboxPath, systemSession).getId();

        String taskId = given()
            .queryParam("action", "triage")
            .body(RULE_PAYLOAD.formatted(otherMailboxId.serialize()))
            .post(MAILBOX_NAME + "/messages")
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", Matchers.is("completed"))
            .body("taskId", Matchers.is(notNullValue()))
            .body("type", Matchers.is(RunRulesOnMailboxTask.TASK_TYPE.asString()))
            .body("startedDate", Matchers.is(notNullValue()))
            .body("submitDate", Matchers.is(notNullValue()))
            .body("completedDate", Matchers.is(notNullValue()))
            .body("additionalInformation.username", Matchers.is(USERNAME.asString()))
            .body("additionalInformation.mailboxName", Matchers.is(MAILBOX_NAME))
            .body("additionalInformation.rulesOnMessagesApplySuccessfully", Matchers.is(2))
            .body("additionalInformation.rulesOnMessagesApplyFailed", Matchers.is(0));
    }
}
