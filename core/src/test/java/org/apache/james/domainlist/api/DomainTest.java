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

package org.apache.james.domainlist.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Domain;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class DomainTest {

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(Domain.class)
            .withIgnoredFields("domainName")
            .verify();
    }

    @Test
    void shouldBeCaseInsensitive() {
        assertThat(Domain.of("Domain")).isEqualTo(Domain.of("domain"));
    }

    @Test
    void shouldRemoveBrackets() {
        assertThat(Domain.of("[domain]")).isEqualTo(Domain.of("domain"));
    }

    @Test
    void openBracketWithTextShouldNotBeRemoved() {
        assertThat(Domain.of("[domain")).isEqualTo(Domain.of("[Domain"));
    }

    @Test
    void singleOpenBracketShouldNotBeRemoved() {
        assertThat(Domain.of("[")).isEqualTo(Domain.of("["));
    }

    @Test
    void singleClosingBracketShouldNotBeRemoved() {
        assertThat(Domain.of("]")).isEqualTo(Domain.of("]"));
    }

    @Test
    void closeBracketWithTextShouldNotBeRemoved() {
        assertThat(Domain.of("aaa]")).isEqualTo(Domain.of("aaa]"));
    }

    @Test
    void bracketSurroundedWithTextShouldNotBeRemoved() {
        assertThat(Domain.of("a[aaa]a")).isEqualTo(Domain.of("a[aaa]a"));
    }

    @Test
    void bracketWithTextSuffixShouldNotBeRemoved() {
        assertThat(Domain.of("[aaa]a")).isEqualTo(Domain.of("[aaa]a"));
    }

    @Test
    void bracketWithTextPrefixShouldNotBeRemoved() {
        assertThat(Domain.of("a[aaa]")).isEqualTo(Domain.of("a[aaa]"));
    }

    @Test
    void singleBracketShouldNotBeRemoved() {
        assertThat(Domain.of("[]")).isEqualTo(Domain.of("[]"));
    }

    @Test
    void shouldThrowWhenDomainContainAtSymbol() {
        assertThatThrownBy(() -> Domain.of("Dom@in")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenDomainIsEmpty() {
        assertThatThrownBy(() -> Domain.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnNullArgument() {
        assertThatThrownBy(() -> Domain.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldAllow255LongDomain() {
        assertThat(Domain.of(StringUtils.repeat('a', 255)).asString())
            .hasSize(255);
    }

    @Test
    void shouldThrowWhenTooLong() {
        assertThatThrownBy(() -> Domain.of(StringUtils.repeat('a', 256)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}