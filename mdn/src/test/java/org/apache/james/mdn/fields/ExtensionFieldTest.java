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

class ExtensionFieldTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(ExtensionField.class)
            .verify();
    }

    @Test
    void shouldThrowOnNullFieldName() {
        assertThatThrownBy(() -> ExtensionField.builder()
                .fieldName(null)
                .rawValue("rawValue")
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullRawValue() {
        assertThatThrownBy(() -> ExtensionField.builder()
                .fieldName("name")
                .rawValue(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnMultilineName() {
        assertThatThrownBy(() -> ExtensionField.builder()
                .fieldName("name\nmultiline")
                .rawValue("rawValue")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void formattedValueShouldDisplayNameAndRawValue() {
        assertThat(ExtensionField.builder()
                .fieldName("name")
                .rawValue("rawValue")
                .build()
                .formattedValue())
            .isEqualTo("name: rawValue");
    }
}
