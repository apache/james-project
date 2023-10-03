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

import static org.apache.james.MailsShouldBeWellReceived.CALMLY_AWAIT;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.TestingSession;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

interface CassandraBlobStoreContract {
    class TestingSessionProbe implements GuiceProbe {
        private final TestingSession testingSession;

        @Inject
        private TestingSessionProbe(TestingSession testingSession) {
            this.testingSession = testingSession;
        }

        public TestingSession getTestingSession() {
            return testingSession;
        }
    }

    class TestingSessionModule extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder.newSetBinder(binder(), GuiceProbe.class)
                .addBinding()
                .to(TestingSessionProbe.class);

            bind(CqlSession.class).to(TestingSession.class);
        }

        @Provides
        @Singleton
        TestingSession provideSession(SessionWithInitializedTablesFactory factory) {
            return new TestingSession(factory.get());
        }
    }

    String DOMAIN_1 = "apache1.org";
    String JAMES_USER_1 = "james-user1@" + DOMAIN_1;
    String PASSWORD_1 = "secret";

    @BeforeEach
    default void contractSetup(GuiceJamesServer server) throws Exception {
        if (!server.getProbe(DataProbeImpl.class).listDomains().contains(DOMAIN_1)) {
            server.getProbe(DataProbeImpl.class)
                .addDomain(DOMAIN_1);
        }

        if (!Sets.newHashSet(server.getProbe(DataProbeImpl.class).listUsers()).contains(JAMES_USER_1)) {
            server.getProbe(DataProbeImpl.class)
                .addUser(JAMES_USER_1, PASSWORD_1);
        }
    }

    @Test
    default void noCallToBlobStoreWhenMoveMessages(GuiceJamesServer server) throws Exception {
        // Given Mailbox1, Mailbox2. And a message in Mailbox1
        String myBox1 = "MyBox1" + UUID.randomUUID();
        String myBox2 = "MyBox2" + UUID.randomUUID();
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER_1, myBox1);
        mailboxProbe.createMailbox("#private", JAMES_USER_1, myBox2);

        ComposedMessageId composedMessageId = server.getProbe(MailboxProbeImpl.class).appendMessage(JAMES_USER_1,
            MailboxPath.forUser(Username.of(JAMES_USER_1), myBox1),
            MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)
                .build()));

        try (TestIMAPClient imapClient = new TestIMAPClient()) {
            int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
            imapClient.connect("127.0.0.1", imapPort)
                .login(JAMES_USER_1, PASSWORD_1)
                .select(myBox1)
                .awaitMessageCount(CALMLY_AWAIT, 1);

            StatementRecorder statementRecorder = server.getProbe(TestingSessionProbe.class)
                .getTestingSession()
                .recordStatements();
            // When move MyBox1 -> MyBox2
            imapClient.moveFirstMessage(myBox2);

            imapClient.awaitMessageCount(CALMLY_AWAIT, 0);
            imapClient.select(myBox2)
                .awaitMessageCount(CALMLY_AWAIT, 1);

            // Then no call to blob store
            assertSoftly(softly -> {
                softly.assertThat(statementRecorder.listExecutedStatements(
                        StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobpartsinbucket WHERE")))
                    .hasSize(0);
                softly.assertThat(statementRecorder.listExecutedStatements(
                        StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobparts WHERE")))
                    .hasSize(0);
            });
        }
    }

    @Test
    default void noCallToBlobStoreWhenResetTheFlags(GuiceJamesServer server) throws Exception {
        // Given Mailbox1 And a message in Mailbox1
        String myBox1 = "MyBox1" + UUID.randomUUID();
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER_1, myBox1);

        server.getProbe(MailboxProbeImpl.class).appendMessage(JAMES_USER_1,
            MailboxPath.forUser(Username.of(JAMES_USER_1), myBox1),
            MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)
                .build()));

        try (TestIMAPClient imapClient = new TestIMAPClient()) {
            int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
            imapClient.connect("127.0.0.1", imapPort)
                .login(JAMES_USER_1, PASSWORD_1)
                .select(myBox1)
                .awaitMessageCount(CALMLY_AWAIT, 1);

            StatementRecorder statementRecorder = server.getProbe(TestingSessionProbe.class)
                .getTestingSession()
                .recordStatements();

            // When reset flags
            imapClient.setFlagsForAllMessagesInMailbox("\\Flaged1");

            Thread.sleep(2000);

            // Then no call to blob store
            assertSoftly(softly -> {
                softly.assertThat(statementRecorder.listExecutedStatements(
                        StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobpartsinbucket WHERE")))
                    .hasSize(0);
                softly.assertThat(statementRecorder.listExecutedStatements(
                        StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobparts WHERE")))
                    .hasSize(0);
            });
        }
    }
}
