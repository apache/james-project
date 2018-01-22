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

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.RestAssured.with;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Throwables;
import com.jayway.restassured.RestAssured;

public class TasksRoutesTest {

    private MemoryTaskManager taskManager;
    private WebAdminServer webAdminServer;

    @Before
    public void setUp() throws Exception {
        taskManager = new MemoryTaskManager();

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new DefaultMetricFactory(),
            new TasksRoutes(taskManager, new JsonTransformer()));

        webAdminServer.configure(NO_CONFIGURATION);
        webAdminServer.await();

        RestAssured.requestSpecification = WebAdminUtils.defineRequestSpecification(webAdminServer)
            .setBasePath(TasksRoutes.BASE)
            .build();
    }

    @After
    public void tearDown() {
        taskManager.stop();
        webAdminServer.destroy();
    }

    @Test
    public void listShouldReturnEmptyWhenNoTask() {
        when()
            .get()
        .then()
            .body("", hasSize(0));
    }

    @Test
    public void listShouldReturnTaskDetailsWhenTaskInProgress() throws Exception {
        CountDownLatch taskInProgressLatch = new CountDownLatch(1);
        TaskId taskId = taskManager.submit(() -> {
            taskInProgressLatch.countDown();
            await();
            return Task.Result.COMPLETED;
        });

        taskInProgressLatch.await();

        when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("[0].status", is(TaskManager.Status.IN_PROGRESS.getValue()))
            .body("[0].taskId", is(taskId.getValue().toString()))
            .body("[0].class", is(not(empty())));
    }

    private void await() {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Throwables.propagate(e);
        }
    }

    @Test
    public void listShouldListTaskWhenStatusFilter() {
        TaskId taskId = taskManager.submit(() -> {
            await();
            return Task.Result.COMPLETED;
        });

        given()
            .param("status", TaskManager.Status.IN_PROGRESS.getValue())
        .when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .body("[0].status", is(TaskManager.Status.IN_PROGRESS.getValue()))
            .body("[0].taskId", is(taskId.getValue().toString()))
            .body("[0].type", is(Task.UNKNOWN));
    }

    @Test
    public void listShouldReturnEmptyWhenNonMatchingStatusFilter() {
        taskManager.submit(() -> {
            await();
            return Task.Result.COMPLETED;
        });

        given()
            .param("status", TaskManager.Status.WAITING.getValue())
        .when()
            .get()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    public void getShouldReturnTaskDetails() {
        TaskId taskId = taskManager.submit(() -> {
            await();
            return Task.Result.COMPLETED;
        });

        when()
            .get("/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("inProgress"));
    }

    @Test
    public void getAwaitShouldAwaitTaskCompletion() {
        TaskId taskId = taskManager.submit(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
            return Task.Result.COMPLETED;
        });

        when()
            .get("/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("completed"));
    }

    @Test
    public void deleteShouldReturnOk() {
        TaskId taskId = taskManager.submit(() -> {
            await();
            return Task.Result.COMPLETED;
        });

        when()
            .delete("/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void deleteShouldCancelMatchingTask() {
        TaskId taskId = taskManager.submit(() -> {
            await();
            return Task.Result.COMPLETED;
        });

        with()
            .delete("/" + taskId.getValue());

        when()
            .get("/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("canceled"));
    }

    @Test
    public void getShouldReturnNotFoundWhenIdDoesNotExist() {
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
    public void getShouldReturnErrorWhenInvalidId() {
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
    public void deleteShouldReturnOkWhenNonExistingId() {
        String taskId = UUID.randomUUID().toString();

        when()
            .delete("/" + taskId)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void deleteShouldReturnAnErrorOnInvalidId() {
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
    public void listShouldReturnErrorWhenNonExistingStatus() {
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