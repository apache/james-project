/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james;

import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.apache.james.user.ldap.DockerLdapSingleton.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.core.Domain;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraLdapJamesServerTest implements JamesServerContract {
    private static Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    private static ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    private IMAPClient imapClient = new IMAPClient();

    @RegisterExtension
    TestIMAPClient testIMAPClient = new TestIMAPClient();
    SMTPMessageSender messageSender = new SMTPMessageSender(Domain.LOCALHOST.asString());

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .extension(new LdapTestExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CassandraLdapJamesServerMain.MODULES)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(DOMAIN_LIST_CONFIGURATION_MODULE))
        .build();

    @Test
    void userFromLdapShouldLoginViaImapProtocol(GuiceJamesServer server) throws Exception {
        imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

        assertThat(imapClient.login(JAMES_USER.asString(), PASSWORD)).isTrue();
    }

    @Test
    void mailsShouldBeWellReceivedBeforeFirstUserConnectionWithLdap(GuiceJamesServer server) throws Exception {
        messageSender.connect(JAMES_SERVER_HOST, server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("bob@any.com", JAMES_USER.asString() + "@localhost");

        calmlyAwait.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        testIMAPClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(JAMES_USER, PASSWORD)
            .select("INBOX")
            .awaitMessage(calmlyAwait);
    }
}
