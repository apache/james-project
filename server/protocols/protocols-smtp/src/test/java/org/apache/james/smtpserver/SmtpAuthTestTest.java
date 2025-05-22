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
package org.apache.james.smtpserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.smtpserver.SMTPServerTestSystem.BOB;
import static org.apache.james.smtpserver.SMTPServerTestSystem.PASSWORD;
import static org.apache.mailet.DsnParameters.Notify.DELAY;
import static org.apache.mailet.DsnParameters.Notify.FAILURE;
import static org.apache.mailet.DsnParameters.Notify.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.EnumSet;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.core.MailAddress;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmtpAuthTestTest {
    protected Configuration configuration;

    private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

    @BeforeEach
    void setUp() throws Exception {
        testSystem.preSetUp();
    }

    @AfterEach
    void tearDown() {
        testSystem.smtpServer.destroy();
    }

    private void authenticate(SMTPClient smtpProtocol) throws IOException {
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
    }

    @Test
    void shouldSupportAuthParameterInMailFrom() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-dsn.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> AUTH=other@localhost");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("From: bob@localhost\r\n\r\nSubject: test mail\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n");

        assertThat(testSystem.queue.getLastMail().attributesMap().get(Mail.TRUE_SENDER).getValue().getValue())
            .isEqualTo("other@localhost");
    }

    @Test
    void shouldSupportAuthParameterInMailFromWhenNone() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-dsn.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> AUTH=<>");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("From: bob@localhost\r\n\r\nSubject: test mail\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n");

        assertThat(testSystem.queue.getLastMail().attributesMap().get(Mail.TRUE_SENDER).getValue().getValue())
            .isEqualTo("<>");
    }

}
