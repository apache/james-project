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
package org.apache.james.user.jpa.model;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class JPAUserTest {

    private static final String RANDOM_PASSWORD = "baeMiqu7";

    @Test
    void hashPasswordShouldBeNoopWhenNone() {
        //I doubt the expected result was the author intent
        Assertions.assertThat(JPAUser.hashPassword(RANDOM_PASSWORD, "NONE")).isEqualTo("password");
    }

    @Test
    void hashPasswordShouldHashWhenMD5() {
        Assertions.assertThat(JPAUser.hashPassword(RANDOM_PASSWORD, "MD5")).isEqualTo("702000e50c9fd3755b8fc20ecb07d1ac");
    }

    @Test
    void hashPasswordShouldHashWhenSHA1() {
        Assertions.assertThat(JPAUser.hashPassword(RANDOM_PASSWORD, "SHA1")).isEqualTo("05dbbaa7b4bcae245f14d19ae58ef1b80adf3363");
    }

    @Test
    void hashPasswordShouldHashWhenSHA256() {
        Assertions.assertThat(JPAUser.hashPassword(RANDOM_PASSWORD, "SHA-256")).isEqualTo("6d06c72a578fe0b78ede2393b07739831a287774dcad0b18bc4bde8b0c948b82");
    }

    @Test
    void hashPasswordShouldHashWhenSHA512() {
        Assertions.assertThat(JPAUser.hashPassword(RANDOM_PASSWORD, "SHA-512")).isEqualTo("f9cc82d1c04bb2ce0494a51f7a21d07ac60b6f79a8a55397f454603acac29d8589fdfd694d5c01ba01a346c76b090abca9ad855b5b0c92c6062ad6d93cdc0d03");
    }

    @Test
    void hashPasswordShouldSha1WhenRandomString() {
        Assertions.assertThat(JPAUser.hashPassword(RANDOM_PASSWORD, "random")).isEqualTo("05dbbaa7b4bcae245f14d19ae58ef1b80adf3363");
    }

    @Test
    void hashPasswordShouldMD5WhenNull() {
        Assertions.assertThat(JPAUser.hashPassword(RANDOM_PASSWORD, null)).isEqualTo("702000e50c9fd3755b8fc20ecb07d1ac");
    }
}