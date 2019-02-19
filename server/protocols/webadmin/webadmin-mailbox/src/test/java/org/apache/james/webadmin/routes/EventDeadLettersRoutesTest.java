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

import static io.restassured.RestAssured.when;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventBusTestFixture;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.MemoryEventDeadLetters;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class EventDeadLettersRoutesTest {
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

    private WebAdminServer webAdminServer;
    private EventDeadLetters deadLetters;

    @BeforeEach
    void beforeEach() throws Exception {
        deadLetters = new MemoryEventDeadLetters();
        JsonTransformer jsonTransformer = new JsonTransformer();

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new EventDeadLettersRoutes(
                deadLetters,
                jsonTransformer));
        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
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
                .body(".", contains(EventBusTestFixture.GroupA.class.getName()));
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
                .body(".", contains(EventBusTestFixture.GroupA.class.getName()));
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
}