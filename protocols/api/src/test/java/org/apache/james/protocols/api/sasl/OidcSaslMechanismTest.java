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

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

class OidcSaslMechanismTest {
    private static final Username USER = Username.of("user@example.com");
    private static final String TOKEN = "token";

    @Test
    void oauthBearerShouldReturnBearerTokenCredentialsFromDecodedInitialResponse() {
        // GIVEN a decoded OAUTHBEARER initial response
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("n,a=" + USER.asString() + ",\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));

        // WHEN the mechanism consumes the response
        SaslStep step = new OauthBearerSaslMechanism().start(request).firstStep();

        // THEN it returns protocol-neutral bearer token credentials
        assertThat(step).isEqualTo(new SaslStep.Credentials(new SaslCredentials.BearerToken(TOKEN, USER)));
    }

    @Test
    void xOauth2ShouldReturnBearerTokenCredentialsFromDecodedInitialResponse() {
        // GIVEN a decoded XOAUTH2 initial response
        SaslInitialRequest request = new SaslInitialRequest(XOauth2SaslMechanism.NAME,
            Optional.of(bytes("user=" + USER.asString() + "\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));

        // WHEN the mechanism consumes the response
        SaslStep step = new XOauth2SaslMechanism().start(request).firstStep();

        // THEN it exposes the same generic bearer-token credential shape
        assertThat(step).isEqualTo(new SaslStep.Credentials(new SaslCredentials.BearerToken(TOKEN, USER)));
    }

    @Test
    void shouldChallengeWhenNoInitialResponse() {
        // GIVEN an OIDC SASL exchange without SASL-IR
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME, Optional.empty());

        // WHEN the mechanism starts
        SaslStep firstStep = new OauthBearerSaslMechanism().start(request).firstStep();

        // THEN the server asks for one client response
        assertThat(firstStep).isEqualTo(new SaslStep.Challenge(Optional.empty()));
    }

    @Test
    void shouldFailMalformedResponse() {
        // GIVEN a malformed OIDC SASL response
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("invalid")));

        // WHEN the mechanism consumes the response
        SaslStep step = new OauthBearerSaslMechanism().start(request).firstStep();

        // THEN it fails before any protocol-specific token validation
        assertThat(step).isEqualTo(new SaslStep.Failure("Malformed authentication command."));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
