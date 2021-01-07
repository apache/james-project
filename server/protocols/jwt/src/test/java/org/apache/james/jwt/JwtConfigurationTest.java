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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class JwtConfigurationTest {
    private static final String INVALID_PUBLIC_KEY = "invalidPublicKey";
    private static final String VALID_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n" +
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtlChO/nlVP27MpdkG0Bh\n" +
        "16XrMRf6M4NeyGa7j5+1UKm42IKUf3lM28oe82MqIIRyvskPc11NuzSor8HmvH8H\n" +
        "lhDs5DyJtx2qp35AT0zCqfwlaDnlDc/QDlZv1CoRZGpQk1Inyh6SbZwYpxxwh0fi\n" +
        "+d/4RpE3LBVo8wgOaXPylOlHxsDizfkL8QwXItyakBfMO6jWQRrj7/9WDhGf4Hi+\n" +
        "GQur1tPGZDl9mvCoRHjFrD5M/yypIPlfMGWFVEvV5jClNMLAQ9bYFuOc7H1fEWw6\n" +
        "U1LZUUbJW9/CH45YXz82CYqkrfbnQxqRb2iVbVjs/sHopHd1NTiCfUtwvcYJiBVj\n" +
        "kwIDAQAB\n" +
        "-----END PUBLIC KEY-----";

    @Test
    void getJwtPublicKeyPemShouldReturnEmptyWhenEmptyPublicKey() {
        JwtConfiguration jwtConfiguration = new JwtConfiguration(Optional.empty());

        assertThat(jwtConfiguration.getJwtPublicKeyPem()).isNotPresent();
    }

    @Test
    void constructorShouldThrowWhenNullPublicKey() {
        assertThatThrownBy(() -> new JwtConfiguration(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenNonePublicKey() {
        assertThatThrownBy(() -> new JwtConfiguration(Optional.of("")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructorShouldThrowWhenInvalidPublicKey() {
        assertThatThrownBy(() -> new JwtConfiguration(Optional.of(INVALID_PUBLIC_KEY)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getJwtPublicKeyPemShouldReturnWhenValidPublicKey() {
        JwtConfiguration jwtConfiguration = new JwtConfiguration(Optional.of(VALID_PUBLIC_KEY));

        assertThat(jwtConfiguration.getJwtPublicKeyPem()).isPresent();
    }
}