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

package org.apache.james.managesieve.sasl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.junit.jupiter.api.Test;

class ManageSieveSaslCodecTest {
    private static final SaslIdentity IDENTITY = new SaslIdentity(Username.of("bob"), Username.of("bob"));

    @Test
    void parseInitialRequestShouldDistinguishAbsentResponse() throws Exception {
        ManageSieveSaslCodec.InitialRequest request = ManageSieveSaslCodec.parseInitialRequest("\"PLAIN\"");

        assertThat(request.mechanismName()).isEqualTo("PLAIN");
        assertThat(request.saslInitialRequest().initialResponse()).isEmpty();
    }

    @Test
    void parseInitialRequestShouldPreserveExplicitEmptyResponse() throws Exception {
        ManageSieveSaslCodec.InitialRequest request = ManageSieveSaslCodec.parseInitialRequest("\"PLAIN\" \"\"");

        assertThat(request.saslInitialRequest().initialResponse()).hasValueSatisfying(response -> assertThat(response).isEmpty());
    }

    @Test
    void parseInitialRequestShouldDecodeQuotedResponse() throws Exception {
        ManageSieveSaslCodec.InitialRequest request = ManageSieveSaslCodec.parseInitialRequest("\"PLAIN\" \"dXNlcgBwYXNz\"");

        assertThat(request.saslInitialRequest().initialResponse()).hasValueSatisfying(response ->
            assertThat(response).containsExactly(bytes("user\0pass")));
    }

    @Test
    void parseInitialRequestShouldDecodeLiteralResponse() throws Exception {
        ManageSieveSaslCodec.InitialRequest request = ManageSieveSaslCodec.parseInitialRequest("\"PLAIN\" {12+}\r\ndXNlcgBwYXNz");

        assertThat(request.saslInitialRequest().initialResponse()).hasValueSatisfying(response ->
            assertThat(response).containsExactly(bytes("user\0pass")));
    }

    @Test
    void parseInitialRequestShouldMeasureLiteralSizeInUtf8Octets() throws Exception {
        ManageSieveSaslCodec.InitialRequest request = ManageSieveSaslCodec.parseInitialRequest("\"PLAIN\" {4+}\r\né p");

        assertThat(request.saslInitialRequest().initialResponse()).hasValueSatisfying(response ->
            assertThat(response).containsExactly(bytes("é\0p")));
    }

    @Test
    void parseInitialRequestShouldRejectLiteralSizeSplittingUtf8Character() {
        assertThatThrownBy(() -> ManageSieveSaslCodec.parseInitialRequest("\"PLAIN\" {1+}\r\né"))
            .isInstanceOf(SyntaxException.class)
            .hasMessage("authentication literal size splits a UTF-8 character");
    }

    @Test
    void parseInitialRequestShouldRejectServerToClientLiteralSyntax() {
        assertThatThrownBy(() -> ManageSieveSaslCodec.parseInitialRequest("\"PLAIN\" {12}\r\ndXNlcgBwYXNz"))
            .isInstanceOf(SyntaxException.class)
            .hasMessage("invalid client authentication literal syntax");
    }

    @Test
    void parseInitialRequestShouldRejectNegativeLiteralSize() {
        assertThatThrownBy(() -> ManageSieveSaslCodec.parseInitialRequest("{-1+}\r\n"))
            .isInstanceOf(SyntaxException.class)
            .hasMessage("invalid authentication literal size");
    }

    @Test
    void parseInitialRequestShouldRejectOverflowingLiteralSize() {
        assertThatThrownBy(() -> ManageSieveSaslCodec.parseInitialRequest("{2147483648+}\r\n"))
            .isInstanceOf(SyntaxException.class)
            .hasMessage("invalid authentication literal size");
    }

    @Test
    void parseClientResponseShouldAcceptLegacyRawPlainData() throws Exception {
        assertThat(ManageSieveSaslCodec.parseClientResponse("PLAIN", "  user  password"))
            .containsExactly(bytes("user\0password"));
    }

    @Test
    void parseClientResponseShouldRejectRawDataForOtherMechanisms() {
        assertThatThrownBy(() -> ManageSieveSaslCodec.parseClientResponse("CUSTOM", "not base64 data"))
            .isInstanceOf(SyntaxException.class)
            .hasMessage("authentication data must be Base64 encoded");
    }

    @Test
    void parseClientResponseShouldAcceptUnpaddedBase64() throws Exception {
        assertThat(ManageSieveSaslCodec.parseClientResponse("CUSTOM", "dXNlcg"))
            .containsExactly(bytes("user"));
    }

    @Test
    void challengeShouldSerializeEmptyPayloadAsQuotedString() {
        assertThat(ManageSieveSaslCodec.challenge(new SaslStep.Challenge(Optional.empty())))
            .isEqualTo("\"\"");
    }

    @Test
    void challengeShouldSerializePayloadAsQuotedBase64() {
        assertThat(ManageSieveSaslCodec.challenge(new SaslStep.Challenge(Optional.of(bytes("challenge")))))
            .isEqualTo("\"Y2hhbGxlbmdl\"");
    }

    @Test
    void challengeShouldUseLiteralForLargePayload() {
        byte[] payload = new byte[769];
        String encoded = Base64.getEncoder().encodeToString(payload);

        assertThat(ManageSieveSaslCodec.challenge(new SaslStep.Challenge(Optional.of(payload))))
            .isEqualTo("{" + encoded.length() + "}\r\n" + encoded);
    }

    @Test
    void successShouldSerializeFinalServerData() {
        assertThat(ManageSieveSaslCodec.success(new SaslStep.Success(IDENTITY, Optional.of(bytes("server-data")))))
            .isEqualTo("OK (SASL \"c2VydmVyLWRhdGE=\")");
    }

    @Test
    void isAbortShouldAcceptBareAndQuotedCancellation() {
        assertThat(ManageSieveSaslCodec.isAbort("*")).isTrue();
        assertThat(ManageSieveSaslCodec.isAbort("\"*\"")).isTrue();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
