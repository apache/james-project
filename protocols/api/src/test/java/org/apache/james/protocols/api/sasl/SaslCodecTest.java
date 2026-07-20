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

package org.apache.james.protocols.api.sasl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SaslCodecTest {
    @Test
    void initialRequestShouldDecodeInitialClientResponse() {
        String encodedInitialResponse = Base64.getEncoder().encodeToString(bytes("initial"));

        SaslInitialRequest request = SaslCodec.initialRequest("PLAIN", Optional.of(encodedInitialResponse));

        assertThat(request.mechanismName()).isEqualTo("PLAIN");
        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("initial")));
    }

    @Test
    void initialRequestShouldDecodeEqualSignAsEmptyInitialClientResponse() {
        SaslInitialRequest request = SaslCodec.initialRequest("PLAIN", Optional.of("="));

        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).isEmpty());
    }

    @Test
    void initialRequestShouldPreserveAbsentInitialClientResponse() {
        SaslInitialRequest request = SaslCodec.initialRequest("PLAIN", Optional.empty());

        assertThat(request.initialResponse()).isEmpty();
    }

    @Test
    void initialRequestShouldRejectMalformedInitialClientResponse() {
        assertThatThrownBy(() -> SaslCodec.initialRequest("PLAIN", Optional.of("not-base64!")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeClientResponseShouldDecodeLineTerminatedByCrlf() {
        byte[] line = (Base64.getEncoder().encodeToString(bytes("response")) + "\r\n").getBytes(StandardCharsets.US_ASCII);

        assertThat(SaslCodec.decodeClientResponse(line)).containsExactly(bytes("response"));
    }

    @Test
    void decodeClientResponseShouldDecodeLineTerminatedByLfOnly() {
        byte[] line = (Base64.getEncoder().encodeToString(bytes("response")) + "\n").getBytes(StandardCharsets.US_ASCII);

        assertThat(SaslCodec.decodeClientResponse(line)).containsExactly(bytes("response"));
    }

    @Test
    void decodeClientResponseShouldRejectMalformedBase64() {
        assertThatThrownBy(() -> SaslCodec.decodeClientResponse("not-base64!\r\n".getBytes(StandardCharsets.US_ASCII)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeShouldBase64EncodePayload() {
        assertThat(SaslCodec.encode(Optional.of(bytes("challenge"))))
            .isEqualTo(Base64.getEncoder().encodeToString(bytes("challenge")));
    }

    @Test
    void encodeShouldReturnEmptyStringWhenPayloadIsAbsent() {
        assertThat(SaslCodec.encode(Optional.empty())).isEmpty();
    }

    @Test
    void encodeShouldEncodeAuthLoginUsernamePrompt() {
        assertThat(SaslCodec.encode(Optional.of(bytes("Username:")))).isEqualTo("VXNlcm5hbWU6");
    }

    @Test
    void encodeShouldEncodeAuthLoginPasswordPrompt() {
        assertThat(SaslCodec.encode(Optional.of(bytes("Password:")))).isEqualTo("UGFzc3dvcmQ6");
    }

    @Test
    void isAbortShouldDetectAbortLine() {
        assertThat(SaslCodec.isAbort("*\r\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }

    @Test
    void isAbortShouldDetectAbortLineWithLfOnly() {
        assertThat(SaslCodec.isAbort("*\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }

    @Test
    void isAbortShouldRejectRegularClientResponse() {
        byte[] line = (Base64.getEncoder().encodeToString(bytes("response")) + "\r\n").getBytes(StandardCharsets.US_ASCII);

        assertThat(SaslCodec.isAbort(line)).isFalse();
    }

    @Test
    void isEmptyClientResponseShouldDetectEmptyLine() {
        assertThat(SaslCodec.isEmptyClientResponse("\r\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }

    @Test
    void isEmptyClientResponseShouldRejectNonEmptyLine() {
        assertThat(SaslCodec.isEmptyClientResponse("data\r\n".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
