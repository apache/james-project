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

package org.apache.james.protocols.pop3.sasl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.junit.jupiter.api.Test;

class Pop3SaslBridgeTest {
    private static final Username USER = Username.of("user@example.com");
    private static final SaslIdentity IDENTITY = new SaslIdentity(USER, USER);

    private final Pop3SaslBridge testee = new Pop3SaslBridge();

    private static class RecordingExchange implements SaslExchange {
        private byte[] lastClientResponse;
        private boolean closed;

        @Override
        public SaslStep firstStep() {
            return new SaslStep.Challenge(Optional.empty());
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            lastClientResponse = clientResponse.clone();
            return new SaslStep.Success(IDENTITY, Optional.empty());
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void initialRequestShouldDecodeCanonicalBase64() {
        SaslInitialRequest request = testee.initialRequest("PLAIN", Optional.of(encoded("initial")));

        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("initial")));
    }

    @Test
    void initialRequestShouldDecodeEqualSignAsEmptyResponse() {
        SaslInitialRequest request = testee.initialRequest("PLAIN", Optional.of("="));

        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).isEmpty());
    }

    @Test
    void initialRequestShouldRejectMissingPadding() {
        assertThatThrownBy(() -> testee.initialRequest("PLAIN", Optional.of("YQ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialRequestShouldRejectMisplacedPadding() {
        assertThatThrownBy(() -> testee.initialRequest("PLAIN", Optional.of("Y=Q=")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void challengeShouldEncodePop3Continuation() {
        Response response = testee.challenge(new SaslStep.Challenge(Optional.of(bytes("challenge"))));

        assertThat(response.getLines()).containsExactly("+ " + encoded("challenge"));
    }

    @Test
    void challengeShouldPreserveSpaceForEmptyPayload() {
        Response response = testee.challenge(new SaslStep.Challenge(Optional.empty()));

        assertThat(response.getLines()).containsExactly("+ ");
    }

    @Test
    void successDataShouldEncodePop3Continuation() {
        Response response = testee.successData(new SaslStep.Success(IDENTITY, Optional.of(bytes("server-data"))));

        assertThat(response.getLines()).containsExactly("+ " + encoded("server-data"));
    }

    @Test
    void onClientResponseShouldDecodeContinuation() {
        RecordingExchange exchange = new RecordingExchange();

        SaslStep result = testee.onClientResponse(exchange, (encoded("response") + "\r\n").getBytes(StandardCharsets.US_ASCII));

        assertThat(result).isInstanceOf(SaslStep.Success.class);
        assertThat(exchange.lastClientResponse).containsExactly(bytes("response"));
    }

    @Test
    void onClientResponseShouldAcceptEmptyResponse() {
        RecordingExchange exchange = new RecordingExchange();

        testee.onClientResponse(exchange, "\r\n".getBytes(StandardCharsets.US_ASCII));

        assertThat(exchange.lastClientResponse).isEmpty();
    }

    @Test
    void onClientResponseShouldRejectMissingPadding() {
        RecordingExchange exchange = new RecordingExchange();

        assertThatThrownBy(() -> testee.onClientResponse(exchange, "YQ\r\n".getBytes(StandardCharsets.US_ASCII)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isAbortShouldOnlyAcceptSingleAsterisk() {
        assertThat(testee.isAbort("*\r\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(testee.isAbort("**\r\n".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void isEmptyClientResponseShouldAcceptEmptyLine() {
        assertThat(testee.isEmptyClientResponse("\r\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(testee.isEmptyClientResponse("=\r\n".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void abortShouldUseCombinedExchangeLifecycleContract() {
        RecordingExchange exchange = new RecordingExchange();

        testee.abort(exchange);

        assertThat(exchange.closed).isTrue();
    }

    private static String encoded(String value) {
        return Base64.getEncoder().encodeToString(bytes(value));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
