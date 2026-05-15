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

package org.apache.james.webadmin.service;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.apache.james.webadmin.service.ExportServiceTestSystem.BOB;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipOutputStream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.export.file.FileSystemExtension;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.json.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
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
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.RestAssured;
import spark.Service;

@ExtendWith(FileSystemExtension.class)
class MailboxesRestoreRequestToTaskTest {

    private final class RestoreRoutes implements Routes {
        private final RestoreService restoreService;
        private final TaskManager taskManager;
        private final UsersRepository usersRepository;
        private final BlobStore blobStore;

        private RestoreRoutes(RestoreService restoreService, TaskManager taskManager, UsersRepository usersRepository, BlobStore blobStore) {
            this.restoreService = restoreService;
            this.taskManager = taskManager;
            this.usersRepository = usersRepository;
            this.blobStore = blobStore;
        }

        @Override
        public String getBasePath() {
            return BASE_PATH;
        }

        @Override
        public void define(Service service) {
            service.post(BASE_PATH,
                TaskFromRequestRegistry.builder()
                    .parameterName("task")
                    .registrations(new MailboxesRestoreRequestToTask(this.restoreService, usersRepository, blobStore))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    private static final String BASE_PATH = "users/:username/mailboxes";
    private static final BlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private ExportServiceTestSystem testSystem;

    @BeforeEach
    void setUp(FileSystem fileSystem) throws Exception {
        testSystem = new ExportServiceTestSystem(fileSystem);
        taskManager = new MemoryTaskManager(new Hostname("foo"));

        JsonTransformer jsonTransformer = new JsonTransformer();
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer,
                DTOConverter.of(MailboxesRestoreTaskAdditionalInformationDTO.SERIALIZATION_MODULE)),
            new RestoreRoutes(
                new RestoreService(testSystem.backup, testSystem.blobStore), taskManager, testSystem.usersRepository, testSystem.blobStore))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("users/" + BOB.asString() + "/mailboxes")
            .build();
    }

    @AfterEach
    void afterEach() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    private byte[] emptyZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // empty zip
        }
        return baos.toByteArray();
    }

    @Test
    void taskRequestParameterShouldBeCompulsory() {
        when()
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'task' query parameter is compulsory. Supported values are [restore]"));
    }

    @Test
    void restoreMailboxesShouldFailUponEmptyTask() {
        given()
            .queryParam("task", "")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'task' query parameter cannot be empty or blank. Supported values are [restore]"));
    }

    @Test
    void restoreMailboxesShouldFailUponInvalidTask() {
        given()
            .queryParam("task", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'task': invalid. Supported values are [restore]"));
    }

    @Test
    void restoreMailboxesShouldFailUponBadUsername() throws Exception {
        given()
            .basePath("users/bad@bad@bad/mailboxes")
            .queryParam("task", "restore")
            .body(emptyZip())
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Domain parts ASCII chars must be a-z A-Z 0-9 - or _ in bad@bad"));
    }

    @Test
    void restoreMailboxesShouldFailUponUnknownUser() throws Exception {
        given()
            .basePath("users/notFound/mailboxes")
            .queryParam("task", "restore")
            .body(emptyZip())
            .post()
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("User 'notfound' does not exist"));
    }

    @Test
    void restoreMailboxesShouldFailUponEmptyBody() {
        given()
            .queryParam("task", "restore")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Request body must contain the ZIP backup data"));
    }

    @Test
    void postShouldCreateANewTask() throws Exception {
        given()
            .queryParam("task", "restore")
            .body(emptyZip())
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", is(notNullValue()));
    }

    @Test
    void restoreMailboxesShouldCompleteWhenUserHasNoMailbox() throws Exception {
        String taskId = given()
            .queryParam("task", "restore")
            .body(emptyZip())
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("MailboxesRestoreTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }
}
