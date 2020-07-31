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

package org.apache.james.mdn.modifier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class DispositionModifierTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(DispositionModifier.class)
            .verify();
    }

    @Test
    void shouldThrowOnNull() {
        assertThatThrownBy(() -> new DispositionModifier(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnMultiLine() {
        assertThatThrownBy(() -> new DispositionModifier("multi\nline"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnEndBreakLine() {
        assertThatThrownBy(() -> new DispositionModifier("multi\n"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnBeginningBreakLine() {
        assertThatThrownBy(() -> new DispositionModifier("\nline"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnEmptyValue() {
        assertThatThrownBy(() -> new DispositionModifier(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnFoldingWhiteSpaceValue() {
        assertThatThrownBy(() -> new DispositionModifier("    "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
