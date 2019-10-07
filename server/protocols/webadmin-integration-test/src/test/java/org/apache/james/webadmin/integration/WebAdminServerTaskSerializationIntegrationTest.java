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

package org.apache.james.webadmin.integration;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRoutes.MESSAGE_PATH_PARAM;
import static org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRoutes.USERS;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.CassandraRabbitMQAwsS3JmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.core.User;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTask;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.GenericGroup;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.EventDeadLettersProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.task.TaskManager;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.CassandraMailboxMergingRoutes;
import org.apache.james.webadmin.routes.MailQueueRoutes;
import org.apache.james.webadmin.routes.MailRepositoriesRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.service.ClearMailQueueTask;
import org.apache.james.webadmin.service.ClearMailRepositoryTask;
import org.apache.james.webadmin.service.DeleteMailsFromMailQueueTask;
import org.apache.james.webadmin.service.EventDeadLettersRedeliverTask;
import org.apache.james.webadmin.service.ReprocessingAllMailsTask;
import org.apache.james.webadmin.service.ReprocessingOneMailTask;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTask;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultExportTask;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTask;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRoutes;
import org.apache.mailbox.tools.indexer.FullReindexingTask;
import org.apache.mailbox.tools.indexer.MessageIdReIndexingTask;
import org.apache.mailbox.tools.indexer.SingleMailboxReindexingTask;
import org.apache.mailbox.tools.indexer.SingleMessageReindexingTask;
import org.apache.mailbox.tools.indexer.UserReindexingTask;
import org.apache.mailet.base.test.FakeMail;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WebAdminServerTaskSerializationIntegrationTest {

    private static final String DOMAIN = "domain";
    private static final String USERNAME = "username@" + DOMAIN;

    @Rule
    public DockerCassandraRule cassandra = new DockerCassandraRule();

    @Rule
    public CassandraRabbitMQAwsS3JmapTestRule jamesTestRule = CassandraRabbitMQAwsS3JmapTestRule.defaultTestRule();

    private GuiceJamesServer guiceJamesServer;
    private DataProbe dataProbe;
    private MailboxProbe mailboxProbe;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = jamesTestRule.jmapServer(cassandra.getModule());
        guiceJamesServer.start();
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void fullReindexingShouldCompleteWhenNoMail() {
        String taskId = with()
            .post("/mailboxes?task=reIndex")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(FullReindexingTask.FULL_RE_INDEXING.asString()));
    }

    @Test
    public void deleteMailsFromMailQueueShouldCompleteWhenSenderIsValid() {
        String firstMailQueue = with()
                .basePath(MailQueueRoutes.BASE_URL)
            .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("[0]");

        String taskId = with()
                .basePath(MailQueueRoutes.BASE_URL)
                .param("sender", USERNAME)
            .delete(firstMailQueue + "/mails")
                .jsonPath()
                .getString("taskId");

        given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("taskId", is(notNullValue()))
                .body("type", is(DeleteMailsFromMailQueueTask.TYPE.asString()));
    }

    @Test
    public void reprocessingAllMailsShouldComplete() {
        String escapedRepositoryPath = with()
                .basePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
            .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("[0].path");

        String taskId = with()
                .basePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
                .param("action", "reprocess")
            .patch(escapedRepositoryPath + "/mails")
            .then()
                .statusCode(HttpStatus.CREATED_201)
                .extract()
                .jsonPath()
                .getString("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingAllMailsTask.TYPE.asString()));
    }

    @Test
    public void reprocessingOneMailShouldCreateATask() throws Exception {
        MailRepositoryStore mailRepositoryStore = guiceJamesServer.getProbe(MailRepositoryProbeImpl.class).getMailRepositoryStore();
        Stream<MailRepositoryUrl> urls = mailRepositoryStore.getUrls();
        MailRepositoryUrl mailRepositoryUrl = urls.findAny().get();
        MailRepository repository = mailRepositoryStore.get(mailRepositoryUrl).get();

        repository.store(FakeMail.builder()
            .name("name1")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder().build())
            .build());

        String taskId = with()
            .basePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
            .param("action", "reprocess")
        .patch(mailRepositoryUrl.urlEncoded() + "/mails/name1")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .getString("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("failed"))
            .body("taskId", is(notNullValue()))
            .body("type", is(ReprocessingOneMailTask.TYPE.asString()));
    }

    @Test
    public void singleMessageReindexingShouldCompleteWhenMail() throws Exception {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);
        ComposedMessageId composedMessageId = mailboxProbe.appendMessage(
                USERNAME,
                MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
                new Date(),
                false,
                new Flags());

        String taskId = with()
            .post("/mailboxes/" + mailboxId.serialize() + "/mails/"
                    + composedMessageId.getUid().asLong() + "?task=reIndex")
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(SingleMessageReindexingTask.MESSAGE_RE_INDEXING.asString()));
    }

    @Test
    public void messageIdReIndexingShouldCompleteWhenMail() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);
        ComposedMessageId composedMessageId = mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        String taskId = with()
            .post("/messages/" + composedMessageId.getMessageId().serialize() + "?task=reIndex")
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(MessageIdReIndexingTask.TYPE.asString()));
    }

    @Test
    public void userReindexingShouldComplete() {
        String taskId = with()
                .queryParam("user", USERNAME)
                .queryParam("task", "reIndex")
            .post("/mailboxes")
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(UserReindexingTask.USER_RE_INDEXING.asString()));
    }

    @Test
    public void deletedMessageVaultRestoreShouldComplete() throws Exception {
        dataProbe.addUser(USERNAME, "password");
        String query =
            "{" +
                "  \"fieldName\": \"subject\"," +
                "  \"operator\": \"contains\"," +
                "  \"value\": \"subject contains\"" +
                "}";

        String taskId =
            with()
                .basePath(DeletedMessagesVaultRoutes.ROOT_PATH)
                .queryParam("action", "restore")
                .body(query)
            .post(USERS + SEPARATOR + USERNAME)
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(DeletedMessagesVaultRestoreTask.TYPE.asString()));
    }

    @Test
    public void deletedMessageVaultExportShouldComplete() throws Exception {
        dataProbe.addUser(USERNAME, "password");
        String query = "{" +
            "\"combinator\": \"and\"," +
            "\"criteria\": []" +
            "}";

        String taskId = with()
            .basePath(DeletedMessagesVaultRoutes.ROOT_PATH)
            .queryParam("action", "export")
            .queryParam("exportTo", "exportTo@james.org")
            .body(query)
        .post(USERS + SEPARATOR + USERNAME)
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(DeletedMessagesVaultExportTask.TYPE.asString()));
    }

    @Test
    public void errorRecoveryIndexationShouldCompleteWhenNoMail() {
        String taskId = with()
            .post("/mailboxes?task=reIndex")
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        String fixingTaskId = with()
            .queryParam("reIndexFailedMessagesOf", taskId)
            .queryParam("task", "reIndex")
        .post("/mailboxes")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(fixingTaskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is("ErrorRecoveryIndexation"));
    }

    @Test
    public void eventDeadLettersRedeliverShouldComplete() {
        String taskId = with()
            .queryParam("action", "reDeliver")
        .post("/events/deadLetter")
            .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(EventDeadLettersRedeliverTask.TYPE.asString()));

    }

    @Test
    public void eventDeadLettersRedeliverShouldCreateATask() {
        String uuid = "6e0dd59d-660e-4d9b-b22f-0354479f47b4";
        String insertionUuid = "6e0dd59d-660e-4d9b-b22f-0354479f47b7";
        Group group = new GenericGroup("a");
        EventDeadLetters.InsertionId insertionId = EventDeadLetters.InsertionId.of(insertionUuid);
        MailboxListener.MailboxAdded event = EventFactory.mailboxAdded()
            .eventId(Event.EventId.of(uuid))
            .user(User.fromUsername(USERNAME))
            .sessionId(MailboxSession.SessionId.of(452))
            .mailboxId(InMemoryId.of(453))
            .mailboxPath(MailboxPath.forUser(USERNAME, "Important-mailbox"))
            .build();

        guiceJamesServer
            .getProbe(EventDeadLettersProbe.class)
            .getEventDeadLetters()
            .store(group, event, insertionId)
            .block();

        String taskId = with()
            .queryParam("action", "reDeliver")
        .post("/events/deadLetter/groups/" + group.asString())
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("failed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(EventDeadLettersRedeliverTask.TYPE.asString()));
    }

    @Test
    public void postRedeliverSingleEventShouldCreateATask() {
        String uuid = "6e0dd59d-660e-4d9b-b22f-0354479f47b4";
        String insertionUuid = "6e0dd59d-660e-4d9b-b22f-0354479f47b7";
        Group group = new GenericGroup("a");
        EventDeadLetters.InsertionId insertionId = EventDeadLetters.InsertionId.of(insertionUuid);
        MailboxListener.MailboxAdded event = EventFactory.mailboxAdded()
            .eventId(Event.EventId.of(uuid))
            .user(User.fromUsername(USERNAME))
            .sessionId(MailboxSession.SessionId.of(452))
            .mailboxId(InMemoryId.of(453))
            .mailboxPath(MailboxPath.forUser(USERNAME, "Important-mailbox"))
            .build();

        guiceJamesServer
            .getProbe(EventDeadLettersProbe.class)
            .getEventDeadLetters()
            .store(group, event, insertionId)
            .block();

        String taskId = with()
            .queryParam("action", "reDeliver")
        .post("/events/deadLetter/groups/" + group.asString() + "/" + insertionUuid)
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("failed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(EventDeadLettersRedeliverTask.TYPE.asString()));
    }

    @Test
    public void clearMailQueueShouldCompleteWhenNoQueryParameters() {
        String firstMailQueue = with()
                .basePath(MailQueueRoutes.BASE_URL)
            .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("[0]");

        String taskId = with()
                .basePath(MailQueueRoutes.BASE_URL)
            .delete(firstMailQueue + "/mails")
                .jsonPath()
                .getString("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(ClearMailQueueTask.TYPE.asString()));
    }

    @Test
    public void blobStoreBasedGarbageCollectionShoudComplete() {
        String taskId =
            with()
                .basePath(DeletedMessagesVaultRoutes.ROOT_PATH)
                .queryParam("scope", "expired")
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
            .body("type", is("deletedMessages/blobStoreBasedGarbageCollection"));
    }

    @Test
    public void clearMailRepositoryShouldComplete() {
        String escapedRepositoryPath = with()
                .basePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
            .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("[0].path");

        String taskId = with()
                .basePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
            .delete(escapedRepositoryPath + "/mails")
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is(ClearMailRepositoryTask.TYPE.asString()));
    }


    @Test
    public void mailboxMergingShouldComplete() {
        MailboxId origin = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);
        MailboxId destination = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX + "2");

        String taskId = given()
                .body("{" +
                    "    \"mergeOrigin\":\"" + origin.serialize() + "\"," +
                    "    \"mergeDestination\":\"" + destination.serialize() + "\"" +
                    "}")
            .post(CassandraMailboxMergingRoutes.BASE)
                .jsonPath()
                .getString("taskId");

        with()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is(TaskManager.Status.COMPLETED.getValue()))
            .body("taskId", is(taskId))
            .body("type", is(MailboxMergingTask.MAILBOX_MERGING.asString()));
    }

    @Test
    public void singleMailboxReindexingShouldComplete() {
        MailboxId mailboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);

        String taskId = when()
            .post("/mailboxes/" + mailboxId.serialize() + "?task=reIndex")
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is(SingleMailboxReindexingTask.MAILBOX_RE_INDEXING.asString()));
    }

    @Test
    public void deletedMessagesVaultDeleteShouldCompleteEvenNoDeletedMessageExisted() throws Exception {
        dataProbe.addUser(USERNAME, "password");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);
        ComposedMessageId composedMessageId = mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.forUser(USERNAME, MailboxConstants.INBOX),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        String taskId =
            with()
                .basePath(DeletedMessagesVaultRoutes.ROOT_PATH)
            .delete(USERS + SEPARATOR + USERNAME + SEPARATOR + MESSAGE_PATH_PARAM + SEPARATOR + composedMessageId.getMessageId().serialize())
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is(DeletedMessagesVaultDeleteTask.TYPE.asString()));
    }
}