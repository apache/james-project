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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;

public class JwtTokenVerifier {

    public interface Factory {
        JwtTokenVerifier create();
    }

    public static JwtTokenVerifier create(JwtConfiguration jwtConfiguration) {
        PublicKeyProvider publicKeyProvider = new PublicKeyProvider(jwtConfiguration, new PublicKeyReader());
        return new JwtTokenVerifier(publicKeyProvider);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenVerifier.class);
    private final PublicKeyProvider pubKeyProvider;

    public JwtTokenVerifier(PublicKeyProvider pubKeyProvider) {
        this.pubKeyProvider = pubKeyProvider;
    }

    public boolean verify(String token) {
        try {
            String subject = extractLogin(token);
            if (Strings.isNullOrEmpty(subject)) {
                throw new MalformedJwtException("'subject' field in token is mandatory");
            }
            return true;
        } catch (JwtException e) {
            LOGGER.info("Failed Jwt verification", e);
            return false;
        }
    }

    public String extractLogin(String token) throws JwtException {
        Jws<Claims> jws = parseToken(token);
        return jws
                .getBody()
                .getSubject();
    }

    public boolean hasAttribute(String attributeName, Object expectedValue, String token) {
        try {
            Jwts
                .parser()
                .require(attributeName, expectedValue)
                .setSigningKey(pubKeyProvider.get())
                .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            LOGGER.info("Jwt validation failed for claim {} to {}", attributeName, expectedValue, e);
            return false;
        }
    }

    private Jws<Claims> parseToken(String token) throws JwtException {
        return Jwts
                .parser()
                .setSigningKey(pubKeyProvider.get())
                .parseClaimsJws(token);
    }
}
