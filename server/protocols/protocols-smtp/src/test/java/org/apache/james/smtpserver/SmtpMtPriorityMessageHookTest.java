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
import org.apache.mailet.AttributeName;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SmtpMtPriorityMessageHookTest {

    private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();
    private static final AttributeName MAIL_PRIORITY_ATTRIBUTE_NAME = AttributeName.of("MAIL_PRIORITY");

    @BeforeEach
    void setUp() throws Exception {
        testSystem.setUp("smtpserver-mtPriority.xml");
    }

    @AfterEach
    void tearDown() {
        testSystem.smtpServer.destroy();
    }

    @Test
    void ehloShouldAdvertiseMtPriorityExtension() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).contains("250 MT-PRIORITY");
        });
    }

    @ParameterizedTest
    @CsvSource({"-9,-9", "-8,-8", "-7,-7", "-6,-6", "-5,-5", "-4,-4", "-3,-3", "-2,-2", "-1,-1", "0,0", "1,1",
        "2,2", "3,3", "4,4", "5,5", "6,6", "7,7", "8,8", "9,9"})
    void mtPriorityParametersShouldBeSetOnTheFinalEmailAsMailSupportPriorityValue(String inputPriorityValue, String expectedPriorityValue) throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> MT-PRIORITY=" + inputPriorityValue);
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("From: bob@localhost\r\n\r\nSubject: test mail\r\n\r\nTest body testSimpleMailSendWithMtPriority\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();

        assertThat(lastMail.getAttribute(MAIL_PRIORITY_ATTRIBUTE_NAME))
            .hasValueSatisfying(v -> assertThat(v.getValue().getValue().toString()).isEqualTo(expectedPriorityValue));

        assertThat(lastMail.getMessage().getHeader("Received"))
            .hasOnlyOneElementSatisfying(s -> assertThat(s).contains("(PRIORITY " + inputPriorityValue + ")"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "null", "-10", "10"})
    void mailShouldBeRejectedWhenInvalidMtPriorityParameters(String incorrectPriorityValue) throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> MT-PRIORITY=" + incorrectPriorityValue);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 Incorrect syntax when handling MT-PRIORITY mail parameter");
        });
    }

    @Test
    void mailShouldBeRejectedWhenInvalidMtPriorityParameterIsDuplicated() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> MT-PRIORITY=3 MT-PRIORITY=4");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 The Mail parameter cannot contain more than one MT-PRIORITY parameter at the same time");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-2", "-3", "-4", "-5", "-6", "-7", "-8", "-9", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"})
    void shouldSetDefaultPriorityWhenUnauthenticated(String priorityValue) throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        smtpProtocol.sendCommand("EHLO whatever.tld");
        smtpProtocol.sendCommand("MAIL FROM: <bob@whatever.tld> MT-PRIORITY=" + priorityValue);
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("From: bob@whatever.tld\r\n\r\nSubject: test mail\r\n\r\nTest body testSimpleMailSendWithMtPriority\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();

        assertThat(lastMail.getAttribute(MAIL_PRIORITY_ATTRIBUTE_NAME))
            .hasValueSatisfying(v -> assertThat((int) v.getValue().getValue()).isNotPositive());
    }

    private void authenticate(SMTPClient smtpProtocol) throws IOException {
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
    }
}