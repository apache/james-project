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

import org.apache.james.PostgresJamesConfiguration.EventBusImpl;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.modules.EventDeadLettersProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.Host;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.io.Resources;

public class PostgresWithOpenSearchDisabledTest implements MailsShouldBeWellReceived {
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.openSearchDisabled())
            .usersRepository(DEFAULT)
            .eventBusImpl(EventBusImpl.IN_MEMORY)
            .build())
        .server(configuration -> PostgresJamesServerMain.createServer(configuration)
            .overrideWith(binder -> binder.bind(OpenSearchConfiguration.class)
            .toInstance(OpenSearchConfiguration.builder()
                .addHost(Host.from("127.0.0.1", 9042))
                .build())))
        .extension(postgresExtension)
        .build();

    @Override
    public int imapPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    @Override
    public int smtpPort(GuiceJamesServer server) {
        return server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue();
    }

    @Test
    void mailsShouldBeKeptInDeadLetterForLaterIndexing(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(SENDER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        Port smtpPort = Port.of(smtpPort(server));
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            sender.connect(JAMES_SERVER_HOST, smtpPort).authenticate(SENDER, PASSWORD);
            sendUniqueMessage(sender, message);
        }

        CALMLY_AWAIT.until(() -> server.getProbe(EventDeadLettersProbe.class).getEventDeadLetters()
            .groupsWithFailedEvents().collectList().block().contains(new OpenSearchListeningMessageSearchIndex.OpenSearchListeningMessageSearchIndexGroup()));
    }

    @Test
    void searchShouldFail(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(SENDER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        try (TestIMAPClient reader = new TestIMAPClient()) {
            int imapPort = imapPort(server);
            reader.connect(JAMES_SERVER_HOST, imapPort)
                .login(JAMES_USER, PASSWORD)
                .select(TestIMAPClient.INBOX);

            assertThat(reader.sendCommand("SEARCH SUBJECT thy"))
                .contains("NO SEARCH processing failed");
        }
    }

    @Test
    @Disabled("Overrides not implemented yet for Postgresql")
    void searchShouldSucceedOnSearchOverrides(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(SENDER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        try (TestIMAPClient reader = new TestIMAPClient()) {
            int imapPort = imapPort(server);
            reader.connect(JAMES_SERVER_HOST, imapPort)
                .login(JAMES_USER, PASSWORD)
                .select(TestIMAPClient.INBOX);

            assertThat(reader.sendCommand("SEARCH UNSEEN"))
                .contains("OK SEARCH");
        }
    }
}
