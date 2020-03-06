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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.file.FileSystemExtension;
import org.apache.james.blob.export.file.LocalFileBlobExportMechanism;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.blob.memory.MemoryDumbBlobStore;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.ArchiveService;
import org.apache.james.mailbox.backup.DefaultMailboxBackup;
import org.apache.james.mailbox.backup.MailArchivesLoader;
import org.apache.james.mailbox.backup.MailboxBackup;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.backup.ZipMailArchiveRestorer;
import org.apache.james.mailbox.backup.zip.ZipArchivesLoader;
import org.apache.james.mailbox.backup.zip.Zipper;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
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
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMailContext;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import spark.Service;

@ExtendWith(FileSystemExtension.class)
class MailboxesExportRequestToTaskTest {

    private final class JMAPRoutes implements Routes {
        private final ExportService service;
        private final TaskManager taskManager;
        private final UsersRepository usersRepository;

        private JMAPRoutes(ExportService service, TaskManager taskManager, UsersRepository usersRepository) {
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
    private static final String JAMES_HOST = "james-host";
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final String CORRESPONDING_FILE_HEADER = "corresponding-file";
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final Username CEDRIC = Username.fromLocalPartWithDomain("cedric", DOMAIN);
    private static final String PASSWORD = "password";
    private static final BlobId.Factory FACTORY = new HashBlobId.Factory();
    private static final String MESSAGE_CONTENT = "header: value\n" +
        "\n" +
        "body";

    private FakeMailContext mailetContext;
    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private InMemoryMailboxManager mailboxManager;
    private MemoryUsersRepository usersRepository;
    private MailboxSession bobSession;
    private BlobStore blobStore;

    @BeforeEach
    void setUp(FileSystem fileSystem) throws Exception {
        JsonTransformer jsonTransformer = new JsonTransformer();
        taskManager = new MemoryTaskManager(new Hostname("foo"));

        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        MailboxBackup backup = createMailboxBackup();
        DNSService dnsService = createDnsService();

        usersRepository = createUsersRepository(dnsService);

        bobSession = mailboxManager.createSystemSession(BOB);

        blobStore = new MemoryBlobStore(FACTORY, new MemoryDumbBlobStore());
        mailetContext = FakeMailContext.builder().postmaster(MailAddressFixture.POSTMASTER_AT_JAMES).build();
        BlobExportMechanism blobExport = new LocalFileBlobExportMechanism(mailetContext, blobStore, fileSystem, dnsService,
            LocalFileBlobExportMechanism.Configuration.DEFAULT_CONFIGURATION);

        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer),
            new JMAPRoutes(
                new ExportService(backup, blobStore, blobExport, usersRepository),
                taskManager, usersRepository))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("users/" + BOB.asString() + "/mailboxes")
            .log(LogDetail.URI)
            .build();
    }

    private MemoryUsersRepository createUsersRepository(DNSService dnsService) throws Exception {
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

        domainList.addDomain(DOMAIN);
        usersRepository.addUser(BOB, PASSWORD);
        return usersRepository;
    }

    private DefaultMailboxBackup createMailboxBackup() {
        ArchiveService archiveService = new Zipper();
        MailArchivesLoader archiveLoader = new ZipArchivesLoader();
        ZipMailArchiveRestorer archiveRestorer = new ZipMailArchiveRestorer(mailboxManager, archiveLoader);
        return new DefaultMailboxBackup(mailboxManager, archiveService, archiveRestorer);
    }

    private DNSService createDnsService() throws UnknownHostException {
        InetAddress localHost = mock(InetAddress.class);
        Mockito.when(localHost.getHostName()).thenReturn(JAMES_HOST);
        DNSService dnsService = mock(DNSService.class);
        Mockito.when(dnsService.getLocalHost()).thenReturn(localHost);
        return dnsService;
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
            .body("message", is("User 'notfound' does not exists"));
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
    void exportMailboxesShouldCompleteWhenUserHaveNoMailbox() throws Exception {
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
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void exportMailboxesShouldProduceEmptyZipWhenUserHaveNoMailbox() throws Exception {
        String taskId = with()
            .queryParam("action", "export")
            .post()
            .jsonPath()
            .get("taskId");

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .hasNoEntry();
    }

    @Test
    void exportMailboxesShouldCompleteWhenUserHaveNoMessage() throws Exception {
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession);

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
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void exportMailboxesShouldProduceAZipFileWhenUserHaveNoMessage() throws Exception {
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession);

        String taskId = with()
            .queryParam("action", "export")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory());
    }

    @Test
    void exportMailboxesShouldCompleteWhenUserHaveMessage() throws Exception {
        MailboxId bobInboxboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession)
            .get();

        mailboxManager.getMailbox(bobInboxboxId, bobSession).appendMessage(
            MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
            bobSession);

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
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()));
    }

    @Test
    void exportMailboxesShouldProduceAZipFileWhenUserHaveMessage() throws Exception {
        MailboxId bobInboxboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession)
            .get();

        ComposedMessageId id = mailboxManager.getMailbox(bobInboxboxId, bobSession).appendMessage(
            MessageManager.AppendCommand.builder().build(MESSAGE_CONTENT),
            bobSession);

        String taskId = with()
            .queryParam("action", "export")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory(),
                ZipAssert.EntryChecks.hasName(id.getMessageId().serialize()).hasStringContent(MESSAGE_CONTENT));
    }

    @Test
    void exportMailboxesShouldBeUserBound() throws Exception {
        MailboxId bobInboxboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession)
            .get();

        ComposedMessageId id = mailboxManager.getMailbox(bobInboxboxId, bobSession).appendMessage(
            MessageManager.AppendCommand.builder().build(MESSAGE_CONTENT),
            bobSession);

        usersRepository.addUser(CEDRIC, PASSWORD);
        MailboxSession cedricSession = mailboxManager.createSystemSession(CEDRIC);
        Optional<MailboxId> mailboxIdCedric = mailboxManager.createMailbox(MailboxPath.inbox(CEDRIC), cedricSession);
        mailboxManager.getMailbox(mailboxIdCedric.get(), cedricSession).appendMessage(
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

        String fileUrl = mailetContext.getSentMails().get(0).getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];
        ZipAssert.assertThatZip(new FileInputStream(fileUrl))
            .containsOnlyEntriesMatching(
                ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory(),
                ZipAssert.EntryChecks.hasName(id.getMessageId().serialize()).hasStringContent(MESSAGE_CONTENT));
    }
}