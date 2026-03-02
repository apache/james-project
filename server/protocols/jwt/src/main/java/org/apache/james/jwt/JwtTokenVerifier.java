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

import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

public class JwtTokenVerifier {

    public interface Factory {
        JwtTokenVerifier create();
    }

    public static JwtTokenVerifier create(JwtConfiguration jwtConfiguration) {
        PublicKeyProvider publicKeyProvider = new DefaultPublicKeyProvider(jwtConfiguration, new PublicKeyReader());
        return new JwtTokenVerifier(publicKeyProvider);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenVerifier.class);

    private final List<JwtParser> jwtParsers;

    public JwtTokenVerifier(PublicKeyProvider pubKeyProvider) {
        this.jwtParsers = pubKeyProvider.get()
            .stream()
            .map(this::toImmutableJwtParser)
            .collect(ImmutableList.toImmutableList());
    }

    public Optional<String> verifyAndExtractLogin(String token) {
        return verifyAndExtractClaim(token, "sub", String.class)
            .filter(s -> !s.isEmpty());
    }

    public <T> Optional<T> verifyAndExtractClaim(String token, String claimName, Class<T> returnType) {
        return verify(token)
            .flatMap(claims -> {
                try {
                    return Optional.ofNullable(claims.get(claimName, returnType));
                } catch (JwtException e) {
                    LOGGER.info("Failed Jwt verification", e);
                    return Optional.empty();
                }
            });
    }

    public Optional<Claims> verify(String token) {
        return jwtParsers.stream()
            .flatMap(parser -> retrieveClaims(token, parser).stream())
            .findFirst();
    }

    private Optional<Claims> retrieveClaims(String token, JwtParser parser) {
        try {
            Jws<Claims> jws = parser.parseClaimsJws(token);
            return Optional.of(jws.getBody());
        } catch (JwtException e) {
            LOGGER.info("Failed Jwt verification", e);
            return Optional.empty();
        }
    }

    public boolean hasAttribute(String attributeName, Object expectedValue, String token) {
        try {
            return verifyAndExtractClaim(token, attributeName, Object.class)
                .map(expectedValue::equals)
                .orElse(false);
        } catch (JwtException e) {
            LOGGER.info("Jwt validation failed for claim {} to {}", attributeName, expectedValue, e);
            return false;
        }
    }

    private JwtParser toImmutableJwtParser(PublicKey publicKey) {
        return Jwts.parserBuilder()
            .setSigningKey(publicKey)
            .build();
    }
}
