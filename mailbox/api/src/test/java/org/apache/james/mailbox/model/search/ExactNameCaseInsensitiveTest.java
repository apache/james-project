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

package org.apache.james.mailbox.model.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ExactNameCaseInsensitiveTest {
    public static final String NAME = "toto";
    public static final String NAME_DIFFERENT_CASE_1 = "Toto";
    public static final String NAME_DIFFERENT_CASE_2 = "TOTO";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(ExactNameCaseInsensitive.class)
            .verify();
    }

    @Test
    void constructorShouldThrowOnNullName() {
        assertThatThrownBy(() -> new ExactNameCaseInsensitive(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isWildShouldReturnFalse() {
        assertThat(new ExactNameCaseInsensitive(NAME).isWild())
            .isFalse();
    }

    @Test
    void getCombinedNameShouldReturnName() {
        assertThat(new ExactNameCaseInsensitive(NAME).getCombinedName())
            .isEqualTo(NAME);
    }

    @Test
    void isExpressionMatchShouldReturnTrueWhenName() {
        assertThat(new ExactNameCaseInsensitive(NAME).isExpressionMatch(NAME))
            .isTrue();
    }

    @Test
    void isExpressionMatchShouldReturnTrueWhenNameDifferentCase1() {
        assertThat(new ExactNameCaseInsensitive(NAME).isExpressionMatch(NAME_DIFFERENT_CASE_1))
                .isTrue();
    }

    @Test
    void isExpressionMatchShouldReturnTrueWhenNameDifferentCase2() {
        assertThat(new ExactNameCaseInsensitive(NAME).isExpressionMatch(NAME_DIFFERENT_CASE_2))
                .isTrue();
    }

    @Test
    void isExpressionMatchShouldReturnFalseWhenOtherValue() {
        assertThat(new ExactNameCaseInsensitive(NAME).isExpressionMatch("other"))
            .isFalse();
    }

    @Test
    void isExpressionMatchShouldThrowOnNullValue() {
        assertThatThrownBy(() -> new ExactNameCaseInsensitive(NAME).isExpressionMatch(null))
            .isInstanceOf(NullPointerException.class);
    }

}