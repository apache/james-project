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
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EnforcedHeaderLimitationHookIntegrationTest {
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

    @Test
    void shouldRejectWhenTooManyHeaderLines() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-EnforceHeader.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> RET=HDRS");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");

        StringBuilder headers = new StringBuilder();
        headers.append("From: bob@localhost\r\n");
        headers.append("To: rcpt@localhost\r\n");
        headers.append("HEADER1: value1\r\n");
        headers.append("HEADER2: value2\r\n");
        headers.append("HEADER3: value3\r\n");
        smtpProtocol.sendShortMessageData(headers.toString() + "Subject: test mail\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n\"");

        assertThat(smtpProtocol.getReplyString())
            .as("expected 552 error")
            .isEqualTo("552 Header Lines are too many\r\n");
    }

    @Test
    void shouldRejectWhenSizeTooLarge() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-EnforceHeader.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> RET=HDRS");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");

        StringBuilder headers = new StringBuilder();
        headers.append("From: bob@localhost\r\n");
        headers.append("HEADER1: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaazertyuytgyuetgfrugreyryutgryuryfguyrtgfruyrftguyyurgryegyugfreyurgiopqsdfghjklwxfyhgyuguygftfytfytfytftfteffsrzfztzzftztdtfcvbnzertyuiosdfghjkjnnzaesfrvfdt\r\n");
        headers.append("HEADER2: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaazertyuighgghgguygjygyugyuguyyfuftyfytfyfythfytfryefopqsdfghjklwxfyhgyuguygftfytfytfytftfteffsrzfztzzftztdtfcvbnzertyuiosdfghjkjnnzaesfrvfdt\r\n");

        smtpProtocol.sendShortMessageData(headers.toString() + "Subject: test mail for the smtp sever to check if the hook that controls the limits of lines and size allowed is correctly stopping the server from excepting ecxeeded quota iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii \r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n\"");

        assertThat(smtpProtocol.getReplyString())
            .as("expected 552 error")
            .isEqualTo("552 Header size is too large\r\n");
    }

    @Test
    void shouldSendMessageWhenWithinLimits() throws Exception {
        testSystem.smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-EnforceHeader.xml")));
        testSystem.smtpServer.init();

        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> RET=HDRS");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("From: bob@localhost\r\nFrom: bob@localhost\r\nto: aziz@localhost\r\n\r\nTest body testSimpleMailSendWithDSN\r\n.\r\n");

        assertThat(smtpProtocol.getReplyCode())
            .as("expected message to be sent")
            .isEqualTo(250);
    }

    private void authenticate(SMTPClient smtpProtocol) throws IOException {
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
    }
}
