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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.james.core.Username;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;
import spark.Service;

public class RunRulesOnAllUsersMailboxToTaskTest {
    private static final Username USERNAME = Username.of("username");
    private static final Username BOB = Username.of("bob");
    private static final Username ALICE = Username.of("alice");
    private static final String MAILBOX_NAME = "myMailboxName";
    private static final String OTHER_MAILBOX_NAME = "myOtherMailboxName";
    private static final String MOVE_TO_MAILBOX_NAME = "moveToMailbox";
    private static final String ERROR_TYPE_INVALIDARGUMENT = "InvalidArgument";
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

    private static final class JMAPRoutes implements Routes {
        private final UsersRepository usersRepository;
        private final MailboxManager mailboxManager;
        private final RunRulesOnMailboxService runRulesOnMailboxService;
        private final TaskManager taskManager;

        private JMAPRoutes(UsersRepository usersRepository, MailboxManager mailboxManager, RunRulesOnMailboxService runRulesOnMailboxService, TaskManager taskManager) {
            this.usersRepository = usersRepository;
            this.mailboxManager = mailboxManager;
            this.runRulesOnMailboxService = runRulesOnMailboxService;
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
                    .registrations(new RunRulesOnAllUsersMailboxToTask(usersRepository, mailboxManager, runRulesOnMailboxService))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    static final String BASE_PATH = "/messages";

    private UsersRepository usersRepository;
    private InMemoryMailboxManager mailboxManager;
    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;

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
        MessageIdManager messageIdManager = resources.getMessageIdManager();

        usersRepository = mock(UsersRepository.class);

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new JMAPRoutes(usersRepository, mailboxManager, new RunRulesOnMailboxService(mailboxManager, new InMemoryId.Factory(), messageIdManager), taskManager),
                new TasksRoutes(taskManager, new JsonTransformer(),
                    DTOConverter.of(RunRulesOnMailboxTaskAdditionalInformationDTO.SERIALIZATION_MODULE)))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(BASE_PATH)
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();

        when(usersRepository.listReactive())
            .thenReturn(Flux.fromIterable(ImmutableList.of(USERNAME, ALICE, BOB)));
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
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
    void runRulesOnAllUsersMailboxShouldReturnListOfTaskIdPerUser() throws MailboxException {
        createUserMailboxes();

        Map<String, String> map = given()
            .queryParams("action", "triage", "mailboxName", MAILBOX_NAME)
            .body(RULE_MOVE_TO_PAYLOAD.formatted(MOVE_TO_MAILBOX_NAME))
            .post()
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .getMap(".");

        assertThat(map)
            .satisfies(result -> assertThat(map).hasSize(3)
                .containsOnlyKeys(ALICE.asString(), BOB.asString(), USERNAME.asString())
                .extractingByKey(ALICE.asString(), MAP)
                .asInstanceOf(MAP)
                .containsKey("taskId"));
    }

    @Test
    void runRulesOnAllUsersMailboxShouldManageMixedCase() throws Exception {
        createUserMailboxes();
        createUserMessages(MailboxPath.forUser(USERNAME, MAILBOX_NAME));
        createUserMessages(MailboxPath.forUser(ALICE, MAILBOX_NAME));
        createUserMessages(MailboxPath.forUser(BOB, MAILBOX_NAME));

        Map<String, Map<String, String>> results = given()
            .queryParams("action", "triage", "mailboxName", MAILBOX_NAME)
            .body(RULE_MOVE_TO_PAYLOAD.formatted(MOVE_TO_MAILBOX_NAME))
            .post()
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .getMap(".");

        results
            .forEach( (username, taskId) ->
                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId.get(0) + "/await"));

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

        Map<String, String> results = given()
            .queryParams("action", "triage", "mailboxName", OTHER_MAILBOX_NAME)
            .body(RULE_MOVE_TO_PAYLOAD.formatted(MOVE_TO_MAILBOX_NAME))
            .post()
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .getMap(".");

        assertThat(results)
            .satisfies(map -> assertThat(map).hasSize(1)
                .containsOnlyKeys(USERNAME.asString()));
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
