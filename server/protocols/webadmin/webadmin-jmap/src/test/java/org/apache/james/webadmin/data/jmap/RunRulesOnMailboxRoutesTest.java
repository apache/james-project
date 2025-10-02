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
import java.util.List;
import java.util.Map;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.FlagsBuilder;
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
import org.apache.james.mime4j.stream.RawField;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;

public class RunRulesOnMailboxRoutesTest {
    private static final Username USERNAME = Username.of("username");
    private static final Username BOB = Username.of("bob");
    private static final Username ALICE = Username.of("alice");
    private static final String MAILBOX_NAME = "myMailboxName";
    private static final String OTHER_MAILBOX_NAME = "myOtherMailboxName";
    private static final String MOVE_TO_MAILBOX_NAME = "moveToMailbox";
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

    private static final String RULE_MOVE_TO_PAYLOAD = """
                    {
                      "id": "1",
                      "name": "rule 1",
                      "action": {
                        "appendIn": {
                          "mailboxIds": []
                        },
                        "moveTo": {
                          "mailboxName": "%s"
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

    private void createServer(String path) throws Exception {
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
            .setBasePath(path)
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Nested
    class RunRulesOnMailbox {
        @BeforeEach
        void setUp() throws Exception {
            createServer(USERS_BASE + SEPARATOR + USERNAME.asString() + SEPARATOR + UserMailboxesRoutes.MAILBOXES);
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
        void runRulesOnMailboxShouldReturnErrorWhenActionQueryParamIsMissing() throws UsersRepositoryException {
            Map<String, Object> errors = given()
                .body(RULE_PAYLOAD.formatted("2"))
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
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "'action' query parameter is compulsory. Supported values are [triage]");
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
                .containsEntry("details", String.format("Mailbox does not exist: %s", MAILBOX_NAME));
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
        void runRulesOnMailboxShouldSupportMoveToMailboxNameWhenMatchingMessage() throws Exception {
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
                            .setBody("body", StandardCharsets.UTF_8)
                            .addField(new RawField("X-Custom-Header", "value"))),
                    systemSession);

            String taskId = given()
                .queryParam("action", "triage")
                .body("""
                    {
                      "id": "1",
                      "name": "rule 1",
                      "action": {
                        "appendIn": {
                          "mailboxIds": []
                        },
                        "moveTo": {
                          "mailboxName": "%s"
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
                            "comparator": "any",
                            "field": "header:X-Custom-Header",
                            "value": "disregarded"
                          }
                        ]
                      }
                    }""".formatted(OTHER_MAILBOX_NAME))
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
        void runRulesOnMailboxShouldSupportMoveToMailboxWhenMatchingMessageAndTargetMailboxDoesNotExist() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
            MailboxPath moveToMailboxPath = MailboxPath.forUser(USERNAME, MOVE_TO_MAILBOX_NAME);
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

            mailboxManager.createMailbox(mailboxPath, systemSession);

            mailboxManager.getMailbox(mailboxPath, systemSession)
                .appendMessage(MessageManager.AppendCommand.builder()
                        .build(Message.Builder.of()
                            .setSubject("plop")
                            .setFrom("alice@example.com")
                            .setBody("body", StandardCharsets.UTF_8)),
                    systemSession);

            String taskId = given()
                .queryParam("action", "triage")
                .body(RULE_MOVE_TO_PAYLOAD.formatted(MOVE_TO_MAILBOX_NAME))
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
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(moveToMailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                        .isEqualTo(1);
                }
            );
        }

        @Test
        void runRulesOnMailboxShouldNotMoveToMailboxNameWhenNonMatchingMessage() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
            MailboxPath otherMailboxPath = MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME);
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

            mailboxManager.createMailbox(mailboxPath, systemSession);
            mailboxManager.createMailbox(otherMailboxPath, systemSession);

            mailboxManager.getMailbox(mailboxPath, systemSession)
                .appendMessage(MessageManager.AppendCommand.builder()
                        .build(Message.Builder.of()
                            .setSubject("not match rules")
                            .setFrom("alice@example.com")
                            .setBody("body", StandardCharsets.UTF_8)),
                    systemSession);

            String taskId = given()
                .queryParam("action", "triage")
                .body(RULE_MOVE_TO_PAYLOAD.formatted(OTHER_MAILBOX_NAME))
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
        void bothMoveToAndAppendInMailboxesShouldWork() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
            MailboxPath appendIdMailboxPath = MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME);
            MailboxPath moveToMailboxPath = MailboxPath.forUser(USERNAME, MOVE_TO_MAILBOX_NAME);
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

            mailboxManager.createMailbox(mailboxPath, systemSession);
            mailboxManager.createMailbox(appendIdMailboxPath, systemSession);
            mailboxManager.createMailbox(moveToMailboxPath, systemSession);
            MailboxId appendIdMailboxId = mailboxManager.getMailbox(appendIdMailboxPath, systemSession).getId();

            mailboxManager.getMailbox(mailboxPath, systemSession)
                .appendMessage(MessageManager.AppendCommand.builder()
                        .build(Message.Builder.of()
                            .setSubject("plop")
                            .setFrom("alice@example.com")
                            .setBody("body", StandardCharsets.UTF_8)),
                    systemSession);

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
                        "moveTo": {
                          "mailboxName": "%s"
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
                    }"""
                    .formatted(appendIdMailboxId.serialize(), MOVE_TO_MAILBOX_NAME))
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
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(appendIdMailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                        .isEqualTo(1);
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(moveToMailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                        .isEqualTo(1);
                }
            );
        }

        @Test
        void bothMoveToAndAppendInMailboxesShouldNotDuplicateMessageWhenTheSameTargetMailbox() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
            MailboxPath targetMailboxPath = MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME);
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

            mailboxManager.createMailbox(mailboxPath, systemSession);
            mailboxManager.createMailbox(targetMailboxPath, systemSession);
            MailboxId targetMailboxId = mailboxManager.getMailbox(targetMailboxPath, systemSession).getId();

            mailboxManager.getMailbox(mailboxPath, systemSession)
                .appendMessage(MessageManager.AppendCommand.builder()
                        .build(Message.Builder.of()
                            .setSubject("plop")
                            .setFrom("alice@example.com")
                            .setBody("body", StandardCharsets.UTF_8)),
                    systemSession);

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
                        "moveTo": {
                          "mailboxName": "%s"
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
                    }"""
                    .formatted(targetMailboxId.serialize(), OTHER_MAILBOX_NAME))
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
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(targetMailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
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
        void runRulesOnMailboxShouldApplyFlagCriteria() throws Exception {
            MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, MAILBOX_NAME);
            MailboxPath otherMailboxPath = MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME);
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);

            mailboxManager.createMailbox(mailboxPath, systemSession);
            mailboxManager.createMailbox(otherMailboxPath, systemSession);

            MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, systemSession);

            messageManager.appendMessage(
                MessageManager.AppendCommand.builder()
                    .withFlags(new FlagsBuilder().add(Flags.Flag.FLAGGED, Flags.Flag.SEEN)
                        .build())
                    .build(Message.Builder.of()
                        .setSubject("plop")
                        .setFrom("alice@example.com")
                        .setBody("body", StandardCharsets.UTF_8)),
                systemSession).getId();

            messageManager.appendMessage(
                MessageManager.AppendCommand.builder()
                    .withFlags(new FlagsBuilder().add(Flags.Flag.ANSWERED)
                        .add("custom")
                        .build())
                    .build(Message.Builder.of()
                        .setSubject("hello")
                        .setFrom("alice@example.com")
                        .setBody("body", StandardCharsets.UTF_8)),
                systemSession).getId();

            messageManager.appendMessage(
                MessageManager.AppendCommand.builder()
                    .withFlags(new FlagsBuilder().add(Flags.Flag.SEEN)
                        .add("custom")
                        .build())
                    .build(Message.Builder.of()
                        .setSubject("hello")
                        .setFrom("bob@example.com")
                        .setBody("body", StandardCharsets.UTF_8)),
                systemSession).getId();

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
                    "comparator": "isSet",
                    "field": "flag",
                    "value": "$seen"
                  },
                  {
                    "comparator": "isUnset",
                    "field": "flag",
                    "value": "$flagged"
                  },
                  {
                    "comparator": "isSet",
                    "field": "flag",
                    "value": "custom"
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
                        .isEqualTo(2);
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(otherMailboxPath, systemSession).getMailboxCounters(systemSession).getCount()).get())
                        .isEqualTo(1);
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

    @Nested
    class RunRulesOnAllUsersMailbox {
        @BeforeEach
        void setUp() throws Exception {
            createServer("/messages");

            when(usersRepository.listReactive())
                .thenReturn(Flux.fromIterable(ImmutableList.of(USERNAME, ALICE, BOB)));
        }

        @Test
        void runRulesOnAllUsersMailboxShouldReturnErrorWhenMailboxNameQueryParamIsMissing() {
            Map<String, Object> errors = given()
                .queryParam("action", "triage")
                .body(RULE_MOVE_TO_PAYLOAD.formatted("2"))
                .post()
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
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "mailboxName query param is missing");
        }

        @Test
        void runRulesOnAllUsersMailboxShouldReturnErrorWhenActionQueryParamIsMissing() {
            Map<String, Object> errors = given()
                .queryParam("mailboxName", MAILBOX_NAME)
                .body(RULE_MOVE_TO_PAYLOAD.formatted("2"))
                .post()
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
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "'action' query parameter is compulsory. Supported values are [triage]");
        }

        @Test
        void runRulesOnAllUsersMailboxShouldReturnErrorWhenNoPayload() {
            Map<String, Object> errors = given()
                .queryParams("action", "triage", "mailboxName", MAILBOX_NAME)
                .post()
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
        void runRulesOnAllUsersMailboxShouldReturnErrorWhenBadPayload() {
            Map<String, Object> errors = given()
                .queryParams("action", "triage", "mailboxName", MAILBOX_NAME)
                .body("""
                    {
                      "id": "1",
                      "name": "rule 1",
                      "condition": bad condition",
                      "action": "bad action"
                    }""")
                .post()
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
        void runRulesOnAllUsersMailboxShouldReturnErrorWhenRuleActionAppendInMailboxesIsDefined() {
            Map<String, Object> errors = given()
                .queryParams("action", "triage", "mailboxName", MAILBOX_NAME)
                .body("""
                    {
                      "id": "1",
                      "name": "rule 1",
                      "action": {
                        "appendIn": {
                          "mailboxIds": ["123"]
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
                    }""")
                .post()
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
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "Rule payload should not have [appendInMailboxes] action defined for runRulesOnAllUsersMailbox route");
        }

        @Test
        void runRulesOnMailboxShouldReturnListOfTaskIdPerUser() throws MailboxException {
            createUserMailboxes();

            List<Map<String, String>> list = given()
                .queryParams("action", "triage", "mailboxName", MAILBOX_NAME)
                .body(RULE_MOVE_TO_PAYLOAD.formatted(MOVE_TO_MAILBOX_NAME))
                .post()
            .then()
                .statusCode(CREATED_201)
                .extract()
                .jsonPath()
                .getList(".");

            assertThat(list)
                .hasSize(3)
                .first()
                .satisfies(map -> assertThat(map).hasSize(2)
                    .containsKeys("taskId")
                    .containsEntry("username", USERNAME.asString()));
        }

        @Test
        void runRulesOnMailboxShouldManageMixedCase() throws Exception {
            createUserMailboxes();
            createUserMessages(MailboxPath.forUser(USERNAME, MAILBOX_NAME));
            createUserMessages(MailboxPath.forUser(ALICE, MAILBOX_NAME));
            createUserMessages(MailboxPath.forUser(BOB, MAILBOX_NAME));

            List<Map<String, String>> results = given()
                .queryParams("action", "triage", "mailboxName", MAILBOX_NAME)
                .body(RULE_MOVE_TO_PAYLOAD.formatted(MOVE_TO_MAILBOX_NAME))
                .post()
            .then()
                .statusCode(CREATED_201)
                .extract()
                .jsonPath()
                .getList(".");

            results.stream()
                .map(result -> result.get("taskId"))
                .forEach(taskId ->
                    given()
                        .basePath(TasksRoutes.BASE)
                    .when()
                        .get(taskId + "/await"));

            SoftAssertions.assertSoftly(
                softly -> {
                    MailboxSession usernameSession = mailboxManager.createSystemSession(USERNAME);
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(MailboxPath.forUser(USERNAME, MAILBOX_NAME), usernameSession).getMailboxCounters(usernameSession).getCount()).get())
                        .isEqualTo(1);
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(MailboxPath.forUser(USERNAME, MOVE_TO_MAILBOX_NAME), usernameSession).getMailboxCounters(usernameSession).getCount()).get())
                        .isEqualTo(2);

                    MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(MailboxPath.forUser(ALICE, MAILBOX_NAME), aliceSession).getMailboxCounters(aliceSession).getCount()).get())
                        .isEqualTo(1);
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(MailboxPath.forUser(ALICE, MOVE_TO_MAILBOX_NAME), aliceSession).getMailboxCounters(aliceSession).getCount()).get())
                        .isEqualTo(2);

                    MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(MailboxPath.forUser(BOB, MAILBOX_NAME), bobSession).getMailboxCounters(bobSession).getCount()).get())
                        .isEqualTo(1);
                    softly.assertThat(Throwing.supplier(() -> mailboxManager.getMailbox(MailboxPath.forUser(BOB, MOVE_TO_MAILBOX_NAME), bobSession).getMailboxCounters(bobSession).getCount()).get())
                        .isEqualTo(2);
                }
            );
        }

        @Test
        void runRulesOnAllUsersMailboxShouldReturnNoopOnUsersWhenMailboxNameDoesNotExist() throws Exception {
            createUserMailboxes();
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, OTHER_MAILBOX_NAME), systemSession);

            List<Map<String, String>> results = given()
                .queryParams("action", "triage", "mailboxName", OTHER_MAILBOX_NAME)
                .body(RULE_MOVE_TO_PAYLOAD.formatted(MOVE_TO_MAILBOX_NAME))
                .post()
            .then()
                .statusCode(CREATED_201)
                .extract()
                .jsonPath()
                .getList(".");

            assertThat(results)
                .hasSize(1)
                .first()
                .satisfies(map -> assertThat(map).hasSize(2)
                    .containsKeys("taskId")
                    .containsEntry("username", USERNAME.asString()));
        }

        private void createUserMailboxes() throws MailboxException {
            MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, MAILBOX_NAME), systemSession);
            mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, MOVE_TO_MAILBOX_NAME), systemSession);

            systemSession = mailboxManager.createSystemSession(ALICE);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, MAILBOX_NAME), systemSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, MOVE_TO_MAILBOX_NAME), systemSession);

            systemSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, MAILBOX_NAME), systemSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, MOVE_TO_MAILBOX_NAME), systemSession);
        }

        private void createUserMessages(MailboxPath mailboxPath) throws Exception {
            MailboxSession systemSession = mailboxManager.createSystemSession(mailboxPath.getUser());
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
        }
    }
}
