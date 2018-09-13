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

package org.apache.james.blob.objectstorage.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.google.crypto.tink.subtle.Hex;

class CryptoConfigBuilderTest {

    public static final char[] PASSWORD = "password".toCharArray();
    public static final String SALT = "0123456789abcdef";

    @Test
    void shouldNotBuildCryptoConfigIfSaltMissing() {
        CryptoConfigBuilder builder = new CryptoConfigBuilder();
        builder.password(PASSWORD);
        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotBuildCryptoConfigIfSaltEmpty() {
        CryptoConfigBuilder builder = new CryptoConfigBuilder();
        builder.password(PASSWORD);
        builder.salt("");
        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotBuildCryptoConfigIfSaltNotHex() {
        CryptoConfigBuilder builder = new CryptoConfigBuilder();
        builder.password(PASSWORD);
        builder.salt("ghijk");
        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotBuildCryptoConfigIfPasswordMissing() {
        CryptoConfigBuilder builder = new CryptoConfigBuilder();
        builder.salt(SALT);
        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotBuildCryptoConfigIfPasswordEmpty() {
        CryptoConfigBuilder builder = new CryptoConfigBuilder();
        builder.salt(SALT);
        builder.password("".toCharArray());
        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldBuildCryptoConfig() {
        CryptoConfigBuilder builder = new CryptoConfigBuilder();
        builder.salt(SALT);
        builder.password(PASSWORD);
        CryptoConfig actual = builder.build();
        assertThat(actual.password()).isEqualTo(PASSWORD);
        assertThat(actual.salt()).isEqualTo(Hex.decode(SALT));
    }

}