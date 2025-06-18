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

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Security;
import java.security.interfaces.RSAPublicKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class DefaultPublicKeyProviderTest {

    private static final String PUBLIC_PEM_KEY = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtlChO/nlVP27MpdkG0Bh\n" +
            "16XrMRf6M4NeyGa7j5+1UKm42IKUf3lM28oe82MqIIRyvskPc11NuzSor8HmvH8H\n" +
            "lhDs5DyJtx2qp35AT0zCqfwlaDnlDc/QDlZv1CoRZGpQk1Inyh6SbZwYpxxwh0fi\n" +
            "+d/4RpE3LBVo8wgOaXPylOlHxsDizfkL8QwXItyakBfMO6jWQRrj7/9WDhGf4Hi+\n" +
            "GQur1tPGZDl9mvCoRHjFrD5M/yypIPlfMGWFVEvV5jClNMLAQ9bYFuOc7H1fEWw6\n" +
            "U1LZUUbJW9/CH45YXz82CYqkrfbnQxqRb2iVbVjs/sHopHd1NTiCfUtwvcYJiBVj\n" +
            "kwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    @BeforeAll
    static void init() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void getShouldNotThrowWhenPEMKeyProvided() {
        JwtConfiguration configWithPEMKey = new JwtConfiguration(ImmutableList.of(PUBLIC_PEM_KEY));

        PublicKeyProvider sut = new DefaultPublicKeyProvider(configWithPEMKey, new PublicKeyReader());

        assertThat(sut.get()).allSatisfy(key -> assertThat(key).isInstanceOf(RSAPublicKey.class));
    }

    @Test
    void getShouldNotThrowWhenPEMKeyNotProvided() {
        JwtConfiguration configWithPEMKey = new JwtConfiguration(ImmutableList.of());

        PublicKeyProvider sut = new DefaultPublicKeyProvider(configWithPEMKey, new PublicKeyReader());

        assertThat(sut.get()).isEmpty();
    }

    private static void testKid(String exptected, String pem) {
        JwtConfiguration configWithPEMKey = new JwtConfiguration(ImmutableList.of(pem));
        PublicKeyProvider sut = new DefaultPublicKeyProvider(configWithPEMKey, new PublicKeyReader());
        String kid = DefaultPublicKeyProvider.computeKid(sut.get().get(0));
        assertThat(kid).isEqualTo(exptected);
    }

    @Test
    void computeKidShouldComputeJWKThumbprintCorrectly() {
        testKid("2iUdFiYTvwSzAzJuMRwvr70CLKKmYdbfDz0TpNQs0tc", PUBLIC_PEM_KEY);

        String pemRSA = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0vx7agoebGcQSuuPiLJX\n" +
            "ZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tS\n" +
            "oc/BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ/2W+5JsGY4Hc5n9yBXArwl93lqt\n" +
            "7/RN5w6Cf0h4QyQ5v+65YGjQR0/FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0\n" +
            "zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt+bFTWhAI4vMQFh6WeZu0f\n" +
            "M4lFd2NcRwr3XPksINHaQ+G/xBniIqbw0Ls1jF44+csFCur+kEgU8awapJzKnqDK\n" +
            "gwIDAQAB\n" +
            "-----END PUBLIC KEY-----";
        testKid("NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs", pemRSA);

        String pemEc = "-----BEGIN PUBLIC KEY-----\n" +
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7d1Se1rTIqVTXszA8gIHagLlqPH8\n" +
            "a98VUCRHWaWW8S3J+WnwJsJVy/4qgZx6yFoJN7zAOIBcseO95zVrbet4gg==\n" +
            "-----END PUBLIC KEY-----\n";
        testKid("WdX4yCEsy0Xx48ZfI_4DV0RPhFMdydNFqqwEqiAFAc8", pemEc);

        String pemEd = "-----BEGIN PUBLIC KEY-----\n" +
            "MCowBQYDK2VwAyEA11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=\n" +
            "-----END PUBLIC KEY-----";
        testKid("kPrK_qmxVWaYVA9wwBF6Iuo3vVzz7TxHCTwXBygrS4k", pemEd);
    }
}
