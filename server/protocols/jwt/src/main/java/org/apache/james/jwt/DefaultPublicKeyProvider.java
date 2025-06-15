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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

public class DefaultPublicKeyProvider implements PublicKeyProvider {

    private final JwtConfiguration jwtConfiguration;
    private final PublicKeyReader reader;

    public DefaultPublicKeyProvider(JwtConfiguration jwtConfiguration, PublicKeyReader reader) {
        this.jwtConfiguration = jwtConfiguration;
        this.reader = reader;
    }

    @Override
    public List<PublicKey> get() throws MissingOrInvalidKeyException {
        ImmutableList<PublicKey> keys = jwtConfiguration.getJwtPublicKeyPem()
            .stream()
            .flatMap(s -> reader.fromPEM(s).stream())
            .collect(ImmutableList.toImmutableList());
        if (keys.size() != jwtConfiguration.getJwtPublicKeyPem().size()) {
            throw new MissingOrInvalidKeyException();
        }
        return keys;
    }

    @Override
    public Optional<PublicKey> get(String kid) throws MissingOrInvalidKeyException {
        return get().stream().filter(k -> computeKid(k).equals(kid)).findFirst();
    }

    protected static byte[] unpad(byte[] bytes) {
        return (bytes.length > 1 && bytes[0] == 0x00 && (bytes[1] & 0x80) != 0)
            ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length)
            : bytes;
    }

    protected static String b64u(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    protected static String toCanonicalJwkJson(PublicKey key) {
        // json, only required keys, lexicographically sorted keys,
        // no whitespace, UTF8, base64url encoded unmodified raw values
        if (key instanceof RSAPublicKey rsa) {
            String n = b64u(unpad(rsa.getModulus().toByteArray()));
            String e = b64u(unpad(rsa.getPublicExponent().toByteArray()));
            return String.format("{\"e\":\"%s\",\"kty\":\"RSA\",\"n\":\"%s\"}", e, n);
        }

        if (key instanceof ECPublicKey ec) {
            String crv = "P-" + ec.getParams().getCurve().getField().getFieldSize();
            String x = b64u(unpad(ec.getW().getAffineX().toByteArray()));
            String y = b64u(unpad(ec.getW().getAffineY().toByteArray()));
            return String.format("{\"crv\":\"%s\",\"kty\":\"EC\",\"x\":\"%s\",\"y\":\"%s\"}", crv, x, y);
        }

        // ED key bits are more complicated to get from BigInteger
        // (reversing, adding x bit, adding/removing padding to exact size,
        // but we don't know the key size itself form the java instance)
        // so we do very basic ASN.1 parsing ourselves to find the
        // position and length of the raw bits (which differ in different curves)
        if (key instanceof EdECPublicKey ed) {
            String crv = ed.getParams().getName();
            byte[] encoded = key.getEncoded(); // SubjectPublicKeyInfo structure, encoded using ASN.1 DER
            int pos = 2; // skip outer SEQUENCE header
            pos += 2 + encoded[pos + 1] + 1; // skip AlgorithmIdentifier SEQUENCE and BIT STRING tag (0x03)
            int len = encoded[pos++]; // BIT STRING length (assume short form - less than 128 raw bytes)
            pos++; // skip unused bits byte
            byte[] raw = new byte[len - 1];
            System.arraycopy(encoded, pos, raw, 0, raw.length);
            String x = b64u(raw);
            return String.format("{\"crv\":\"%s\",\"kty\":\"OKP\",\"x\":\"%s\"}", crv, x);
        }

        throw new IllegalArgumentException("Unsupported key algorithm: " + key.getAlgorithm());
    }

    public static String computeKid(PublicKey key) {
        // calculate the JWK Thumbprint of the key (RFC 7638),
        // which is base64url(sha256(canonicalJson(jwk(key))))
        String jwk = toCanonicalJwkJson(key);
        byte[] jwkBytes = jwk.getBytes(StandardCharsets.UTF_8);
        try {
            return b64u(MessageDigest.getInstance("SHA-256").digest(jwkBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
