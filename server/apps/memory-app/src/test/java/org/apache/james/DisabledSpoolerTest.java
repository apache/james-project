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
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JmapJamesServerContract.JAMES_SERVER_HOST;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailetcontainer.impl.JamesMailSpooler;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DisabledSpoolerTest {
    private static final String MESSAGE = "Subject: test\r\n" +
        "\r\n" +
        "testmail";

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(JamesMailSpooler.Configuration.class)
                .toInstance(new JamesMailSpooler.Configuration(0, MailRepositoryUrl.from("memory://repo1")))))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @RegisterExtension
    SMTPMessageSender smtpMessageSender = new SMTPMessageSender(DOMAIN);
    @RegisterExtension
    TestIMAPClient testIMAPClient = new TestIMAPClient();

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        DataProbeImpl dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.fluent()
            .addDomain(DOMAIN)
            .addUser(ALICE.asString(), ALICE_PASSWORD)
            .addUser(BOB.asString(), BOB_PASSWORD);
    }

    @Test
    void emailsShouldNotBeDequeuedWhenZeroSpoolerThreads(GuiceJamesServer server) throws Exception {
        smtpMessageSender.connect(LOCALHOST_IP, server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), ALICE_PASSWORD)
            .sendMessageWithHeaders(ALICE.asString(), BOB.asString(), MESSAGE);

        Thread.sleep(5000);

        long messageCount = testIMAPClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, BOB_PASSWORD)
            .getMessageCount(MailboxConstants.INBOX);
        assertThat(messageCount).isEqualTo(0L);
    }
}
