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
import static org.apache.james.events.NamingStrategy.MAILBOX_EVENT_BUS_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.core.Username;
import org.apache.james.events.DispatchingFailureGroup;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.EventDeadLettersRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@Tag(BasicFeature.TAG)
@Tag(Unstable.TAG)
@SuppressWarnings("checkstyle:regexpsingleline")
class RabbitMQEventDeadLettersIntegrationTest {
    public static class RetryEventsListenerGroup extends Group {
    }

    public static class RetryEventsListenerGroup2 extends Group {
    }

    public static class RetryEventsListener implements EventListener.GroupEventListener {
        static final Group GROUP = new RetryEventsListenerGroup();

        private final AtomicInteger totalCalls;
        private int callsBeforeSuccess;
        private Map<Event.EventId, Integer> callsByEventId;
        private List<Event> successfulEvents;

        RetryEventsListener() {
            this.callsBeforeSuccess = 0;
            this.callsByEventId = new HashMap<>();
            this.successfulEvents = new ArrayList<>();
            this.totalCalls = new AtomicInteger(0);
        }

        @Override
        public Group getDefaultGroup() {
            return GROUP;
        }

        @Override
        public void event(Event event) {
            totalCalls.incrementAndGet();
            if (done(event)) {
                callsByEventId.remove(event.getEventId());
                successfulEvents.add(event);
            } else {
                increaseRetriesCount(event);
                throw new RuntimeException("throw to trigger retry");
            }
        }

        private void increaseRetriesCount(Event event) {
            callsByEventId.put(event.getEventId(), retriesCount(event) + 1);
        }

        int retriesCount(Event event) {
            return callsByEventId.getOrDefault(event.getEventId(), 0);
        }

        boolean done(Event event) {
            return retriesCount(event) >= callsBeforeSuccess;
        }

        List<Event> getSuccessfulEvents() {
            return successfulEvents;
        }

        void callsPerEventBeforeSuccess(int retriesBeforeSuccess) {
            this.callsBeforeSuccess = retriesBeforeSuccess;
        }
    }

    public static class RetryEventsListener2 extends RetryEventsListener {
        static final Group GROUP = new RetryEventsListenerGroup2();

        @Override
        public Group getDefaultGroup() {
            return GROUP;
        }
    }

    public static class RetryEventsListenerExtension implements GuiceModuleTestExtension {
        private RetryEventsListener retryEventsListener;
        private RetryEventsListener2 retryEventsListener2;

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            retryEventsListener = new RetryEventsListener();
            retryEventsListener2 = new RetryEventsListener2();
        }

        @Override
        public Module getModule() {
            return binder -> {
                Multibinder<EventListener.GroupEventListener> setBinder = Multibinder.newSetBinder(binder, EventListener.GroupEventListener.class);
                setBinder.addBinding().toInstance(retryEventsListener);
                setBinder.addBinding().toInstance(retryEventsListener2);
            };
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            Class<?> paramType = parameterContext.getParameter().getType();
            return paramType == RetryEventsListener.class
                || paramType == RetryEventsListener2.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            Class<?> paramType = parameterContext.getParameter().getType();
            if (paramType == RetryEventsListener.class) {
                return retryEventsListener;
            } else if (paramType == RetryEventsListener2.class) {
                return retryEventsListener2;
            }

            throw new IllegalArgumentException("unsupported type");
        }
    }

    //This value is duplicated from default configuration to ensure we keep the same behavior over time
    //unless we really want to change that default value
    private static final int MAX_RETRIES = 2;

    private static RabbitMQExtension RABBIT_MQ_EXTENSION = new RabbitMQExtension();
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .disableCache()
                .deduplication()
                .noCryptoConfig())
            .searchConfiguration(SearchConfiguration.openSearch())
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(RABBIT_MQ_EXTENSION)
        .extension(new RetryEventsListenerExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(binder -> binder.bind(RetryBackoffConfiguration.class)
                .toInstance(RetryBackoffConfiguration.builder()
                    .maxRetries(MAX_RETRIES)
                    .firstBackoff(java.time.Duration.ofMillis(5))
                    .jitterFactor(0.2)
                    .build())))
        .build();

    private static final String DOMAIN = "domain.tld";
    private static final String BOB = "bob@" + DOMAIN;
    private static final String BOB_PASSWORD = "bobPassword";
    private static final String EVENTS_ACTION = "reDeliver";
    private static final String GROUP_ID = new RetryEventsListenerGroup().asString();
    private static final String DISPATCHING_FAILURE_GROUP_ID = new DispatchingFailureGroup(MAILBOX_EVENT_BUS_NAME).asString();
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(Username.of(BOB));

    private Duration slowPacedPollInterval = Duration.ofMillis(100);
    private ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    private ConditionFactory awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS);
    private MailboxProbeImpl mailboxProbe;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        DataProbe dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB, BOB_PASSWORD);
    }

    private MailboxId generateInitialEvent() {
        return mailboxProbe.createMailbox(BOB_INBOX_PATH);
    }

    private void generateSecondEvent() {
        mailboxProbe.createMailbox(MailboxPath.forUser(Username.of(BOB), DefaultMailboxes.OUTBOX));
    }

    private String retrieveFirstFailedInsertionId() {
        calmlyAwait.atMost(ONE_MINUTE)
            .untilAsserted(() ->
                when()
                    .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
                    .then()
                    .body(".", hasSize(1)));

        return (String) with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
            .jsonPath()
            .getList(".")
            .get(0);
    }

    @Test
    void failedEventShouldBeStoredInDeadLetterUnderItsGroupId(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(1)));

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
            .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(1));
    }

    @Test
    void successfulEventShouldNotBeStoredInDeadLetter(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES - 1);
        generateInitialEvent();

        calmlyAwait.atMost(ONE_MINUTE).until(() -> !retryEventsListener.successfulEvents.isEmpty());

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups")
            .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(0));
    }

    @Test
    void groupIdOfFailedEventShouldBeStoredInDeadLetter(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(1)));

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups")
            .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", containsInAnyOrder(GROUP_ID));
    }

    @Test
    void failedEventShouldBeStoredInDeadLetter(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        MailboxId mailboxId = generateInitialEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(1)));

        String failedInsertionId = retrieveFirstFailedInsertionId();

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedInsertionId)
            .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("MailboxAdded.mailboxId", is(mailboxId.serialize()))
            .body("MailboxAdded.user", is(BOB))
            .body("MailboxAdded.mailboxPath.namespace", is(BOB_INBOX_PATH.getNamespace()))
            .body("MailboxAdded.mailboxPath.user", is(BOB_INBOX_PATH.getUser().asString()))
            .body("MailboxAdded.mailboxPath.name", is(BOB_INBOX_PATH.getName()));
    }

    @Test
    void multipleFailedEventShouldBeStoredInDeadLetter(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(2)));

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
            .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(2));
    }

    @Test
    void failedEventShouldNotBeInDeadLetterAfterBeingDeleted(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(1)));

        String failedInsertionId = retrieveFirstFailedInsertionId();

        with()
            .delete(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedInsertionId);

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedInsertionId)
            .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void taskShouldBeCompletedAfterSuccessfulRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(1)));

        String failedInsertionId = retrieveFirstFailedInsertionId();

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedInsertionId)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("completed"))
            .body("additionalInformation.successfulRedeliveriesCount", is(1))
            .body("additionalInformation.failedRedeliveriesCount", is(0))
            .body("additionalInformation.group", is(GROUP_ID))
            .body("additionalInformation.insertionId", is(failedInsertionId));
    }

    @Test
    void failedEventShouldNotBeInDeadLettersAfterSuccessfulRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(1)));

        String failedInsertionId = retrieveFirstFailedInsertionId();

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedInsertionId)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        when()
            .get("/events/deadLetter/groups/" + GROUP_ID + "/" + failedInsertionId)
            .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void failedEventShouldBeCorrectlyProcessedByListenerAfterSuccessfulRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(1)));

        String failedInsertionId = retrieveFirstFailedInsertionId();

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedInsertionId)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        awaitAtMostTenSeconds.until(() -> retryEventsListener.getSuccessfulEvents().size() == 1);
    }


    @Test
    void taskShouldBeCompletedAfterSuccessfulGroupRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(2)));

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("completed"))
            .body("additionalInformation.successfulRedeliveriesCount", is(2))
            .body("additionalInformation.failedRedeliveriesCount", is(0))
            .body("additionalInformation.group", is(GROUP_ID));
    }

    @Test
    void multipleFailedEventsShouldNotBeInDeadLettersAfterSuccessfulGroupRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(2)));

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
            .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(0));
    }

    @Test
    void multipleFailedEventsShouldBeCorrectlyProcessedByListenerAfterSuccessfulGroupRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(2)));

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        awaitAtMostTenSeconds.until(() -> retryEventsListener.getSuccessfulEvents().size() == 2);
    }

    @Test
    void taskShouldBeCompletedAfterSuccessfulAllRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(2)));

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("completed"))
            .body("additionalInformation.successfulRedeliveriesCount", is(2))
            .body("additionalInformation.failedRedeliveriesCount", is(0));
    }

    @Test
    void multipleFailedEventsShouldNotBeInDeadLettersAfterSuccessfulAllRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(2)));

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
            .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(0));
    }

    @Test
    void multipleFailedEventsShouldBeCorrectlyProcessedByListenerAfterSuccessfulAllRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(2)));

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        awaitAtMostTenSeconds.until(() -> retryEventsListener.getSuccessfulEvents().size() == 2);
    }

    @Disabled("retry rest API delivers only once, see JAMES-2907. We need same retry cound for this test to work")
    @Test
    void failedEventShouldStillBeInDeadLettersAfterFailedRedelivery(RetryEventsListener retryEventsListener) {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES * 2 + 1);
        generateInitialEvent();

        calmlyAwait.atMost(ONE_MINUTE).untilAsserted(() -> with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID).prettyPeek()
            .then()
            .body(".", hasSize(1)));

        String failedInsertionId = retrieveFirstFailedInsertionId();

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedInsertionId)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        String newFailedInsertionId = retrieveFirstFailedInsertionId();

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + newFailedInsertionId)
            .then()
            .statusCode(HttpStatus.OK_200);
    }

    @Test
    void failedDispatchingShouldBeRedeliveredToAllListeners(RetryEventsListener listener1,
                                                            RetryEventsListener2 listener2,
                                                            DockerRabbitMQ dockerRabbitMQ) {
        dockerRabbitMQ.pause();
        try {
            generateInitialEvent();
        } catch (Exception e) {
            // ignore
        }
        dockerRabbitMQ.unpause();

        waitForFailedDispatching();
        waitForReDeliver(DISPATCHING_FAILURE_GROUP_ID);

        awaitAtMostTenSeconds.untilAsserted(() ->
            assertThat(listener1.getSuccessfulEvents())
                .hasSameSizeAs(listener2.getSuccessfulEvents())
                .hasSize(1));
    }

    private void waitForReDeliver(String groupId) {
        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
            .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + groupId)
            .jsonPath()
            .get("taskId");
        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");
    }

    private void waitForFailedDispatching() {
        calmlyAwait.untilAsserted(() ->
            given()
                .basePath(EventDeadLettersRoutes.BASE_PATH + "/groups/" + DISPATCHING_FAILURE_GROUP_ID)
                .get()
                .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(".", hasSize(1)));
    }
}
