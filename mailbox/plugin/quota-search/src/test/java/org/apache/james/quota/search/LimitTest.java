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

package org.apache.james.quota.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class LimitTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Limit.class)
            .verify();
    }

    @Test
    void getValueShouldReturnEmptyWhenUnlimited() {
        assertThat(Limit.unlimited()
            .getValue())
            .isEmpty();
    }

    @Test
    void getValueShouldReturnZeroWhenZero() {
        assertThat(Limit.of(0)
            .getValue())
            .contains(0);
    }

    @Test
    void getValueShouldReturnSuppliedValue() {
        assertThat(Limit.of(3)
            .getValue())
            .contains(3);
    }

    @Test
    void isLimitedShouldBeTrueWhenAValueIsSpecified() {
        assertThat(Limit.of(3).isLimited())
            .isTrue();
    }

    @Test
    void isLimitedShouldBeFalseWhenUnlimited() {
        assertThat(Limit.unlimited().isLimited())
            .isFalse();
    }

    @Test
    void ofShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> Limit.of(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}