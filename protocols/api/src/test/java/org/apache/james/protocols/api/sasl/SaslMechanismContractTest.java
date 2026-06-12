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

/**
 * Validates the shared SASL SPI shape with fake mechanisms before wiring real protocol mechanisms to it.
 */
class SaslMechanismContractTest {
    private static final Username AUTHENTICATION_ID = Username.of("authentication@example.com");
    private static final Username AUTHORIZATION_ID = Username.of("authorization@example.com");
    private static final String PASSWORD = "secret";
    private static final String TOKEN = "access-token";
    private static final SaslIdentity SAME_USER_IDENTITY = new SaslIdentity(AUTHENTICATION_ID, AUTHENTICATION_ID);
    private static final SaslIdentity DELEGATED_IDENTITY = new SaslIdentity(AUTHENTICATION_ID, AUTHORIZATION_ID);

    /**
     * Models one-step mechanisms that can immediately succeed or fail on the first server step.
     */
    private static class FixedStepMechanism implements SaslMechanism {
        private final SaslStep firstStep;

        private FixedStepMechanism(SaslStep firstStep) {
            this.firstStep = firstStep;
        }

        @Override
        public String name() {
            return "FIXED";
        }

        @Override
        public SaslExchange start(SaslInitialRequest request) {
            return new FixedStepExchange(firstStep);
        }
    }

    private static class FixedStepExchange implements SaslExchange {
        private final SaslStep firstStep;
        private boolean aborted;
        private boolean closed;

        private FixedStepExchange(SaslStep firstStep) {
            this.firstStep = firstStep;
        }

        @Override
        public SaslStep firstStep() {
            return firstStep;
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return firstStep;
        }

        @Override
        public void abort() {
            aborted = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Models challenge/response mechanisms where state must survive between client lines.
     */
    private static class TwoStepMechanism implements SaslMechanism {
        @Override
        public String name() {
            return "TWO_STEP";
        }

        @Override
        public SaslExchange start(SaslInitialRequest request) {
            return new TwoStepExchange();
        }
    }

    private static class TwoStepExchange implements SaslExchange {
        private boolean challenged;

        @Override
        public SaslStep firstStep() {
            challenged = true;
            return new SaslStep.Challenge(Optional.of(bytes("continue")));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            if (!challenged) {
                return new SaslStep.Failure("response received before challenge");
            }
            if (new String(clientResponse, StandardCharsets.UTF_8).equals("accepted")) {
                return new SaslStep.Success(SAME_USER_IDENTITY, Optional.empty());
            }
            return new SaslStep.Failure("rejected");
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
        }
    }

    /**
     * Models generic password mechanisms that parse SASL payloads but leave verification to the protocol.
     */
    private static class PasswordLikeMechanism implements SaslMechanism {
        @Override
        public String name() {
            return "PASSWORD_LIKE";
        }

        @Override
        public SaslExchange start(SaslInitialRequest request) {
            return new FixedStepExchange(request.initialResponse()
                .map(this::credentials)
                .orElseGet(() -> new SaslStep.Challenge(Optional.empty())));
        }

        private SaslStep credentials(byte[] payload) {
            String[] parts = new String(payload, StandardCharsets.UTF_8).split("\u0000", -1);
            return new SaslStep.Credentials(new SaslCredentials.Password(
                Username.of(parts[1]),
                Optional.of(parts[0]).filter(value -> !value.isEmpty()).map(Username::of),
                parts[2]));
        }
    }

    @Test
    void oneStepMechanismShouldReturnSuccess() {
        // GIVEN a one-step mechanism configured to immediately succeed
        SaslStep.Success success = new SaslStep.Success(SAME_USER_IDENTITY, Optional.empty());
        SaslExchange exchange = new FixedStepMechanism(success).start(initialRequest(Optional.empty()));

        // WHEN the exchange starts
        SaslStep firstStep = exchange.firstStep();

        // THEN the mechanism can complete without a client continuation
        assertThat(firstStep).isEqualTo(success);
    }

    @Test
    void oneStepMechanismShouldReturnFailure() {
        // GIVEN a one-step mechanism configured to immediately fail
        SaslStep.Failure failure = new SaslStep.Failure("failure");
        SaslExchange exchange = new FixedStepMechanism(failure).start(initialRequest(Optional.empty()));

        // WHEN the exchange starts
        SaslStep firstStep = exchange.firstStep();

        // THEN the mechanism can fail without a client continuation
        assertThat(firstStep).isEqualTo(failure);
    }

    @Test
    void multiStepMechanismShouldKeepStateAcrossResponses() {
        // GIVEN a mechanism that requires one challenge before accepting a response
        SaslExchange exchange = new TwoStepMechanism().start(initialRequest(Optional.empty()));

        // WHEN the server sends a challenge and later receives the expected client response
        SaslStep firstStep = exchange.firstStep();
        SaslStep success = exchange.onResponse(bytes("accepted"));

        // THEN the exchange keeps enough state to complete after the continuation
        assertThat(firstStep).isInstanceOf(SaslStep.Challenge.class);
        assertThat(((SaslStep.Success) success).identity()).isEqualTo(SAME_USER_IDENTITY);
    }

    @Test
    void passwordLikeMechanismShouldReturnProtocolNeutralCredentials() {
        // GIVEN a password-like mechanism and a PLAIN-like initial response
        SaslExchange exchange = new PasswordLikeMechanism()
            .start(initialRequest(Optional.of(bytes("\u0000" + AUTHENTICATION_ID.asString() + "\u0000" + PASSWORD))));

        // WHEN the generic mechanism consumes the initial response
        SaslStep firstStep = exchange.firstStep();

        // THEN it returns credentials without depending on IMAP or SMTP authentication services
        assertThat(firstStep).isEqualTo(new SaslStep.Credentials(new SaslCredentials.Password(
            AUTHENTICATION_ID, Optional.empty(), PASSWORD)));
    }

    @Test
    void passwordLikeMechanismShouldPreserveDelegatedIdentityInCredentials() {
        // GIVEN a PLAIN-like initial response with distinct authorization and authentication identities
        SaslExchange exchange = new PasswordLikeMechanism()
            .start(initialRequest(Optional.of(bytes(AUTHORIZATION_ID.asString() + "\u0000" + AUTHENTICATION_ID.asString() + "\u0000" + PASSWORD))));

        // WHEN the generic mechanism consumes the initial response
        SaslStep firstStep = exchange.firstStep();

        // THEN the credentials carry both identities for protocol-level delegation handling
        assertThat(firstStep).isEqualTo(new SaslStep.Credentials(new SaslCredentials.Password(
            AUTHENTICATION_ID, Optional.of(AUTHORIZATION_ID), PASSWORD)));
    }

    @Test
    void credentialsToStringShouldRedactSecrets() {
        // GIVEN credentials carrying sensitive password and bearer token values
        SaslCredentials.Password password = new SaslCredentials.Password(AUTHENTICATION_ID, Optional.of(AUTHORIZATION_ID), PASSWORD);
        SaslCredentials.BearerToken bearerToken = new SaslCredentials.BearerToken(TOKEN, AUTHORIZATION_ID);

        // WHEN credentials are converted to strings, for example by accidental logging
        String passwordString = password.toString();
        String bearerTokenString = bearerToken.toString();

        // THEN the sensitive fields are redacted while identity fields remain useful for diagnostics
        assertThat(passwordString)
            .contains("password=******", AUTHENTICATION_ID.asString(), AUTHORIZATION_ID.asString())
            .doesNotContain(PASSWORD);
        assertThat(bearerTokenString)
            .contains("token=******", AUTHORIZATION_ID.asString())
            .doesNotContain(TOKEN);
    }

    @Test
    void successStepShouldPreserveDelegatedIdentity() {
        // GIVEN a self-authenticating mechanism returning a delegated identity
        SaslExchange exchange = new FixedStepMechanism(new SaslStep.Success(DELEGATED_IDENTITY, Optional.empty()))
            .start(initialRequest(Optional.empty()));

        // WHEN the exchange starts
        SaslStep firstStep = exchange.firstStep();

        // THEN the identity keeps both authentication and authorization users
        assertThat(((SaslStep.Success) firstStep).identity()).isEqualTo(DELEGATED_IDENTITY);
    }

    @Test
    void initialRequestShouldDefensivelyCopyInitialResponse() {
        // GIVEN an initial response backed by a mutable byte array
        byte[] initialResponse = bytes("initial");
        SaslInitialRequest request = initialRequest(Optional.of(initialResponse));

        // WHEN the caller mutates the original array
        initialResponse[0] = 'I';

        // THEN the request keeps the original payload
        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("initial")));
    }

    @Test
    void saslStepsShouldDefensivelyCopyPayloads() {
        // GIVEN challenge and success steps backed by mutable byte arrays
        byte[] challengePayload = bytes("challenge");
        byte[] serverData = bytes("server");
        SaslStep.Challenge challenge = new SaslStep.Challenge(Optional.of(challengePayload));
        SaslStep.Success success = new SaslStep.Success(SAME_USER_IDENTITY, Optional.of(serverData));

        // WHEN the caller mutates the original arrays
        challengePayload[0] = 'C';
        serverData[0] = 'S';

        // THEN the SASL steps keep their original payloads
        assertThat(challenge.payload()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("challenge")));
        assertThat(success.serverData()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("server")));
    }

    @Test
    void exchangeShouldExposeAbortAndCloseLifecycle() {
        // GIVEN an active exchange
        FixedStepExchange exchange = new FixedStepExchange(new SaslStep.Failure("failure"));

        // WHEN the protocol aborts and then closes it
        exchange.abort();
        exchange.close();

        // THEN mechanisms can observe both lifecycle events
        assertThat(exchange.aborted).isTrue();
        assertThat(exchange.closed).isTrue();
    }

    private static SaslInitialRequest initialRequest(Optional<byte[]> initialResponse) {
        return new SaslInitialRequest("TEST", initialResponse);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
