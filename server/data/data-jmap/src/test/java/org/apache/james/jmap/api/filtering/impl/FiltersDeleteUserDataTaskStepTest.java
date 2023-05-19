/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.jmap.api.filtering.impl;

import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_1;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_2;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.FiltersDeleteUserDataTaskStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class FiltersDeleteUserDataTaskStepTest {
    private static final Username BOB = Username.of("bob");

    private FilteringManagement filteringManagement;
    private FiltersDeleteUserDataTaskStep testee;

    @BeforeEach
    void beforeEach() {
        filteringManagement = new EventSourcingFilteringManagement(new InMemoryEventStore());
        testee = new FiltersDeleteUserDataTaskStep(filteringManagement);
    }

    @Test
    void shouldClearRules() {
        Mono.from(filteringManagement.defineRulesForUser(BOB, Optional.empty(), RULE_1, RULE_2)).block();

        Mono.from(testee.deleteUserData(BOB)).block();

        assertThat(Mono.from(filteringManagement.listRulesForUser(BOB)).block().getRules())
            .isEmpty();
    }
}
