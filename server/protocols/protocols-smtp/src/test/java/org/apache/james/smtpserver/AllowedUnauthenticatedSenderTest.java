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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AllowedUnauthenticatedSenderTest {

    @Nested
    class WithNullSenderForbidden {
        private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

        @BeforeEach
        void setUp() throws Exception {
            testSystem.setUp("smtpserver-allowed-unauthenticated-sender.xml");
        }

        @AfterEach
        void tearDown() {
            testSystem.smtpServer.destroy();
        }

        @Test
        void unauthenticatedSenderShouldBeAcceptedWhenInAllowedList() throws Exception {
            SMTPClient smtpProtocol = new SMTPClient();
            InetSocketAddress bindedAddress = testSystem.getBindedAddress();
            smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

            smtpProtocol.sendCommand("EHLO localhost");
            smtpProtocol.sendCommand("MAIL FROM: <allowed@example.com>");

            assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
        }

        @Test
        void unauthenticatedSenderShouldBeRejectedWhenNotInAllowedList() throws Exception {
            SMTPClient smtpProtocol = new SMTPClient();
            InetSocketAddress bindedAddress = testSystem.getBindedAddress();
            smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

            smtpProtocol.sendCommand("EHLO localhost");
            smtpProtocol.sendCommand("MAIL FROM: <forbidden@example.com>");

            assertThat(smtpProtocol.getReplyCode()).isEqualTo(550);
        }

        @Test
        void unauthenticatedSenderWithIpRestrictionShouldBeRejectedFromWrongIp() throws Exception {
            // ip-restricted@example.com is only allowed from 172.34.56.0/24
            // but we are connecting from 127.0.0.1
            SMTPClient smtpProtocol = new SMTPClient();
            InetSocketAddress bindedAddress = testSystem.getBindedAddress();
            smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

            smtpProtocol.sendCommand("EHLO localhost");
            smtpProtocol.sendCommand("MAIL FROM: <ip-restricted@example.com>");

            assertThat(smtpProtocol.getReplyCode()).isEqualTo(550);
        }

        @Test
        void nullSenderShouldBeRejectedWhenAllowNullSenderIsFalse() throws Exception {
            SMTPClient smtpProtocol = new SMTPClient();
            InetSocketAddress bindedAddress = testSystem.getBindedAddress();
            smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

            smtpProtocol.sendCommand("EHLO localhost");
            smtpProtocol.sendCommand("MAIL FROM: <>");

            assertThat(smtpProtocol.getReplyCode()).isEqualTo(550);
        }

        @Test
        void authenticatedUserShouldBypassAllowedSenderRestriction() throws Exception {
            SMTPClient smtpProtocol = new SMTPClient();
            InetSocketAddress bindedAddress = testSystem.getBindedAddress();
            smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            authenticate(smtpProtocol);

            smtpProtocol.sendCommand("EHLO localhost");
            smtpProtocol.sendCommand("MAIL FROM: <forbidden@example.com>");

            assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
        }

        private void authenticate(SMTPClient smtpProtocol) throws IOException {
            smtpProtocol.sendCommand("AUTH PLAIN");
            smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
            assertThat(smtpProtocol.getReplyCode())
                .as("authenticated")
                .isEqualTo(235);
        }
    }

    @Nested
    class WithNullSenderAllowed {
        private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

        @BeforeEach
        void setUp() throws Exception {
            testSystem.setUp("smtpserver-allowed-unauthenticated-sender-allow-null.xml");
        }

        @AfterEach
        void tearDown() {
            testSystem.smtpServer.destroy();
        }

        @Test
        void nullSenderShouldBeAcceptedWhenAllowNullSenderIsTrue() throws Exception {
            SMTPClient smtpProtocol = new SMTPClient();
            InetSocketAddress bindedAddress = testSystem.getBindedAddress();
            smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

            smtpProtocol.sendCommand("EHLO localhost");
            smtpProtocol.sendCommand("MAIL FROM: <>");

            assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
        }

        @Test
        void nonNullSenderShouldStillBeCheckedAgainstAllowedList() throws Exception {
            SMTPClient smtpProtocol = new SMTPClient();
            InetSocketAddress bindedAddress = testSystem.getBindedAddress();
            smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

            smtpProtocol.sendCommand("EHLO localhost");
            smtpProtocol.sendCommand("MAIL FROM: <forbidden@example.com>");

            assertThat(smtpProtocol.getReplyCode()).isEqualTo(550);
        }
    }
}
