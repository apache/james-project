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

package org.apache.james;

import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
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

import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.cassandra.mail.task.MailboxMergingTask;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV2Table;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.probe.ACLProbe;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.server.CassandraProbe;
import org.apache.james.task.TaskManager;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.CassandraMailboxMergingRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.RequestSpecification;

public class FixingGhostMailboxTest {

    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String FIRST_MAILBOX = ARGUMENTS + ".list[0]";

    @ClassRule
    public static DockerCassandraRule cassandra = new DockerCassandraRule();

    @Rule
    public CassandraJmapTestRule rule = CassandraJmapTestRule.defaultTestRule();

    private AccessToken accessToken;
    private String domain;
    private String alice;
    private String bob;
    private String cedric;
    private GuiceJamesServer jmapServer;
    private MailboxProbeImpl mailboxProbe;
    private ACLProbe aclProbe;
    private Session session;
    private ComposedMessageId message1;
    private MailboxId aliceGhostInboxId;
    private MailboxPath aliceInboxPath;
    private ComposedMessageId message2;
    private WebAdminGuiceProbe webAdminProbe;
    private RequestSpecification webadminSpecification;

    @Before
    public void setup() throws Throwable {
        jmapServer = rule.jmapServer(cassandra.getModule(),
            binder -> binder.bind(WebAdminConfiguration.class)
            .toInstance(WebAdminConfiguration.TEST_CONFIGURATION));
        jmapServer.start();
        webAdminProbe = jmapServer.getProbe(WebAdminGuiceProbe.class);
        mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        aclProbe = jmapServer.getProbe(ACLProbeImpl.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        webadminSpecification = WebAdminUtils.buildRequestSpecification(webAdminProbe.getWebAdminPort())
            .build();

        domain = "domain.tld";
        alice = "alice@" + domain;
        String alicePassword = "aliceSecret";
        bob = "bob@" + domain;
        cedric = "cedric@" + domain;
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(domain);
        dataProbe.addUser(alice, alicePassword);
        dataProbe.addUser(bob, "bobSecret");
        accessToken = authenticateJamesUser(baseUri(jmapServer), alice, alicePassword);

        session = Cluster.builder()
            .addContactPoint(cassandra.getIp())
            .withPort(cassandra.getMappedPort(9042))
            .build()
            .connect(jmapServer.getProbe(CassandraProbe.class).getKeyspace());

        simulateGhostMailboxBug();
    }

    private void simulateGhostMailboxBug() throws MailboxException, IOException {
        // State before ghost mailbox bug
        // Alice INBOX is delegated to Bob and contains one message
        aliceInboxPath = MailboxPath.forUser(alice, MailboxConstants.INBOX);
        aliceGhostInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, alice, MailboxConstants.INBOX);
        aclProbe.addRights(aliceInboxPath, bob, MailboxACL.FULL_RIGHTS);
        message1 = mailboxProbe.appendMessage(alice, aliceInboxPath, AppendCommand.from(generateMessageContent()));
        rule.await();

        // Simulate ghost mailbox bug
        session.execute(delete().from(CassandraMailboxPathV2Table.TABLE_NAME)
            .where(eq(CassandraMailboxPathV2Table.NAMESPACE, MailboxConstants.USER_NAMESPACE))
            .and(eq(CassandraMailboxPathV2Table.USER, alice))
            .and(eq(CassandraMailboxPathV2Table.MAILBOX_NAME, MailboxConstants.INBOX)));

        // trigger provisioning
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        // Received a new message
        message2 = mailboxProbe.appendMessage(alice, aliceInboxPath, AppendCommand.from(generateMessageContent()));
        rule.await();
    }

    private Message generateMessageContent() throws IOException {
        return Message.Builder.of()
            .setSubject("toto")
            .setBody("content", StandardCharsets.UTF_8)
            .build();
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void ghostMailboxBugShouldChangeMailboxId() throws Exception {
        Mailbox newAliceInbox = mailboxProbe.getMailbox(MailboxConstants.USER_NAMESPACE, alice, MailboxConstants.INBOX);

        assertThat(aliceGhostInboxId).isNotEqualTo(newAliceInbox.getMailboxId());
    }

    @Test
    public void ghostMailboxBugShouldDiscardOldContent() throws Exception {
        Mailbox newAliceInbox = mailboxProbe.getMailbox(MailboxConstants.USER_NAMESPACE, alice, MailboxConstants.INBOX);

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + newAliceInbox.getMailboxId().serialize() + "\"]}}, \"#0\"]]")
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
    public void webadminCanMergeTwoMailboxes() throws Exception {
        Mailbox newAliceInbox = mailboxProbe.getMailbox(MailboxConstants.USER_NAMESPACE, alice, MailboxConstants.INBOX);

        fixGhostMailboxes(newAliceInbox);

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + newAliceInbox.getMailboxId().serialize() + "\"]}}, \"#0\"]]")
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
    public void webadminCanMergeTwoMailboxesRights() throws Exception {
        Mailbox newAliceInbox = mailboxProbe.getMailbox(MailboxConstants.USER_NAMESPACE, alice, MailboxConstants.INBOX);
        aclProbe.addRights(aliceInboxPath, cedric, MailboxACL.FULL_RIGHTS);

        fixGhostMailboxes(newAliceInbox);

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + newAliceInbox.getMailboxId().serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(FIRST_MAILBOX + ".sharedWith", hasKey(bob))
            .body(FIRST_MAILBOX + ".sharedWith", hasKey(cedric));
    }

    @Test
    public void oldGhostedMailboxShouldNoMoreBeAccessible() throws Exception {
        Mailbox newAliceInbox = mailboxProbe.getMailbox(MailboxConstants.USER_NAMESPACE, alice, MailboxConstants.INBOX);
        aclProbe.addRights(aliceInboxPath, cedric, MailboxACL.FULL_RIGHTS);

        fixGhostMailboxes(newAliceInbox);

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": [\"" + aliceGhostInboxId.serialize() + "\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("mailboxes"))
            .body(ARGUMENTS + ".list", hasSize(0));
    }

    @Test
    public void mergingMailboxTaskShouldBeInformative() {
        Mailbox newAliceInbox = mailboxProbe.getMailbox(MailboxConstants.USER_NAMESPACE, alice, MailboxConstants.INBOX);

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
            .body("additionalInformation.newMailboxId", is(newAliceInbox.getMailboxId().serialize()))
            .body("additionalInformation.totalMessageCount", is(1))
            .body("additionalInformation.messageMovedCount", is(1))
            .body("additionalInformation.messageFailedCount", is(0))
            .body("type", is(MailboxMergingTask.MAILBOX_MERGING))
            .body("submitDate", is(not(nullValue())))
            .body("startedDate", is(not(nullValue())))
            .body("completedDate", is(not(nullValue())));
    }

    private String fixGhostMailboxes(Mailbox newAliceInbox) {
        String taskId = given()
            .spec(webadminSpecification)
            .body("{" +
                "    \"mergeOrigin\":\"" + aliceGhostInboxId.serialize() + "\"," +
                "    \"mergeDestination\":\"" + newAliceInbox.getMailboxId().serialize() + "\"" +
                "}")
            .post(CassandraMailboxMergingRoutes.BASE)
            .jsonPath()
            .getString("taskId");

        given()
            .spec(webadminSpecification)
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");
        rule.await();
        return taskId;
    }

}
