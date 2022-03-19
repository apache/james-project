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

package org.apache.james.pop3.webadmin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStoreFactory;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.pop3server.mailbox.CassandraPop3MetadataStore;
import org.apache.james.pop3server.mailbox.Pop3MetadataModule;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesAdditionalInformationDTO;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import spark.Service;

class Pop3MetaDataFixInconsistenciesRoutesTest {

    private static final class Pop3MetaDataFixInconsistenciesRoute implements Routes {

        private final TaskManager taskManager;
        private final MetaDataFixInconsistenciesService fixInconsistenciesService;

        private Pop3MetaDataFixInconsistenciesRoute(TaskManager taskManager,
                                                    MetaDataFixInconsistenciesService fixInconsistenciesService) {
            this.taskManager = taskManager;
            this.fixInconsistenciesService = fixInconsistenciesService;
        }

        @Override
        public String getBasePath() {
            return BASE_PATH;
        }

        @Override
        public void define(Service service) {
            service.post(BASE_PATH,
                TaskFromRequestRegistry.builder()
                    .registrations(new Pop3MetaDataFixInconsistenciesTaskRegistration(fixInconsistenciesService))
                    .parameterName("task")
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    private static final String BASE_PATH = "/mailboxes";
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final CassandraMessageId MESSAGE_ID_1 = new CassandraMessageId.Factory().fromString("d2bee791-7e63-11ea-883c-95b84008f979");
    private static final CassandraMessageId MESSAGE_ID_2 = new CassandraMessageId.Factory().fromString("eeeeeeee-7e63-11ea-883c-95b84008f979");
    private static final MessageUid MESSAGE_UID_1 = MessageUid.of(1L);
    private static final MessageUid MESSAGE_UID_2 = MessageUid.of(2L);
    private static final String CONTENT_MESSAGE = "CONTENT 123 BLA BLA";
    private static final ModSeq MOD_SEQ_1 = ModSeq.of(1L);
    private static final ModSeq MOD_SEQ_2 = ModSeq.of(2L);
    private static final HashBlobId HEADER_BLOB_ID_1 = new HashBlobId.Factory().forPayload("abc".getBytes());
    private static final CassandraMessageMetadata MESSAGE_1 = CassandraMessageMetadata.builder()
        .ids(ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_1, MESSAGE_UID_1))
            .modSeq(MOD_SEQ_1)
            .flags(new Flags())
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
            .build())
        .internalDate(new Date())
        .bodyStartOctet(18L)
        .size(36L)
        .headerContent(Optional.of(HEADER_BLOB_ID_1))
        .build();

    private static final CassandraMessageMetadata MESSAGE_2 = CassandraMessageMetadata.builder()
        .ids(ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_2, MESSAGE_UID_2))
            .modSeq(MOD_SEQ_2)
            .flags(new Flags())
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_2))
            .build())
        .internalDate(new Date())
        .bodyStartOctet(18L)
        .size(36L)
        .headerContent(Optional.of(HEADER_BLOB_ID_1))
        .build();


    private static final SimpleMailboxMessage MAILBOX_MESSAGE_1 = SimpleMailboxMessage.builder()
        .messageId(MESSAGE_ID_1)
        .mailboxId(MAILBOX_ID)
        .uid(MESSAGE_UID_1)
        .internalDate(new Date())
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
        .bodyStartOctet(16)
        .size(CONTENT_MESSAGE.length())
        .content(new ByteContent(CONTENT_MESSAGE.getBytes(StandardCharsets.UTF_8)))
        .flags(new Flags())
        .properties(new PropertyBuilder())
        .addAttachments(ImmutableList.of())
        .build();

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private CassandraMessageIdToImapUidDAO imapUidDAO;
    private CassandraMessageDAOV3 cassandraMessageDAOV3;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraSchemaVersionModule.MODULE,
        CassandraMessageModule.MODULE,
        CassandraBlobModule.MODULE,
        Pop3MetadataModule.MODULE));

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        Pop3MetadataStore pop3MetadataStore = new CassandraPop3MetadataStore(cassandra.getConf());
        imapUidDAO = new CassandraMessageIdToImapUidDAO(
            cassandra.getConf(),
            new HashBlobId.Factory(),
            CassandraConfiguration.DEFAULT_CONFIGURATION);

        cassandraMessageDAOV3 = new CassandraMessageDAOV3(
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
                .passthrough(),
            new HashBlobId.Factory());
        MetaDataFixInconsistenciesService fixInconsistenciesService = new MetaDataFixInconsistenciesService(imapUidDAO, pop3MetadataStore, cassandraMessageDAOV3);

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        Pop3MetaDataFixInconsistenciesRoute routeTestee = new Pop3MetaDataFixInconsistenciesRoute(taskManager, fixInconsistenciesService);
        TasksRoutes tasksRoutes = new TasksRoutes(taskManager, new JsonTransformer(), DTOConverter.of(MetaDataFixInconsistenciesAdditionalInformationDTO.module()));
        webAdminServer = WebAdminUtils.createWebAdminServer(routeTestee, tasksRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(BASE_PATH)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void taskShouldSuccess() {
        given()
            .queryParam("task", "fixPop3Inconsistencies")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void taskShouldSuccessWithRunningOption() {
        given()
            .queryParam("task", "fixPop3Inconsistencies")
            .queryParam("messagesPerSecond", "5")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void taskShouldFailWhenMessagesPerSecondsIsInvalid() {
        given()
            .queryParam("task", "fixPop3Inconsistencies")
            .queryParam("messagesPerSecond", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("details", containsString("'messagesPerSecond' must be numeric"));
    }

    @Test
    void taskShouldFailWhenMessagesPerSecondsIsNotStrictlyPositive() {
        given()
            .queryParam("task", "fixPop3Inconsistencies")
            .queryParam("messagesPerSecond", "-1")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("details", containsString("'messagesPerSecond' must be strictly positive"));
    }

    @Test
    void taskShouldReturnDetail() {
        String taskId = given()
            .queryParam("task", "fixPop3Inconsistencies")
            .post()
            .jsonPath()
            .get("taskId");
        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("type", Matchers.is("Pop3MetaDataFixInconsistenciesTask"))
            .body("taskId", is(notNullValue()))
            .body("additionalInformation.type", is("Pop3MetaDataFixInconsistenciesTask"))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(100))
            .body("additionalInformation.processedImapUidEntries", is(notNullValue()))
            .body("additionalInformation.processedPop3MetaDataStoreEntries", is(notNullValue()))
            .body("additionalInformation.stalePOP3Entries", is(notNullValue()))
            .body("additionalInformation.missingPOP3Entries", is(notNullValue()))
            .body("additionalInformation.fixedInconsistencies", is(notNullValue()))
            .body("additionalInformation.errors", is(notNullValue()));
    }

    @Test
    void taskWithMessagesPerSecondShouldReturnDetail() {
        String taskId = given()
            .queryParam("task", "fixPop3Inconsistencies")
            .queryParam("messagesPerSecond", "250")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("type", Matchers.is("Pop3MetaDataFixInconsistenciesTask"))
            .body("taskId", is(notNullValue()))
            .body("status", is("completed"))
            .body("additionalInformation.type", is("Pop3MetaDataFixInconsistenciesTask"))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(250))
            .body("additionalInformation.processedImapUidEntries", is(notNullValue()))
            .body("additionalInformation.processedPop3MetaDataStoreEntries", is(notNullValue()))
            .body("additionalInformation.stalePOP3Entries", is(notNullValue()))
            .body("additionalInformation.missingPOP3Entries", is(notNullValue()))
            .body("additionalInformation.fixedInconsistencies", is(notNullValue()))
            .body("additionalInformation.errors", is(notNullValue()));
    }

    @Test
    void errorsPropertyShouldBeNotEmptyWhenProcessFailure() {
        // Failure, Because missing data in CassandraMessageDAOV3
        imapUidDAO.insert(MESSAGE_1).block();

        String taskId = given()
            .queryParam("task", "fixPop3Inconsistencies")
            .queryParam("messagesPerSecond", "250")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("type", Matchers.is("Pop3MetaDataFixInconsistenciesTask"))
            .body("status", is("failed"))
            .body("additionalInformation.errors[0].mailboxId", is(MAILBOX_ID.serialize()))
            .body("additionalInformation.errors[0].messageId", is(MESSAGE_ID_1.serialize()));
    }

    @Test
    void fixedInconsistenciesPropertyShouldBeNotEmptyWhenProcessCompleted() throws MailboxException {
        imapUidDAO.insert(MESSAGE_1).block();
        cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();

        String taskId = given()
            .queryParam("task", "fixPop3Inconsistencies")
            .queryParam("messagesPerSecond", "250")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("type", Matchers.is("Pop3MetaDataFixInconsistenciesTask"))
            .body("status", is("completed"))
            .body("additionalInformation.fixedInconsistencies[0].mailboxId", is(MAILBOX_ID.serialize()))
            .body("additionalInformation.fixedInconsistencies[0].messageId", is(MESSAGE_ID_1.serialize()));
    }

}