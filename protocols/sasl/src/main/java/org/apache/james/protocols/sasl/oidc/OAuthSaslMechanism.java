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

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.protocols.api.OIDCSASLParser;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;

/**
 * OIDC bearer-token SASL mechanism. OAUTHBEARER and XOAUTH2 share the same exchange and only differ by
 * their advertised name, so a single implementation is parameterized with the mechanism name.
 */
public class OAuthSaslMechanism implements SaslMechanism {
    private final String name;
    private final OidcJwtTokenVerifier verifier;

    public OAuthSaslMechanism(String name, OidcJwtTokenVerifier verifier) {
        this.name = name;
        this.verifier = verifier;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
        return new OAuthSaslExchange(request.initialResponse(), authenticator);
    }

    private class OAuthSaslExchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;
        private final SaslAuthenticator authenticator;

        private OAuthSaslExchange(Optional<byte[]> initialResponse, SaslAuthenticator authenticator) {
            this.initialResponse = initialResponse;
            this.authenticator = authenticator;
        }

        @Override
        public SaslStep firstStep() {
            return initialResponse
                .map(this::authenticate)
                .orElseGet(() -> new SaslStep.Challenge(Optional.empty()));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return authenticate(clientResponse);
        }

        @Override
        public void close() {
        }

        private SaslStep authenticate(byte[] clientResponse) {
            return OIDCSASLParser.parseDecoded(new String(clientResponse, StandardCharsets.US_ASCII))
                .map(response -> {
                    Username authorizationId = Username.of(response.getAssociatedUser());
                    return verifier.validateToken(response.getToken())
                        .map(authenticationId -> authorize(authenticationId, authorizationId))
                        .orElseGet(() -> new SaslStep.Failure(SaslFailure.authenticationFailed(
                            Optional.empty(), Optional.of(authorizationId), "OAuth authentication failed.")));
                })
                .orElseGet(() -> new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command.")));
        }

        private SaslStep authorize(Username authenticationId, Username authorizationId) {
            SaslAuthenticationResult result = authenticator.authorize(new SaslIdentity(authenticationId, authorizationId));
            return switch (result) {
                case SaslAuthenticationResult.Success success -> new SaslStep.Success(success.identity(), Optional.empty());
                case SaslAuthenticationResult.Failure failure -> new SaslStep.Failure(failure.failure());
            };
        }
    }
}
