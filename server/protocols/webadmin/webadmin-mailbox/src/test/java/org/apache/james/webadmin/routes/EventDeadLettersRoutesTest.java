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
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.apache.james.core.User;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.EventBusTestFixture;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.InVMEventBus;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.MemoryEventDeadLetters;
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.util.EventCollector;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverTask;
import org.apache.james.webadmin.service.EventDeadLettersService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class EventDeadLettersRoutesTest {
    private static final String EVENTS_ACTION = "reDeliver";
    private static final String BOB = "bob@apache.org";
    private static final String UUID_1 = "6e0dd59d-660e-4d9b-b22f-0354479f47b4";
    private static final String UUID_2 = "6e0dd59d-660e-4d9b-b22f-0354479f47b5";
    private static final MailboxListener.MailboxAdded EVENT_1 = EventFactory.mailboxAdded()
        .eventId(Event.EventId.of(UUID_1))
        .user(User.fromUsername(BOB))
        .sessionId(MailboxSession.SessionId.of(452))
        .mailboxId(InMemoryId.of(453))
        .mailboxPath(MailboxPath.forUser(BOB, "Important-mailbox"))
        .build();
    private static final MailboxListener.MailboxAdded EVENT_2 = EventFactory.mailboxAdded()
        .eventId(Event.EventId.of(UUID_2))
        .user(User.fromUsername(BOB))
        .sessionId(MailboxSession.SessionId.of(455))
        .mailboxId(InMemoryId.of(456))
        .mailboxPath(MailboxPath.forUser(BOB, "project-3"))
        .build();
    private static final String JSON_1 = "{" +
        "  \"MailboxAdded\":{" +
        "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "    \"mailboxPath\":{" +
        "      \"namespace\":\"#private\"," +
        "      \"user\":\"bob@apache.org\"," +
        "      \"name\":\"Important-mailbox\"" +
        "     }," +
        "     \"mailboxId\":\"453\"," +
        "     \"user\":\"bob@apache.org\"," +
        "     \"sessionId\":452" +
        "  }" +
        "}";
    private static final String SERIALIZED_GROUP_A = new EventBusTestFixture.GroupA().asString();
    private static final String SERIALIZED_GROUP_B = new EventBusTestFixture.GroupB().asString();

    private WebAdminServer webAdminServer;
    private EventDeadLetters deadLetters;
    private EventBus eventBus;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void beforeEach() throws Exception {
        deadLetters = new MemoryEventDeadLetters();
        JsonTransformer jsonTransformer = new JsonTransformer();
        EventSerializer eventSerializer = new EventSerializer(new InMemoryId.Factory(), new InMemoryMessageId.Factory());
        eventBus = new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory()), RetryBackoffConfiguration.DEFAULT, deadLetters);
        EventDeadLettersService service = new EventDeadLettersService(deadLetters, eventBus, eventSerializer);

        taskManager = new MemoryTaskManager();
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new EventDeadLettersRoutes(service, taskManager, jsonTransformer),
            new TasksRoutes(taskManager, jsonTransformer));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Nested
    class ListGroups {
        @Test
        void getGroupsShouldReturnEmptyWhenNone() {
            when()
                .get("/events/deadLetter/groups")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(".", hasSize(0));
        }

        @Test
        void getGroupsShouldReturnGroupsOfContainedEvents() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();

            when()
                .get("/events/deadLetter/groups")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(".", containsInAnyOrder(EventBusTestFixture.GroupA.class.getName()));
        }

        @Test
        void getGroupsShouldReturnGroupsOfContainedEventsWithoutDuplicates() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_2).block();

            when()
                .get("/events/deadLetter/groups")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(".", containsInAnyOrder(EventBusTestFixture.GroupA.class.getName()));
        }

        @Test
        void getGroupsShouldReturnGroupsOfAllContainedEvents() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();
            deadLetters.store(new EventBusTestFixture.GroupB(), EVENT_2).block();

            when()
                .get("/events/deadLetter/groups")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(".", containsInAnyOrder(EventBusTestFixture.GroupA.class.getName(), EventBusTestFixture.GroupB.class.getName()));
        }
    }

    @Nested
    class ListEvents {
        @Test
        void listEventsShouldFailWhenInvalidGroup() {
            when()
                .get("/events/deadLetter/groups/invalid")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Can not deserialize the supplied group: invalid"));
        }

        @Test
        void listEventsShouldReturnEmptyWhenNone() {
            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(".", hasSize(0));
        }

        @Test
        void listEventsShouldReturnContainedEvents() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(".", containsInAnyOrder(UUID_1));
        }

        @Test
        void listEventsShouldNotReturnEventsOfOtherGroups() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();
            deadLetters.store(new EventBusTestFixture.GroupB(), EVENT_2).block();

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(".", containsInAnyOrder(UUID_1));
        }

        @Test
        void listEventsShouldReturnAllEvents() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_2).block();

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(".", containsInAnyOrder(UUID_1, UUID_2));
        }
    }

    @Nested
    class GetEvent {
        @Test
        void getEventShouldReturnEvent() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();

            String response = when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .asString();

            assertThatJson(response).isEqualTo(JSON_1);
        }

        @Test
        void getEventShouldReturn404WhenNotFound() {
            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void getEventShouldFailWhenInvalidEventId() {
            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/invalid")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Can not deserialize the supplied eventId: invalid"));
        }

        @Test
        void getEventShouldFailWhenInvalidGroup() {
            when()
                .get("/events/deadLetter/groups/invalid/" + UUID_1)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Can not deserialize the supplied group: invalid"));
        }
    }

    @Nested
    class Delete {
        @Test
        void deleteShouldReturnOk() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();

            when()
                .delete("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void deleteShouldReturnOkWhenEventNotFound() {
            when()
                .delete("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void deleteShouldFailWhenInvalidGroup() {
            when()
                .delete("/events/deadLetter/groups/invalid/" + UUID_1)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Can not deserialize the supplied group: invalid"));
        }

        @Test
        void deleteShouldFailWhenInvalidEventId() {
            when()
                .delete("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/invalid")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Can not deserialize the supplied eventId: invalid"));
        }

        @Test
        void deleteShouldRemoveEvent() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();

            with()
                .delete("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1);

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }
    }

    @Nested
    class RedeliverAllEvents {
        private Group groupA;
        private Group groupB;

        @BeforeEach
        void nestedBeforeEach() {
            EventCollector eventCollectorA = new EventCollector();
            EventCollector eventCollectorB = new EventCollector();
            groupA = new EventBusTestFixture.GroupA();
            groupB = new EventBusTestFixture.GroupB();
            eventBus.register(eventCollectorA, groupA);
            eventBus.register(eventCollectorB, groupB);
        }

        @Test
        void postRedeliverAllEventsShouldCreateATask() {
            deadLetters.store(groupA, EVENT_1).block();

            given()
                .queryParam("action", EVENTS_ACTION)
            .when()
                .post("/events/deadLetter")
            .then()
                .statusCode(HttpStatus.CREATED_201)
                .header("Location", is(notNullValue()))
                .body("taskId", is(notNullValue()));
        }

        @Test
        void postRedeliverAllEventsShouldHaveSuccessfulCompletedTask() {
            deadLetters.store(groupA, EVENT_1).block();

            String taskId = with()
                .queryParam("action", EVENTS_ACTION)
                .post("/events/deadLetter")
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("taskId", is(taskId))
                .body("additionalInformation.successfulRedeliveriesCount", is(1))
                .body("additionalInformation.failedRedeliveriesCount", is(0))
                .body("type", is(EventDeadLettersRedeliverTask.TYPE))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()))
                .body("completedDate", is(notNullValue()));
        }

        @Test
        void postRedeliverAllEventsShouldRedeliverAndRemoveEventFromDeadLetters() {
            deadLetters.store(groupA, EVENT_1).block();

            String taskId = with()
                .queryParam("action", EVENTS_ACTION)
                .post("/events/deadLetter")
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
                .when()
                .get(taskId + "/await")
                .then()
                .body("status", is("completed"))
                .body("additionalInformation.successfulRedeliveriesCount", is(1))
                .body("additionalInformation.failedRedeliveriesCount", is(0));

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void postRedeliverAllEventsShouldRedeliverAndRemoveAllEventsFromDeadLetters() {
            deadLetters.store(groupA, EVENT_1).block();
            deadLetters.store(groupA, EVENT_2).block();
            deadLetters.store(groupB, EVENT_2).block();

            String taskId = with()
                .queryParam("action", EVENTS_ACTION)
                .post("/events/deadLetter")
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.successfulRedeliveriesCount", is(3))
                .body("additionalInformation.failedRedeliveriesCount", is(0));

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_2)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_B + "/" + UUID_2)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void postRedeliverAllEventsShouldFailWhenInvalidAction() {
            deadLetters.store(groupA, EVENT_1).block();

            given()
                .queryParam("action", "invalid-action")
            .when()
                .post("/events/deadLetter")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'invalid-action' is not a valid action query parameter"));
        }

        @Test
        void postRedeliverAllEventsShouldFailWhenMissingAction() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();

            when()
                .post("/events/deadLetter")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'action' url parameter is mandatory"));
        }
    }

    @Nested
    class RedeliverGroupEvents {
        private Group groupA;

        @BeforeEach
        void nestedBeforeEach() {
            EventCollector eventCollector = new EventCollector();
            groupA = new EventBusTestFixture.GroupA();
            eventBus.register(eventCollector, groupA);
        }

        @Test
        void postRedeliverGroupEventsShouldCreateATask() {
            deadLetters.store(groupA, EVENT_1).block();

            given()
                .queryParam("action", EVENTS_ACTION)
            .when()
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
            .then()
                .statusCode(HttpStatus.CREATED_201)
                .header("Location", is(notNullValue()))
                .body("taskId", is(notNullValue()));
        }

        @Test
        void postRedeliverGroupEventsShouldHaveSuccessfulCompletedTask() {
            deadLetters.store(groupA, EVENT_1).block();

            String taskId = with()
                .queryParam("action", EVENTS_ACTION)
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("taskId", is(taskId))
                .body("additionalInformation.successfulRedeliveriesCount", is(1))
                .body("additionalInformation.failedRedeliveriesCount", is(0))
                .body("type", is(EventDeadLettersRedeliverTask.TYPE))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()))
                .body("completedDate", is(notNullValue()));
        }

        @Test
        void postRedeliverGroupEventsShouldRedeliverAndRemoveEventFromDeadLetters() {
            deadLetters.store(groupA, EVENT_1).block();

            String taskId = with()
                .queryParam("action", EVENTS_ACTION)
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.successfulRedeliveriesCount", is(1))
                .body("additionalInformation.failedRedeliveriesCount", is(0));

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void postRedeliverGroupEventsShouldRedeliverAndRemoveAllGroupEventsFromDeadLetters() {
            deadLetters.store(groupA, EVENT_1).block();
            deadLetters.store(groupA, EVENT_2).block();

            String taskId = with()
                .queryParam("action", EVENTS_ACTION)
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
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

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_2)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void postRedeliverGroupEventsShouldFailWhenInvalidAction() {
            deadLetters.store(groupA, EVENT_1).block();

            given()
                .queryParam("action", "invalid-action")
            .when()
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'invalid-action' is not a valid action query parameter"));
        }

        @Test
        void postRedeliverGroupEventsShouldFailWhenMissingAction() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();

            when()
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'action' url parameter is mandatory"));
        }

        @Test
        void postRedeliverGroupEventsShouldFailWhenInvalidGroup() {
            given()
                .queryParam("action", EVENTS_ACTION)
            .when()
                .post("/events/deadLetter/groups/invalid")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Can not deserialize the supplied group: invalid"));
        }
    }

    @Nested
    class RedeliverSingleEvent {
        private Group groupA;

        @BeforeEach
        void nestedBeforeEach() {
            EventCollector eventCollector = new EventCollector();
            groupA = new EventBusTestFixture.GroupA();
            eventBus.register(eventCollector, groupA);
        }

        @Test
        void postRedeliverSingleEventShouldCreateATask() {
            deadLetters.store(groupA, EVENT_1).block();

            given()
                .queryParam("action", EVENTS_ACTION)
            .when()
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.CREATED_201)
                .header("Location", is(notNullValue()))
                .body("taskId", is(notNullValue()));
        }

        @Test
        void postRedeliverSingleEventShouldHaveSuccessfulCompletedTask() {
            deadLetters.store(groupA, EVENT_1).block();

            String taskId = with()
                .queryParam("action", EVENTS_ACTION)
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("taskId", is(taskId))
                .body("additionalInformation.successfulRedeliveriesCount", is(1))
                .body("additionalInformation.failedRedeliveriesCount", is(0))
                .body("type", is(EventDeadLettersRedeliverTask.TYPE))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()))
                .body("completedDate", is(notNullValue()));
        }

        @Test
        void postRedeliverSingleEventShouldRedeliverAndRemoveEventFromDeadLetters() {
            deadLetters.store(groupA, EVENT_1).block();

            String taskId = with()
                .queryParam("action", EVENTS_ACTION)
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("additionalInformation.successfulRedeliveriesCount", is(1))
                .body("additionalInformation.failedRedeliveriesCount", is(0));

            when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void postRedeliverSingleEventShouldReturn404WhenEventNotFound() {
            given()
                .queryParam("action", EVENTS_ACTION)
            .when()
                .get("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void postRedeliverSingleEventShouldFailWhenInvalidAction() {
            deadLetters.store(groupA, EVENT_1).block();

            given()
                .queryParam("action", "invalid-action")
            .when()
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'invalid-action' is not a valid action query parameter"));
        }

        @Test
        void postRedeliverSingleEventShouldFailWhenMissingAction() {
            deadLetters.store(new EventBusTestFixture.GroupA(), EVENT_1).block();

            when()
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/" + UUID_1)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Invalid arguments supplied in the user request"))
                .body("details", is("'action' url parameter is mandatory"));
        }

        @Test
        void postRedeliverSingleEventShouldFailWhenInvalidEventId() {
            given()
                .queryParam("action", EVENTS_ACTION)
            .when()
                .post("/events/deadLetter/groups/" + SERIALIZED_GROUP_A + "/invalid")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Can not deserialize the supplied eventId: invalid"));
        }

        @Test
        void postRedeliverSingleEventShouldFailWhenInvalidGroup() {
            given()
                .queryParam("action", EVENTS_ACTION)
            .when()
                .post("/events/deadLetter/groups/invalid/" + UUID_1)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .body("statusCode", is(400))
                .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", is("Can not deserialize the supplied group: invalid"));
        }
    }
}