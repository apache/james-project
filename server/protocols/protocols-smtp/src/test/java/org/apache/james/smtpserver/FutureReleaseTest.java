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

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.queue.api.ManageableMailQueue;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.chrono.ChronoZonedDateTime;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.smtpserver.SMTPServerTestSystem.BOB;
import static org.apache.james.smtpserver.SMTPServerTestSystem.DATE;
import static org.apache.james.smtpserver.SMTPServerTestSystem.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

class FutureReleaseTest {
    private  final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

    @BeforeEach
    void setUp() throws Exception {
        testSystem.setUp("smtpserver-futurerelease.xml");
    }

    @AfterEach
    void tearDown() {
        testSystem.smtpServer.destroy();
    }

    @Test
    void rejectFutureReleaseUsageWhenUnauthenticated() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        smtpProtocol.sendCommand("EHLO whatever.tld");
        smtpProtocol.sendCommand("MAIL FROM: <bob@whatever.tld> HOLDFOR=83200");

        assertThat(smtpProtocol.getReplyString()).isEqualTo("554 Needs to be logged in in order to use future release extension\r\n");
    }

    @Test
    void ehloShouldAdvertiseFutureReleaseExtension() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).contains("250 FUTURERELEASE 86400 2023-04-15T10:00:00Z");
        });
    }

    @Test
    void ehloShouldNotAdvertiseFutureReleaseExtensionWhenUnauthenticated() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).doesNotContain("250 FUTURERELEASE 86400 2023-04-15T10:00:00Z");
        });
    }

    private void authenticate(SMTPClient smtpProtocol) throws IOException {
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
    }

    @Test
    void testSuccessCaseWithHoldForParams() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=83200");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithFutureRelease\r\n.\r\n");

        ManageableMailQueue.MailQueueIterator browse = testSystem.queue.browse();
        assertThat(browse.hasNext()).isTrue();
        assertThat(browse.next().getNextDelivery().map(ChronoZonedDateTime::toInstant))
            .contains(DATE.plusSeconds(83200));
    }

    @Test
    void testSuccessCaseWithHoldUntilParams() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=2023-04-14T10:30:00Z");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithFutureRelease\r\n.\r\n");

        ManageableMailQueue.MailQueueIterator browse = testSystem.queue.browse();
        assertThat(browse.hasNext()).isTrue();
        assertThat(browse.next().getNextDelivery().map(ChronoZonedDateTime::toInstant))
            .contains(Instant.parse("2023-04-14T10:30:00Z"));
    }

    @Test
    void mailShouldBeRejectedWhenExceedingMaxFutureReleaseInterval() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=93200");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor is greater than max-future-release-interval or holdUntil exceeded max-future-release-date-time");
        });
    }

    @Test
    void mailShouldBeRejectedWhenInvalidHoldFor() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=BAD");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 Incorrect syntax when handling FUTURE-RELEASE mail parameter");
        });
    }

    @Test
    void mailShouldBeRejectedWhenInvalidHoldUntil() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=BAD");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isNotEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).doesNotContain("250");
        });
    }

    @Test
    void mailShouldBeRejectedWhenHoldUntilIsADate() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=2023-04-15");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isNotEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).doesNotContain("250");
        });
    }

    @Test
    void mailShouldBeRejectedWhenMaxFutureReleaseDateTime() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=2023-04-15T11:00:00Z");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor is greater than max-future-release-interval or holdUntil exceeded max-future-release-date-time");
        });
    }

    @Test
    void mailShouldBeRejectedWhenHoldForIsNegative() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=-30");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor value is negative or holdUntil value is before now");
        });
    }

    @Test
    void mailShouldBeRejectedWhenHoldUntilBeforeNow() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=2023-04-13T05:00:00Z");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor value is negative or holdUntil value is before now");
        });
    }

    @Test
    void mailShouldBeRejectedWhenMailParametersContainBothHoldForAndHoldUntil() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=83017 HOLDUNTIL=2023-04-12T11:00:00Z");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor value is negative or holdUntil value is before now");
        });
    }

    @Test
    void mailShouldBeSentWhenThereIsNoMailParameters() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost>");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithFutureRelease\r\n.\r\n");

        assertThat(testSystem.queue.getSize()).isEqualTo(1L);
    }
}
