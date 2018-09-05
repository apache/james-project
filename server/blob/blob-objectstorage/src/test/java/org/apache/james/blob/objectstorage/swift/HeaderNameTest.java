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

package org.apache.james.blob.objectstorage.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class HeaderNameTest {
    private static final String EXPECTED = "expected";

    @Test
    public void headerNameShouldRespectBeanContract() {
        EqualsVerifier.forClass(HeaderName.class).verify();
    }

    @Test
    void passHeaderNameCanBeBuiltFromNonEmptyString() {
        PassHeaderName actual = PassHeaderName.of(EXPECTED);
        assertThat(actual.value()).isEqualTo(EXPECTED);
    }

    @Test
    void passHeaderNameCanNotBeBuiltFromEmptyString() {
        assertThatThrownBy(() -> PassHeaderName.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passHeaderNameCanNotBeBuiltFromNull() {
        assertThatThrownBy(() -> PassHeaderName.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userHeaderNameCanBeBuiltFromNonEmptyString() {
        UserHeaderName actual = UserHeaderName.of(EXPECTED);
        assertThat(actual.value()).isEqualTo(EXPECTED);
    }

    @Test
    void userHeaderNameCanNotBeBuiltFromEmptyString() {
        assertThatThrownBy(() -> UserHeaderName.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userHeaderNameCanNotBeBuiltFromNull() {
        assertThatThrownBy(() -> UserHeaderName.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}