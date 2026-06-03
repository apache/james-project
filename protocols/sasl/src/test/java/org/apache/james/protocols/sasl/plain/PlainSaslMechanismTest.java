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

package org.apache.james.protocols.sasl.plain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.junit.jupiter.api.Test;

class PlainSaslMechanismTest {
    private static final Username AUTHENTICATION_ID = Username.of("user@example.com");
    private static final Username AUTHORIZATION_ID = Username.of("delegated@example.com");
    private static final String PASSWORD = "secret";

    private final PlainSaslMechanism testee = new PlainSaslMechanism();

    @Test
    void shouldBeAvailableOnClearTransportByDefault() {
        assertThat(testee.isAvailableOnTransport(false)).isTrue();
    }

    @Test
    void shouldNotBeAvailableOnClearTransportWhenSslIsRequired() {
        assertThat(new PlainSaslMechanism(true, true).isAvailableOnTransport(false)).isFalse();
    }

    @Test
    void shouldBeAvailableOnEncryptedTransportWhenSslIsRequired() {
        assertThat(new PlainSaslMechanism(true, true).isAvailableOnTransport(true)).isTrue();
    }

    @Test
    void shouldNotBeAvailableWhenDisabled() {
        assertThat(new PlainSaslMechanism(false, false).isAvailableOnTransport(true)).isFalse();
    }

    @Test
    void shouldChallengeWhenNoInitialResponse() {
        // GIVEN a PLAIN exchange without SASL-IR
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME, Optional.empty());

        // WHEN the mechanism starts
        SaslStep firstStep = testee.start(request, authenticating()).firstStep();

        // THEN the server asks for one client response
        assertThat(firstStep).isEqualTo(new SaslStep.Challenge(Optional.empty()));
    }

    @Test
    void shouldAuthenticateInitialResponseWithoutDelegation() {
        // GIVEN a valid PLAIN initial response without an authorization identity
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism consumes the initial response
        SaslStep step = testee.start(request, authenticating()).firstStep();

        // THEN it authenticates through the shared SASL authenticator and returns the authenticated identity
        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(AUTHENTICATION_ID, AUTHENTICATION_ID), Optional.empty()));
    }

    @Test
    void shouldAuthenticateContinuationResponseWithDelegation() {
        // GIVEN a PLAIN exchange waiting for the client response
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME, Optional.empty());
        SaslExchange exchange = testee.start(request, authenticating());

        // WHEN the client sends a response with an authorization identity
        SaslStep step = exchange.onResponse(bytes(AUTHORIZATION_ID.asString() + "\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD));

        // THEN both identities are preserved after mechanism-owned authentication
        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(AUTHENTICATION_ID, AUTHORIZATION_ID), Optional.empty()));
    }

    @Test
    void shouldAcceptTwoPartResponseWithoutAuthorizationIdentity() {
        // GIVEN a PLAIN response encoded as authcid/password
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes(AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request, authenticating()).firstStep();

        // THEN it treats the response as non-delegated authentication
        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(AUTHENTICATION_ID, AUTHENTICATION_ID), Optional.empty()));
    }

    @Test
    void shouldPassPasswordUnmodifiedToAuthenticator() {
        // GIVEN a PLAIN response whose password is made of whitespace only
        AtomicReference<String> capturedPassword = new AtomicReference<>();
        String whitespacePassword = "   ";
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + whitespacePassword)));

        // WHEN the mechanism consumes the response
        testee.start(request, authenticating(capturedPassword)).firstStep();

        // THEN the password is kept unchanged instead of being filtered as blank
        assertThat(capturedPassword).hasValue(whitespacePassword);
    }

    @Test
    void shouldReturnAuthenticatorFailure() {
        // GIVEN a valid PLAIN response but an authenticator rejecting the password
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));
        SaslFailure failure = SaslFailure.invalidCredentials(AUTHENTICATION_ID, Optional.empty(), "rejected");

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request, rejecting(failure)).firstStep();

        // THEN the typed failure is returned to the protocol driver
        assertThat(step).isEqualTo(new SaslStep.Failure(failure));
    }

    @Test
    void shouldFailMalformedResponse() {
        // GIVEN a PLAIN response without the expected separators
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("missing-separators")));

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request, authenticating()).firstStep();

        // THEN it fails before calling protocol-neutral authentication
        assertThat(step).isEqualTo(new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command.")));
    }

    private static SaslAuthenticator authenticating() {
        return authenticating(new AtomicReference<>());
    }

    private static SaslAuthenticator authenticating(AtomicReference<String> capturedPassword) {
        return new SaslAuthenticator() {
            @Override
            public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
                capturedPassword.set(password);
                return new SaslAuthenticationResult.Success(new SaslIdentity(authenticationId, authorizationId.orElse(authenticationId)));
            }

            @Override
            public SaslAuthenticationResult authorize(SaslIdentity identity) {
                return new SaslAuthenticationResult.Success(identity);
            }
        };
    }

    private static SaslAuthenticator rejecting(SaslFailure failure) {
        return new SaslAuthenticator() {
            @Override
            public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
                return new SaslAuthenticationResult.Failure(failure);
            }

            @Override
            public SaslAuthenticationResult authorize(SaslIdentity identity) {
                return new SaslAuthenticationResult.Success(identity);
            }
        };
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
