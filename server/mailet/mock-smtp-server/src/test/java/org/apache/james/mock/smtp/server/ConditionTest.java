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

package org.apache.james.mock.smtp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ConditionTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Condition.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenNullOperator() {
        assertThatThrownBy(() -> new Condition(null, "matchingValue"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenNullMatchingValue() {
        assertThatThrownBy(() -> new Condition(Operator.CONTAINS, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void matchesShouldReturnTrueWhenOperatorMatches() {
        Condition condition = new Condition(Operator.CONTAINS, "match me");

        assertThat(condition.matches("this contains match me string"))
            .isTrue();
    }

    @Test
    void matchesShouldReturnFalseWhenOperatorDoesNotMatch() {
        Condition condition = new Condition(Operator.CONTAINS, "match me");

        assertThat(condition.matches("this contains another string"))
            .isFalse();
    }

    @Test
    void matchesShouldThrowWhenNullLine() {
        Condition condition = new Condition(Operator.CONTAINS, "match me");

        assertThatThrownBy(() -> condition.matches(null))
            .isInstanceOf(NullPointerException.class);
    }
}
