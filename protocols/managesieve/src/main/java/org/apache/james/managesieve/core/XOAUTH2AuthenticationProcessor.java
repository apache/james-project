/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.core;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.managesieve.api.AuthenticationException;
import org.apache.james.managesieve.api.AuthenticationProcessor;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.protocols.api.OIDCSASLParser;
import org.apache.james.protocols.api.OIDCSASLParser.OIDCInitialResponse;
import org.apache.james.protocols.api.OidcSASLConfiguration;

import reactor.core.publisher.Mono;

public class XOAUTH2AuthenticationProcessor implements AuthenticationProcessor {

    private final OidcSASLConfiguration oidcConfiguration;

    public XOAUTH2AuthenticationProcessor(OidcSASLConfiguration oidcConfiguration) {
        this.oidcConfiguration = oidcConfiguration;
    }

    @Override
    public String initialServerResponse(Session session) {
        return "+ \"\"";
    }

    @Override
    public Username isAuthenticationSuccesfull(Session session, String suppliedClientData) throws SyntaxException, AuthenticationException {
        Optional<OIDCInitialResponse> oidcInitialResponseResult = OIDCSASLParser.parse(suppliedClientData);
        if (oidcInitialResponseResult.isEmpty()) {
            throw new SyntaxException("Could not parse the given JWT");
        }
        OIDCInitialResponse oidcInitialResponse = oidcInitialResponseResult.get();

        Optional<Username> authenticatedUserResult = Optional.empty();
        try {
            authenticatedUserResult = validateToken(oidcInitialResponse.getToken());
        } catch (Exception e) {
            throw new AuthenticationException("Could not validate the JWT");
        }
        if (authenticatedUserResult.isEmpty()) {
            throw new AuthenticationException("Could not validate the JWT");
        }
        Username authenticatedUser = authenticatedUserResult.get();

        // The user from the managesieve AUTHENTICATE command must match the username in the token.
        Username associatedUser = Username.of(oidcInitialResponse.getAssociatedUser());
        if (!authenticatedUser.equals(associatedUser)) {
            throw new AuthenticationException("Mismatch between user from command and JWT");
        }

        return authenticatedUser;
    }

    private Optional<Username> validateToken(String token) {
        if (this.oidcConfiguration.isCheckTokenByIntrospectionEndpoint()) {
            return validTokenWithIntrospection(token);
        } else if (this.oidcConfiguration.isCheckTokenByUserinfoEndpoint()) {
            return validTokenWithUserInfo(token);
        } else {
            return OidcJwtTokenVerifier.verifySignatureAndExtractClaim(token, this.oidcConfiguration.getJwksURL(), this.oidcConfiguration.getClaim())
                .map(Username::of);
        }
    }

    private Optional<Username> validTokenWithUserInfo(String token) {
        return Mono.from(OidcJwtTokenVerifier.verifyWithUserinfo(token,
                this.oidcConfiguration.getJwksURL(),
                this.oidcConfiguration.getClaim(),
                this.oidcConfiguration.getUserInfoEndpoint().orElseThrow()))
            .blockOptional()
            .map(Username::of);
    }

    private Optional<Username> validTokenWithIntrospection(String token) {
        return Mono.from(OidcJwtTokenVerifier.verifyWithIntrospection(token,
                this.oidcConfiguration.getJwksURL(),
                this.oidcConfiguration.getClaim(),
                this.oidcConfiguration.getIntrospectionEndpoint()
                    .map(endpoint -> new IntrospectionEndpoint(endpoint, this.oidcConfiguration.getIntrospectionEndpointAuthorization()))
                    .orElseThrow()))
            .blockOptional()
            .map(Username::of);
    }
}

