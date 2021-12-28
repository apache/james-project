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

package org.apache.james.user.lib.model;

import static org.apache.james.user.lib.model.Algorithm.HashingMode.LEGACY;
import static org.apache.james.user.lib.model.Algorithm.HashingMode.PLAIN;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.Before;
import org.junit.Test;

public class DefaultUserTest {

    private DefaultUser user;

    @Before
    public void setUp() {
        user = new DefaultUser(
                Username.of("joe"),
                "5en6G6MezRroT3XKqkdPOmY/", // SHA-1 legacy hash of "secret"
                Algorithm.of("SHA-1", LEGACY),
                Algorithm.of("SHA-256", PLAIN));
    }

    @Test
    public void shouldYieldVerifyAlgorithm() {
        assertThat(user.getHashAlgorithm().asString()).isEqualTo("SHA-1/legacy");
        assertThat(user.getHashAlgorithm().getHashingMode()).isEqualToIgnoringCase(LEGACY.name());
    }

    @Test
    public void shouldVerifyPasswordUsingVerifyAlgorithm() {
        assertThat(user.verifyPassword("secret")).isTrue();
        assertThat(user.verifyPassword("secret2")).isFalse();
    }

    @Test
    public void shouldSetPasswordUsingUpdateAlgorithm() {
        user.setPassword("secret2");
        assertThat(user.getHashAlgorithm().asString()).isEqualTo("SHA-256/plain");
        assertThat(user.getHashAlgorithm().getHashingMode()).isEqualToIgnoringCase(PLAIN.name());

        assertThat(user.verifyPassword("secret2")).isTrue();
        assertThat(user.verifyPassword("secret")).isFalse();
    }
}
