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

package org.apache.james.webadmin.integration.rabbitmq;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.mail.Flags;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.events.Event;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.GenericGroup;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.EventDeadLettersProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.server.core.MailImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.MailRepositoriesRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;

@Tag(BasicFeature.TAG)
class RabbitMQWebAdminServerTaskSerializationIntegrationTest {
    @RegisterExtension
    static JamesServerExtension testExtension = jamesServerBuilder()
        .build();

    static JamesServerBuilder<CassandraRabbitMQJamesConfiguration> jamesServerBuilder() {
        return new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
            CassandraRabbitMQJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(BlobStoreConfiguration.builder()
                        .s3()
                        .disableCache()
                        .deduplication()
                        .noCryptoConfig())
                .searchConfiguration(SearchConfiguration.openSearch())
                .vaultConfiguration(VaultConfiguration.ENABLED_DEFAULT)
                .build())
            .extension(new DockerOpenSearchExtension())
            .extension(new CassandraExtension())
            .extension(new AwsS3BlobStoreExtension())
            .extension(new RabbitMQExtension())
            .server(CassandraRabbitMQJamesServerMain::createServer);
    }

    private static final String DOMAIN = "domain";
    private static final String USERNAME = "username@" + DOMAIN;
    private static final String USERNAME_2 = "username2@" + DOMAIN;

    private DataProbe dataProbe;
    private MailboxProbe mailboxProbe;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void recomputeFastViewProjectionItemsShouldComplete(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addUser(USERNAME, "secret");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);
        mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.inbox(Username.of(USERNAME)),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        String taskId = with()
            .post("/mailboxes?task=recomputeFastViewProjectionItems")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is("RecomputeAllFastViewProjectionItemsTask"))
            .body("additionalInformation.processedMessageCount", is(1))
            .body("additionalInformation.failedMessageCount", is(0));
    }

    @Test
    void populateEmailQueryViewShouldComplete(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addUser(USERNAME, "secret");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);
        mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.inbox(Username.of(USERNAME)),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        String taskId = with()
            .post("/mailboxes?task=populateEmailQueryView")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is("PopulateEmailQueryViewTask"))
            .body("additionalInformation.processedMessageCount", is(1))
            .body("additionalInformation.failedMessageCount", is(0));
    }

    @Test
    void reprocessingOneMailShouldCreateATask(GuiceJamesServer guiceJamesServer) throws Exception {
        MailRepositoryStore mailRepositoryStore = guiceJamesServer.getProbe(MailRepositoryProbeImpl.class).getMailRepositoryStore();
        Stream<MailRepositoryUrl> urls = mailRepositoryStore.getUrls();
        MailRepositoryUrl mailRepositoryUrl = urls.findAny().get();
        MailRepository repository = mailRepositoryStore.get(mailRepositoryUrl).get();

        String mailKey = "name1";
        repository.store(MailImpl.builder()
            .name(mailKey)
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
            .body("type", is("reprocessing-one"))
            .body("additionalInformation.repositoryPath", is(mailRepositoryUrl.asString()))
            .body("additionalInformation.targetQueue", is(notNullValue()))
            .body("additionalInformation.consume", is(notNullValue()))
            .body("additionalInformation.mailKey", is(mailKey))
            .body("additionalInformation.targetProcessor", is(nullValue()));
    }

    @Test
    void eventDeadLettersRedeliverShouldCreateATask(GuiceJamesServer guiceJamesServer) {
        Group group = new GenericGroup("a");

        MailboxAdded event = createMailboxAdded();

        guiceJamesServer
            .getProbe(EventDeadLettersProbe.class)
            .getEventDeadLetters()
            .store(group, event)
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
            .body("type", is("event-dead-letters-redeliver-group"))
            .body("additionalInformation.successfulRedeliveriesCount", is(0))
            .body("additionalInformation.failedRedeliveriesCount", is(0))
            .body("additionalInformation.group", is(group.asString()));
    }

    @Test
    void postRedeliverSingleEventShouldCreateATask(GuiceJamesServer guiceJamesServer) {
        Group group = new GenericGroup("a");

        MailboxAdded event = createMailboxAdded();

        EventDeadLetters.InsertionId insertionId = guiceJamesServer
            .getProbe(EventDeadLettersProbe.class)
            .getEventDeadLetters()
            .store(group, event)
            .block();

        String taskId = with()
            .queryParam("action", "reDeliver")
        .post("/events/deadLetter/groups/" + group.asString() + "/" + insertionId.asString())
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
            .body("type", is("event-dead-letters-redeliver-one"))
            .body("additionalInformation.successfulRedeliveriesCount", is(0))
            .body("additionalInformation.failedRedeliveriesCount", is(0))
            .body("additionalInformation.group", is(group.asString()))
            .body("additionalInformation.insertionId", is(insertionId.asString()));
    }

    @Test
    void runRulesOnAllUsersMailboxShouldComplete(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addUser(USERNAME, "secret");
        server.getProbe(DataProbeImpl.class).addUser(USERNAME_2, "secret");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "otherMailbox");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME_2, MailboxConstants.INBOX);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME_2, "otherMailbox");

        mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.inbox(Username.of(USERNAME)),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        mailboxProbe.appendMessage(
            USERNAME_2,
            MailboxPath.inbox(Username.of(USERNAME_2)),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            false,
            new Flags());

        List<Map<String, String>> list = given()
            .queryParams("action", "triage", "mailboxName", MailboxConstants.INBOX)
            .body("""
            {
              "id": "1",
              "name": "rule 1",
              "action": {
                "appendIn": {
                  "mailboxIds": []
                },
                "moveTo": {
                  "mailboxName": "otherMailbox"
                },
                "important": false,
                "keyworkds": [],
                "reject": false,
                "seen": false
              },
              "conditionGroup": {
                "conditionCombiner": "AND",
                "conditions": [
                  {
                    "comparator": "contains",
                    "field": "subject",
                    "value": "test"
                  }
                ]
              }
            }""")
            .post("/messages")
        .then()
            .statusCode(CREATED_201)
            .extract()
            .jsonPath()
            .getList(".");

        assertThat(list)
            .hasSize(2)
            .first()
            .satisfies(map -> assertThat(map).hasSize(2)
                .containsKeys("taskId", "username"));
    }

    private MailboxAdded createMailboxAdded() {
        String uuid = "6e0dd59d-660e-4d9b-b22f-0354479f47b4";
        return EventFactory.mailboxAdded()
            .eventId(Event.EventId.of(uuid))
            .user(Username.of(USERNAME))
            .sessionId(MailboxSession.SessionId.of(452))
            .mailboxId(InMemoryId.of(453))
            .mailboxPath(MailboxPath.forUser(Username.of(USERNAME), "Important-mailbox"))
            .build();
    }
}
