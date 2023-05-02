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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.TestingSession;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.blob.cassandra.cache.CassandraBlobCacheModule;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

class CassandraCacheQueryTest {
    private static class TestingSessionProbe implements GuiceProbe {
        private final TestingSession testingSession;

        @Inject
        private TestingSessionProbe(TestingSession testingSession) {
            this.testingSession = testingSession;
        }

        public TestingSession getTestingSession() {
            return testingSession;
        }
    }

    private static class TestingSessionModule extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder.newSetBinder(binder(), GuiceProbe.class)
                .addBinding()
                .to(TestingSessionProbe.class);

            bind(CqlSession.class)
                .annotatedWith(Names.named("cache"))
                .to(TestingSession.class);
            bind(CqlSession.class)
                .to(TestingSession.class);
            Multibinder.newSetBinder(binder(), CassandraModule.class).addBinding().toInstance(CassandraBlobCacheModule.MODULE);
        }

        @Provides
        @Singleton
        TestingSession provideSession(SessionWithInitializedTablesFactory factory) {
            return new TestingSession(factory.get());
        }
    }

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = WithCacheImmutableTest.baseExtensionBuilder()
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .overrideServerModule(new TestingSessionModule())
        .build();

    private static final int MESSAGE_COUNT = 1;
    private static final String JAMES_SERVER_HOST = "127.0.0.1";
    private static final String DOMAIN = "apache.org";
    private static final String JAMES_USER = "james-user@" + DOMAIN;
    private static final String PASSWORD = "secret";
    private static final String SENDER = "bob@apache.org";
    private static final String UNICODE_BODY = "Unicode €uro symbol.";
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    private static StatementRecorder statementRecorder;

    @BeforeAll
    static void beforeEach(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        statementRecorder = server.getProbe(TestingSessionProbe.class).getTestingSession().recordStatements();

        sendAMail(server);
        readAMail(server);
    }

    @Test
    void deDuplicatingBlobStoreShouldNotClearCache() {
        assertThat(statementRecorder.listExecutedStatements(
              StatementRecorder.Selector.preparedStatementStartingWith("DELETE FROM blob_cache")))
            .isEmpty();
    }

    @Test
    void cacheShouldBeRead() {
        assertThat(statementRecorder.listExecutedStatements(
                StatementRecorder.Selector.preparedStatementStartingWith("SELECT data FROM blob_cache")))
            .isNotEmpty();
    }

    private static void readAMail(GuiceJamesServer server) throws IOException {
        try (TestIMAPClient reader = new TestIMAPClient()) {
            int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
            reader.connect(JAMES_SERVER_HOST, imapPort)
                .login(JAMES_USER, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, MESSAGE_COUNT);

            assertThat(reader.readFirstMessage())
                .contains(UNICODE_BODY);
        }
    }

    private static void sendAMail(GuiceJamesServer server) throws IOException, MessagingException {
        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            sender.connect(JAMES_SERVER_HOST, smtpPort);
            MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/mail-containing-unicode-characters.eml"));

            FakeMail.Builder mail = FakeMail.builder()
                .name("test-unicode-body")
                .sender(SENDER)
                .recipient(JAMES_USER)
                .mimeMessage(mimeMessage);

            sender.sendMessage(mail);
        }

        CALMLY_AWAIT.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());
    }

}