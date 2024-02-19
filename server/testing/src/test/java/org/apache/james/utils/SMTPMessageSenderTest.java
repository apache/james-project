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

package org.apache.james.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.Domain;
import org.apache.james.util.Port;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.subethamail.wiser.Wiser;

class SMTPMessageSenderTest {

    private static final int RANDOM_PORT = 0;
    private static final String LOCALHOST = "localhost";
    private static final String SENDER = "sender@localhost";
    private static final String RECIPIENT = "receiver@localhost";
    private static final String UNICODE_BODY = "Unicode characters Ê Â á tiếng việt";
    private static final String ASCII_BODY = "ASCII characters E A A tieng viet";

    private Wiser testingSMTPServer;
    private SMTPMessageSender testee;

    @BeforeEach
    void setUp() throws IOException {
        testingSMTPServer = Wiser.port(RANDOM_PORT);
        testingSMTPServer.start();

        testee = new SMTPMessageSender(Domain.LOCALHOST.asString())
            .connect(LOCALHOST, Port.of(testingSMTPServer.getServer().getPortAllocated()));
    }

    @AfterEach
    void teardown() {
        testingSMTPServer.stop();
    }

    @Test
    void sendMessageWithHeadersShouldDeliverUnicodeBodyCharacters() throws IOException {
        testee.sendMessageWithHeaders(SENDER, RECIPIENT, UNICODE_BODY);

        assertThat(testingSMTPServer.getMessages())
            .extracting(message -> new String(message.getData(), StandardCharsets.UTF_8))
            .hasOnlyOneElementSatisfying(messageContent ->
                assertThat(messageContent)
                    .contains(UNICODE_BODY));
    }

    @Test
    void sendMessageWithHeadersShouldDeliverASCIIBodyCharacters() throws IOException {
        testee.sendMessageWithHeaders(SENDER, RECIPIENT, ASCII_BODY);

        assertThat(testingSMTPServer.getMessages())
            .extracting(message -> new String(message.getData(), StandardCharsets.UTF_8))
            .hasOnlyOneElementSatisfying(messageContent ->
                assertThat(messageContent)
                    .contains(ASCII_BODY));
    }

    @Test
    void sendMessageWithHeadersShouldPreserveRightEnvelop() throws IOException {
        testee.sendMessageWithHeaders(SENDER, RECIPIENT, ASCII_BODY);

        assertThat(testingSMTPServer.getMessages())
            .hasOnlyOneElementSatisfying(message -> {
                assertThat(message.getEnvelopeReceiver())
                    .isEqualTo(RECIPIENT);
                assertThat(message.getEnvelopeSender())
                    .isEqualTo(SENDER);
            });
    }
}