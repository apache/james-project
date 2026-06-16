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
    private static final SaslIdentity SAME_USER_IDENTITY = new SaslIdentity(AUTHENTICATION_ID, AUTHENTICATION_ID);
    private static final SaslIdentity DELEGATED_IDENTITY = new SaslIdentity(AUTHENTICATION_ID, AUTHORIZATION_ID);
    private static final SaslAuthenticator NOOP_AUTHENTICATOR = new SaslAuthenticator() {
        @Override
        public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
            return new SaslAuthenticationResult.Failure(SaslFailure.invalidCredentials(authenticationId, authorizationId, "unused"));
        }

        @Override
        public SaslAuthenticationResult authorize(SaslIdentity identity) {
            return new SaslAuthenticationResult.Success(identity);
        }
    };

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
        public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
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
        public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
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
                return new SaslStep.Failure(SaslFailure.malformed("response received before challenge"));
            }
            if (new String(clientResponse, StandardCharsets.UTF_8).equals("accepted")) {
                return new SaslStep.Success(SAME_USER_IDENTITY, Optional.empty());
            }
            return new SaslStep.Failure(SaslFailure.invalidCredentials(AUTHENTICATION_ID, Optional.empty(), "rejected"));
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
        }
    }

    @Test
    void oneStepMechanismShouldReturnSuccess() {
        // GIVEN a one-step mechanism configured to immediately succeed
        SaslStep.Success success = new SaslStep.Success(SAME_USER_IDENTITY, Optional.empty());
        SaslExchange exchange = new FixedStepMechanism(success).start(initialRequest(Optional.empty()), NOOP_AUTHENTICATOR);

        // WHEN the exchange starts
        SaslStep firstStep = exchange.firstStep();

        // THEN the mechanism can complete without a client continuation
        assertThat(firstStep).isEqualTo(success);
    }

    @Test
    void oneStepMechanismShouldReturnFailure() {
        // GIVEN a one-step mechanism configured to immediately fail
        SaslStep.Failure failure = new SaslStep.Failure(SaslFailure.malformed("failure"));
        SaslExchange exchange = new FixedStepMechanism(failure).start(initialRequest(Optional.empty()), NOOP_AUTHENTICATOR);

        // WHEN the exchange starts
        SaslStep firstStep = exchange.firstStep();

        // THEN the mechanism can fail without a client continuation
        assertThat(firstStep).isEqualTo(failure);
    }

    @Test
    void multiStepMechanismShouldKeepStateAcrossResponses() {
        // GIVEN a mechanism that requires one challenge before accepting a response
        SaslExchange exchange = new TwoStepMechanism().start(initialRequest(Optional.empty()), NOOP_AUTHENTICATOR);

        // WHEN the server sends a challenge and later receives the expected client response
        SaslStep firstStep = exchange.firstStep();
        SaslStep success = exchange.onResponse(bytes("accepted"));

        // THEN the exchange keeps enough state to complete after the continuation
        assertThat(firstStep).isInstanceOf(SaslStep.Challenge.class);
        assertThat(((SaslStep.Success) success).identity()).isEqualTo(SAME_USER_IDENTITY);
    }

    @Test
    void successStepShouldPreserveDelegatedIdentity() {
        // GIVEN a self-authenticating mechanism returning a delegated identity
        SaslExchange exchange = new FixedStepMechanism(new SaslStep.Success(DELEGATED_IDENTITY, Optional.empty()))
            .start(initialRequest(Optional.empty()), NOOP_AUTHENTICATOR);

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
        FixedStepExchange exchange = new FixedStepExchange(new SaslStep.Failure(SaslFailure.malformed("failure")));

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
