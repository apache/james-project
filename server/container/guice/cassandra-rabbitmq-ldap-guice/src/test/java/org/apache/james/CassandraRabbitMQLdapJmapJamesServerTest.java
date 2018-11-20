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

import static org.apache.james.JmapJamesServerContract.JAMES_SERVER_HOST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.core.Domain;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.SwiftBlobStoreExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreChoosingConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraRabbitMQLdapJmapJamesServerTest {
    private static final int LIMIT_TO_10_MESSAGES = 10;
    private static final String JAMES_USER = "james-user";
    private static final String PASSWORD = "secret";
    private static Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;

    private static ConditionFactory calmlyAwait = Awaitility
        .with().pollInterval(slowPacedPollInterval)
        .and().with().pollDelay(slowPacedPollInterval)
        .await();

    interface MailsShouldBeWellReceived {
        @RegisterExtension
        IMAPMessageReader imapMessageReader = new IMAPMessageReader();
        SMTPMessageSender messageSender = new SMTPMessageSender(Domain.LOCALHOST.asString());

        @Test
        default void mailsShouldBeWellReceived(GuiceJamesServer server) throws Exception {
            messageSender.connect(JAMES_SERVER_HOST, server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .sendMessage("bob@any.com", JAMES_USER + "@localhost");

            calmlyAwait.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

            imapMessageReader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(JAMES_USER, PASSWORD)
                .select("INBOX")
                .awaitMessage(calmlyAwait);
        }
    }

    interface UserFromLdapShouldLogin {

        @Test
        default void userFromLdapShouldLoginViaImapProtocol(GuiceJamesServer server) throws IOException {
            IMAPClient imapClient = new IMAPClient();
            imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

            assertThat(imapClient.login(JAMES_USER, PASSWORD)).isTrue();
        }
    }

    interface ContractSuite extends JmapJamesServerContract, MailsShouldBeWellReceived, UserFromLdapShouldLogin {}

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithSwift implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = new JamesServerExtensionBuilder()
            .extension(new EmbeddedElasticSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new LdapTestExtention())
            .extension(new SwiftBlobStoreExtension())
            .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
                .combineWith(CassandraRabbitMQLdapJamesServerMain.MODULES)
                .overrideWith(binder -> binder.bind(BlobStoreChoosingConfiguration.class)
                    .toInstance(BlobStoreChoosingConfiguration.objectStorage()))
                .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
                .overrideWith(JmapJamesServerContract.DOMAIN_LIST_CONFIGURATION_MODULE))
            .build();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithoutSwift implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = new JamesServerExtensionBuilder()
            .extension(new EmbeddedElasticSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new LdapTestExtention())
            .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
                .combineWith(CassandraRabbitMQLdapJamesServerMain.MODULES)
                .overrideWith(binder -> binder.bind(BlobStoreChoosingConfiguration.class)
                    .toInstance(BlobStoreChoosingConfiguration.cassandra()))
                .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
                .overrideWith(JmapJamesServerContract.DOMAIN_LIST_CONFIGURATION_MODULE))
            .build();
    }
}
