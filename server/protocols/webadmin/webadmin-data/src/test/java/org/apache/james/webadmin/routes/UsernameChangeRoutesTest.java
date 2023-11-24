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
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.json.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.UsernameChangeService;
import org.apache.james.webadmin.service.UsernameChangeTaskAdditionalInformationDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.core.publisher.Mono;

class UsernameChangeRoutesTest {

    private static final Username OLD_USER = Username.of("jessy.jones@domain.tld");
    private static final Username NEW_USER = Username.of("jessy.smith@domain.tld");

    public static class StepImpl implements UsernameChangeTaskStep {
        private final StepName name;
        private final int priority;
        private final Mono<Void> behaviour;

        public StepImpl(StepName name, int priority, Mono<Void> behaviour) {
            this.name = name;
            this.priority = priority;
            this.behaviour = behaviour;
        }

        @Override
        public StepName name() {
            return name;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
            return behaviour;
        }
    }

    private MemoryUsersRepository usersRepository;

    WebAdminServer setUp(ImmutableSet<UsernameChangeTaskStep> steps) {
        MemoryTaskManager taskManager = new MemoryTaskManager(new Hostname("foo"));
        UsernameChangeService service = new UsernameChangeService(steps);
        WebAdminServer webAdminServer = WebAdminUtils
            .createWebAdminServer(new UsernameChangeRoutes(usersRepository, service, taskManager, new JsonTransformer()),
                new TasksRoutes(taskManager, new JsonTransformer(), DTOConverter.of(UsernameChangeTaskAdditionalInformationDTO.module())))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .build();

        return webAdminServer;
    }

    @BeforeEach
    void setUpUsersRepo() throws Exception {
        MemoryDomainList domainList = new MemoryDomainList();
        domainList.addDomain(Domain.of("domain.tld"));
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
    }

    @Nested
    class BasicTests {
        private WebAdminServer webAdminServer;
        private AtomicBoolean behaviour1;
        private AtomicBoolean behaviour2;

        @BeforeEach
        void setUp() throws Exception {
            behaviour1 = new AtomicBoolean(false);
            behaviour2 = new AtomicBoolean(false);
            webAdminServer = UsernameChangeRoutesTest.this.setUp(
                ImmutableSet.of(new StepImpl(new UsernameChangeTaskStep.StepName("A"), 35, Mono.fromRunnable(() -> behaviour1.set(true))),
                    new StepImpl(new UsernameChangeTaskStep.StepName("B"), 3, Mono.fromRunnable(() -> behaviour2.set(true)))));

            usersRepository.addUser(OLD_USER, "pass");
            usersRepository.addUser(NEW_USER, "pass");
        }

        @AfterEach
        void stop() {
            webAdminServer.destroy();
        }

        @Test
        void shouldPerformMigration() {
            String taskId = with()
                .queryParam("action", "rename")
                .post("/users/" + OLD_USER.asString() + "/rename/" + NEW_USER.asString())
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("type", is("UsernameChangeTask"))
                .body("status", is("completed"))
                .body("additionalInformation.type", is("UsernameChangeTask"))
                .body("additionalInformation.oldUser", is("jessy.jones@domain.tld"))
                .body("additionalInformation.newUser", is("jessy.smith@domain.tld"))
                .body("additionalInformation.status.A", is("DONE"))
                .body("additionalInformation.status.B", is("DONE"));

            assertThat(behaviour1.get()).isTrue();
            assertThat(behaviour2.get()).isTrue();
        }

        @Test
        void shouldRejectUnknownDestinationUser() {
            given()
                .queryParam("action", "rename")
            .when()
                .post("/users/" + OLD_USER.asString() + "/rename/unknown@domain.tld")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", Matchers.is(400))
                .body("type", Matchers.is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", Matchers.is("Invalid arguments supplied in the user request"))
                .body("details", Matchers.is("'newUser' parameter should be an existing user"));
        }

        @Test
        void shouldRejectUnknownSourceUser() {
            given()
                .queryParam("action", "rename")
            .when()
                .post("/users/unknown@domain.tld/rename/" + NEW_USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", Matchers.is(400))
                .body("type", Matchers.is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", Matchers.is("Invalid arguments supplied in the user request"))
                .body("details", Matchers.is("'oldUser' parameter should be an existing user"));
        }
    }

    @Nested
    class ErrorTests {
        private WebAdminServer webAdminServer;
        private AtomicBoolean behaviour1;
        private AtomicBoolean behaviour2;

        @BeforeEach
        void setUp() throws Exception {
            behaviour1 = new AtomicBoolean(false);
            behaviour2 = new AtomicBoolean(false);
            webAdminServer = UsernameChangeRoutesTest.this.setUp(
                ImmutableSet.of(new StepImpl(new UsernameChangeTaskStep.StepName("A"), 1, Mono.fromRunnable(() -> behaviour1.set(true))),
                    new StepImpl(new UsernameChangeTaskStep.StepName("B"), 2, Mono.error(RuntimeException::new)),
                    new StepImpl(new UsernameChangeTaskStep.StepName("C"), 3, Mono.fromRunnable(() -> behaviour2.set(true)))));

            usersRepository.addUser(OLD_USER, "pass");
            usersRepository.addUser(NEW_USER, "pass");
        }

        @AfterEach
        void stop() {
            webAdminServer.destroy();
        }

        @Test
        void shouldReportFailures() {
            String taskId = with()
                .queryParam("action", "rename")
                .post("/users/" + OLD_USER.asString() + "/rename/" + NEW_USER.asString())
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await").prettyPeek()
            .then()
                .body("type", is("UsernameChangeTask"))
                .body("status", is("failed"))
                .body("additionalInformation.type", is("UsernameChangeTask"))
                .body("additionalInformation.oldUser", is("jessy.jones@domain.tld"))
                .body("additionalInformation.newUser", is("jessy.smith@domain.tld"))
                .body("additionalInformation.status.A", is("DONE"))
                .body("additionalInformation.status.B", is("FAILED"))
                .body("additionalInformation.status.C", is("ABORTED"));

            assertThat(behaviour1.get()).isTrue();
            assertThat(behaviour2.get()).isFalse();
        }

        @Test
        void shouldSupportSkipWhenFailure() {
            String taskId = with()
                .queryParam("action", "rename")
                .queryParam("fromStep", "B")
                .post("/users/" + OLD_USER.asString() + "/rename/" + NEW_USER.asString())
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("type", is("UsernameChangeTask"))
                .body("status", is("failed"))
                .body("additionalInformation.type", is("UsernameChangeTask"))
                .body("additionalInformation.oldUser", is("jessy.jones@domain.tld"))
                .body("additionalInformation.newUser", is("jessy.smith@domain.tld"))
                .body("additionalInformation.status.A", is("SKIPPED"))
                .body("additionalInformation.status.B", is("FAILED"))
                .body("additionalInformation.status.C", is("ABORTED"));

            assertThat(behaviour1.get()).isFalse();
            assertThat(behaviour2.get()).isFalse();
        }

        @Test
        void shouldRejectInvalidFromStep() {
            given()
                .queryParam("action", "rename")
                .queryParam("fromStep", "invalid")
            .when()
                .post("/users/" + OLD_USER.asString() + "/rename/" + NEW_USER.asString())
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .body("statusCode", Matchers.is(400))
                .body("type", Matchers.is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
                .body("message", Matchers.is("Invalid arguments supplied in the user request"))
                .body("details", Matchers.is("Starting step not found: invalid"));
        }
    }
}