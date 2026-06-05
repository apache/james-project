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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

class OidcSaslMechanismTest {
    private static final Username USER = Username.of("user@example.com");
    private static final String TOKEN = "token";

    @Test
    void oauthBearerShouldAuthenticateDecodedInitialResponse() {
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<Username> authorizationId = new AtomicReference<>();
        BearerTokenSaslAuthenticationService service = (bearerToken, user) -> {
            token.set(bearerToken);
            authorizationId.set(user);
            return new SaslAuthenticationResult.Success(new SaslIdentity(user, user));
        };
        SaslInitialRequest request = new SaslInitialRequest(SaslProtocol.IMAP, OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("n,a=" + USER.asString() + ",\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));

        SaslStep step = new OauthBearerSaslMechanism()
            .start(request, new TestSaslSessionContext(Optional.empty(), Optional.of(service)))
            .firstStep();

        assertThat(step).isInstanceOf(SaslStep.Success.class);
        assertThat(token.get()).isEqualTo(TOKEN);
        assertThat(authorizationId.get()).isEqualTo(USER);
    }

    @Test
    void xOauth2ShouldAuthenticateDecodedInitialResponse() {
        AtomicReference<String> token = new AtomicReference<>();
        BearerTokenSaslAuthenticationService service = (bearerToken, user) -> {
            token.set(bearerToken);
            return new SaslAuthenticationResult.Success(new SaslIdentity(user, user));
        };
        SaslInitialRequest request = new SaslInitialRequest(SaslProtocol.IMAP, XOauth2SaslMechanism.NAME,
            Optional.of(bytes("user=" + USER.asString() + "\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));

        SaslStep step = new XOauth2SaslMechanism()
            .start(request, new TestSaslSessionContext(Optional.empty(), Optional.of(service)))
            .firstStep();

        assertThat(step).isInstanceOf(SaslStep.Success.class);
        assertThat(token.get()).isEqualTo(TOKEN);
    }

    @Test
    void shouldFailMalformedResponse() {
        SaslInitialRequest request = new SaslInitialRequest(SaslProtocol.IMAP, OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("invalid")));

        SaslStep step = new OauthBearerSaslMechanism()
            .start(request, new TestSaslSessionContext(Optional.empty(), Optional.empty()))
            .firstStep();

        assertThat(step).isEqualTo(new SaslStep.Failure("Malformed authentication command."));
    }

    @Test
    void shouldBeAvailableOnlyWhenBearerTokenServiceExists() {
        assertThat(new OauthBearerSaslMechanism().isAvailable(new TestSaslSessionContext(Optional.empty(), Optional.empty()))).isFalse();
        assertThat(new OauthBearerSaslMechanism().isAvailable(new TestSaslSessionContext(Optional.empty(), Optional.of((token, user) ->
            new SaslAuthenticationResult.Failure("failure"))))).isTrue();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
