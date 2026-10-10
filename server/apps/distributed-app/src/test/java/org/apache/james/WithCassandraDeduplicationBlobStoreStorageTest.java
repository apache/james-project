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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.MailsShouldBeWellReceived.CALMLY_AWAIT;
import static org.apache.james.MailsShouldBeWellReceived.DOMAIN;
import static org.apache.james.MailsShouldBeWellReceived.JAMES_USER;
import static org.apache.james.MailsShouldBeWellReceived.OTHER_USER;
import static org.apache.james.MailsShouldBeWellReceived.PASSWORD;
import static org.apache.james.MailsShouldBeWellReceived.PASSWORD_OTHER;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.blob.cassandra.BlobTables;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.server.CassandraProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

// Counts the blobs of the whole server, so it does not share the WithCassandraDeduplicationBlobStoreTest server
class WithCassandraDeduplicationBlobStoreStorageTest {
    private static final String JAMES_SERVER_HOST = "127.0.0.1";
    private static final String YET_ANOTHER_USER = "yet-another-user@" + DOMAIN;

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = WithCassandraDeduplicationBlobStoreTest.jamesServerBuilder()
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void blobsShouldBeDeduplicated(GuiceJamesServer server) throws Exception {
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

        // Given a mail sent to two recipients
        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            sender.connect(JAMES_SERVER_HOST, smtpPort);
            sender.sendMessageWithHeaders("bob@apache.org", ImmutableList.of(JAMES_USER, OTHER_USER, YET_ANOTHER_USER),
                message.replace("banana", "UUID " + UUID.randomUUID()));
        }
        CALMLY_AWAIT.untilAsserted(() -> assertThat(server.getProbe(SpoolerProbe.class).processingFinished()).isTrue());

        // When the mails are received
        try (TestIMAPClient reader = new TestIMAPClient()) {
            reader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(JAMES_USER, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);
            reader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(OTHER_USER, PASSWORD_OTHER)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);
            reader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(YET_ANOTHER_USER, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);
        }

        // Then the blobs are deduplicated
        // (1 mail transiting in the mail queue that is not deleted
        // and  mailbox messages with headers and body)
        // = 1 header (mailqueue) + 1 header (mailbox message) + one body
        // = 3 blobs
        CassandraProbe probe = server.getProbe(CassandraProbe.class);
        ClusterConfiguration cassandraConfiguration = probe.getConfiguration();
        try (CqlSession session = ClusterFactory.create(cassandraConfiguration, probe.getMainKeyspaceConfiguration())) {
            assertThat(session.execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME).all().build()))
                .hasSize(3);
        }
    }
}