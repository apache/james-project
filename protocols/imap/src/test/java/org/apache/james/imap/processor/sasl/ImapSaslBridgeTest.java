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

package org.apache.james.imap.processor.sasl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslProtocol;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.junit.jupiter.api.Test;

class ImapSaslBridgeTest {
    private static final Username USER = Username.of("user@example.com");
    private static final SaslIdentity IDENTITY = new SaslIdentity(USER, USER);

    private final ImapSaslBridge testee = new ImapSaslBridge();

    private static class RecordingExchange implements SaslExchange {
        private final List<String> lifecycleEvents;
        private byte[] lastClientResponse;

        private RecordingExchange() {
            this.lifecycleEvents = new ArrayList<>();
        }

        @Override
        public SaslStep firstStep() {
            return new SaslStep.Challenge(Optional.of(bytes("challenge")));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            lastClientResponse = clientResponse.clone();
            return new SaslStep.Success(IDENTITY, Optional.empty());
        }

        @Override
        public void abort() {
            lifecycleEvents.add("abort");
        }

        @Override
        public void close() {
            lifecycleEvents.add("close");
        }
    }

    @Test
    void initialRequestShouldDecodeInitialClientResponse() {
        String encodedInitialResponse = Base64.getEncoder().encodeToString(bytes("initial"));

        SaslInitialRequest request = testee.initialRequest("PLAIN", Optional.of(encodedInitialResponse));

        assertThat(request.protocol()).isEqualTo(SaslProtocol.IMAP);
        assertThat(request.mechanismName()).isEqualTo("PLAIN");
        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("initial")));
    }

    @Test
    void initialRequestShouldDecodeEqualSignAsEmptyInitialClientResponse() {
        SaslInitialRequest request = testee.initialRequest("PLAIN", Optional.of("="));

        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).isEmpty());
    }

    @Test
    void continuationShouldBase64EncodeChallengePayload() {
        SaslStep.Challenge challenge = new SaslStep.Challenge(Optional.of(bytes("challenge")));

        String continuation = testee.continuation(challenge);

        assertThat(continuation).isEqualTo(Base64.getEncoder().encodeToString(bytes("challenge")));
    }

    @Test
    void continuationShouldReturnEmptyStringWhenChallengeHasNoPayload() {
        SaslStep.Challenge challenge = new SaslStep.Challenge(Optional.empty());

        String continuation = testee.continuation(challenge);

        assertThat(continuation).isEmpty();
    }

    @Test
    void onClientResponseShouldDecodeLineAndContinueExchange() {
        RecordingExchange exchange = new RecordingExchange();
        byte[] line = (Base64.getEncoder().encodeToString(bytes("response")) + "\r\n").getBytes(StandardCharsets.US_ASCII);

        SaslStep step = testee.onClientResponse(exchange, line);

        assertThat(((SaslStep.Success) step).identity()).isEqualTo(IDENTITY);
        assertThat(exchange.lastClientResponse).containsExactly(bytes("response"));
    }

    @Test
    void onClientResponseShouldDecodeLineWithLfOnly() {
        RecordingExchange exchange = new RecordingExchange();
        byte[] line = (Base64.getEncoder().encodeToString(bytes("response")) + "\n").getBytes(StandardCharsets.US_ASCII);

        SaslStep step = testee.onClientResponse(exchange, line);

        assertThat(((SaslStep.Success) step).identity()).isEqualTo(IDENTITY);
        assertThat(exchange.lastClientResponse).containsExactly(bytes("response"));
    }

    @Test
    void isAbortShouldDetectImapSaslAbortLine() {
        assertThat(testee.isAbort("*\r\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }

    @Test
    void isAbortShouldDetectImapSaslAbortLineWithLfOnly() {
        assertThat(testee.isAbort("*\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }

    @Test
    void isAbortShouldRejectRegularClientResponse() {
        byte[] line = (Base64.getEncoder().encodeToString(bytes("response")) + "\r\n").getBytes(StandardCharsets.US_ASCII);

        assertThat(testee.isAbort(line)).isFalse();
    }

    @Test
    void abortShouldAbortThenCloseExchange() {
        RecordingExchange exchange = new RecordingExchange();

        testee.abort(exchange);

        assertThat(exchange.lifecycleEvents).containsExactly("abort", "close");
    }

    @Test
    void closeShouldCloseExchange() {
        RecordingExchange exchange = new RecordingExchange();

        testee.close(exchange);

        assertThat(exchange.lifecycleEvents).containsExactly("close");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
