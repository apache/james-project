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

import org.apache.james.jwt.introspection.DefaultIntrospectionClient;
import org.apache.james.jwt.introspection.IntrospectionClient;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.jwt.introspection.TokenIntrospectionResponse;
import org.reactivestreams.Publisher;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import reactor.core.publisher.Mono;

public class OidcJwtTokenVerifier {
    public static final IntrospectionClient INTROSPECTION_CLIENT = new DefaultIntrospectionClient();

    public static Optional<String> verifySignatureAndExtractClaim(String jwtToken, URL jwksURL, String claimName) {
        Optional<String> unverifiedClaim = getClaimWithoutSignatureVerification(jwtToken, "kid");
        PublicKeyProvider jwksPublicKeyProvider = unverifiedClaim
            .map(kidValue -> JwksPublicKeyProvider.of(jwksURL, kidValue))
            .orElse(JwksPublicKeyProvider.of(jwksURL));
        return new JwtTokenVerifier(jwksPublicKeyProvider).verifyAndExtractClaim(jwtToken, claimName, String.class);
    }

    public static <T> Optional<T> getClaimWithoutSignatureVerification(String token, String claimName) {
        int signatureIndex = token.lastIndexOf('.');
        if (signatureIndex <= 0) {
            return Optional.empty();
        }
        String nonSignedToken = token.substring(0, signatureIndex + 1);
        try {
            Jwt<Header, Claims> headerClaims = Jwts.parserBuilder().build().parseClaimsJwt(nonSignedToken);
            T claim = (T) headerClaims.getHeader().get(claimName);
            if (claim == null) {
                throw new MalformedJwtException("'" + claimName + "' field in token is mandatory");
            }
            return Optional.of(claim);
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    public static Publisher<String> verifyWithMaybeIntrospection(String jwtToken, URL jwksURL, String claimName, Optional<IntrospectionEndpoint> introspectionEndpoint) {
        return Mono.fromCallable(() -> verifySignatureAndExtractClaim(jwtToken, jwksURL, claimName))
            .flatMap(optional -> optional.map(Mono::just).orElseGet(Mono::empty))
            .flatMap(claimResult -> {
                if (introspectionEndpoint.isEmpty()) {
                    return Mono.just(claimResult);
                }
                return Mono.justOrEmpty(introspectionEndpoint)
                    .flatMap(endpoint -> Mono.from(INTROSPECTION_CLIENT.introspect(endpoint, jwtToken)))
                    .filter(TokenIntrospectionResponse::active)
                    .map(activeToken -> claimResult);
            });
    }
}
