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

import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_1;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_2;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_3;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_FROM;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_RECIPIENT;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_SUBJECT;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FilteringManagementContract {

    String BART_SIMPSON_CARTOON = "bart@simpson.cartoon";
    Username USERNAME = Username.of(BART_SIMPSON_CARTOON);

    default FilteringManagement instantiateFilteringManagement(EventStore eventStore) {
        return new EventSourcingFilteringManagement(eventStore);
    }

    @Test
    default void listingRulesForUnknownUserShouldReturnEmptyList(EventStore eventStore) {
        assertThat(Mono.from(instantiateFilteringManagement(eventStore).listRulesForUser(USERNAME)).block())
            .isEqualTo(new Rules(ImmutableList.of(), new Version(-1)));
    }

    @Test
    default void listingRulesShouldThrowWhenNullUser(EventStore eventStore) {
        Username username = null;
        assertThatThrownBy(() -> instantiateFilteringManagement(eventStore).listRulesForUser(username))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void listingRulesShouldReturnDefinedRules(EventStore eventStore) {
        FilteringManagement testee = instantiateFilteringManagement(eventStore);

        Mono.from(testee.defineRulesForUser(USERNAME, RULE_1, RULE_2)).block();

        assertThat(Mono.from(testee.listRulesForUser(USERNAME)).block())
            .isEqualTo(new Rules(ImmutableList.of(RULE_1, RULE_2), new Version(0)));
    }

    @Test
    default void listingRulesShouldReturnLastDefinedRules(EventStore eventStore) {
        FilteringManagement testee = instantiateFilteringManagement(eventStore);

        Mono.from(testee.defineRulesForUser(USERNAME, RULE_1, RULE_2)).block();
        Mono.from(testee.defineRulesForUser(USERNAME, RULE_2, RULE_1)).block();

        assertThat(Mono.from(testee.listRulesForUser(USERNAME)).block())
            .isEqualTo(new Rules(ImmutableList.of(RULE_2, RULE_1), new Version(1)));
    }

    @Test
    default void definingRulesShouldThrowWhenDuplicateRules(EventStore eventStore) {
        FilteringManagement testee = instantiateFilteringManagement(eventStore);

        assertThatThrownBy(() -> Mono.from(testee.defineRulesForUser(USERNAME, RULE_1, RULE_1)).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void definingRulesShouldThrowWhenNullUser(EventStore eventStore) {
        FilteringManagement testee = instantiateFilteringManagement(eventStore);

        assertThatThrownBy(() -> Mono.from(testee.defineRulesForUser(null, RULE_1, RULE_1)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void definingRulesShouldThrowWhenNullRuleList(EventStore eventStore) {
        FilteringManagement testee = instantiateFilteringManagement(eventStore);

        List<Rule> rules = null;
        assertThatThrownBy(() -> Mono.from(testee.defineRulesForUser(USERNAME, rules)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void definingRulesShouldKeepOrdering(EventStore eventStore) {
        FilteringManagement testee = instantiateFilteringManagement(eventStore);
        Mono.from(testee.defineRulesForUser(USERNAME, RULE_3, RULE_2, RULE_1)).block();


        assertThat(Mono.from(testee.listRulesForUser(USERNAME)).block())
            .isEqualTo(new Rules(ImmutableList.of(RULE_3, RULE_2, RULE_1), new Version(0)));
    }

    @Test
    default void definingEmptyRuleListShouldRemoveExistingRules(EventStore eventStore) {
        FilteringManagement testee = instantiateFilteringManagement(eventStore);

        Mono.from(testee.defineRulesForUser(USERNAME, RULE_3, RULE_2, RULE_1)).block();
        Mono.from(testee.clearRulesForUser(USERNAME)).block();

        assertThat(Mono.from(testee.listRulesForUser(USERNAME)).block())
            .isEqualTo(new Rules(ImmutableList.of(), new Version(1)));
    }

    @Test
    default void allFieldsAndComparatorShouldWellBeStored(EventStore eventStore) {
        FilteringManagement testee = instantiateFilteringManagement(eventStore);

        Mono.from(testee.defineRulesForUser(USERNAME, RULE_FROM, RULE_RECIPIENT, RULE_SUBJECT, RULE_TO, RULE_1)).block();


        assertThat(Mono.from(testee.listRulesForUser(USERNAME)).block())
            .isEqualTo(new Rules(ImmutableList.of(RULE_FROM, RULE_RECIPIENT, RULE_SUBJECT, RULE_TO, RULE_1), new Version(0)));
    }

}