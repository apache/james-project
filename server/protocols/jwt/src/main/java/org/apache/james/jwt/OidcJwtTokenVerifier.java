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
import java.util.function.Predicate;

import org.apache.james.core.Username;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.jwt.introspection.TokenIntrospectionResponse;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

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
                .filter(claims -> oidcSASLConfiguration.getAud().map(expectedAud -> claims.getAudience().contains(expectedAud))
                    .orElse(true)) // true if no aud is configured
                .flatMap(claims -> Optional.ofNullable(claims.get(oidcSASLConfiguration.getClaim(), String.class)));
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
                .filter(TokenIntrospectionResponse::active)
                .filter(validateAud())
                .filter(tokenIntrospectionResponse -> tokenIntrospectionResponse.claimByPropertyName(oidcSASLConfiguration.getClaim())
                    .map(claim -> claim.equals(claimResult))
                    .orElse(false))
                .map(activeResponse -> claimResult));
    }

    private Predicate<TokenIntrospectionResponse> validateAud() {
        return oidcSASLConfiguration.getAud()
            .map(this::validateAud)
            .orElse(any -> true);
    }

    private Predicate<TokenIntrospectionResponse> validateAud(String expectedAud) {
        return token -> {
            boolean result = token.aud().map(expectedAud::equals).orElse(false);
            LOGGER.warn("Wrong aud. Expected {} got {}", expectedAud, token.aud());
            return result;
        };
    }

    @VisibleForTesting
   Publisher<String> verifyWithUserinfo(String jwtToken, URL userinfoEndpoint) {
        return Mono.fromCallable(() -> verifySignatureAndExtractClaim(jwtToken))
            .flatMap(optional -> optional.map(Mono::just).orElseGet(Mono::empty))
            .flatMap(claimResult -> Mono.from(CHECK_TOKEN_CLIENT.userInfo(userinfoEndpoint, jwtToken))
                .filter(userinfoResponse -> userinfoResponse.claimByPropertyName(oidcSASLConfiguration.getClaim())
                    .map(claim -> claim.equals(claimResult))
                    .orElse(false))
                .map(userinfoResponse -> claimResult));
    }
}
