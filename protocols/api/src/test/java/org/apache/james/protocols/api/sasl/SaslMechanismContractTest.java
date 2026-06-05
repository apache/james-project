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
import java.util.HashMap;
import java.util.Map;
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
        public boolean supports(SaslProtocol protocol) {
            return protocol == SaslProtocol.IMAP;
        }

        @Override
        public boolean isAvailable(SaslSessionContext context) {
            return true;
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslSessionContext context) {
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
        public boolean supports(SaslProtocol protocol) {
            return protocol == SaslProtocol.IMAP;
        }

        @Override
        public boolean isAvailable(SaslSessionContext context) {
            return true;
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslSessionContext context) {
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
     * Models generic password mechanisms that parse SASL payloads but delegate credential verification to the protocol.
     */
    private static class PasswordLikeMechanism implements SaslMechanism {
        @Override
        public String name() {
            return "PASSWORD_LIKE";
        }

        @Override
        public boolean supports(SaslProtocol protocol) {
            return protocol == SaslProtocol.IMAP;
        }

        @Override
        public boolean isAvailable(SaslSessionContext context) {
            return context.service(PasswordSaslAuthenticationService.class).isPresent();
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslSessionContext context) {
            return new FixedStepExchange(request.initialResponse()
                .map(payload -> authenticate(payload, context))
                .orElseGet(() -> new SaslStep.Failure("missing initial response")));
        }

        private SaslStep authenticate(byte[] payload, SaslSessionContext context) {
            return context.service(PasswordSaslAuthenticationService.class)
                .map(service -> service.authenticate(authenticationId(payload), authorizationId(payload), password(payload)))
                .map(this::toSaslStep)
                .orElseGet(() -> new SaslStep.Failure("missing password authentication service"));
        }

        private Username authenticationId(byte[] payload) {
            return Username.of(parts(payload)[1]);
        }

        private Optional<Username> authorizationId(byte[] payload) {
            return Optional.of(parts(payload)[0])
                .filter(value -> !value.isEmpty())
                .map(Username::of);
        }

        private String password(byte[] payload) {
            return parts(payload)[2];
        }

        private String[] parts(byte[] payload) {
            return new String(payload, StandardCharsets.UTF_8).split("\u0000", -1);
        }

        private SaslStep toSaslStep(SaslAuthenticationResult authenticationResult) {
            if (authenticationResult instanceof SaslAuthenticationResult.Success(SaslIdentity identity)) {
                return new SaslStep.Success(identity, Optional.empty());
            }
            return new SaslStep.Failure(((SaslAuthenticationResult.Failure) authenticationResult).reason());
        }
    }

    private static class FakeSaslSessionContext implements SaslSessionContext {
        private final Map<Class<?>, Object> services;

        private FakeSaslSessionContext(Map<Class<?>, Object> services) {
            this.services = services;
        }

        private static FakeSaslSessionContext withPasswordAuthenticationService(PasswordSaslAuthenticationService service) {
            Map<Class<?>, Object> services = new HashMap<>();
            services.put(PasswordSaslAuthenticationService.class, service);
            return new FakeSaslSessionContext(services);
        }

        @Override
        public <T> Optional<T> service(Class<T> serviceType) {
            return Optional.ofNullable(services.get(serviceType))
                .map(serviceType::cast);
        }

        @Override
        public <T> void register(Class<T> serviceType, T service) {
            services.put(serviceType, service);
        }
    }

    @Test
    void oneStepMechanismShouldReturnSuccess() {
        // Given a one-step mechanism configured to immediately succeed
        SaslStep.Success success = new SaslStep.Success(SAME_USER_IDENTITY, Optional.empty());
        SaslExchange exchange = new FixedStepMechanism(success).start(initialRequest(Optional.empty()), new FakeSaslSessionContext(Map.of()));

        // When the exchange starts
        SaslStep firstStep = exchange.firstStep();

        // Then the mechanism can complete without a client continuation
        assertThat(firstStep).isEqualTo(success);
    }

    @Test
    void oneStepMechanismShouldReturnFailure() {
        // Given a one-step mechanism configured to immediately fail
        SaslStep.Failure failure = new SaslStep.Failure("failure");
        SaslExchange exchange = new FixedStepMechanism(failure).start(initialRequest(Optional.empty()), new FakeSaslSessionContext(Map.of()));

        // When the exchange starts
        SaslStep firstStep = exchange.firstStep();

        // Then the mechanism can fail without a client continuation
        assertThat(firstStep).isEqualTo(failure);
    }

    @Test
    void multiStepMechanismShouldKeepStateAcrossResponses() {
        // Given a mechanism that requires one challenge before accepting a response
        SaslExchange exchange = new TwoStepMechanism().start(initialRequest(Optional.empty()), new FakeSaslSessionContext(Map.of()));

        // When the server sends a challenge and later receives the expected client response
        SaslStep firstStep = exchange.firstStep();
        SaslStep success = exchange.onResponse(bytes("accepted"));

        // Then the exchange keeps enough state to complete after the continuation
        assertThat(firstStep).isInstanceOf(SaslStep.Challenge.class);
        assertThat(((SaslStep.Success) success).identity()).isEqualTo(SAME_USER_IDENTITY);
    }

    @Test
    void passwordLikeMechanismShouldAuthenticateThroughSessionContextService() {
        // Given a protocol-provided password service and a PLAIN-like initial response
        PasswordSaslAuthenticationService service = (authenticationId, authorizationId, password) -> {
            assertThat(authenticationId).isEqualTo(AUTHENTICATION_ID);
            assertThat(password).isEqualTo("secret");
            assertThat(authorizationId).isEmpty();
            return new SaslAuthenticationResult.Success(SAME_USER_IDENTITY);
        };
        SaslExchange exchange = new PasswordLikeMechanism()
            .start(initialRequest(Optional.of(bytes("\u0000" + AUTHENTICATION_ID.asString() + "\u0000secret"))),
                FakeSaslSessionContext.withPasswordAuthenticationService(service));

        // When the generic mechanism consumes the initial response
        SaslStep firstStep = exchange.firstStep();

        // Then authentication succeeds without depending on an IMAP or SMTP class
        assertThat(((SaslStep.Success) firstStep).identity()).isEqualTo(SAME_USER_IDENTITY);
    }

    @Test
    void passwordLikeMechanismShouldPreserveDelegatedIdentity() {
        // Given a PLAIN-like initial response with distinct authorization and authentication identities
        PasswordSaslAuthenticationService service = (authenticationId, authorizationId, password) -> {
            assertThat(authenticationId).isEqualTo(AUTHENTICATION_ID);
            assertThat(password).isEqualTo("secret");
            assertThat(authorizationId).contains(AUTHORIZATION_ID);
            return new SaslAuthenticationResult.Success(DELEGATED_IDENTITY);
        };
        SaslExchange exchange = new PasswordLikeMechanism()
            .start(initialRequest(Optional.of(bytes(AUTHORIZATION_ID.asString() + "\u0000" + AUTHENTICATION_ID.asString() + "\u0000secret"))),
                FakeSaslSessionContext.withPasswordAuthenticationService(service));

        // When the generic mechanism authenticates through the context service
        SaslStep firstStep = exchange.firstStep();

        // Then the success step carries both identities for protocol-level delegation handling
        assertThat(((SaslStep.Success) firstStep).identity()).isEqualTo(DELEGATED_IDENTITY);
    }

    @Test
    void initialRequestShouldDefensivelyCopyInitialResponse() {
        // Given an initial response backed by a mutable byte array
        byte[] initialResponse = bytes("initial");
        SaslInitialRequest request = initialRequest(Optional.of(initialResponse));

        // When the caller mutates the original array
        initialResponse[0] = 'I';

        // Then the request keeps the original payload
        assertThat(request.initialResponse()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("initial")));
    }

    @Test
    void saslStepsShouldDefensivelyCopyPayloads() {
        // Given challenge and success steps backed by mutable byte arrays
        byte[] challengePayload = bytes("challenge");
        byte[] serverData = bytes("server");
        SaslStep.Challenge challenge = new SaslStep.Challenge(Optional.of(challengePayload));
        SaslStep.Success success = new SaslStep.Success(SAME_USER_IDENTITY, Optional.of(serverData));

        // When the caller mutates the original arrays
        challengePayload[0] = 'C';
        serverData[0] = 'S';

        // Then the SASL steps keep their original payloads
        assertThat(challenge.payload()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("challenge")));
        assertThat(success.serverData()).hasValueSatisfying(value -> assertThat(value).containsExactly(bytes("server")));
    }

    @Test
    void exchangeShouldExposeAbortAndCloseLifecycle() {
        // Given an active exchange
        FixedStepExchange exchange = new FixedStepExchange(new SaslStep.Failure("failure"));

        // When the protocol aborts and then closes it
        exchange.abort();
        exchange.close();

        // Then mechanisms can observe both lifecycle events
        assertThat(exchange.aborted).isTrue();
        assertThat(exchange.closed).isTrue();
    }

    private static SaslInitialRequest initialRequest(Optional<byte[]> initialResponse) {
        return new SaslInitialRequest(SaslProtocol.IMAP, "TEST", initialResponse);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
