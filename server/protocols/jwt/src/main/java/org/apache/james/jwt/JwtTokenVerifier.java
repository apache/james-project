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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.CompressionCodecResolver;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.impl.compression.DefaultCompressionCodecResolver;

public class JwtTokenVerifier {
    private static final CompressionCodecResolver DEFAULT_COMPRESSION_CODEC_RESOLVER = new DefaultCompressionCodecResolver();
    private static final CompressionCodecResolver SECURE_COMPRESSION_CODEC_RESOLVER = header -> {
        if (Optional.ofNullable(header.getCompressionAlgorithm()).isPresent()) {
            throw new RuntimeException("Rejecting a ZIP JWT. Usage of ZIPPED JWT can result in " +
                "excessive memory usage with malicious JWT tokens. To activate support for ZIPPed" +
                "JWT please run James with the -Djames.jwt.zip.allow=true system property.");
        }
        return DEFAULT_COMPRESSION_CODEC_RESOLVER.resolveCompressionCodec(header);
    };
    private static final boolean allowZipJWT = Optional.ofNullable(System.getProperty("james.jwt.zip.allow"))
        .map(Boolean::parseBoolean)
        .orElse(false);
    @VisibleForTesting
    static CompressionCodecResolver CONFIGURED_COMPRESSION_CODEC_RESOLVER = Optional.of(allowZipJWT)
        .filter(b -> b)
        .map(any -> DEFAULT_COMPRESSION_CODEC_RESOLVER)
        .orElse(SECURE_COMPRESSION_CODEC_RESOLVER);

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
        return jwtParsers.stream()
            .flatMap(parser -> verifyAndExtractClaim(token, claimName, returnType, parser).stream())
            .findFirst();
    }

    private <T> Optional<T> verifyAndExtractClaim(String token, String claimName, Class<T> returnType, JwtParser parser) {
        try {
            Jws<Claims> jws = parser.parseClaimsJws(token);
            T claim = jws
                .getBody()
                .get(claimName, returnType);
            if (claim == null) {
                throw new MalformedJwtException("'" + claimName + "' field in token is mandatory");
            }
            return Optional.of(claim);
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
            .setCompressionCodecResolver(CONFIGURED_COMPRESSION_CODEC_RESOLVER)
            .build();
    }
}
