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

package org.apache.james.protocols.sasl.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.junit.jupiter.api.Test;

class OidcSaslMechanismTest {
    private static final Username USER = Username.of("user@example.com");
    private static final Username TOKEN_SUBJECT = Username.of("token-subject@example.com");
    private static final String TOKEN = "token";

    @Test
    void oauthBearerShouldValidateTokenAndAuthorizeDecodedInitialResponse() {
        // GIVEN a decoded OAUTHBEARER initial response
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("n,a=" + USER.asString() + ",\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));

        // WHEN the mechanism consumes and validates the response
        SaslStep step = new OauthBearerSaslMechanism(verifyingToken()).start(request, authorizing()).firstStep();

        // THEN it returns the authorized identity directly to the protocol driver
        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(TOKEN_SUBJECT, USER), Optional.empty()));
    }

    @Test
    void xOauth2ShouldValidateTokenAndAuthorizeDecodedInitialResponse() {
        // GIVEN a decoded XOAUTH2 initial response
        SaslInitialRequest request = new SaslInitialRequest(XOauth2SaslMechanism.NAME,
            Optional.of(bytes("user=" + USER.asString() + "\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));

        // WHEN the mechanism consumes and validates the response
        SaslStep step = new XOauth2SaslMechanism(verifyingToken()).start(request, authorizing()).firstStep();

        // THEN it exposes the same authorized identity shape
        assertThat(step).isEqualTo(new SaslStep.Success(new SaslIdentity(TOKEN_SUBJECT, USER), Optional.empty()));
    }

    @Test
    void shouldChallengeWhenNoInitialResponse() {
        // GIVEN an OIDC SASL exchange without SASL-IR
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME, Optional.empty());

        // WHEN the mechanism starts
        SaslStep firstStep = new OauthBearerSaslMechanism(verifyingToken()).start(request, authorizing()).firstStep();

        // THEN the server asks for one client response
        assertThat(firstStep).isEqualTo(new SaslStep.Challenge(Optional.empty()));
    }

    @Test
    void shouldFailMalformedResponse() {
        // GIVEN a malformed OIDC SASL response
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("invalid")));

        // WHEN the mechanism consumes the response
        SaslStep step = new OauthBearerSaslMechanism(verifyingToken()).start(request, authorizing()).firstStep();

        // THEN it fails before any token validation side effect
        assertThat(step).isEqualTo(new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command.")));
    }

    @Test
    void shouldFailWhenTokenIsRejected() {
        // GIVEN an OIDC SASL response with an invalid bearer token
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("n,a=" + USER.asString() + ",\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));

        // WHEN token validation rejects the token
        SaslStep step = new OauthBearerSaslMechanism(rejectingToken()).start(request, authorizing()).firstStep();

        // THEN the mechanism returns a typed authentication failure
        assertThat(step).isEqualTo(new SaslStep.Failure(SaslFailure.authenticationFailed(
            Optional.empty(), Optional.of(USER), "OAuth authentication failed.")));
    }

    @Test
    void shouldReturnAuthorizationFailure() {
        // GIVEN a valid token but a James authorization rule rejecting the requested identity
        SaslInitialRequest request = new SaslInitialRequest(OauthBearerSaslMechanism.NAME,
            Optional.of(bytes("n,a=" + USER.asString() + ",\u0001auth=Bearer " + TOKEN + "\u0001\u0001")));
        SaslFailure failure = SaslFailure.delegationForbidden(TOKEN_SUBJECT, USER, "forbidden");

        // WHEN authorization rejects the identity
        SaslStep step = new OauthBearerSaslMechanism(verifyingToken()).start(request, rejectingAuthorization(failure)).firstStep();

        // THEN the failure is returned to the protocol driver
        assertThat(step).isEqualTo(new SaslStep.Failure(failure));
    }

    private static OidcJwtTokenVerifier verifyingToken() {
        return new TestOidcJwtTokenVerifier(Optional.of(TOKEN_SUBJECT));
    }

    private static OidcJwtTokenVerifier rejectingToken() {
        return new TestOidcJwtTokenVerifier(Optional.empty());
    }

    private static SaslAuthenticator authorizing() {
        return new SaslAuthenticator() {
            @Override
            public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
                return new SaslAuthenticationResult.Failure(SaslFailure.invalidCredentials(authenticationId, authorizationId, "unused"));
            }

            @Override
            public SaslAuthenticationResult authorize(SaslIdentity identity) {
                return new SaslAuthenticationResult.Success(identity);
            }
        };
    }

    private static SaslAuthenticator rejectingAuthorization(SaslFailure failure) {
        return new SaslAuthenticator() {
            @Override
            public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
                return new SaslAuthenticationResult.Failure(SaslFailure.invalidCredentials(authenticationId, authorizationId, "unused"));
            }

            @Override
            public SaslAuthenticationResult authorize(SaslIdentity identity) {
                return new SaslAuthenticationResult.Failure(failure);
            }
        };
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static class TestOidcJwtTokenVerifier extends OidcJwtTokenVerifier {
        private final Optional<Username> validateTokenResult;

        private TestOidcJwtTokenVerifier(Optional<Username> validateTokenResult) {
            super(null);
            this.validateTokenResult = validateTokenResult;
        }

        @Override
        public Optional<Username> validateToken(String token) {
            return validateTokenResult;
        }
    }
}
