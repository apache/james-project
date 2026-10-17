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

import static org.apache.james.smtpserver.SMTPServerTestSystem.LOCAL_DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RFC 6531 SMTPUTF8, exercised through a real {@link org.apache.james.smtpserver.netty.SMTPServer}
 * and the stock handler chain, so that what is covered here is what an operator
 * actually gets: {@code SMTPUTF8Extension} is pulled in by
 * {@link CoreCmdHandlerLoader}, not wired by hand in the test configuration.
 */
class SmtpUtf8AnnounceTest {
    private static final String UTF8_SENDER = "expéditeur@remote.org";
    private static final String UTF8_RECIPIENT = "réception@" + LOCAL_DOMAIN;
    /** RFC 6531 §4.2 rejection: 553 5.6.7. */
    private static final String NON_ASCII_WITHOUT_SMTPUTF8 = "553 5.6.7";

    private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

    @BeforeEach
    void setUp() throws Exception {
        testSystem.setUp("smtpserver-noauth.xml");
    }

    @AfterEach
    void tearDown() {
        testSystem.smtpServer.destroy();
    }

    @Test
    void ehloShouldAnnounceSmtpUtf8() throws Exception {
        SMTPClient smtpProtocol = connect();
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).contains("SMTPUTF8");
        });
    }

    @Test
    void ehloShouldAnnounceSmtpUtf8Once() throws Exception {
        SMTPClient smtpProtocol = connect();
        smtpProtocol.sendCommand("EHLO localhost");

        // EhloCmdHandler appends one line per EhloExtension without
        // deduplicating, so a second handler advertising the keyword would
        // silently produce a duplicate 250- line.
        assertThat(smtpProtocol.getReplyString().split("SMTPUTF8", -1)).hasSize(2);
    }

    @Test
    void unicodeAddressesShouldBeAcceptedWhenSmtpUtf8IsRequested() throws Exception {
        SMTPClient smtpProtocol = connect();
        smtpProtocol.sendCommand("EHLO remote.org");
        smtpProtocol.sendCommand("MAIL FROM: <" + UTF8_SENDER + "> SMTPUTF8");
        assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
        smtpProtocol.sendCommand("RCPT TO:<" + UTF8_RECIPIENT + ">");
        assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
        smtpProtocol.sendShortMessageData("From: " + UTF8_SENDER + "\r\nSubject: test\r\n\r\nbody\r\n.\r\n");

        Mail lastMail = testSystem.queue.getLastMail();
        assertThat(lastMail).isNotNull();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(lastMail.getMaybeSender().asString()).isEqualTo(UTF8_SENDER);
            softly.assertThat(lastMail.getRecipients())
                .extracting(rcpt -> rcpt.asString())
                .containsExactly(UTF8_RECIPIENT);
        });
    }

    @Test
    void nonAsciiSenderShouldBeRejectedWithoutSmtpUtf8() throws Exception {
        SMTPClient smtpProtocol = connect();
        smtpProtocol.sendCommand("EHLO remote.org");
        smtpProtocol.sendCommand("MAIL FROM: <" + UTF8_SENDER + ">");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(553);
            softly.assertThat(smtpProtocol.getReplyString()).contains(NON_ASCII_WITHOUT_SMTPUTF8);
        });
    }

    @Test
    void nonAsciiRecipientShouldBeRejectedWithoutSmtpUtf8() throws Exception {
        SMTPClient smtpProtocol = connect();
        smtpProtocol.sendCommand("EHLO remote.org");
        smtpProtocol.sendCommand("MAIL FROM: <bob@remote.org>");
        assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
        smtpProtocol.sendCommand("RCPT TO:<" + UTF8_RECIPIENT + ">");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(553);
            softly.assertThat(smtpProtocol.getReplyString()).contains(NON_ASCII_WITHOUT_SMTPUTF8);
        });
    }

    @Test
    void asciiEmailWithSmtpUtf8ShouldBeReceived() throws Exception {
        SMTPClient smtpProtocol = connect();
        smtpProtocol.sendCommand("EHLO remote.org");
        smtpProtocol.sendCommand("MAIL FROM: <bob@remote.org> SMTPUTF8");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@" + LOCAL_DOMAIN + ">");
        smtpProtocol.sendShortMessageData("From: bob@remote.org\r\nSubject: test\r\n\r\nbody\r\n.\r\n");

        assertThat(testSystem.queue.getLastMail()).isNotNull();
    }

    private SMTPClient connect() throws IOException {
        SMTPClient smtpProtocol = new SMTPClient("UTF-8");
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        return smtpProtocol;
    }
}
