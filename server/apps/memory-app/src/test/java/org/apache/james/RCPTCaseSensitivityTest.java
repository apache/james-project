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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RCPTCaseSensitivityTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), BOB_PASSWORD)
            .addUser(ALICE.asString(), ALICE_PASSWORD);
    }

    @Test
    void sendMailUsingSMTPAsInternalUserThenRcptShouldBeCaseInsensitive(GuiceJamesServer guiceJamesServer) throws Exception {
        // send mail using SMTP as bob@domain.tld to alice@domain.tld
        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect(LOCALHOST_IP, guiceJamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
        authenticateAsBob(smtpProtocol);
        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@domain.tld>");
        smtpProtocol.sendCommand("RCPT TO:<aLICe@domain.tld>");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body.\r\n.\r\n");

        // alice@domain.tld should receive the mail
        calmlyAwait.atMost(Duration.ofMinutes(1))
            .untilAsserted(() -> {
                TestIMAPClient testIMAPClient = new TestIMAPClient();
                long messageCount = testIMAPClient.connect(LOCALHOST_IP, guiceJamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                    .login(ALICE.asString(), ALICE_PASSWORD)
                    .getMessageCount("INBOX");
                assertThat(messageCount).isEqualTo(1);
            });
    }

    @Test
    void sendMailUsingSMTPAsExternalUserThenRcptShouldBeCaseInsensitive(GuiceJamesServer guiceJamesServer) throws Exception {
        // send mail using SMTP as external.user@example.com to alice@domain.tld
        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect(LOCALHOST_IP, guiceJamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <external.user@example.com>");
        smtpProtocol.sendCommand("RCPT TO:<aLICe@domain.tld>");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body.\r\n.\r\n");

        // alice@domain.tld should receive the mail
        calmlyAwait.atMost(Duration.ofMinutes(1))
            .untilAsserted(() -> {
                TestIMAPClient testIMAPClient = new TestIMAPClient();
                long messageCount = testIMAPClient.connect(LOCALHOST_IP, guiceJamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                    .login(ALICE.asString(), ALICE_PASSWORD)
                    .getMessageCount("INBOX");
                assertThat(messageCount).isEqualTo(1);
            });
    }

    private void authenticateAsBob(SMTPClient smtpProtocol) throws IOException {
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + BOB_PASSWORD + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
    }
}
