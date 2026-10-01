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

package org.apache.james.jwt;

import java.net.URL;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import reactor.core.publisher.Mono;

public class OidcJwtTokenVerifier {
    public static final CheckTokenClient CHECK_TOKEN_CLIENT = new DefaultCheckTokenClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcJwtTokenVerifier.class);

    private final OidcSASLConfiguration oidcSASLConfiguration;

    public OidcJwtTokenVerifier(OidcSASLConfiguration oidcSASLConfiguration) {
        this.oidcSASLConfiguration = oidcSASLConfiguration;
    }

    public Optional<Username> validateToken(String token) {
        if (oidcSASLConfiguration.isCheckTokenByIntrospectionEndpoint()) {
            return validTokenWithIntrospection(token);
        } else if (oidcSASLConfiguration.isCheckTokenByUserinfoEndpoint()) {
            return validTokenWithUserInfo(token);
        } else {
            return verifySignatureAndExtractClaim(token)
                .map(Username::of);
        }
    }

    private Optional<Username> validTokenWithUserInfo(String token) {
        return Mono.from(verifyWithUserinfo(token, oidcSASLConfiguration.getUserInfoEndpoint().orElseThrow()))
            .blockOptional()
            .map(Username::of);
    }

    private Optional<Username> validTokenWithIntrospection(String token) {
        return Mono.from(verifyWithIntrospection(token,
                oidcSASLConfiguration.getIntrospectionEndpoint()
                    .map(endpoint -> new IntrospectionEndpoint(endpoint, oidcSASLConfiguration.getIntrospectionEndpointAuthorization()))
                    .orElseThrow()))
            .blockOptional()
            .map(Username::of);
    }

    @VisibleForTesting
    Optional<String> verifySignatureAndExtractClaim(String jwtToken) {
        try {
            return new JwtTokenVerifier(JwksPublicKeyProvider.of(oidcSASLConfiguration.getJwksURL()))
                .verify(jwtToken)
                .filter(this::hasExpectedAudience)
                .flatMap(this::extractConfiguredClaim);
        } catch (JwtException e) {
            LOGGER.info("Failed Jwt verification", e);
            return Optional.empty();
        }
    }

    @VisibleForTesting
    Publisher<String> verifyWithIntrospection(String jwtToken, IntrospectionEndpoint introspectionEndpoint) {
        return Mono.fromCallable(() -> verifySignatureAndExtractClaim(jwtToken))
            .flatMap(optional -> optional.map(Mono::just).orElseGet(Mono::empty))
            .flatMap(claimResult -> Mono.from(CHECK_TOKEN_CLIENT.introspect(introspectionEndpoint, jwtToken))
                .filter(tokenIntrospectionResponse -> {
                    if (!tokenIntrospectionResponse.active()) {
                        LOGGER.info("OIDC token rejected: introspection endpoint reported the token as inactive");
                        return false;
                    }
                    return true;
                })
                .filter(tokenIntrospectionResponse -> claimMatches("introspection", tokenIntrospectionResponse.claimByPropertyName(oidcSASLConfiguration.getClaim()), claimResult))
                .map(activeResponse -> claimResult));
    }

    @VisibleForTesting
    Publisher<String> verifyWithUserinfo(String jwtToken, URL userinfoEndpoint) {
        return Mono.fromCallable(() -> verifySignatureAndExtractClaim(jwtToken))
            .flatMap(optional -> optional.map(Mono::just).orElseGet(Mono::empty))
            .flatMap(claimResult -> Mono.from(CHECK_TOKEN_CLIENT.userInfo(userinfoEndpoint, jwtToken))
                .filter(userinfoResponse -> claimMatches("userinfo", userinfoResponse.claimByPropertyName(oidcSASLConfiguration.getClaim()), claimResult))
                .map(userinfoResponse -> claimResult));
    }

    private boolean hasExpectedAudience(Claims claims) {
        return oidcSASLConfiguration.getAud()
            .map(expectedAud -> {
                boolean matches = claims.getAudience() != null && claims.getAudience().contains(expectedAud);
                if (!matches) {
                    LOGGER.info("OIDC token rejected: expected audience '{}' but token audience is {}", expectedAud, claims.getAudience());
                }
                return matches;
            })
            .orElse(true); // true if no aud is configured
    }

    private Optional<String> extractConfiguredClaim(Claims claims) {
        Optional<String> claim = Optional.ofNullable(claims.get(oidcSASLConfiguration.getClaim(), String.class));
        if (claim.isEmpty()) {
            LOGGER.info("OIDC token rejected: claim '{}' is missing from the token", oidcSASLConfiguration.getClaim());
        }
        return claim;
    }

    private boolean claimMatches(String source, Optional<String> remoteClaim, String tokenClaim) {
        if (remoteClaim.isEmpty()) {
            LOGGER.info("OIDC token rejected: claim '{}' is missing from the {} response", oidcSASLConfiguration.getClaim(), source);
            return false;
        }
        if (!remoteClaim.get().equals(tokenClaim)) {
            LOGGER.info("OIDC token rejected: claim '{}' from the {} response ({}) does not match the token ({})",
                oidcSASLConfiguration.getClaim(), source, remoteClaim.get(), tokenClaim);
            return false;
        }
        return true;
    }
}
