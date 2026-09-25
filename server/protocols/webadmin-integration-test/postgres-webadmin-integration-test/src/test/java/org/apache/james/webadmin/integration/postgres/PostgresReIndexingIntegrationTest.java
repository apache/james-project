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
import static org.hamcrest.Matchers.is;

import java.time.Duration;

import org.apache.james.FakeMessageSearchIndex;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.PostgresJamesConfiguration;
import org.apache.james.PostgresJamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.mailbox.tools.indexer.FullReindexingTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

/**
 * Reproduces the "Time out executing Postgres query" error reported when running a throttled full re-indexing
 * against the Postgres app: the mailbox listing query stays open while the messages of the first mailbox are slowly
 * re-indexed, which must not be mistaken for a hanging Postgres query.
 */
class PostgresReIndexingIntegrationTest {
    private static class AcceptingMessageSearchIndex extends FakeMessageSearchIndex {
        @Override
        public Mono<Void> add(MailboxSession session, Mailbox mailbox, MailboxMessage message) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteAll(MailboxSession session, MailboxId mailboxId) {
            return Mono.empty();
        }

        @Override
        public void postReindexing() {

        }
    }

    private static final Duration JOOQ_REACTIVE_TIMEOUT = Duration.ofSeconds(2);
    private static final int ONE_MESSAGE_PER_SECOND = 1;
    private static final int INBOX_MESSAGE_COUNT = 4;
    private static final int SENT_MESSAGE_COUNT = 1;
    private static final String DOMAIN = "domain.tld";
    private static final Username BOB = Username.of("bob@" + DOMAIN);
    private static final String PASSWORD = "password";
    private static final MailboxPath BOB_INBOX = MailboxPath.inbox(BOB);
    private static final MailboxPath BOB_SENT = MailboxPath.forUser(BOB, "Sent");

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .usersRepository(DEFAULT)
            .eventBusImpl(PostgresJamesConfiguration.EventBusImpl.IN_MEMORY)
            .build())
        .extension(PostgresExtension.empty().withJooqReactiveTimeout(JOOQ_REACTIVE_TIMEOUT))
        .server(configuration -> PostgresJamesServerMain.createServer(configuration)
            .overrideWith(binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(new AcceptingMessageSearchIndex())))
        .build();

    private MailboxProbeImpl mailboxProbe;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        DataProbe dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB.asString(), PASSWORD);
        mailboxProbe.createMailbox(BOB_INBOX);
        mailboxProbe.createMailbox(BOB_SENT);
        appendMessages(BOB_INBOX, INBOX_MESSAGE_COUNT);
        appendMessages(BOB_SENT, SENT_MESSAGE_COUNT);
    }

    @Test
    void throttledFullReIndexingShouldNotTimeoutWhenIndexingAMailboxTakesLongerThanTheJooqReactiveTimeout() {
        String taskId = with()
            .queryParam("task", "reIndex")
            .queryParam("messagesPerSecond", ONE_MESSAGE_PER_SECOND)
            .post("/mailboxes")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("type", is(FullReindexingTask.FULL_RE_INDEXING.asString()))
            .body("additionalInformation.successfullyReprocessedMailCount", is(INBOX_MESSAGE_COUNT + SENT_MESSAGE_COUNT))
            .body("additionalInformation.failedReprocessedMailCount", is(0))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(ONE_MESSAGE_PER_SECOND));
    }

    private void appendMessages(MailboxPath mailboxPath, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mailboxProbe.appendMessage(BOB.asString(), mailboxPath,
                AppendCommand.builder().build("header: value\r\n\r\nbody " + i));
        }
    }
}
