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

import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.LocalHostURIBuilder.baseUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.CassandraExtension;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTask;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV2Table;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.ACLProbe;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.server.CassandraProbe;
import org.apache.james.task.TaskManager;
import org.apache.james.util.Host;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.integration.WebadminIntergrationTestModule;
import org.apache.james.webadmin.routes.CassandraMailboxMergingRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@Category(BasicFeature.class)
class FixingGhostMailboxTest {
    private static final int LIMIT_TO_10_MESSAGES = 10;
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String FIRST_MAILBOX = ARGUMENTS + ".list[0]";

    private static final String DOMAIN = "domain.tld";
    private static final String CEDRIC = "cedric@" + DOMAIN;
    private static final String BOB = "bob@" + DOMAIN;
    private static final String ALICE = "alice@" + DOMAIN;
    private static final String ALICE_SECRET = "aliceSecret";
    private static final String BOB_SECRET = "bobSecret";

    public static final CassandraExtension dockerCassandra = new CassandraExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(new DockerElasticSearchExtension())
        .extension(dockerCassandra)
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(new WebadminIntergrationTestModule()))
        .build();

    private AccessToken accessToken;
    private MailboxProbeImpl mailboxProbe;
    private ACLProbe aclProbe;
    private Session session;
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
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        webadminSpecification = WebAdminUtils.buildRequestSpecification(webAdminProbe.getWebAdminPort())
            .build();

        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(ALICE, ALICE_SECRET)
            .addUser(BOB, BOB_SECRET);
        accessToken = authenticateJamesUser(baseUri(jmapPort), Username.of(ALICE), ALICE_SECRET);

        Host cassandraHost = dockerCassandra.getCassandra().getHost();
        session = Cluster.builder()
            .withoutJMXReporting()
            .addContactPoint(cassandraHost.getHostName())
            .withPort(cassandraHost.getPort())
            .build()
            .connect(server.getProbe(CassandraProbe.class).getKeyspace());

        simulateGhostMailboxBug();
    }

    private void simulateGhostMailboxBug() throws MailboxException, IOException {
        // State before ghost mailbox bug
        // Alice INBOX is delegated to Bob and contains one message
        aliceInboxPath = MailboxPath.inbox(Username.of(ALICE));
        aliceGhostInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);
        aclProbe.addRights(aliceInboxPath, BOB, MailboxACL.FULL_RIGHTS);
        message1 = mailboxProbe.appendMessage(ALICE, aliceInboxPath, AppendCommand.from(generateMessageContent()));
        testExtension.await();

        // Simulate ghost mailbox bug
        session.execute(delete().from(CassandraMailboxPathV2Table.TABLE_NAME)
            .where(eq(CassandraMailboxPathV2Table.NAMESPACE, MailboxConstants.USER_NAMESPACE))
            .and(eq(CassandraMailboxPathV2Table.USER, ALICE))
            .and(eq(CassandraMailboxPathV2Table.MAILBOX_NAME, MailboxConstants.INBOX)));

        // trigger provisioning
        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
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

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + newAliceInbox.serialize() + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(1))
            .body(ARGUMENTS + ".messageIds", not(contains(message1.getMessageId().serialize())))
            .body(ARGUMENTS + ".messageIds", contains(message2.getMessageId().serialize()));
    }

    @Test
    void webadminCanMergeTwoMailboxes() {
        MailboxId newAliceInbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);

        fixGhostMailboxes(newAliceInbox);

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + newAliceInbox.serialize() + "\"]}}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("messageList"))
            .body(ARGUMENTS + ".messageIds", hasSize(2))
            .body(ARGUMENTS + ".messageIds", containsInAnyOrder(
                message1.getMessageId().serialize(),
                message2.getMessageId().serialize()));
    }

    @Test
    void webadminCanMergeTwoMailboxesRights() throws Exception {
        MailboxId newAliceInbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);
        aclProbe.addRights(aliceInboxPath, CEDRIC, MailboxACL.FULL_RIGHTS);

        fixGhostMailboxes(newAliceInbox);

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + newAliceInbox.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".sharedWith", hasKey(BOB))
            .body(FIRST_MAILBOX + ".sharedWith", hasKey(CEDRIC));
    }

    @Test
    void oldGhostedMailboxShouldNoMoreBeAccessible() throws Exception {
        MailboxId newAliceInbox = mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, ALICE, MailboxConstants.INBOX);
        aclProbe.addRights(aliceInboxPath, CEDRIC, MailboxACL.FULL_RIGHTS);

        fixGhostMailboxes(newAliceInbox);

        given()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + aliceGhostInboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(0));
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
