/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james;

import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;

import org.apache.james.core.Domain;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraRabbitMQJamesServerTest implements JmapJamesServerContract {
    private static final String DOMAIN = "domain";
    private static final String JAMES_USER = "james-user@" + DOMAIN;
    private static final String PASSWORD = "secret";
    private static Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    private static ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    private static final int LIMIT_TO_10_MESSAGES = 10;

    private IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    private SMTPMessageSender messageSender = new SMTPMessageSender(Domain.LOCALHOST.asString());

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerExtensionBuilder()
            .extension(new EmbeddedElasticSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
                    .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
                    .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
                    .overrideWith(DOMAIN_LIST_CONFIGURATION_MODULE))
            .build();

    @Test
    void mailsShouldBeWellReceived(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        messageSender.connect(JAMES_SERVER_HOST, server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("bob@any.com", JAMES_USER);

        calmlyAwait.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        imapMessageReader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(JAMES_USER, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait);
    }
}
