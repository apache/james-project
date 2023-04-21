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
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Comparator.CONTAINS;
import static org.apache.james.jmap.api.filtering.Rule.Condition.Field.SUBJECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.ReactiveSubscriber;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.apache.james.jmap.api.filtering.impl.RuleSetDefined;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;
import spark.Service;

class PopulateFilteringProjectionRequestToTaskTest {
    private static final String UNSCRAMBLED_SUBJECT = "this is the subject Frédéric MARTIN of the mail";

    private EventSourcingFilteringManagement.NoReadProjection noReadProjection;
    private EventSourcingFilteringManagement.ReadProjection readProjection;

    private static final class JMAPRoutes implements Routes {
        private final TaskManager taskManager;
        private final EventSourcingFilteringManagement.NoReadProjection noReadProjection;
        private final EventSourcingFilteringManagement.ReadProjection readProjection;
        private final UsersRepository usersRepository;

        private JMAPRoutes(EventSourcingFilteringManagement.NoReadProjection noReadProjection,
                           EventSourcingFilteringManagement.ReadProjection readProjection,
                           UsersRepository usersRepository,
                           TaskManager taskManager) {
            this.noReadProjection = noReadProjection;
            this.readProjection = readProjection;
            this.usersRepository = usersRepository;
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
                    .registrations(new PopulateFilteringProjectionRequestToTask(noReadProjection, readProjection, usersRepository))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    static final String BASE_PATH = "/mailboxes";

    static final DomainList NO_DOMAIN_LIST = null;
    static final Username BOB = Username.of("bob");

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private MailboxId bobInboxboxId;

    @BeforeEach
    void setUp() throws Exception {
        JsonTransformer jsonTransformer = new JsonTransformer();
        taskManager = new MemoryTaskManager(new Hostname("foo"));

        InMemoryMailboxManager mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        usersRepository.addUser(BOB, "pass");
        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        bobInboxboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession)
            .get();

        noReadProjection = mock(EventSourcingFilteringManagement.NoReadProjection.class);
        readProjection = mock(EventSourcingFilteringManagement.ReadProjection.class);
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer,
                DTOConverter.of(PopulateFilteringProjectionTaskAdditionalInformationDTO.module())),
            new JMAPRoutes(
                noReadProjection,
                readProjection,
                usersRepository,
                taskManager))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("/mailboxes")
            .build();
    }

    @AfterEach
    void afterEach() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void actionRequestParameterShouldBeCompulsory() {
        when()
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter is compulsory. Supported values are [populateFilteringProjection]"));
    }

    @Test
    void postShouldFailUponEmptyAction() {
        given()
            .queryParam("action", "")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter cannot be empty or blank. Supported values are [populateFilteringProjection]"));
    }

    @Test
    void postShouldFailUponInvalidAction() {
        given()
            .queryParam("action", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [populateFilteringProjection]"));
    }

    @Test
    void postShouldCreateANewTask() {
        given()
            .queryParam("action", "populateFilteringProjection")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void populateShouldUpdateProjection() {
        Rule rule = Rule.builder()
            .id(Rule.Id.of("2"))
            .name("rule 2")
            .condition(Rule.Condition.of(SUBJECT, CONTAINS, UNSCRAMBLED_SUBJECT))
            .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(ImmutableList.of(bobInboxboxId.serialize()))))
            .build();

        Mockito.when(noReadProjection.listRulesForUser(any()))
            .thenReturn(Mono.just(new Rules(ImmutableList.of(rule), new Version(4))));
        ReactiveSubscriber subscriber = mock(ReactiveSubscriber.class);
        Mockito.when(readProjection.subscriber(any())).thenReturn(Optional.of(subscriber));
        Mockito.when(subscriber.handleReactive(any())).thenReturn(Mono.empty());

        String taskId = with()
            .queryParam("action", "populateFilteringProjection")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        ArgumentCaptor<EventWithState> captor = ArgumentCaptor.forClass(EventWithState.class);
        verify(subscriber, times(1)).handleReactive(captor.capture());

        assertThat(captor.getValue().event().eventId()).isEqualTo(EventId.fromSerialized(4));
        assertThat(captor.getValue().event().getAggregateId()).isEqualTo(new FilteringAggregateId(BOB));
        assertThat(captor.getValue().event()).isInstanceOf(RuleSetDefined.class);
        RuleSetDefined ruleSetDefined = (RuleSetDefined) captor.getValue().event();
        assertThat(ruleSetDefined.getRules()).containsOnly(rule);
    }
}