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

package org.apache.james.imapserver.netty;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.commons.net.imap.IMAPReply;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class IMAPServerSaslExchangeLifecycleTest extends AbstractIMAPServerTest {
    private static final String CHALLENGE = "Y2hhbGxlbmdl";
    private static final String CLIENT_RESPONSE = "cmVzcG9uc2U=";

    private static class RecordingSaslMechanism implements SaslMechanism {
        private final AtomicInteger closeCount;
        private final AtomicInteger abortCount;

        private RecordingSaslMechanism(AtomicInteger closeCount, AtomicInteger abortCount) {
            this.closeCount = closeCount;
            this.abortCount = abortCount;
        }

        @Override
        public String name() {
            return "RECORDING";
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
            return new SaslExchange() {
                @Override
                public SaslStep firstStep() {
                    return new SaslStep.Challenge(Optional.of("challenge".getBytes(US_ASCII)));
                }

                @Override
                public SaslStep onResponse(byte[] clientResponse) {
                    return new SaslStep.Failure(SaslFailure.authenticationFailed(
                        Optional.empty(), Optional.empty(), "Test-only mechanism"));
                }

                @Override
                public void abort() {
                    abortCount.incrementAndGet();
                }

                @Override
                public void close() {
                    closeCount.incrementAndGet();
                }
            };
        }
    }

    private final AtomicInteger closeCount = new AtomicInteger();
    private final AtomicInteger abortCount = new AtomicInteger();

    private IMAPServer imapServer;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        imapServer = createImapServer("imapServer.xml",
            ImmutableList.of(new RecordingSaslMechanism(closeCount, abortCount)));
        port = imapServer.getListenAddresses().get(0).getPort();
    }

    @AfterEach
    void tearDown() {
        if (imapServer != null) {
            imapServer.destroy();
        }
    }

    @Test
    void disconnectDuringSaslContinuationShouldCloseExchangeOnce() throws Exception {
        IMAPClient client = connectedClient();
        try {
            assertThat(client.sendCommand("AUTHENTICATE RECORDING")).isEqualTo(IMAPReply.CONT);
            assertThat(client.getReplyString()).contains("+ " + CHALLENGE);
        } finally {
            client.disconnect();
        }

        Awaitility.await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(closeCount.get()).isEqualTo(1));
    }

    @Test
    void abortDuringSaslContinuationShouldAbortExchangeOnce() throws Exception {
        IMAPClient client = connectedClient();
        try {
            assertThat(client.sendCommand("AUTHENTICATE RECORDING")).isEqualTo(IMAPReply.CONT);
            assertThat(client.getReplyString()).contains("+ " + CHALLENGE);
            assertThat(client.sendData("*")).isEqualTo(IMAPReply.NO);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed.");
        } finally {
            client.disconnect();
        }

        Awaitility.await().during(Duration.ofMillis(100)).atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> {
                assertThat(abortCount.get()).isEqualTo(1);
                assertThat(closeCount.get()).isZero();
            });
    }

    @Test
    void disconnectAfterTerminalSaslStepShouldNotCloseExchangeAgain() throws Exception {
        IMAPClient client = connectedClient();
        try {
            assertThat(client.sendCommand("AUTHENTICATE RECORDING")).isEqualTo(IMAPReply.CONT);
            assertThat(client.getReplyString()).contains("+ " + CHALLENGE);
            assertThat(client.sendData(CLIENT_RESPONSE)).isEqualTo(IMAPReply.NO);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed.");
        } finally {
            client.disconnect();
        }

        Awaitility.await().during(Duration.ofMillis(100)).atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(closeCount.get()).isEqualTo(1));
    }

    private IMAPClient connectedClient() throws IOException {
        IMAPClient client = new IMAPClient();
        client.connect("127.0.0.1", port);
        return client;
    }
}
