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

public class PrefixedWildcardCaseInsensitiveTest {
    public static final String NAME = "toto";
    public static final String NAME_DIFFERENT_CASE_1 = "Toto";
    public static final String NAME_DIFFERENT_CASE_2 = "TOTO";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(PrefixedWildcardCaseInsensitive.class)
            .verify();
    }

    @Test
    void constructorShouldThrowOnNullName() {
        assertThatThrownBy(() -> new PrefixedWildcardCaseInsensitive(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isWildShouldReturnTrue() {
        assertThat(new PrefixedWildcardCaseInsensitive(NAME).isWild())
            .isTrue();
    }

    @Test
    void getCombinedNameShouldReturnName() {
        assertThat(new PrefixedWildcardCaseInsensitive(NAME).getCombinedName())
            .isEqualTo(NAME + MailboxNameExpression.FREEWILDCARD);
    }

    @Test
    void isExpressionMatchShouldReturnTrueWhenName() {
        assertThat(new PrefixedWildcardCaseInsensitive(NAME).isExpressionMatch(NAME))
            .isTrue();
    }

    @Test
    void isExpressionMatchShouldReturnTrueWhenNameWithDifferentCase1() {
        assertThat(new PrefixedWildcardCaseInsensitive(NAME).isExpressionMatch(NAME_DIFFERENT_CASE_1))
            .isTrue();
    }

    @Test
    void isExpressionMatchShouldReturnTrueWhenNameWithDifferentCase2() {
        assertThat(new PrefixedWildcardCaseInsensitive(NAME).isExpressionMatch(NAME_DIFFERENT_CASE_2))
            .isTrue();
    }

    @Test
    void isExpressionMatchShouldReturnTrueWhenNameAndPostfix() {
        assertThat(new PrefixedWildcardCaseInsensitive(NAME).isExpressionMatch(NAME + "any"))
            .isTrue();
    }

    @Test
    void isExpressionMatchShouldReturnTrueWhenNameWithDifferentCase1AndPostfix() {
        assertThat(new PrefixedWildcardCaseInsensitive(NAME).isExpressionMatch(NAME_DIFFERENT_CASE_1 + "any"))
            .isTrue();
    }

    @Test
    void isExpressionMatchShouldReturnTrueWhenNameWithDifferentCase2AndPostfix() {
        assertThat(new PrefixedWildcardCaseInsensitive(NAME).isExpressionMatch(NAME_DIFFERENT_CASE_2 + "any"))
            .isTrue();
    }

    @Test
    void isExpressionMatchShouldReturnFalseWhenOtherValue() {
        assertThat(new PrefixedWildcardCaseInsensitive(NAME).isExpressionMatch("other"))
            .isFalse();
    }

    @Test
    void isExpressionMatchShouldThrowOnNullValue() {
        assertThatThrownBy(() -> new PrefixedWildcardCaseInsensitive(NAME).isExpressionMatch(null))
            .isInstanceOf(NullPointerException.class);
    }
}