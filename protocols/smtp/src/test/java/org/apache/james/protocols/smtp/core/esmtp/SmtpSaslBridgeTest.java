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

package org.apache.james.protocols.smtp.core.esmtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.junit.jupiter.api.Test;

class SmtpSaslBridgeTest {
    private static final Username USER = Username.of("user@example.com");
    private static final SaslIdentity IDENTITY = new SaslIdentity(USER, USER);

    private final SmtpSaslBridge testee = new SmtpSaslBridge();

    private static class RecordingExchange implements SaslExchange {
        protected final List<String> lifecycleEvents;
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
        public void close() {
            lifecycleEvents.add("close");
        }
    }

    private static class RecordingAbortExchange extends RecordingExchange {
        @Override
        public void abort() {
            lifecycleEvents.add("abort");
            close();
        }
    }

    @Test
    void initialRequestShouldDecodeInitialClientResponse() {
        String encodedInitialResponse = Base64.getEncoder().encodeToString(bytes("initial"));

        SaslInitialRequest request = testee.initialRequest("PLAIN", Optional.of(encodedInitialResponse));

        assertThat(request.mechanismName()).isEqualTo("PLAIN");
        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("initial")));
    }

    @Test
    void initialRequestShouldDecodeEqualSignAsEmptyInitialClientResponse() {
        SaslInitialRequest request = testee.initialRequest("PLAIN", Optional.of("="));

        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).isEmpty());
    }

    @Test
    void initialRequestShouldRejectMalformedInitialClientResponse() {
        assertThatThrownBy(() -> testee.initialRequest("PLAIN", Optional.of("not-base64!")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void challengeShouldBase64EncodePayloadAsSmtp334Response() {
        SaslStep.Challenge challenge = new SaslStep.Challenge(Optional.of(bytes("challenge")));

        Response response = testee.challenge(challenge);

        assertThat(response.getRetCode()).isEqualTo(SMTPRetCode.AUTH_READY);
        assertThat(response.getLines()).containsExactly("334 " + Base64.getEncoder().encodeToString(bytes("challenge")));
    }

    @Test
    void challengeShouldReturnEmptySmtp334ResponseWhenChallengeHasNoPayload() {
        SaslStep.Challenge challenge = new SaslStep.Challenge(Optional.empty());

        Response response = testee.challenge(challenge);

        assertThat(response.getRetCode()).isEqualTo(SMTPRetCode.AUTH_READY);
        assertThat(response.getLines()).containsExactly("334 ");
    }

    @Test
    void challengeShouldEncodeAuthLoginUsernamePrompt() {
        SaslStep.Challenge challenge = new SaslStep.Challenge(Optional.of(bytes("Username:")));

        Response response = testee.challenge(challenge);

        assertThat(response.getLines()).containsExactly("334 VXNlcm5hbWU6");
    }

    @Test
    void challengeShouldEncodeAuthLoginPasswordPrompt() {
        SaslStep.Challenge challenge = new SaslStep.Challenge(Optional.of(bytes("Password:")));

        Response response = testee.challenge(challenge);

        assertThat(response.getLines()).containsExactly("334 UGFzc3dvcmQ6");
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
    void onClientResponseShouldRejectMalformedBase64() {
        RecordingExchange exchange = new RecordingExchange();

        assertThatThrownBy(() -> testee.onClientResponse(exchange, "not-base64!\r\n".getBytes(StandardCharsets.US_ASCII)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isAbortShouldDetectSmtpSaslAbortLine() {
        assertThat(testee.isAbort("*\r\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }

    @Test
    void isAbortShouldDetectSmtpSaslAbortLineWithLfOnly() {
        assertThat(testee.isAbort("*\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }

    @Test
    void isAbortShouldRejectRegularClientResponse() {
        byte[] line = (Base64.getEncoder().encodeToString(bytes("response")) + "\r\n").getBytes(StandardCharsets.US_ASCII);

        assertThat(testee.isAbort(line)).isFalse();
    }

    @Test
    void successDataShouldBase64EncodePayloadAsSmtp334Response() {
        SaslStep.Success success = new SaslStep.Success(IDENTITY, Optional.of(bytes("server-data")));

        Response response = testee.successData(success);

        assertThat(response.getRetCode()).isEqualTo(SMTPRetCode.AUTH_READY);
        assertThat(response.getLines()).containsExactly("334 " + Base64.getEncoder().encodeToString(bytes("server-data")));
    }

    @Test
    void successDataShouldReturnEmptySmtp334ResponseWhenPayloadIsEmpty() {
        SaslStep.Success success = new SaslStep.Success(IDENTITY, Optional.empty());

        Response response = testee.successData(success);

        assertThat(response.getRetCode()).isEqualTo(SMTPRetCode.AUTH_READY);
        assertThat(response.getLines()).containsExactly("334 ");
    }

    @Test
    void isEmptyClientResponseShouldDetectEmptyLine() {
        assertThat(testee.isEmptyClientResponse("\r\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }

    @Test
    void isEmptyClientResponseShouldRejectNonEmptyLine() {
        assertThat(testee.isEmptyClientResponse("data\r\n".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void abortShouldCloseExchangeByDefault() {
        RecordingExchange exchange = new RecordingExchange();

        testee.abort(exchange);

        assertThat(exchange.lifecycleEvents).containsExactly("close");
    }

    @Test
    void abortShouldUseExchangeSpecificAbortWhenOverridden() {
        RecordingAbortExchange exchange = new RecordingAbortExchange();

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
