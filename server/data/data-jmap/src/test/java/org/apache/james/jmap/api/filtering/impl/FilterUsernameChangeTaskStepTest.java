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

package org.apache.james.jmap.api.filtering.impl;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStoreExtension;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.core.publisher.Mono;

@ExtendWith(InMemoryEventStoreExtension.class)
public class FilterUsernameChangeTaskStepTest {
    private static final Username BOB = Username.of("bob");
    private static final Username ALICE = Username.of("alice");
    private static final Optional<Version> NO_VERSION = Optional.empty();

    private static final String NAME = "a name";
    private static final Rule.Condition CONDITION = Rule.Condition.of(Rule.Condition.Field.CC, Rule.Condition.Comparator.CONTAINS, "something");
    private static final Rule.Action ACTION = Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds("id-01"));
    private static final Rule.Builder RULE_BUILDER = Rule.builder().name(NAME).conditionGroup(CONDITION).action(ACTION);
    private static final Rule RULE_1 = RULE_BUILDER.id(Rule.Id.of("1")).build();
    private static final Rule RULE_2 = RULE_BUILDER.id(Rule.Id.of("2")).build();

    private FilterUsernameChangeTaskStep testee;
    private EventSourcingFilteringManagement filteringManagement;

    @BeforeEach
    void setup(EventStore eventStore) {
        filteringManagement = new EventSourcingFilteringManagement(eventStore);
        testee = new FilterUsernameChangeTaskStep(filteringManagement);
    }

    @Test
    void shouldMigrateFilters() {
        Mono.from(filteringManagement.defineRulesForUser(BOB, NO_VERSION, RULE_1))
            .block();

        Mono.from(testee.changeUsername(BOB, ALICE))
            .block();

        assertThat(Mono.from(filteringManagement.listRulesForUser(ALICE))
            .block().getRules())
            .containsOnly(RULE_1);
    }

    @Test
    void shouldRemoveFiltersFromOriginalAccount() {
        Mono.from(filteringManagement.defineRulesForUser(BOB, NO_VERSION, RULE_1))
            .block();

        Mono.from(testee.changeUsername(BOB, ALICE))
            .block();

        assertThat(Mono.from(filteringManagement.listRulesForUser(BOB))
            .block().getRules())
            .isEmpty();
    }

    @Test
    void shouldOverrideFiltersFromDestinationAccount() {
        Mono.from(filteringManagement.defineRulesForUser(BOB, NO_VERSION, RULE_1))
            .block();
        Mono.from(filteringManagement.defineRulesForUser(ALICE, NO_VERSION, RULE_2))
            .block();

        Mono.from(testee.changeUsername(BOB, ALICE))
            .block();

        assertThat(Mono.from(filteringManagement.listRulesForUser(ALICE))
            .block().getRules())
            .containsOnly(RULE_1);
    }

    @Test
    void shouldNotOverrideFiltersFromDestinationAccountWhenNoDataInSourceAccount() {
        Mono.from(filteringManagement.defineRulesForUser(ALICE, NO_VERSION, RULE_2))
            .block();

        Mono.from(testee.changeUsername(BOB, ALICE))
            .block();

        assertThat(Mono.from(filteringManagement.listRulesForUser(ALICE))
            .block().getRules())
            .containsOnly(RULE_2);
    }
}
