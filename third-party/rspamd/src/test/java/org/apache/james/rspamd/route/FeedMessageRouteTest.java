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

package org.apache.james.rspamd.route;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.james.rspamd.RspamdExtension.PASSWORD;
import static org.apache.james.rspamd.route.FeedMessageRoute.BASE_PATH;
import static org.apache.james.rspamd.task.FeedHamToRspamdTaskTest.ALICE_INBOX_MAILBOX;
import static org.apache.james.rspamd.task.FeedHamToRspamdTaskTest.BOB_INBOX_MAILBOX;
import static org.apache.james.rspamd.task.FeedSpamToRspamdTaskTest.ALICE;
import static org.apache.james.rspamd.task.FeedSpamToRspamdTaskTest.ALICE_SPAM_MAILBOX;
import static org.apache.james.rspamd.task.FeedSpamToRspamdTaskTest.BOB;
import static org.apache.james.rspamd.task.FeedSpamToRspamdTaskTest.BOB_SPAM_MAILBOX;
import static org.apache.james.rspamd.task.FeedSpamToRspamdTaskTest.NOW;
import static org.apache.james.rspamd.task.FeedSpamToRspamdTaskTest.ONE_DAY_IN_SECOND;
import static org.apache.james.rspamd.task.FeedSpamToRspamdTaskTest.THREE_DAYS_IN_SECOND;
import static org.apache.james.rspamd.task.FeedSpamToRspamdTaskTest.TWO_DAYS_IN_SECOND;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.stream.IntStream;

import jakarta.mail.Flags;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.json.DTOConverter;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.rspamd.RspamdExtension;
import org.apache.james.rspamd.client.RspamdClientConfiguration;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.rspamd.task.FeedHamToRspamdTask;
import org.apache.james.rspamd.task.FeedHamToRspamdTaskAdditionalInformationDTO;
import org.apache.james.rspamd.task.FeedSpamToRspamdTask;
import org.apache.james.rspamd.task.FeedSpamToRspamdTaskAdditionalInformationDTO;
import org.apache.james.rspamd.task.RunningOptions;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.DurationParser;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import com.github.fge.lambdas.Throwing;

import io.restassured.RestAssured;

@Tag(Unstable.TAG)
public class FeedMessageRouteTest {
    @RegisterExtension
    static RspamdExtension rspamdExtension = new RspamdExtension();

    private InMemoryMailboxManager mailboxManager;
    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources inMemoryIntegrationResources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = inMemoryIntegrationResources.getMailboxManager();
        DomainList domainList = mock(DomainList.class);
        Mockito.when(domainList.containsDomain(any())).thenReturn(true);
        UsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "anyPassword");
        usersRepository.addUser(ALICE, "anyPassword");
        mailboxManager.createMailbox(BOB_SPAM_MAILBOX, mailboxManager.createSystemSession(BOB));
        mailboxManager.createMailbox(BOB_INBOX_MAILBOX, mailboxManager.createSystemSession(BOB));
        mailboxManager.createMailbox(ALICE_SPAM_MAILBOX, mailboxManager.createSystemSession(ALICE));
        mailboxManager.createMailbox(ALICE_INBOX_MAILBOX, mailboxManager.createSystemSession(ALICE));

        taskManager = new MemoryTaskManager(new Hostname("foo"));
        UpdatableTickingClock clock = new UpdatableTickingClock(NOW);
        JsonTransformer jsonTransformer = new JsonTransformer();
        RspamdClientConfiguration rspamdConfiguration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(rspamdConfiguration);
        MessageIdManager messageIdManager = inMemoryIntegrationResources.getMessageIdManager();
        MailboxSessionMapperFactory mapperFactory = mailboxManager.getMapperFactory();

        TasksRoutes tasksRoutes = new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of(FeedSpamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE,
            FeedHamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE));
        FeedMessageRoute feedMessageRoute = new FeedMessageRoute(taskManager, mailboxManager, usersRepository, client, jsonTransformer, clock,
            messageIdManager, mapperFactory, rspamdConfiguration);

        webAdminServer = WebAdminUtils.createWebAdminServer(feedMessageRoute, tasksRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(BASE_PATH)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    private void appendMessage(MailboxPath mailboxPath, Date internalDate) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());
        mailboxManager.getMailbox(mailboxPath, session)
            .appendMessage(new ByteArrayInputStream(String.format("random content %4.3f", Math.random()).getBytes()),
                internalDate,
                session,
                true,
                new Flags());
    }

    @Nested
    class FeedSpam {
        @Test
        void taskShouldReportAllSpamMessagesOfAllUsersByDefault() throws MailboxException {
            appendMessage(BOB_SPAM_MAILBOX, Date.from(NOW));
            appendMessage(ALICE_SPAM_MAILBOX, Date.from(NOW));

            String taskId = given()
                .queryParam("action", "reportSpam")
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.type", is(FeedSpamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.spamMessageCount", is(2))
                .body("additionalInformation.reportedSpamMessageCount", is(2))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.rspamdTimeoutInSeconds", is((int) RunningOptions.DEFAULT_RSPAMD_TIMEOUT.toSeconds()))
                .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
                .body("additionalInformation.runningOptions.samplingProbability", is((float) RunningOptions.DEFAULT_SAMPLING_PROBABILITY));
        }

        @Test
        void taskShouldDisplayClassifiedAsSpamRunningOption() throws MailboxException {
            appendMessage(BOB_SPAM_MAILBOX, Date.from(NOW));
            appendMessage(ALICE_SPAM_MAILBOX, Date.from(NOW));

            String taskId = given()
                .queryParam("action", "reportSpam")
                .queryParam("classifiedAsSpam", "false")
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.type", is(FeedSpamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.spamMessageCount", is(2))
                .body("additionalInformation.reportedSpamMessageCount", is(2))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.classifiedAsSpam", is(false))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
                .body("additionalInformation.runningOptions.samplingProbability", is((float) RunningOptions.DEFAULT_SAMPLING_PROBABILITY));
        }

        @Test
        void taskShouldCountAndReportOnlyMailInPeriod() throws MailboxException {
            appendMessage(BOB_SPAM_MAILBOX, Date.from(NOW.minusSeconds(THREE_DAYS_IN_SECOND)));
            appendMessage(ALICE_SPAM_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)));

            String taskId = given()
                .queryParam("action", "reportSpam")
                .queryParam("period", TWO_DAYS_IN_SECOND)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.type", is(FeedSpamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.spamMessageCount", is(1))
                .body("additionalInformation.reportedSpamMessageCount", is(1))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.periodInSecond", is(172800))
                .body("additionalInformation.runningOptions.samplingProbability", is((float) RunningOptions.DEFAULT_SAMPLING_PROBABILITY));
        }

        @Test
        void taskWithAverageSamplingProbabilityShouldNotReportAllSpamMessages() {
            IntStream.range(0, 10)
                .forEach(Throwing.intConsumer(any -> appendMessage(BOB_SPAM_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)))));

            String taskId = given()
                .queryParam("action", "reportSpam")
                .queryParam("samplingProbability", 0.5)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.type", is(FeedSpamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.spamMessageCount", is(10))
                .body("additionalInformation.reportedSpamMessageCount", is(allOf(greaterThan(0), lessThan(10))))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
                .body("additionalInformation.runningOptions.samplingProbability", is(0.5F));
        }

        @Test
        void feedMessageShouldReturnErrorWhenInvalidAction() {
            given()
                .queryParam("action", "invalid")
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'action' is missing or must be 'reportSpam' or 'reportHam'"));
        }

        @Test
        void feedMessageTaskShouldReturnErrorWhenMissingAction() {
            given()
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'action' is missing or must be 'reportSpam' or 'reportHam'"));
        }

        @Test
        void feedSpamShouldReturnTaskId() {
            given()
                .queryParam("action", "reportSpam")
                .post()
            .then()
                .statusCode(HttpStatus.CREATED_201)
                .body("taskId", notNullValue());
        }

        @Test
        void feedSpamShouldReturnDetail() {
            String taskId = given()
                .queryParam("action", "reportSpam")
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("taskId", is(notNullValue()))
                .body("type", is(FeedSpamToRspamdTask.TASK_TYPE.asString()))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()))
                .body("completedDate", is(notNullValue()))
                .body("additionalInformation.type", is(FeedSpamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.timestamp", is(notNullValue()))
                .body("additionalInformation.spamMessageCount", is(0))
                .body("additionalInformation.reportedSpamMessageCount", is(0))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
                .body("additionalInformation.runningOptions.samplingProbability", is((float) RunningOptions.DEFAULT_SAMPLING_PROBABILITY));
        }

        @ParameterizedTest
        @ValueSource(strings = {"3600", "3600 seconds", "1d", "1day"})
        void feedSpamShouldAcceptPeriodParam(String period) {
            String taskId = given()
                .queryParam("action", "reportSpam")
                .queryParam("period", period)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
                .then()
                .body("additionalInformation.runningOptions.periodInSecond", is((int) DurationParser.parse(period, ChronoUnit.SECONDS).toSeconds()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"-1", "0", "1 t"})
        void feedSpamShouldReturnErrorWhenPeriodInvalid(String period) {
            given()
                .queryParam("action", "reportSpam")
                .queryParam("period", period)
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"));
        }

        @Test
        void feedSpamShouldAcceptMessagesPerSecondParam() {
            String taskId = given()
                .queryParam("action", "reportSpam")
                .queryParam("messagesPerSecond", 20)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("additionalInformation.runningOptions.messagesPerSecond", is(20));
        }

        @ParameterizedTest
        @ValueSource(doubles = {-1, -0.1, 1.1})
        void feedSpamShouldReturnErrorWhenMessagesPerSecondInvalid(double messagesPerSecond) {
            given()
                .queryParam("action", "reportSpam")
                .queryParam("messagesPerSecond", messagesPerSecond)
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", containsString("messagesPerSecond"));
        }

        @Test
        void feedSpamShouldAcceptSamplingProbabilityParam() {
            String taskId = given()
                .queryParam("action", "reportSpam")
                .queryParam("samplingProbability", 0.8)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
                .then()
                .body("additionalInformation.runningOptions.samplingProbability", is(0.8F));
        }

        @ParameterizedTest
        @ValueSource(doubles = {-1, -0.1, 1.1})
        void feedSpamShouldReturnErrorWhenSamplingProbabilityInvalid(double samplingProbability) {
            given()
                .queryParam("action", "reportSpam")
                .queryParam("samplingProbability", samplingProbability)
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", containsString("samplingProbability"));
        }
    }

    @Nested
    class FeedHam {
        @Test
        void taskShouldReportAllHamMessagesOfAllUsersByDefault() throws MailboxException {
            appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW));
            appendMessage(ALICE_INBOX_MAILBOX, Date.from(NOW));

            String taskId = given()
                .queryParam("action", "reportHam")
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.type", is(FeedHamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.hamMessageCount", is(2))
                .body("additionalInformation.reportedHamMessageCount", is(2))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.rspamdTimeoutInSeconds", is((int) RunningOptions.DEFAULT_RSPAMD_TIMEOUT.toSeconds()))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
                .body("additionalInformation.runningOptions.samplingProbability", is((float) RunningOptions.DEFAULT_SAMPLING_PROBABILITY));
        }

        @Test
        void taskShouldDisplayClassifiedAsSpamRunningOption() throws MailboxException {
            appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW));
            appendMessage(ALICE_INBOX_MAILBOX, Date.from(NOW));

            String taskId = given()
                .queryParam("action", "reportHam")
                .queryParam("classifiedAsSpam", "true")
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.type", is(FeedHamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.hamMessageCount", is(2))
                .body("additionalInformation.reportedHamMessageCount", is(2))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.classifiedAsSpam", is(true))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
                .body("additionalInformation.runningOptions.samplingProbability", is((float) RunningOptions.DEFAULT_SAMPLING_PROBABILITY));
        }

        @Test
        void taskShouldCountAndReportOnlyMailInPeriod() throws MailboxException {
            appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(THREE_DAYS_IN_SECOND)));
            appendMessage(ALICE_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)));

            String taskId = given()
                .queryParam("action", "reportHam")
                .queryParam("period", TWO_DAYS_IN_SECOND)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.type", is(FeedHamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.hamMessageCount", is(1))
                .body("additionalInformation.reportedHamMessageCount", is(1))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.periodInSecond", is(172800))
                .body("additionalInformation.runningOptions.samplingProbability", is((float) RunningOptions.DEFAULT_SAMPLING_PROBABILITY));
        }

        @Test
        void taskWithAverageSamplingProbabilityShouldNotReportAllHamMessages() {
            IntStream.range(0, 10)
                .forEach(Throwing.intConsumer(any -> appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)))));

            String taskId = given()
                .queryParam("action", "reportHam")
                .queryParam("samplingProbability", 0.5)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.type", is(FeedHamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.hamMessageCount", is(10))
                .body("additionalInformation.reportedHamMessageCount", is(allOf(greaterThan(0), lessThan(10))))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
                .body("additionalInformation.runningOptions.samplingProbability", is(0.5F));
        }

        @Test
        void feedMessageShouldReturnErrorWhenInvalidAction() {
            given()
                .queryParam("action", "invalid")
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'action' is missing or must be 'reportSpam' or 'reportHam'"));
        }

        @Test
        void feedMessageTaskShouldReturnErrorWhenMissingAction() {
            given()
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'action' is missing or must be 'reportSpam' or 'reportHam'"));
        }

        @Test
        void feedHamShouldReturnTaskId() {
            given()
                .queryParam("action", "reportHam")
                .post()
            .then()
                .statusCode(HttpStatus.CREATED_201)
                .body("taskId", notNullValue());
        }

        @Test
        void feedHamShouldReturnDetail() {
            String taskId = given()
                .queryParam("action", "reportHam")
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("taskId", is(notNullValue()))
                .body("type", is(FeedHamToRspamdTask.TASK_TYPE.asString()))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()))
                .body("completedDate", is(notNullValue()))
                .body("additionalInformation.type", is(FeedHamToRspamdTask.TASK_TYPE.asString()))
                .body("additionalInformation.timestamp", is(notNullValue()))
                .body("additionalInformation.hamMessageCount", is(0))
                .body("additionalInformation.reportedHamMessageCount", is(0))
                .body("additionalInformation.errorCount", is(0))
                .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
                .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
                .body("additionalInformation.runningOptions.samplingProbability", is((float) RunningOptions.DEFAULT_SAMPLING_PROBABILITY));
        }

        @ParameterizedTest
        @ValueSource(strings = {"3600", "3600 seconds", "1d", "1day"})
        void feedHamShouldAcceptPeriodParam(String period) {
            String taskId = given()
                .queryParam("action", "reportHam")
                .queryParam("period", period)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("additionalInformation.runningOptions.periodInSecond", is((int) DurationParser.parse(period, ChronoUnit.SECONDS).toSeconds()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"-1", "0", "1 t"})
        void feedHamShouldReturnErrorWhenPeriodInvalid(String period) {
            given()
                .queryParam("action", "reportHam")
                .queryParam("period", period)
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"));
        }

        @Test
        void feedHamShouldAcceptMessagesPerSecondParam() {
            String taskId = given()
                .queryParam("action", "reportHam")
                .queryParam("messagesPerSecond", 20)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
                .then()
                .body("additionalInformation.runningOptions.messagesPerSecond", is(20));
        }

        @ParameterizedTest
        @ValueSource(doubles = {-1, -0.1, 1.1})
        void feedHamShouldReturnErrorWhenMessagesPerSecondInvalid(double messagesPerSecond) {
            given()
                .queryParam("action", "reportHam")
                .queryParam("messagesPerSecond", messagesPerSecond)
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", containsString("messagesPerSecond"));
        }

        @Test
        void feedHamShouldAcceptSamplingProbabilityParam() {
            String taskId = given()
                .queryParam("action", "reportHam")
                .queryParam("samplingProbability", 0.8)
                .post()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("additionalInformation.runningOptions.samplingProbability", is(0.8F));
        }

        @ParameterizedTest
        @ValueSource(doubles = {-1, -0.1, 1.1})
        void feedHamShouldReturnErrorWhenSamplingProbabilityInvalid(double samplingProbability) {
            given()
                .queryParam("action", "reportHam")
                .queryParam("samplingProbability", samplingProbability)
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .body("statusCode", is(BAD_REQUEST_400))
                .body("type", is("InvalidArgument"))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", containsString("samplingProbability"));
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, -0.1, 1.1})
    void routeShouldReturnErrorWhenRspamdTimeoutInvalid(double rspamdTimeout) {
        given()
            .queryParam("action", "reportSpam")
            .queryParam("rspamdTimeout", rspamdTimeout)
            .post()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid arguments supplied in the user request"));
    }

    @Test
    void taskShouldDisplayRspamdTimeoutAsSpamRunningOption() {
        String taskId = given()
            .queryParam("action", "reportSpam")
            .queryParam("rspamdTimeout", 13)
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("additionalInformation.type", is(FeedSpamToRspamdTask.TASK_TYPE.asString()))
            .body("additionalInformation.spamMessageCount", is(0))
            .body("additionalInformation.reportedSpamMessageCount", is(0))
            .body("additionalInformation.errorCount", is(0))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(RunningOptions.DEFAULT_MESSAGES_PER_SECOND))
            .body("additionalInformation.runningOptions.rspamdTimeoutInSeconds", is(13))
            .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
            .body("additionalInformation.runningOptions.samplingProbability", is((float) RunningOptions.DEFAULT_SAMPLING_PROBABILITY));
    }
}
