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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;

public class OidcJwtTokenVerifier {

    public Optional<String> verifyAndExtractClaim(String jwtToken, URL jwksURL, String claimName) {
        PublicKeyProvider jwksPublicKeyProvider = getClaimWithoutSignatureVerification(jwtToken, "kid", String.class)
            .map(kidValue -> JwksPublicKeyProvider.of(jwksURL, kidValue))
            .orElse(JwksPublicKeyProvider.of(jwksURL));
        return new JwtTokenVerifier(jwksPublicKeyProvider).verifyAndExtractClaim(jwtToken, claimName, String.class);
    }

    public static <T> Optional<T> getClaimWithoutSignatureVerification(String token, String claimName, Class<T> returnType) {
        int signatureIndex = token.lastIndexOf('.');
        if (signatureIndex <= 0) {
            return Optional.empty();
        }
        String nonSignedToken = token.substring(0, signatureIndex + 1);
        try {
            Jwt<Header, Claims> headerClaims = Jwts.parser().parseClaimsJwt(nonSignedToken);
            T claim = (T) headerClaims.getHeader().get(claimName);
            if (claim == null) {
                throw new MalformedJwtException("'" + claimName + "' field in token is mandatory");
            }
            return Optional.of(claim);
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}
