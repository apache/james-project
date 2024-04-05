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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.calmlyAwait;
import static org.apache.james.jmap.JmapRFCCommonRequests.UserCredential;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.core.Username;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.jmap.JmapRFCCommonRequests;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTask;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.ACLProbe;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.server.CassandraProbe;
import org.apache.james.task.TaskManager;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.CassandraMailboxMergingRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@Tag(BasicFeature.TAG)
class FixingGhostMailboxTest {
    private static final String DOMAIN = "domain.tld";
    private static final String CEDRIC = "cedric@" + DOMAIN;
    private static final String BOB = "bob@" + DOMAIN;
    private static final String ALICE = "alice@" + DOMAIN;
    private static final String ALICE_SECRET = "aliceSecret";
    private static final String BOB_SECRET = "bobSecret";
    private static final Duration THIRTY_SECONDS = Duration.ofSeconds(30);

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .disableCache()
                .deduplication()
                .noCryptoConfig())
            .searchConfiguration(SearchConfiguration.openSearch())
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new RabbitMQExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(CassandraConfiguration.class)
                .toInstance(CassandraConfiguration.builder()
                    .mailboxReadRepair(0)
                    .mailboxCountersReadRepairMax(0)
                    .mailboxCountersReadRepairChanceOneHundred(0)
                    .build())))
        .build();

    private UserCredential aliceCredential;
    private MailboxProbeImpl mailboxProbe;
    private ACLProbe aclProbe;
    private ComposedMessageId message1;
    private MailboxId aliceGhostInboxId;
    private MailboxPath aliceInboxPath;
    private ComposedMessageId message2;
    private RequestSpecification webadminSpecification;

    @BeforeEach
    void setup(GuiceJamesServer server) throws Throwable {
        WebAdminGuiceProbe webAdminProbe = server.getProbe(WebAdminGuiceProbe.class);
        mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        aclProbe = server.getProbe(ACLProbeImpl.class);

        Port jmapPort = server.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(jmapPort.getValue())
            .addHeader("accept", "application/json; jmapVersion=rfc-8621")
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        webadminSpecification = WebAdminUtils.buildRequestSpecification(webAdminProbe.getWebAdminPort())
            .build();

        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(ALICE, ALICE_SECRET)
            .addUser(BOB, BOB_SECRET);
        aliceCredential = JmapRFCCommonRequests.getUserCredential(Username.of(ALICE), ALICE_SECRET);

        CassandraProbe probe = server.getProbe(CassandraProbe.class);
        ClusterConfiguration cassandraConfiguration = probe.getConfiguration();
        try (CqlSession session = ClusterFactory.create(cassandraConfiguration, probe.getMainKeyspaceConfiguration())) {
            simulateGhostMailboxBug(session);
        }
    }

    private void simulateGhostMailboxBug(CqlSession session) throws MailboxException, IOException {
        // State before ghost mailbox bug
        // Alice INBOX is delegated to Bob and contains one message
        aliceInboxPath = MailboxPath.inbox(Username.of(ALICE));
        aliceGhostInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);
        aclProbe.addRights(aliceInboxPath, BOB, MailboxACL.FULL_RIGHTS);
        message1 = mailboxProbe.appendMessage(ALICE, aliceInboxPath, AppendCommand.from(generateMessageContent()));
        testExtension.await();

        // Simulate ghost mailbox bug
        session.execute(deleteFrom(CassandraMailboxPathV3Table.TABLE_NAME)
            .where(column(CassandraMailboxPathV3Table.NAMESPACE).isEqualTo(literal(MailboxConstants.USER_NAMESPACE)),
                column(CassandraMailboxPathV3Table.USER).isEqualTo(literal(ALICE)),
                column(CassandraMailboxPathV3Table.MAILBOX_NAME).isEqualTo(literal(MailboxConstants.INBOX)))
            .build());

        // trigger provisioning
        given()
            .auth().basic(aliceCredential.username().asString(), aliceCredential.password())
            .body("""
                {
                    "using": [
                        "urn:ietf:params:jmap:core",
                        "urn:ietf:params:jmap:mail"
                    ],
                    "methodCalls": [
                        [
                            "Mailbox/get",
                            {
                                "accountId": "%s",
                                "ids": null
                            },
                            "c1"
                        ]
                    ]
                }""".formatted(aliceCredential.accountId()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        // Received a new message
        message2 = mailboxProbe.appendMessage(ALICE, aliceInboxPath, AppendCommand.from(generateMessageContent()));
        testExtension.await();
    }

    private Message generateMessageContent() throws IOException {
        return Message.Builder.of()
            .setSubject("toto")
            .setBody("content", StandardCharsets.UTF_8)
            .build();
    }

    @Test
    void ghostMailboxBugShouldChangeMailboxId() {
        MailboxId newAliceInbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);

        assertThat(aliceGhostInboxId).isNotEqualTo(newAliceInbox);
    }

    @Test
    void ghostMailboxBugShouldDiscardOldContent() {
        MailboxId newAliceInbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);

        calmlyAwait
            .timeout(THIRTY_SECONDS)
            .untilAsserted(() ->
                given()
                    .auth().basic(aliceCredential.username().asString(), aliceCredential.password())
                    .body("""
                        {
                            "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
                            "methodCalls": [
                                [
                                    "Email/query",
                                    {
                                        "accountId": "%s",
                                        "filter": {
                                            "inMailbox": "%s"
                                        }
                                    },
                                    "c1"
                                ]
                            ]
                        }
                        """.formatted(aliceCredential.accountId(), newAliceInbox.serialize()))
                .when()
                    .post("/jmap")
                .then()
                    .statusCode(200)
                    .body("methodResponses[0][1].ids", hasSize(1))
                    .body("methodResponses[0][1].ids", not(contains(message1.getMessageId().serialize())))
                    .body("methodResponses[0][1].ids", contains(message2.getMessageId().serialize())));
    }


    /*
     * 1 expectation failed.
     * JSON path [0][1].messageIds doesn't match.
     * Expected: a collection with size <2>
     *   Actual: [95b42310-47b7-11eb-90b1-f5389ace5056]
     * https://builds.apache.org/blue/organizations/jenkins/james%2FApacheJames/detail/PR-268/44/tests
     */
    @Test
    @Tag(Unstable.TAG)
    void webadminCanMergeTwoMailboxes() {
        MailboxId newAliceInbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);

        fixGhostMailboxes(newAliceInbox);

        calmlyAwait
            .timeout(THIRTY_SECONDS)
            .untilAsserted(() -> given()
            .auth().basic(aliceCredential.username().asString(), aliceCredential.password())
            .body("""
                {
                    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
                    "methodCalls": [
                        [
                            "Email/query",
                            {
                                "accountId": "%s",
                                "filter": {
                                    "inMailbox": "%s"
                                }
                            },
                            "c1"
                        ]
                    ]
                }
                """.formatted(aliceCredential.accountId(), newAliceInbox.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body("methodResponses[0][1].ids", hasSize(2))
            .body("methodResponses[0][1].ids", containsInAnyOrder(
                message1.getMessageId().serialize(),
                message2.getMessageId().serialize())));
    }

    @Test
    void webadminCanMergeTwoMailboxesRights() throws Exception {
        MailboxId newAliceInbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);
        aclProbe.addRights(aliceInboxPath, CEDRIC, MailboxACL.FULL_RIGHTS);

        fixGhostMailboxes(newAliceInbox);

        given()
            .auth().basic(aliceCredential.username().asString(), aliceCredential.password())
            .header("accept", "application/json; jmapVersion=rfc-8621")
            .body("""
                {
                    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
                    "methodCalls": [
                        [
                            "Mailbox/get",
                            {
                                "accountId": "%s",
                                "ids": ["%s"]
                            },
                            "c1"
                        ]
                    ]
                }""".formatted(aliceCredential.accountId(), newAliceInbox.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body("methodResponses[0][1].list[0].rights", hasKey(BOB))
            .body("methodResponses[0][1].list[0].rights", hasKey(CEDRIC));
    }

    @Test
    void oldGhostedMailboxShouldNoMoreBeAccessible() throws Exception {
        MailboxId newAliceInbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);
        aclProbe.addRights(aliceInboxPath, CEDRIC, MailboxACL.FULL_RIGHTS);

        fixGhostMailboxes(newAliceInbox);

        given()
            .auth().basic(aliceCredential.username().asString(), aliceCredential.password())
            .body("""
                {
                    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
                    "methodCalls": [
                        [
                            "Email/query",
                            {
                                "accountId": "%s",
                                "filter": {
                                    "inMailbox": "%s"
                                }
                            },
                            "c1"
                        ]
                    ]
                }
                """.formatted(aliceCredential.accountId(), aliceGhostInboxId.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body("methodResponses[0][1].type", Matchers.is("invalidArguments"))
            .body("methodResponses[0][1].description", Matchers.is(aliceGhostInboxId.serialize() + " can not be found"));
    }

    @Test
    void mergingMailboxTaskShouldBeInformative() {
        MailboxId newAliceInbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);

        String taskId = fixGhostMailboxes(newAliceInbox);

        with()
            .spec(webadminSpecification)
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is(TaskManager.Status.COMPLETED.getValue()))
            .body("taskId", is(taskId))
            .body("additionalInformation.oldMailboxId", is(aliceGhostInboxId.serialize()))
            .body("additionalInformation.newMailboxId", is(newAliceInbox.serialize()))
            .body("additionalInformation.totalMessageCount", is(1))
            .body("additionalInformation.messageMovedCount", is(1))
            .body("additionalInformation.messageFailedCount", is(0))
            .body("type", is(MailboxMergingTask.MAILBOX_MERGING.asString()))
            .body("submitDate", is(not(nullValue())))
            .body("startedDate", is(not(nullValue())))
            .body("completedDate", is(not(nullValue())));
    }

    private String fixGhostMailboxes(MailboxId newAliceInbox) {
        String taskId = given()
            .spec(webadminSpecification)
            .body("{" +
                "    \"mergeOrigin\":\"" + aliceGhostInboxId.serialize() + "\"," +
                "    \"mergeDestination\":\"" + newAliceInbox.serialize() + "\"" +
                "}")
            .post(CassandraMailboxMergingRoutes.BASE)
            .jsonPath()
            .getString("taskId");

        given()
            .spec(webadminSpecification)
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");
        testExtension.await();
        return taskId;
    }

}
