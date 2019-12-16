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
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.awaitility.Duration.TEN_SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.CassandraRabbitMQAwsS3JmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.EventDeadLettersRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.google.inject.multibindings.Multibinder;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@Category(BasicFeature.class)
public class RabbitMQEventDeadLettersIntegrationTest {
    public static class RetryEventsListenerGroup extends Group {

    }

    public static class RetryEventsListener implements MailboxListener.GroupMailboxListener {
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
        public void event(Event event) throws Exception {
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

    //This value is duplicated from default configuration to ensure we keep the same behavior over time
    //unless we really want to change that default value
    private static final int MAX_RETRIES = 3;

    private static final String DOMAIN = "domain.tld";
    private static final String BOB = "bob@" + DOMAIN;
    private static final String BOB_PASSWORD = "bobPassword";
    private static final String EVENTS_ACTION = "reDeliver";
    private static final String GROUP_ID = new RetryEventsListenerGroup().asString();

    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(Username.of(BOB));

    @Rule
    public DockerCassandraRule cassandra = new DockerCassandraRule();
    @Rule
    public CassandraRabbitMQAwsS3JmapTestRule jamesTestRule = CassandraRabbitMQAwsS3JmapTestRule.defaultTestRule();

    private Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    private ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    private ConditionFactory awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS);
    private RetryEventsListener retryEventsListener;
    private GuiceJamesServer guiceJamesServer;
    private MailboxProbeImpl mailboxProbe;

    @Before
    public void setUp() throws Exception {
        retryEventsListener = new RetryEventsListener();
        guiceJamesServer = jamesTestRule.jmapServer(cassandra.getModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, MailboxListener.GroupMailboxListener.class)
                .addBinding()
                .toInstance(retryEventsListener));
        guiceJamesServer.start();

        DataProbe dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB, BOB_PASSWORD);
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    private MailboxId generateInitialEvent() {
        return mailboxProbe.createMailbox(BOB_INBOX_PATH);
    }

    private void generateSecondEvent() {
        mailboxProbe.createMailbox(MailboxPath.forUser(Username.of(BOB), DefaultMailboxes.OUTBOX));
    }

    private String retrieveFirstFailedInsertionId() {
        calmlyAwait.atMost(TEN_SECONDS)
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
    public void failedEventShouldBeStoredInDeadLetterUnderItsGroupId() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        waitForCalls(MAX_RETRIES + 1);

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(1));
    }

    @Test
    public void successfulEventShouldNotBeStoredInDeadLetter() {
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
    public void groupIdOfFailedEventShouldBeStoredInDeadLetter() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        waitForCalls(MAX_RETRIES + 1);

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", containsInAnyOrder(GROUP_ID));
    }

    @Test
    public void failedEventShouldBeStoredInDeadLetter() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        MailboxId mailboxId = generateInitialEvent();

        waitForCalls(MAX_RETRIES + 1);

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
    public void multipleFailedEventShouldBeStoredInDeadLetter() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        waitForCalls((MAX_RETRIES + 1) * 2);

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(2));
    }

    @Test
    public void failedEventShouldNotBeInDeadLetterAfterBeingDeleted() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        waitForCalls(MAX_RETRIES + 1);

        String failedInsertionId = retrieveFirstFailedInsertionId();

        with()
            .delete(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedInsertionId);

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedInsertionId)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void taskShouldBeCompletedAfterSuccessfulRedelivery() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        waitForCalls(MAX_RETRIES + 1);

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
    public void failedEventShouldNotBeInDeadLettersAfterSuccessfulRedelivery() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        waitForCalls(MAX_RETRIES + 1);

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

    @Category(BasicFeature.class)
    @Test
    public void failedEventShouldBeCorrectlyProcessedByListenerAfterSuccessfulRedelivery() throws InterruptedException {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();

        waitForCalls(MAX_RETRIES + 1);

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

    private void waitForCalls(int count) {
        calmlyAwait.atMost(ONE_MINUTE).until(() -> retryEventsListener.totalCalls.intValue() >= count);
    }

    @Test
    public void taskShouldBeCompletedAfterSuccessfulGroupRedelivery() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        waitForCalls((MAX_RETRIES + 1) * 2);

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
    public void multipleFailedEventsShouldNotBeInDeadLettersAfterSuccessfulGroupRedelivery() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        waitForCalls((MAX_RETRIES + 1) * 2);

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
    public void multipleFailedEventsShouldBeCorrectlyProcessedByListenerAfterSuccessfulGroupRedelivery() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        waitForCalls((MAX_RETRIES + 1) * 2);

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
    public void taskShouldBeCompletedAfterSuccessfulAllRedelivery() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        waitForCalls((MAX_RETRIES + 1) * 2);

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
    public void multipleFailedEventsShouldNotBeInDeadLettersAfterSuccessfulAllRedelivery() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        waitForCalls((MAX_RETRIES + 1) * 2);

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

    @Category(BasicFeature.class)
    @Test
    public void multipleFailedEventsShouldBeCorrectlyProcessedByListenerAfterSuccessfulAllRedelivery() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES + 1);
        generateInitialEvent();
        generateSecondEvent();

        waitForCalls((MAX_RETRIES + 1) * 2);

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

    @Ignore("retry rest API delivers only once, see JAMES-2907. We need same retry cound for this test to work")
    @Test
    public void failedEventShouldStillBeInDeadLettersAfterFailedRedelivery() {
        retryEventsListener.callsPerEventBeforeSuccess(MAX_RETRIES * 2 + 1);
        generateInitialEvent();

        waitForCalls(MAX_RETRIES + 1);

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
}
