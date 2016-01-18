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
package org.apache.james.jmap.crypto;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

public class JwtTokenVerifier {

    private final PublicKeyProvider pubKeyProvider;

    @Inject
    @VisibleForTesting
    JwtTokenVerifier(PublicKeyProvider pubKeyProvider) {
        this.pubKeyProvider = pubKeyProvider;
    }

    public boolean verify(String token) throws JwtException {
        parseToken(token);
        return true;
    }

    public String extractLogin(String token) throws JwtException {
        Jws<Claims> jws = parseToken(token);
        return jws
                .getBody()
                .getSubject();
    }

    private Jws<Claims> parseToken(String token) throws JwtException {
        return Jwts
                .parser()
                .setSigningKey(pubKeyProvider.get())
                .parseClaimsJws(token);
    }
}
