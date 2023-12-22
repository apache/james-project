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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.postgres.mail.PostgresMessageModule;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

import reactor.core.publisher.Mono;

class BodyDeduplicationIntegrationTest implements MailsShouldBeWellReceived {
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .blobStore(BlobStoreConfiguration.builder()
                .file()
                .disableCache()
                .deduplication()
                .noCryptoConfig())
            .usersRepository(DEFAULT)
            .build())
        .server(PostgresJamesServerMain::createServer)
        .extension(postgresExtension)
        .build();

    private static final String PASSWORD = "123456";
    private static final String YET_ANOTHER_USER = "yet-another-user@" + DOMAIN;

    private TestIMAPClient testIMAPClient;
    private SMTPMessageSender smtpMessageSender;

    @BeforeEach
    void setUp() {
        this.testIMAPClient = new TestIMAPClient();
        this.smtpMessageSender = new SMTPMessageSender(DOMAIN);
    }

    @Override
    public int imapPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    @Override
    public int smtpPort(GuiceJamesServer server) {
        return server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue();
    }

    @Test
    void bodyBlobsShouldBeDeDeduplicated(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(OTHER_USER, PASSWORD_OTHER)
            .addUser(YET_ANOTHER_USER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox("#private", OTHER_USER, DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox("#private", YET_ANOTHER_USER, DefaultMailboxes.INBOX);

        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        // Given a mail sent to 3 recipients
        smtpMessageSender.connect(JAMES_SERVER_HOST, smtpPort);
        sendUniqueMessageToUsers(smtpMessageSender, message, ImmutableList.of(JAMES_USER, OTHER_USER, YET_ANOTHER_USER));
        CALMLY_AWAIT.untilAsserted(() -> assertThat(server.getProbe(SpoolerProbe.class).processingFinished()).isTrue());

        // When 3 mails are received
        testIMAPClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(JAMES_USER, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(CALMLY_AWAIT, 1);
        testIMAPClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(OTHER_USER, PASSWORD_OTHER)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(CALMLY_AWAIT, 1);
        testIMAPClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(YET_ANOTHER_USER, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(CALMLY_AWAIT, 1);

        // Then the body blobs are deduplicated
        int distinctBlobCount = postgresExtension.getPostgresExecutor()
            .executeCount(dslContext -> Mono.from(dslContext.select(DSL.countDistinct(PostgresMessageModule.MessageTable.BODY_BLOB_ID))
                .from(PostgresMessageModule.MessageTable.TABLE_NAME)))
            .block();

        assertThat(distinctBlobCount).isEqualTo(1);
    }
}
