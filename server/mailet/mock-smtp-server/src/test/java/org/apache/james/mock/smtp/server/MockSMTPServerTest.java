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

package org.apache.james.mock.smtp.server;

import static org.apache.james.mock.smtp.server.Fixture.ALICE;
import static org.apache.james.mock.smtp.server.Fixture.BOB;
import static org.apache.james.mock.smtp.server.Fixture.DOMAIN;
import static org.apache.james.mock.smtp.server.Fixture.JACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.util.List;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.Response;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.Port;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.base.test.FakeMail;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.github.fge.lambdas.Throwing;

class MockSMTPServerTest {

    private static final Response.SMTPStatusCode SERVICE_NOT_AVAILABLE_421 = Response.SMTPStatusCode.of(421);

    @Nested
    class NormalBehaviorTests {
        private MockSMTPServer mockServer;

        @BeforeEach
        void setUp() {
            mockServer = new MockSMTPServer();
            mockServer.start();
        }

        @AfterEach
        void tearDown() {
            mockServer.stop();
        }

        @Test
        void serverShouldReceiveMessageFromClient() throws Exception {
            SMTPMessageSender sender = new SMTPMessageSender(DOMAIN)
                .connect("localhost", mockServer.getPort());

            MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText("any text")
            .build();

            FakeMail mail = FakeMail.builder()
                .name("name")
                .sender(BOB)
                .recipients(ALICE, JACK)
                .mimeMessage(message)
                .build();

            sender.sendMessage(mail);

            Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted(() -> {
                    List<Mail> mails = mockServer.listReceivedMails();
                    Mail.Envelope expectedEnvelope = new Mail.Envelope(
                        new MailAddress(BOB),
                        ImmutableList.of(new MailAddress(ALICE), new MailAddress(JACK)));
                    assertThat(mails)
                        .hasSize(1)
                        .allSatisfy(Throwing.consumer(assertedMail -> {
                            assertThat(assertedMail.getEnvelope()).isEqualTo(expectedEnvelope);
                            assertThat(assertedMail.getMessage()).contains(MimeMessageUtil.asString(message));
                        }));
                });
        }
    }

    @Test
    void serverStartShouldOpenASmtpPort() {
        MockSMTPServer mockServer = new MockSMTPServer();
        mockServer.start();

        assertThatCode(() -> new SMTPMessageSender(DOMAIN)
                .connect("localhost", mockServer.getPort()))
            .doesNotThrowAnyException();
    }

    @Test
    void serverShouldBeAbleToStop() {
        MockSMTPServer mockServer = new MockSMTPServer();
        mockServer.start();
        Port port = mockServer.getPort();

        mockServer.stop();
        assertThatThrownBy(() -> new SMTPMessageSender(DOMAIN)
                .connect("localhost", port))
            .isInstanceOf(ConnectException.class)
            .hasMessage("Connection refused (Connection refused)");
    }

    @Test
    void serverStartShouldBeIdempotent() {
        MockSMTPServer mockServer = new MockSMTPServer();
        mockServer.start();

        assertThatCode(() -> mockServer.start())
            .doesNotThrowAnyException();
    }
}