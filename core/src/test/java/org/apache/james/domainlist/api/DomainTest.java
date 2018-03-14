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

import org.apache.james.core.Domain;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DomainTest {

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(Domain.class).verify();
    }

    @Test
    public void shouldBeCaseInsensitive() {
        assertThat(Domain.of("Domain")).isEqualTo(Domain.of("domain"));
    }

    @Test
    public void shouldThrowWhenDomainContainAtSymbol() {
        assertThatThrownBy(() -> Domain.of("Dom@in")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldThrowWhenDomainIsEmpty() {
        assertThatThrownBy(() -> Domain.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldThrowOnNullArgument() {
        assertThatThrownBy(() -> Domain.of(null)).isInstanceOf(NullPointerException.class);
    }

}