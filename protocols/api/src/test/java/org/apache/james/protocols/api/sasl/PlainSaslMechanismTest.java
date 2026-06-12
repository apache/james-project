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

class PlainSaslMechanismTest {
    private static final Username AUTHENTICATION_ID = Username.of("user@example.com");
    private static final Username AUTHORIZATION_ID = Username.of("delegated@example.com");
    private static final String PASSWORD = "secret";

    private final PlainSaslMechanism testee = new PlainSaslMechanism();

    @Test
    void shouldChallengeWhenNoInitialResponse() {
        // GIVEN a PLAIN exchange without SASL-IR
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME, Optional.empty());

        // WHEN the mechanism starts
        SaslStep firstStep = testee.start(request).firstStep();

        // THEN the server asks for one client response
        assertThat(firstStep).isEqualTo(new SaslStep.Challenge(Optional.empty()));
    }

    @Test
    void shouldReturnPasswordCredentialsForInitialResponseWithoutDelegation() {
        // GIVEN a valid PLAIN initial response without an authorization identity
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism consumes the initial response
        SaslStep step = testee.start(request).firstStep();

        // THEN it returns protocol-neutral password credentials
        assertThat(step).isEqualTo(new SaslStep.Credentials(new SaslCredentials.Password(
            AUTHENTICATION_ID, Optional.empty(), PASSWORD)));
    }

    @Test
    void shouldReturnPasswordCredentialsForContinuationResponseWithDelegation() {
        // GIVEN a PLAIN exchange waiting for the client response
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME, Optional.empty());
        SaslExchange exchange = testee.start(request);

        // WHEN the client sends a response with an authorization identity
        SaslStep step = exchange.onResponse(bytes(AUTHORIZATION_ID.asString() + "\0" + AUTHENTICATION_ID.asString() + "\0" + PASSWORD));

        // THEN both identities are preserved for protocol-level authentication and delegation
        assertThat(step).isEqualTo(new SaslStep.Credentials(new SaslCredentials.Password(
            AUTHENTICATION_ID, Optional.of(AUTHORIZATION_ID), PASSWORD)));
    }

    @Test
    void shouldAcceptTwoPartResponseWithoutAuthorizationIdentity() {
        // GIVEN a PLAIN response encoded as authcid/password
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes(AUTHENTICATION_ID.asString() + "\0" + PASSWORD)));

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request).firstStep();

        // THEN it treats the response as non-delegated password credentials
        assertThat(step).isEqualTo(new SaslStep.Credentials(new SaslCredentials.Password(
            AUTHENTICATION_ID, Optional.empty(), PASSWORD)));
    }

    @Test
    void shouldFailMalformedResponse() {
        // GIVEN a PLAIN response without the expected separators
        SaslInitialRequest request = new SaslInitialRequest(PlainSaslMechanism.NAME,
            Optional.of(bytes("missing-separators")));

        // WHEN the mechanism consumes the response
        SaslStep step = testee.start(request).firstStep();

        // THEN it fails before any protocol-specific authentication side effect
        assertThat(step).isEqualTo(new SaslStep.Failure("Malformed authentication command."));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
