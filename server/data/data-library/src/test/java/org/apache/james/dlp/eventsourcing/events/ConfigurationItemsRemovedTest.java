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

package org.apache.james.dlp.eventsourcing.events;

import static org.apache.james.dlp.api.DLPFixture.RULE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Domain;
import org.apache.james.dlp.eventsourcing.aggregates.DLPAggregateId;
import org.apache.james.eventsourcing.EventId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ConfigurationItemsRemovedTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(ConfigurationItemsRemoved.class)
            .verify();
    }

    @Test
    public void constructorShouldThrowWhenNullAggregateId() {
        assertThatThrownBy(() -> new ConfigurationItemsRemoved(null, EventId.first(), ImmutableList.of(RULE)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void constructorShouldThrowWhenNullEventId() {
        assertThatThrownBy(() -> new ConfigurationItemsRemoved(new DLPAggregateId(Domain.LOCALHOST), null, ImmutableList.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void constructorShouldThrowWhenEmptyRulesList() {
        assertThatThrownBy(() -> new ConfigurationItemsRemoved(new DLPAggregateId(Domain.LOCALHOST), EventId.first(), ImmutableList.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void constructorShouldThrowWhenNullRulesList() {
        assertThatThrownBy(() -> new ConfigurationItemsRemoved(new DLPAggregateId(Domain.LOCALHOST), EventId.first(), null))
            .isInstanceOf(NullPointerException.class);
    }

}