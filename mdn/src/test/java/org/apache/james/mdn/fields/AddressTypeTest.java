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

package org.apache.james.mdn.fields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class AddressTypeTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(AddressType.class)
            .verify();
    }

    @Test
    void constructorShouldThrowOnNull() {
        assertThatThrownBy(() -> new AddressType(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowOnEmpty() {
        assertThatThrownBy(() -> new AddressType(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnFoldingWhiteSpaces() {
        assertThatThrownBy(() -> new AddressType("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnLineBreaks() {
        assertThatThrownBy(() -> new AddressType("a\nb"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnLineBreakAtTheEnd() {
        assertThatThrownBy(() -> new AddressType("a\n"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnLineBreakAtTheBeginning() {
        assertThatThrownBy(() -> new AddressType("\na"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldAcceptValidValue() {
        String type = "ab";
        AddressType addressType = new AddressType(type);

        assertThat(addressType.getType())
            .isEqualTo(type);
    }

    @Test
    void typeShouldBeTrimmed() {
        AddressType addressType = new AddressType("  ab  ");

        assertThat(addressType.getType())
            .isEqualTo("ab");
    }
}
