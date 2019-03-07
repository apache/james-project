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

package org.apache.james.webadmin.integration;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.james.CassandraJmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
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
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import com.google.inject.multibindings.Multibinder;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

public class EventDeadLettersIntegrationTest {

    public static class RetryEventsListenerGroup extends Group {}

    public class RetryEventsListener implements MailboxListener.GroupMailboxListener {
        final Group GROUP = new RetryEventsListenerGroup();

        private int retriesBeforeSuccess;
        private Map<Event.EventId, Integer> retries;
        private List<Event> successfulEvents;

        private void init() {
            this.retriesBeforeSuccess = 0;
            this.retries = new HashMap<>();
            this.successfulEvents = new ArrayList<>();
        }

        RetryEventsListener() {
            init();
        }

        @Override
        public Group getDefaultGroup() {
            return GROUP;
        }

        @Override
        public void event(Event event) throws Exception {
            int currentRetries = retries.getOrDefault(event.getEventId(), 0);

            if (currentRetries < retriesBeforeSuccess) {
                retries.put(event.getEventId(), currentRetries + 1);
                throw new RuntimeException("throw to trigger retry");
            } else {
                retries.remove(event.getEventId());
                successfulEvents.add(event);
            }
        }

        List<Event> getSuccessfulEvents() {
            return successfulEvents;
        }

        void setRetriesBeforeSuccess(int retriesBeforeSuccess) {
            this.retriesBeforeSuccess = retriesBeforeSuccess;
        }

        void clear() {
            init();
        }
    }

    private static final String DOMAIN = "domain.tld";
    private static final String BOB = "bob@" + DOMAIN;
    private static final String BOB_PASSWORD = "bobPassword";
    private static final String EVENTS_ACTION = "reDeliver";
    private static final String GROUP_ID = new RetryEventsListenerGroup().asString();

    private Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    private ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    private ConditionFactory awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS);
    private RetryEventsListener retryEventsListener = new RetryEventsListener();

    @ClassRule
    public static DockerCassandraRule cassandra = new DockerCassandraRule();

    @Rule
    public CassandraJmapTestRule cassandraJmapTestRule = CassandraJmapTestRule.defaultTestRule();

    private GuiceJamesServer guiceJamesServer;
    private MailboxProbeImpl mailboxProbe;

    @Before
    public void setUp() throws Exception {
        retryEventsListener.clear();
        guiceJamesServer = cassandraJmapTestRule.jmapServer(cassandra.getModule())
            .overrideWith(new WebAdminConfigurationModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, MailboxListener.GroupMailboxListener.class).addBinding().toInstance(retryEventsListener));
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

    private void generateInitialEvent() {
        mailboxProbe.createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.INBOX));
    }

    private void generateSecondEvent() {
        mailboxProbe.createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.OUTBOX));
    }

    private String retrieveFirstFailedEventId() {
        List<String> response = with()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
            .jsonPath()
            .getList(".");

        return response.get(0);
    }

    @Test
    public void failedEventShouldBeStoredInDeadLetterUnderItsGroupId() {
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(1));
    }

    @Test
    public void successfulEventShouldNotBeStoredInDeadLetter() {
        retryEventsListener.setRetriesBeforeSuccess(3);
        generateInitialEvent();

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(0));
    }

    @Test
    public void groupIdOfFailedEventShouldBeStoredInDeadLetter() {
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", containsInAnyOrder(GROUP_ID));
    }

    @Test
    public void failedEventShouldBeStoredInDeadLetter() {
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();

        String failedEventId = retrieveFirstFailedEventId();

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedEventId)
        .then()
            .statusCode(HttpStatus.OK_200);
    }

    @Test
    public void multipleFailedEventShouldBeStoredInDeadLetter() {
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();
        generateSecondEvent();

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(2));
    }

    @Test
    public void failedEventShouldNotBeInDeadLetterAfterBeingDeleted() {
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();

        String failedEventId = retrieveFirstFailedEventId();

        with()
            .delete(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedEventId);

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedEventId)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void taskShouldBeCompletedAfterSuccessfulRedelivery() {
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();

        String failedEventId = retrieveFirstFailedEventId();

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
        .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedEventId)
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
            .body("additionalInformation.eventId", is(failedEventId));
    }

    @Test
    public void failedEventShouldNotBeInDeadLettersAfterSuccessfulRedelivery() {
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();

        String failedEventId = retrieveFirstFailedEventId();

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
        .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedEventId)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        when()
            .get("/events/deadLetter/groups/" + GROUP_ID + "/" + failedEventId)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void failedEventShouldBeCorrectlyProcessedByListenerAfterSuccessfulRedelivery() {
        retryEventsListener.setRetriesBeforeSuccess(5);
        generateInitialEvent();

        String failedEventId = retrieveFirstFailedEventId();

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
        .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedEventId)
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        awaitAtMostTenSeconds.until(() -> retryEventsListener.getSuccessfulEvents().size() == 1);
    }

    @Test
    public void taskShouldBeCompletedAfterSuccessfulGroupRedelivery() {
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();
        generateSecondEvent();

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
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();
        generateSecondEvent();

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
        retryEventsListener.setRetriesBeforeSuccess(5);
        generateInitialEvent();
        generateSecondEvent();

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
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();
        generateSecondEvent();

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
        retryEventsListener.setRetriesBeforeSuccess(4);
        generateInitialEvent();
        generateSecondEvent();

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
    public void multipleFailedEventsShouldBeCorrectlyProcessedByListenerAfterSuccessfulAllRedelivery() {
        retryEventsListener.setRetriesBeforeSuccess(5);
        generateInitialEvent();
        generateSecondEvent();

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

    @Test
    @Ignore("JAMES-2666 not working yet, need to fix the redeliver task")
    public void failedEventShouldStillBeInDeadLettersAfterFailedRedelivery() {
        retryEventsListener.setRetriesBeforeSuccess(8);
        generateInitialEvent();

        String failedEventId = retrieveFirstFailedEventId();

        String taskId = with()
            .queryParam("action", EVENTS_ACTION)
        .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedEventId)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("failed"))
            .body("additionalInformation.successfulRedeliveriesCount", is(0))
            .body("additionalInformation.failedRedeliveriesCount", is(1))
            .body("additionalInformation.group", is(GROUP_ID))
            .body("additionalInformation.eventId", is(failedEventId));

        when()
            .get(EventDeadLettersRoutes.BASE_PATH + "/groups/" + GROUP_ID + "/" + failedEventId)
        .then()
            .statusCode(HttpStatus.OK_200);
    }
}
