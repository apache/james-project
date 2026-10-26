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

package org.apache.james.webadmin.integration.postgres;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.PostgresJamesConfiguration;
import org.apache.james.PostgresJamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.projections.EmailQueryViewManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.streams.Limit;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;

import io.restassured.RestAssured;

class PostgresRowLevelSecurityPopulateEmailQueryViewTaskIntegrationTest {
    public static class EmailQueryViewProbe implements GuiceProbe {
        private final EmailQueryViewManager emailQueryViewManager;

        @Inject
        EmailQueryViewProbe(EmailQueryViewManager emailQueryViewManager) {
            this.emailQueryViewManager = emailQueryViewManager;
        }

        void clear(Username owner, MailboxId mailboxId) {
            emailQueryViewManager.getEmailQueryView(owner)
                .delete(mailboxId)
                .block();
        }

        List<MessageId> list(Username owner, MailboxId mailboxId) {
            return emailQueryViewManager.getEmailQueryView(owner)
                .listMailboxContentSortedByReceivedAt(mailboxId, Limit.from(10), !COLLAPSE_THREADS)
                .collectList()
                .block();
        }
    }

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .usersRepository(DEFAULT)
            .eventBusImpl(PostgresJamesConfiguration.EventBusImpl.IN_MEMORY)
            .build())
        .extension(PostgresExtension.withRowLevelSecurity(PostgresDataDefinition.EMPTY_MODULE))
        .server(configuration -> PostgresJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(EmailQueryViewProbe.class)))
        .build();

    private static final boolean COLLAPSE_THREADS = true;
    private static final String PASSWORD = "password";
    private static final Username ALICE = Username.of("alice@domain1.tld");
    private static final Username BOB = Username.of("bob@domain2.tld");

    private MailboxProbeImpl mailboxProbe;
    private EmailQueryViewProbe emailQueryViewProbe;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        DataProbe dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);
        emailQueryViewProbe = guiceJamesServer.getProbe(EmailQueryViewProbe.class);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();

        dataProbe.addDomain(ALICE.getDomainPart().get().asString());
        dataProbe.addDomain(BOB.getDomainPart().get().asString());
        dataProbe.addUser(ALICE.asString(), PASSWORD);
        dataProbe.addUser(BOB.asString(), PASSWORD);
    }

    @Test
    void populateEmailQueryViewTaskShouldPopulateTheMailboxesOfEveryDomain() throws Exception {
        ComposedMessageId aliceMessageId = appendMessageThenClearView(ALICE);
        ComposedMessageId bobMessageId = appendMessageThenClearView(BOB);

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
            .body("additionalInformation.processedMailboxCount", is(2))
            .body("additionalInformation.failedMailboxCount", is(0))
            .body("additionalInformation.processedMessageCount", is(2))
            .body("additionalInformation.failedMessageCount", is(0));

        assertThat(emailQueryViewProbe.list(ALICE, aliceMessageId.getMailboxId()))
            .containsOnly(aliceMessageId.getMessageId());
        assertThat(emailQueryViewProbe.list(BOB, bobMessageId.getMailboxId()))
            .containsOnly(bobMessageId.getMessageId());
    }

    private ComposedMessageId appendMessageThenClearView(Username owner) throws Exception {
        MailboxPath inbox = MailboxPath.inbox(owner);
        mailboxProbe.createMailbox(inbox);
        ComposedMessageId messageId = mailboxProbe.appendMessage(owner.asString(), inbox,
            MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setSubject("small message")
                    .setBody("small message for postgres", StandardCharsets.UTF_8)
                    .build()));

        emailQueryViewProbe.clear(owner, messageId.getMailboxId());
        assertThat(emailQueryViewProbe.list(owner, messageId.getMailboxId())).isEmpty();
        return messageId;
    }
}
