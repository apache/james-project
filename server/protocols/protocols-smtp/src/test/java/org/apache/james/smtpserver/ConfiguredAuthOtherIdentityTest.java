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
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.Base64;

import org.apache.commons.net.smtp.SMTPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfiguredAuthOtherIdentityTest {
    private static final String END_USER = "bob@example.local";

    private final SMTPServerTestSystem smtpServerTestSystem = new SMTPServerTestSystem();

    @BeforeEach
    void setUp() throws Exception {
        smtpServerTestSystem.setUp("smtpserver-configured-auth-other-identity.xml");
    }

    @AfterEach
    void tearDown() {
        smtpServerTestSystem.smtpServer.destroy();
    }

    private SMTPClient authenticate(String username, String password) throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = smtpServerTestSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + username + "\0" + password + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
        smtpProtocol.login("domain.tld");
        return smtpProtocol;
    }

    @Test
    void mailFromOtherIdentityShouldBeRejectedWhenNotAllowed() throws Exception {
        SMTPClient smtpProtocol = authenticate("noreply-tdrive@domain.tld", "secret123456");

        smtpProtocol.setSender(END_USER);

        assertThat(smtpProtocol.getReplyCode())
            .isEqualTo(503);
    }

    @Test
    void mailFromOtherIdentityShouldBeAcceptedWhenAllowed() throws Exception {
        SMTPClient smtpProtocol = authenticate("noreply-tcalendar@domain.tld", "secret234567");

        smtpProtocol.setSender(END_USER);

        assertThat(smtpProtocol.getReplyCode())
            .isEqualTo(250);
    }

    @Test
    void headerFromOtherIdentityShouldBeAcceptedWhenAllowed() throws Exception {
        SMTPClient smtpProtocol = authenticate("noreply-tcalendar@domain.tld", "secret234567");

        smtpProtocol.setSender(END_USER);
        smtpProtocol.addRecipient("mail@sample.com");
        smtpProtocol.sendShortMessageData("From: " + END_USER + "\r\nSubject: test\r\n\r\nTest body\r\n.\r\n");
        smtpProtocol.quit();

        assertThat(smtpServerTestSystem.queue.getLastMail().getMaybeSender().asString())
            .isEqualTo(END_USER);
    }
}
