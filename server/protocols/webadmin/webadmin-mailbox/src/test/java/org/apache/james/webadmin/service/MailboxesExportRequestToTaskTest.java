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
import static io.restassured.RestAssured.with;
import static org.apache.james.mailbox.DefaultMailboxes.INBOX;
import static org.apache.james.webadmin.service.ExportServiceTestSystem.BOB;
import static org.apache.james.webadmin.service.ExportServiceTestSystem.CEDRIC;
import static org.apache.james.webadmin.service.ExportServiceTestSystem.PASSWORD;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Optional;

import org.apache.james.blob.export.file.FileSystemExtension;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
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
import io.restassured.filter.log.LogDetail;
import reactor.core.publisher.Mono;
import spark.Service;

@ExtendWith(FileSystemExtension.class)
class MailboxesExportRequestToTaskTest {

    private final class ExportRoutes implements Routes {
        private final ExportService service;
        private final TaskManager taskManager;
        private final UsersRepository usersRepository;

        private ExportRoutes(ExportService service, TaskManager taskManager, UsersRepository usersRepository) {
            this.service = service;
            this.taskManager = taskManager;
            this.usersRepository = usersRepository;
        }

        @Override
        public String getBasePath() {
            return BASE_PATH;
        }

        @Override
        public void define(Service service) {
            service.post(BASE_PATH,
                TaskFromRequestRegistry.builder()
                    .registrations(new MailboxesExportRequestToTask(this.service, usersRepository))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    private static final String BASE_PATH = "users/:username/mailboxes";
    private static final String CORRESPONDING_FILE_HEADER = "corresponding-file";
    private static final String MESSAGE_CONTENT = "header: value\n" +
        "\n" +
        "body";

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private ExportServiceTestSystem testSystem;

    @BeforeEach
    void setUp(FileSystem fileSystem) throws Exception {
        testSystem = new ExportServiceTestSystem(fileSystem);
        taskManager = new MemoryTaskManager(new Hostname("foo"));

        JsonTransformer jsonTransformer = new JsonTransformer();
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer),
            new ExportRoutes(
                new ExportService(testSystem.backup, testSystem.blobStore, testSystem.blobExport, testSystem.usersRepository),
                taskManager, testSystem.usersRepository))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("users/" + BOB.asString() + "/mailboxes")
            .log(LogDetail.URI)
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
            .body("details", is("'action' query parameter is compulsory. Supported values are [export]"));
    }

    @Test
    void exportMailboxesShouldFailUponEmptyAction() {
        given()
            .queryParam("action", "")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter cannot be empty or blank. Supported values are [export]"));
    }

    @Test
    void exportMailboxesShouldFailUponInvalidAction() {
        given()
            .queryParam("action", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [export]"));
    }

    @Test
    void exportMailboxesShouldFailUponBadUsername() {
        given()
            .basePath("users/bad@bad@bad/mailboxes")
            .queryParam("action", "export")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("The username should not contain multiple domain delimiter."));
    }

    @Test
    void exportMailboxesShouldFailUponUnknownUser() {
        given()
            .basePath("users/notFound/mailboxes")
            .queryParam("action", "export")
            .post()
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("User 'notfound' does not exist"));
    }

    @Test
    void postShouldCreateANewTask() {
        given()
            .queryParam("action", "export")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void exportMailboxesShouldCompleteWhenUserHasNoMailbox() {
        String taskId = with()
            .queryParam("action", "export")
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
            .body("type", is("MailboxesExportTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.stage", is(ExportService.Stage.COMPLETED.toString()))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void exportMailboxesShouldProduceEmptyZipWhenUserHasNoMailbox() throws Exception {
        String taskId = with()
            .queryParam("action", "export")
            .post()
            .jsonPath()
            .get("taskId");

        String fileUrl = testSystem.mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .hasNoEntry();
    }

    @Test
    void exportMailboxesShouldCompleteWhenUserHasNoMessage() throws Exception {
        testSystem.mailboxManager.createMailbox(MailboxPath.inbox(BOB), testSystem.bobSession);

        String taskId = with()
            .queryParam("action", "export")
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
            .body("type", is("MailboxesExportTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.stage", is(ExportService.Stage.COMPLETED.toString()))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void exportMailboxesShouldProduceAZipFileWhenUserHasNoMessage() throws Exception {
        testSystem.mailboxManager.createMailbox(MailboxPath.inbox(BOB), testSystem.bobSession);

        String taskId = with()
            .queryParam("action", "export")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        String fileUrl = testSystem.mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory());
    }

    @Test
    void exportMailboxesShouldFailWhenCannotSaveToBlobStore() throws Exception {
        testSystem.mailboxManager.createMailbox(MailboxPath.inbox(BOB), testSystem.bobSession);

        doReturn(Mono.error(new RuntimeException()))
            .when(testSystem.blobStore)
            .save(any(), any(InputStream.class), any());

        String taskId = with()
            .queryParam("action", "export")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("failed"))
            .body("taskId", is(taskId))
            .body("type", is("MailboxesExportTask"))
            .body("additionalInformation.username", is(BOB.asString()));
    }

    @Test
    void exportMailboxesShouldCompleteWhenUserHasMessage() throws Exception {
        MailboxId bobInboxboxId = testSystem.mailboxManager.createMailbox(MailboxPath.inbox(BOB), testSystem.bobSession)
            .get();

        testSystem.mailboxManager.getMailbox(bobInboxboxId, testSystem.bobSession).appendMessage(
            MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
            testSystem.bobSession);

        String taskId = with()
            .queryParam("action", "export")
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
            .body("type", is("MailboxesExportTask"))
            .body("additionalInformation.username", is(BOB.asString()))
            .body("additionalInformation.stage", is(ExportService.Stage.COMPLETED.toString()))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void exportMailboxesShouldProduceAZipFileWhenUserHasMessage() throws Exception {
        MailboxId bobInboxboxId = testSystem.mailboxManager.createMailbox(MailboxPath.inbox(BOB), testSystem.bobSession)
            .get();

        ComposedMessageId id = testSystem.mailboxManager.getMailbox(bobInboxboxId, testSystem.bobSession).appendMessage(
            MessageManager.AppendCommand.builder().build(MESSAGE_CONTENT),
            testSystem.bobSession).getIds();

        String taskId = with()
            .queryParam("action", "export")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        String fileUrl = testSystem.mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory(),
                ZipAssert.EntryChecks.hasName(id.getMessageId().serialize()).hasStringContent(MESSAGE_CONTENT));
    }

    @Test
    void exportMailboxesShouldBeUserBound() throws Exception {
        MailboxId bobInboxboxId = testSystem.mailboxManager.createMailbox(MailboxPath.inbox(BOB), testSystem.bobSession)
            .get();

        ComposedMessageId id = testSystem.mailboxManager.getMailbox(bobInboxboxId, testSystem.bobSession).appendMessage(
            MessageManager.AppendCommand.builder().build(MESSAGE_CONTENT),
            testSystem.bobSession).getIds();

        testSystem.usersRepository.addUser(CEDRIC, PASSWORD);
        MailboxSession cedricSession = testSystem.mailboxManager.createSystemSession(CEDRIC);
        Optional<MailboxId> mailboxIdCedric = testSystem.mailboxManager.createMailbox(MailboxPath.inbox(CEDRIC), cedricSession);
        testSystem.mailboxManager.getMailbox(mailboxIdCedric.get(), cedricSession).appendMessage(
            MessageManager.AppendCommand.builder().build(MESSAGE_CONTENT + CEDRIC.asString()),
            cedricSession);

        String taskId = with()
            .queryParam("action", "export")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        String fileUrl = testSystem.mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory(),
                ZipAssert.EntryChecks.hasName(id.getMessageId().serialize()).hasStringContent(MESSAGE_CONTENT));
    }
}