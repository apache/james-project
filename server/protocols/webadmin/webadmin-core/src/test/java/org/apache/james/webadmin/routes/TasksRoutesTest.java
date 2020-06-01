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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.apache.james.json.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryReferenceTask;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

class TasksRoutesTest {
    private static final String HOSTNAME = "foo";

    private MemoryTaskManager taskManager;
    private WebAdminServer webAdminServer;
    private CountDownLatch waitingForResultLatch;

    @BeforeEach
    void setUp() {
        taskManager = new MemoryTaskManager(new Hostname(HOSTNAME));

        webAdminServer = WebAdminUtils.createWebAdminServer(new TasksRoutes(taskManager, new JsonTransformer(),
            DTOConverter.of()))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(TasksRoutes.BASE)
            .build();

        waitingForResultLatch = new CountDownLatch(1);
    }

    @AfterEach
    void tearDown() {
        waitingForResultLatch.countDown();
        taskManager.stop();
        webAdminServer.destroy();
    }

    @Test
    void listShouldReturnEmptyWhenNoTask() {
        when()
            .get()
        .then()
            .body("", hasSize(0));
    }

    @Test
    void listShouldReturnTaskDetailsWhenTaskInProgress() throws Exception {
        CountDownLatch taskInProgressLatch = new CountDownLatch(1);
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            taskInProgressLatch.countDown();
            waitForResult();
            return Task.Result.COMPLETED;
        }));

        taskInProgressLatch.await();

        when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("[0].status", is(TaskManager.Status.IN_PROGRESS.getValue()))
            .body("[0].taskId", is(taskId.asString()))
            .body("[0].class", is(not(empty())))
            .body("[0].submittedFrom", is(HOSTNAME))
            .body("[0].executedOn", is(HOSTNAME))
            .body("[0].cancelledFrom", nullValue());
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void waitForResult() {
        await(waitingForResultLatch);
    }

    @Test
    void listShouldListTaskWhenStatusFilter() throws Exception {
        CountDownLatch inProgressLatch = new CountDownLatch(1);
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            inProgressLatch.countDown();
            waitForResult();
            return Task.Result.COMPLETED;
        }));

        inProgressLatch.await();

        given()
            .param("status", TaskManager.Status.IN_PROGRESS.getValue())
        .when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("[0].status", is(TaskManager.Status.IN_PROGRESS.getValue()))
            .body("[0].taskId", is(taskId.asString()))
            .body("[0].type", is(MemoryReferenceTask.TYPE.asString()));
    }

    @Test
    void listShouldReturnEmptyWhenNonMatchingStatusFilter() throws Exception {
        CountDownLatch inProgressLatch = new CountDownLatch(1);
        taskManager.submit(new MemoryReferenceTask(() -> {
            inProgressLatch.countDown();
            waitForResult();
            return Task.Result.COMPLETED;
        }));

        inProgressLatch.await();

        given()
            .param("status", TaskManager.Status.WAITING.getValue())
        .when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    void getShouldReturnTaskDetails() throws Exception {
        CountDownLatch inProgressLatch = new CountDownLatch(1);
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            inProgressLatch.countDown();
            waitForResult();
            return Task.Result.COMPLETED;
        }));

        inProgressLatch.await();

        when()
            .get("/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("inProgress"));
    }

    @Test
    void getAwaitShouldAwaitTaskCompletion() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Task.Result.COMPLETED;
        }));

        when()
            .get("/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("completed"));
    }

    @Test
    void getAwaitWithTimeoutShouldAwaitTaskCompletion() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Task.Result.COMPLETED;
        }));

        given()
            .queryParam("timeout", "2s")
        .when()
            .get("/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("completed"));
    }

    @Test
    void getAwaitWithInvalidTimeoutShouldReturnAnError() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> Task.Result.COMPLETED));

        given()
            .queryParam("timeout", "-5")
        .when()
            .get("/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("Invalid timeout"))
            .body("details", containsString("Duration amount should be positive"));
    }

    @Test
    void getAwaitWithATooBigTimeoutShouldReturnAnError() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> Task.Result.COMPLETED));

        given()
            .queryParam("timeout", "5y")
        .when()
            .get("/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("Invalid timeout"))
            .body("details", containsString("Timeout should not exceed 365 days"));
    }

    @Test
    void getAwaitWithAShorterTimeoutShouldReturnTimeout() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Task.Result.COMPLETED;
        }));

        given()
            .queryParam("timeout", "1")
        .when()
            .get("/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.REQUEST_TIMEOUT_408)
            .body("message", is("The timeout has been reached"));
    }

    @Test
    void getAwaitWithANonExistingTaskShouldReturnNotFound() {
        String unknownTaskId = TaskId.generateTaskId().asString();
        when()
            .get("/" + unknownTaskId + "/await")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("message", is("The taskId is not found"));
    }

    @Test
    void getAwaitShouldNotFailUponError() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            throw new RuntimeException();
        }));

        when()
            .get("/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("failed"));
    }

    @Test
    void getAwaitShouldNotFailUponFutureError() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            throw new RuntimeException();
        }));

        when()
            .get("/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("failed"));
    }

    @Test
    void deleteShouldReturnOk() {
        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            waitForResult();
            return Task.Result.COMPLETED;
        }));

        when()
            .delete("/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void deleteShouldCancelMatchingTask() {
        CountDownLatch inProgressLatch = new CountDownLatch(1);

        TaskId taskId = taskManager.submit(new MemoryReferenceTask(() -> {
            try {
                inProgressLatch.await();
            } catch (InterruptedException e) {
                //ignore
            }
            return Task.Result.COMPLETED;
        }));

        with()
            .delete("/" + taskId.getValue());

        when()
            .get("/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", isOneOf("canceledRequested", "canceled"));

        inProgressLatch.countDown();
        when()
            .get("/" + taskId.getValue() + "/await")
            .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("canceled"))
            .body("cancelledFrom", is(HOSTNAME));
    }

    @Test
    void getShouldReturnNotFoundWhenIdDoesNotExist() {
        String taskId = UUID.randomUUID().toString();

        when()
            .get("/" + taskId)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("type", is("notFound"))
            .body("message", is(String.format("%s can not be found", taskId)));
    }

    @Test
    void getShouldReturnErrorWhenInvalidId() {
        String taskId = "invalid";

        when()
            .get("/" + taskId)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid task id"));
    }

    @Test
    void deleteShouldReturnOkWhenNonExistingId() {
        String taskId = UUID.randomUUID().toString();

        when()
            .delete("/" + taskId)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void deleteShouldReturnAnErrorOnInvalidId() {
        String taskId = "invalid";

        when()
            .delete("/" + taskId)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid task id"));
    }

    @Test
    void listShouldReturnErrorWhenNonExistingStatus() {
        given()
            .param("status", "invalid")
            .get()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid status query parameter"));
    }
}
