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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.apache.james.json.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryReferenceWithCounterTask;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskType;
import org.apache.james.task.eventsourcing.Created;
import org.apache.james.task.eventsourcing.MemoryTaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.TaskAggregateId;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.services.TasksCleanupService;
import org.apache.james.webadmin.tasks.TasksCleanupTaskAdditionalInformationDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

public class TasksCleanupRoutesTest {

    private static final TaskExecutionDetails TASK_EXECUTION_DETAILS = new TaskExecutionDetails(TaskId.fromString("2c7f4081-aa30-11e9-bf6c-2d3b9e84aafd"),
        TaskType.of("type"),
        TaskManager.Status.COMPLETED,
        ZonedDateTime.now().minus(20, ChronoUnit.DAYS),
        new Hostname("foo"),
        Optional::empty,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private InMemoryEventStore eventStore;
    private MemoryTaskExecutionDetailsProjection taskExecutionDetailsProjection;


    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemDefaultZone();

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        JsonTransformer jsonTransformer = new JsonTransformer();
        eventStore = new InMemoryEventStore();
        taskExecutionDetailsProjection = new MemoryTaskExecutionDetailsProjection();
        TasksCleanupService tasksCleanupService = new TasksCleanupService(taskExecutionDetailsProjection, eventStore);

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new TasksCleanupRoutes(taskManager, clock, tasksCleanupService, jsonTransformer),
                new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of(TasksCleanupTaskAdditionalInformationDTO.module())))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("/tasks")
            .build();
    }

    @AfterEach
    void afterEach() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void olderThanRequestParameterShouldBeCompulsory() {
        when()
            .delete()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("missing or invalid `olderThan` parameter"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n", "1b", "oneHour", ""})
    void olderThanRequestParameterShouldBeValid(String olderThan) {
        given()
            .queryParam("olderThan", olderThan)
            .delete()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"));
    }

    @Test
    void validRequestShouldCreateANewTask() {
        given()
            .queryParam("olderThan", "15day")
            .delete()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void tasksCleanupShouldCompleteWhenEmptyEntry() {
        String taskId = given()
            .queryParam("olderThan", "15day")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("tasks-cleanup"))
            .body("additionalInformation.removedTaskCount", is(0))
            .body("additionalInformation.processedTaskCount", is(0))
            .body("additionalInformation.olderThan", is(notNullValue()))
            .body("additionalInformation.timestamp", is(notNullValue()))
            .body("additionalInformation.type", is("tasks-cleanup"))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void tasksCleanupShouldRemoveOldTaskData() {
        taskExecutionDetailsProjection.update(TASK_EXECUTION_DETAILS);
        TaskAggregateId taskAggregateId = new TaskAggregateId(TASK_EXECUTION_DETAILS.taskId());
        Created event = new Created(taskAggregateId, EventId.first(), new MemoryReferenceWithCounterTask((counter) -> Task.Result.COMPLETED), new Hostname("foo"));
        Mono.from(eventStore.append(event)).block();

        String taskId = given()
            .queryParam("olderThan", "15day")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("tasks-cleanup"))
            .body("additionalInformation.removedTaskCount", is(1))
            .body("additionalInformation.processedTaskCount", is(1))
            .body("additionalInformation.olderThan", is(notNullValue()))
            .body("additionalInformation.timestamp", is(notNullValue()))
            .body("additionalInformation.type", is("tasks-cleanup"))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));

        assertThat(Mono.from(eventStore.getEventsOfAggregate(taskAggregateId)).block())
            .isEqualTo(History.empty());
        assertThat(taskExecutionDetailsProjection.list().size())
            .isEqualTo(0);
    }

    @Test
    void tasksCleanupShouldRemoveOldTaskDataWhenHaveSeveralEntries() {
        taskExecutionDetailsProjection.update(TASK_EXECUTION_DETAILS);
        Created event = new Created(new TaskAggregateId(TASK_EXECUTION_DETAILS.taskId()), EventId.first(), new MemoryReferenceWithCounterTask((counter) -> Task.Result.COMPLETED), new Hostname("foo"));
        Mono.from(eventStore.append(event)).block();

        TaskExecutionDetails taskExecutionDetails2 = new TaskExecutionDetails(TaskId.generateTaskId(),
            TaskType.of("type"),
            TaskManager.Status.COMPLETED,
            ZonedDateTime.now().minus(20, ChronoUnit.DAYS),
            new Hostname("foo"),
            Optional::empty,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        taskExecutionDetailsProjection.update(taskExecutionDetails2);
        Created event2 = new Created(new TaskAggregateId(taskExecutionDetails2.taskId()), EventId.first(), new MemoryReferenceWithCounterTask((counter) -> Task.Result.COMPLETED), new Hostname("foo"));
        Mono.from(eventStore.append(event2)).block();

        String taskId = given()
            .queryParam("olderThan", "15day")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("tasks-cleanup"))
            .body("additionalInformation.removedTaskCount", is(2))
            .body("additionalInformation.processedTaskCount", is(2))
            .body("additionalInformation.olderThan", is(notNullValue()))
            .body("additionalInformation.timestamp", is(notNullValue()))
            .body("additionalInformation.type", is("tasks-cleanup"))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void tasksCleanupShouldRemoveOnlyOldData() {
        taskExecutionDetailsProjection.update(TASK_EXECUTION_DETAILS);
        TaskAggregateId taskAggregateId = new TaskAggregateId(TASK_EXECUTION_DETAILS.taskId());
        Created event = new Created(taskAggregateId, EventId.first(), new MemoryReferenceWithCounterTask((counter) -> Task.Result.COMPLETED), new Hostname("foo"));
        Mono.from(eventStore.append(event)).block();

        TaskId tasksId2 = TaskId.generateTaskId();
        TaskExecutionDetails taskExecutionDetails2 = new TaskExecutionDetails(tasksId2,
            TaskType.of("type"),
            TaskManager.Status.COMPLETED,
            ZonedDateTime.now(),
            new Hostname("foo"),
            Optional::empty,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        taskExecutionDetailsProjection.update(taskExecutionDetails2);
        TaskAggregateId taskAggregateId2 = new TaskAggregateId(taskExecutionDetails2.taskId());
        Created event2 = new Created(taskAggregateId2, EventId.first(), new MemoryReferenceWithCounterTask((counter) -> Task.Result.COMPLETED), new Hostname("foo"));
        Mono.from(eventStore.append(event2)).block();

        String taskId = given()
            .queryParam("olderThan", "15day")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("tasks-cleanup"))
            .body("additionalInformation.removedTaskCount", is(1))
            .body("additionalInformation.processedTaskCount", is(1))
            .body("additionalInformation.olderThan", is(notNullValue()))
            .body("additionalInformation.timestamp", is(notNullValue()))
            .body("additionalInformation.type", is("tasks-cleanup"))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));

        assertThat(Mono.from(eventStore.getEventsOfAggregate(taskAggregateId)).block())
            .isEqualTo(History.empty());

        assertThat(Mono.from(eventStore.getEventsOfAggregate(taskAggregateId2)).block())
            .isEqualTo(History.of(event2));

        assertThat(CollectionConverters.asJava(taskExecutionDetailsProjection.list()
            .map(TaskExecutionDetails::getTaskId)))
            .containsOnly(tasksId2);
    }

    @ParameterizedTest
    @MethodSource(value = "inProgressStatus")
    void tasksCleanupShouldNotRemoveInProgressTask(TaskManager.Status status) {
        TaskExecutionDetails taskExecutionDetail = new TaskExecutionDetails(TaskId.generateTaskId(),
            TaskType.of("type"),
            status,
            ZonedDateTime.now(),
            new Hostname("foo"),
            Optional::empty,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

        taskExecutionDetailsProjection.update(taskExecutionDetail);
        TaskAggregateId taskAggregateId = new TaskAggregateId(taskExecutionDetail.taskId());
        Created event = new Created(taskAggregateId, EventId.first(), new MemoryReferenceWithCounterTask((counter) -> Task.Result.COMPLETED), new Hostname("foo"));
        Mono.from(eventStore.append(event)).block();

        String taskId = given()
            .queryParam("olderThan", "15day")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("tasks-cleanup"))
            .body("additionalInformation.removedTaskCount", is(0))
            .body("additionalInformation.processedTaskCount", is(0))
            .body("additionalInformation.olderThan", is(notNullValue()))
            .body("additionalInformation.timestamp", is(notNullValue()))
            .body("additionalInformation.type", is("tasks-cleanup"))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));

        assertThat(Mono.from(eventStore.getEventsOfAggregate(taskAggregateId)).block())
            .isEqualTo(History.of(event));
        assertThat(taskExecutionDetailsProjection.list().size())
            .isEqualTo(1);
    }

    static Stream<Arguments> inProgressStatus() {
        return Stream.of(
            Arguments.of(TaskManager.Status.IN_PROGRESS),
            Arguments.of(TaskManager.Status.WAITING)
        );
    }
}
