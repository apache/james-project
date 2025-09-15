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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfiguredAuthTest {
    private final SMTPServerTestSystem smtpServerTestSystem = new SMTPServerTestSystem();

    @BeforeEach
    void setUp() throws Exception {
        smtpServerTestSystem.setUp("smtpserver-configured-auth.xml");
    }

    @AfterEach
    void tearDown() {
        smtpServerTestSystem.smtpServer.destroy();
    }


    @ParameterizedTest
    @CsvSource({
        "noreply-tdrive@domain.tld, secret123456",
        "noreply-tdrive@domain.tld, here_to_ease_secret_rotation",
        "noreply-tdrive@domain.tld, here_to_give_different_creds_to_each_app",
        "noreply-tchat@domain.tld, secret234567",
    })
    void authShouldBePossibleWHenMatchingConfiguredValue(String username, String password) throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = smtpServerTestSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + username + "\0" + password + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .isEqualTo(235);
    }

    @ParameterizedTest
    @CsvSource({
        "noreply-tdrive@domain.tld, bad",
        "noreply-notfound@domain.tld, here_to_ease_secret_rotation",
        "noreply-tdrive@other-domain.tld, secret123456",
        "noreply-tdrive, secret123456"
    })
    void authShouldBeDeniedWhenMatchingConfiguredValue(String username, String password) throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = smtpServerTestSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + username + "\0" + password + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .isEqualTo(535);
    }
}
