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

import org.apache.james.core.User;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.junit.jupiter.api.Test;

public interface FilteringManagementContract {

    String BART_SIMPSON_CARTOON = "bart@simpson.cartoon";
    User USER = User.fromUsername(BART_SIMPSON_CARTOON);

    default FilteringManagement instanciateFilteringManagement(EventStore eventStore) {
        return new EventSourcingFilteringManagement(eventStore);
    }

    @Test
    default void listingRulesForUnknownUserShouldReturnEmptyList(EventStore eventStore) {
        assertThat(instanciateFilteringManagement(eventStore).listRulesForUser(USER))
            .isEmpty();
    }

    @Test
    default void listingRulesShouldThrowWhenNullUser(EventStore eventStore) {
        User user = null;
        assertThatThrownBy(() -> instanciateFilteringManagement(eventStore).listRulesForUser(user))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void listingRulesShouldReturnDefinedRules(EventStore eventStore) {
        FilteringManagement testee = instanciateFilteringManagement(eventStore);

        testee.defineRulesForUser(USER, RULE_1, RULE_2);

        assertThat(testee.listRulesForUser(USER))
            .containsExactly(RULE_1, RULE_2);
    }

    @Test
    default void listingRulesShouldReturnLastDefinedRules(EventStore eventStore) {
        FilteringManagement testee = instanciateFilteringManagement(eventStore);

        testee.defineRulesForUser(USER, RULE_1, RULE_2);
        testee.defineRulesForUser(USER, RULE_2, RULE_1);

        assertThat(testee.listRulesForUser(USER))
            .containsExactly(RULE_2, RULE_1);
    }

    @Test
    default void definingRulesShouldThrowWhenDuplicateRules(EventStore eventStore) {
        FilteringManagement testee = instanciateFilteringManagement(eventStore);

        assertThatThrownBy(() -> testee.defineRulesForUser(USER, RULE_1, RULE_1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void definingRulesShouldThrowWhenNullUser(EventStore eventStore) {
        FilteringManagement testee = instanciateFilteringManagement(eventStore);

        assertThatThrownBy(() -> testee.defineRulesForUser(null, RULE_1, RULE_1))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void definingRulesShouldThrowWhenNullRuleList(EventStore eventStore) {
        FilteringManagement testee = instanciateFilteringManagement(eventStore);

        List<Rule> rules = null;
        assertThatThrownBy(() -> testee.defineRulesForUser(USER, rules))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void definingRulesShouldKeepOrdering(EventStore eventStore) {
        FilteringManagement testee = instanciateFilteringManagement(eventStore);
        testee.defineRulesForUser(USER, RULE_3, RULE_2, RULE_1);

        assertThat(testee.listRulesForUser(USER))
            .containsExactly(RULE_3, RULE_2, RULE_1);
    }

    @Test
    default void definingEmptyRuleListShouldRemoveExistingRules(EventStore eventStore) {
        FilteringManagement testee = instanciateFilteringManagement(eventStore);

        testee.defineRulesForUser(USER, RULE_3, RULE_2, RULE_1);
        testee.clearRulesForUser(USER);

        assertThat(testee.listRulesForUser(USER)).isEmpty();
    }

    @Test
    default void allFieldsAndComparatorShouldWellBeStored(EventStore eventStore) {
        FilteringManagement testee = instanciateFilteringManagement(eventStore);

        testee.defineRulesForUser(USER, RULE_FROM, RULE_RECIPIENT, RULE_SUBJECT, RULE_TO, RULE_1);

        assertThat(testee.listRulesForUser(USER))
            .containsExactly(RULE_FROM, RULE_RECIPIENT, RULE_SUBJECT, RULE_TO, RULE_1);
    }

}