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


import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.CompressionAlgorithm;

public class JwtTokenVerifier {
    static boolean allowZipJWT = Optional.ofNullable(System.getProperty("james.jwt.zip.allow"))
        .map(Boolean::parseBoolean)
        .orElse(false);

    /**
     * A CompressionAlgorithm that delegates to another compression algorithm
     * if compression is allowed, or throws exceptions if not.
     */
    private static class SecureCompressionAlgorithm implements CompressionAlgorithm {
        String id;
        CompressionAlgorithm delegate;

        public SecureCompressionAlgorithm(String id, CompressionAlgorithm delegate) {
            this.id = id;
            this.delegate = delegate;
        }

        private void validate() {
            if (!allowZipJWT) {
                throw new RuntimeException("Rejecting a ZIP JWT. Usage of ZIPPED JWT can result in " +
                    "excessive memory usage with malicious JWT tokens. To activate support for ZIPPed" +
                    "JWT please run James with the -Djames.jwt.zip.allow=true system property.");
            }
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public OutputStream compress(OutputStream out) {
            validate();
            return delegate.compress(out);
        }

        @Override
        public InputStream decompress(InputStream in) {
            validate();
            return delegate.decompress(in);
        }
    }

    // wrap all supported compression algorithms with 'secure' ones that throw exception if disabled
    private static final List<SecureCompressionAlgorithm> SECURE_COMPRESSION_ALGORITHMS =
        Jwts.ZIP.get().values().stream().map(c -> new SecureCompressionAlgorithm(c.getId(), c)).toList();

    public interface Factory {
        JwtTokenVerifier create();
    }

    public static JwtTokenVerifier create(JwtConfiguration jwtConfiguration) {
        PublicKeyProvider publicKeyProvider = new DefaultPublicKeyProvider(jwtConfiguration, new PublicKeyReader());
        return new JwtTokenVerifier(publicKeyProvider);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenVerifier.class);

    private final JwtParser kidJwtParser;
    private final List<JwtParser> jwtParsers;

    public JwtTokenVerifier(PublicKeyProvider pubKeyProvider) {
        // one parser that performs key lookup by kid
        this.kidJwtParser = toImmutableJwtParser(jwtHeaders -> {
            String kid = Objects.requireNonNull((String)jwtHeaders.get("kid"));
            return pubKeyProvider.get(kid).orElse(null);
        });
        // a list of parsers, one for each of the provider's keys
        this.jwtParsers = pubKeyProvider.get()
            .stream()
            .map(key -> toImmutableJwtParser(jwtHeaders -> key))
            .collect(ImmutableList.toImmutableList());
    }

    public Optional<String> verifyAndExtractLogin(String token) {
        return verifyAndExtractClaim(token, "sub", String.class)
            .filter(s -> !s.isEmpty());
    }

    public <T> Optional<T> verifyAndExtractClaim(String token, String claimName, Class<T> returnType) {
        try {
            // if the token contains a kid, verify only with the corresponding key (or fail)
            return verifyAndExtractClaim(token, claimName, returnType, kidJwtParser);
        } catch (NullPointerException npe) { // our own key locator throws NPE when there is no kid
            // if token does not specify kid, fallback to trying all keys
            return jwtParsers.stream()
                .flatMap(parser -> verifyAndExtractClaim(token, claimName, returnType, parser).stream())
                .findFirst();
        }
    }

    private <T> Optional<T> verifyAndExtractClaim(String token, String claimName, Class<T> returnType, JwtParser parser) {
        try {
            Jws<Claims> jws = parser.parseSignedClaims(token);
            T claim = jws
                .getPayload()
                .get(claimName, returnType);
            if (claim == null) {
                throw new MalformedJwtException("'" + claimName + "' field in token is mandatory");
            }
            return Optional.of(claim);
        } catch (JwtException e) { // also if kid was given but our locator didn't find the corresponding key
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

    private JwtParser toImmutableJwtParser(Locator<Key> keyLocator) {
        return Jwts.parser()
            .keyLocator(keyLocator)
            .zip().add(SECURE_COMPRESSION_ALGORITHMS).and()
            .build();
    }
}
