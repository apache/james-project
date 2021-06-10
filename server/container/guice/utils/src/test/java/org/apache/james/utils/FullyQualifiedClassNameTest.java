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

package org.apache.james.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class FullyQualifiedClassNameTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(FullyQualifiedClassName.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenNull() {
        assertThatThrownBy(() -> new FullyQualifiedClassName(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> new FullyQualifiedClassName(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getNameShouldReturnSuppliedValue() {
        String name = "org.apache.MyClass";
        assertThat(new FullyQualifiedClassName(name).getName())
            .isEqualTo(name);
    }

    @Test
    void getNameShouldReturnSuppliedValueWhenOnlyAClassName() {
        String name = "MyClass";
        assertThat(new FullyQualifiedClassName(name).getName())
            .isEqualTo(name);
    }
}