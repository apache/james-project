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
package org.apache.james.jmap.api.filtering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class FilteringAggregateIdTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(FilteringAggregateId.class).verify();
    }

    @Test
    void constructorShouldThrowWhenNullDomain() {
        assertThatThrownBy(() -> new FilteringAggregateId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void asAggregateKeyShouldReturnAStringContainingThePrefixAndTheDomain() {
        assertThat(new FilteringAggregateId(Username.of("foo@bar.space")).asAggregateKey())
            .isEqualTo("FilteringRule/foo@bar.space");
    }

    @Test
    void parseShouldThrowWhenNullString() {
        assertThatThrownBy(() -> FilteringAggregateId.parse(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseShouldThrowWhenStringDoesntMatchPrefix() {
        assertThatThrownBy(() -> FilteringAggregateId.parse("WrongPrefix/foo"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldThrowWhenStringDoesntContainSeparator() {
        assertThatThrownBy(() -> FilteringAggregateId.parse("WrongPrefix"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldThrowWhenStringDoesntContainUser() {
        assertThatThrownBy(() -> FilteringAggregateId.parse("FilteringRule/"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldThrowWhenStringDoesntHavePrefix() {
        assertThatThrownBy(() -> FilteringAggregateId.parse("FilteringRulefoo"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldKeepSlashInUsername() {
        assertThat(FilteringAggregateId.parse("FilteringRule/f/oo@bar.space").asAggregateKey())
            .isEqualTo("FilteringRule/f/oo@bar.space");
    }

}