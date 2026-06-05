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

class PlainSaslMechanismTest {
    private static final Username AUTHENTICATION_ID = Username.of("user@example.com");
    private static final Username AUTHORIZATION_ID = Username.of("delegated@example.com");
    private static final String PASSWORD = "secret";

    private final PlainSaslMechanism testee = new PlainSaslMechanism();

    @Test
    void shouldChallengeWhenNoInitialResponse() {
        // GIVEN a PLAIN exchange without SASL-IR
        SaslInitialRequest request = new SaslInitialRequest(SaslProtocol.IMAP, PlainSaslMechanism.NAME, Optional.empty());

        // WHEN the mechanism starts
        SaslStep firstStep = testee.start(request, new TestSaslSessionContext(Optional.empty(), Optional.empty())).firstStep();

        // THEN the server asks for one client response
        assertThat(firstStep).isEqualTo(new SaslStep.Challenge(Optional.empty()));
    }

    @Test
    void shouldAuthenticateInitialResponseWithoutDelegation() {
        AtomicReference<Username> authenticationId = new AtomicReference<>();
        AtomicReference<String> password = new AtomicReference<>();
        AtomicReference<Optional<Username>> authorizationId = new AtomicReference<>();
        PasswordSaslAuthenticationService service = (user, delegatedUser, secret) -> {
            authenticationId.set(user);
            password.set(secret);
            authorizationId.set(delegatedUser);
            return new SaslAuthenticationResult.Success(new SaslIdentity(user, user));
        };
        SaslInitialRequest request = new SaslInitialRequest(SaslProtocol.IMAP, PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        SaslStep step = testee.start(request, new TestSaslSessionContext(Optional.of(service), Optional.empty())).firstStep();

        assertThat(step).isInstanceOf(SaslStep.Success.class);
        assertThat(authenticationId.get()).isEqualTo(AUTHENTICATION_ID);
        assertThat(password.get()).isEqualTo(PASSWORD);
        assertThat(authorizationId.get()).isEmpty();
    }

    @Test
    void shouldAuthenticateContinuationResponseWithDelegation() {
        AtomicReference<Optional<Username>> authorizationId = new AtomicReference<>();
        PasswordSaslAuthenticationService service = (user, delegatedUser, secret) -> {
            authorizationId.set(delegatedUser);
            return new SaslAuthenticationResult.Success(new SaslIdentity(user, delegatedUser.orElse(user)));
        };
        SaslInitialRequest request = new SaslInitialRequest(SaslProtocol.IMAP, PlainSaslMechanism.NAME, Optional.empty());
        SaslExchange exchange = testee.start(request, new TestSaslSessionContext(Optional.of(service), Optional.empty()));

        SaslStep step = exchange.onResponse(bytes(AUTHORIZATION_ID.asString() + "\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD));

        assertThat(step).isInstanceOf(SaslStep.Success.class);
        assertThat(authorizationId.get()).contains(AUTHORIZATION_ID);
    }

    @Test
    void shouldFailMalformedResponse() {
        SaslInitialRequest request = new SaslInitialRequest(SaslProtocol.IMAP, PlainSaslMechanism.NAME,
            Optional.of(bytes("missing-separators")));

        SaslStep step = testee.start(request, new TestSaslSessionContext(Optional.empty(), Optional.empty())).firstStep();

        assertThat(step).isEqualTo(new SaslStep.Failure("Malformed authentication command."));
    }

    @Test
    void shouldBeAvailableOnlyWhenPasswordServiceExists() {
        assertThat(testee.isAvailable(new TestSaslSessionContext(Optional.empty(), Optional.empty()))).isFalse();
        assertThat(testee.isAvailable(new TestSaslSessionContext(Optional.of((user, authorizationId, password) ->
            new SaslAuthenticationResult.Failure("failure")), Optional.empty()))).isTrue();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
