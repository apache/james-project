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
import org.apache.james.jwt.introspection.TokenIntrospectionResponse;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import io.jsonwebtoken.JwtException;
import reactor.core.publisher.Mono;

public class OidcJwtTokenVerifier {
    public static final CheckTokenClient CHECK_TOKEN_CLIENT = new DefaultCheckTokenClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcJwtTokenVerifier.class);

    private final OidcSASLConfiguration oidcSASLConfiguration;
    // Shared across tokens so that the key set is cached: see JwksPublicKeyProvider
    private final Supplier<JwksPublicKeyProvider> publicKeyProvider;

    public OidcJwtTokenVerifier(OidcSASLConfiguration oidcSASLConfiguration) {
        this.oidcSASLConfiguration = oidcSASLConfiguration;
        this.publicKeyProvider = Suppliers.memoize(() -> JwksPublicKeyProvider.of(oidcSASLConfiguration.getJwksURL()));
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
        if (jwtToken == null || jwtToken.isBlank()) {
            LOGGER.info("Failed Jwt verification: blank token");
            return Optional.empty();
        }
        // A MissingOrInvalidKeyException (the keys could not be fetched) is not a rejected token: it escapes, so
        // that the SASL exchange fails right away rather than with an invalid_token challenge (RFC 7628)
        try {
            return new JwtTokenVerifier(publicKeyProvider.get())
                .verify(jwtToken)
                .filter(claims -> oidcSASLConfiguration.getAud().map(expectedAud -> claims.getAudience().contains(expectedAud))
                    .orElse(true)) // true if no aud is configured
                .flatMap(claims -> Optional.ofNullable(claims.get(oidcSASLConfiguration.getClaim(), String.class)));
        } catch (JwtException e) {
            // Not the exception itself: jjwt messages quote the token header
            LOGGER.info("Failed Jwt verification: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @VisibleForTesting
    Publisher<String> verifyWithIntrospection(String jwtToken, IntrospectionEndpoint introspectionEndpoint) {
        return Mono.fromCallable(() -> verifySignatureAndExtractClaim(jwtToken))
            .flatMap(optional -> optional.map(Mono::just).orElseGet(Mono::empty))
            .flatMap(claimResult -> Mono.from(CHECK_TOKEN_CLIENT.introspect(introspectionEndpoint, jwtToken))
                .filter(TokenIntrospectionResponse::active)
                .filter(tokenIntrospectionResponse -> tokenIntrospectionResponse.claimByPropertyName(oidcSASLConfiguration.getClaim())
                    .map(claim -> claim.equals(claimResult))
                    .orElse(false))
                .map(activeResponse -> claimResult));
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
