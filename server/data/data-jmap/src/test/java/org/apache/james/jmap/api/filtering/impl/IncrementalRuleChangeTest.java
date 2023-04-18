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

import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_1;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_1_MODIFIED;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_2;
import static org.apache.james.jmap.api.filtering.RuleFixture.RULE_3;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.jmap.api.filtering.Rule;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

class IncrementalRuleChangeTest {
    public static final FilteringAggregateId AGGREGATE_ID = new FilteringAggregateId(Username.of("bob"));
    public static final EventId EVENT_ID = EventId.first();

    @Test
    void removingOneRule() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1),
            ImmutableList.of()))
            .contains(new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(),
                ImmutableSet.of(RULE_1.getId())));
    }

    @Test
    void removingOneRuleOutOfTwo() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1, RULE_2),
            ImmutableList.of(RULE_2)))
            .contains(new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(),
                ImmutableSet.of(RULE_1.getId())));
    }

    @Test
    void removingMiddleRule() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1, RULE_2, RULE_3),
            ImmutableList.of(RULE_1, RULE_3)))
            .contains(new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(),
                ImmutableSet.of(RULE_2.getId())));
    }

    @Test
    void noop() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1, RULE_2, RULE_3),
            ImmutableList.of(RULE_1, RULE_2, RULE_3)))
            .contains(new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(), ImmutableSet.of()));
    }

    @Test
    void reorderingRuleIsNotManagedByIncrements() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1, RULE_2, RULE_3),
            ImmutableList.of(RULE_1, RULE_3, RULE_2)))
            .isEmpty();
    }

    @Test
    void addingRuleInTheMiddleIsNotManagedByIncrement() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1, RULE_2),
            ImmutableList.of(RULE_1, RULE_3, RULE_2)))
            .isEmpty();
    }

    @Test
    void postPendingOneRule() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1, RULE_2),
            ImmutableList.of(RULE_1, RULE_2, RULE_3)))
            .contains(new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(RULE_3), ImmutableSet.of()));
    }

    @Test
    void prependingOneRule() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1, RULE_2),
            ImmutableList.of(RULE_3, RULE_1, RULE_2)))
            .contains(new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(RULE_3), ImmutableList.of(), ImmutableSet.of()));
    }

    @Test
    void prependingAndPostpending() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1),
            ImmutableList.of(RULE_3, RULE_1, RULE_2)))
            .contains(new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(RULE_3), ImmutableList.of(RULE_2), ImmutableSet.of()));
    }

    @Test
    void prependingAndPostpendingAndRemoval() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1),
            ImmutableList.of(RULE_3, RULE_2)))
            .contains(new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(RULE_3, RULE_2), ImmutableList.of(), ImmutableSet.of(RULE_1.getId())));
    }

    @Test
    void ruleModificationIsNotManagedByIncrement() {
        assertThat(IncrementalRuleChange.ofDiff(AGGREGATE_ID, EVENT_ID,
            ImmutableList.of(RULE_1),
            ImmutableList.of(RULE_1_MODIFIED)))
            .isEmpty();
    }

    @Test
    void removingOneRuleShouldBeWellApplied() {
        ImmutableList<Rule> origin = ImmutableList.of(RULE_1);
        IncrementalRuleChange incrementalChange = new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(),
            ImmutableSet.of(RULE_1.getId()));
        assertThat(incrementalChange.apply(origin))
            .isEqualTo(ImmutableList.of());
    }

    @Test
    void removingOneRuleOutOfTwoShouldBeWellApplied() {
        ImmutableList<Rule> origin = ImmutableList.of(RULE_1, RULE_2);
        IncrementalRuleChange incrementalChange = new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(),
            ImmutableSet.of(RULE_1.getId()));
        assertThat(incrementalChange.apply(origin))
            .isEqualTo(ImmutableList.of(RULE_2));
    }

    @Test
    void removingMiddleRuleShouldBeWellApplied() {
        ImmutableList<Rule> origin = ImmutableList.of(RULE_1, RULE_2, RULE_3);
        IncrementalRuleChange incrementalChange = new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(),
            ImmutableSet.of(RULE_2.getId()));
        assertThat(incrementalChange.apply(origin))
            .isEqualTo(ImmutableList.of(RULE_1, RULE_3));
    }

    @Test
    void noopShouldBeWellApplied() {
        ImmutableList<Rule> origin = ImmutableList.of(RULE_1, RULE_2, RULE_3);

        IncrementalRuleChange incrementalRuleChange = new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(), ImmutableSet.of());

        assertThat(incrementalRuleChange.apply(origin))
            .isEqualTo(origin);
    }

    @Test
    void postPendingOneRuleShouldBeWellApplied() {
        ImmutableList<Rule> origin = ImmutableList.of(RULE_1, RULE_2);

        IncrementalRuleChange incrementalRuleChange = new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(), ImmutableList.of(RULE_3), ImmutableSet.of());

        assertThat(incrementalRuleChange.apply(origin))
            .isEqualTo(ImmutableList.of(RULE_1, RULE_2, RULE_3));
    }

    @Test
    void prependingOneRuleShouldBeWellApplied() {
        ImmutableList<Rule> origin = ImmutableList.of(RULE_1, RULE_2);

        IncrementalRuleChange incrementalRuleChange = new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(RULE_3), ImmutableList.of(), ImmutableSet.of());

        assertThat(incrementalRuleChange.apply(origin))
            .isEqualTo(ImmutableList.of(RULE_3, RULE_1, RULE_2));
    }

    @Test
    void prependingAndPostpendingShouldBeWellApplied() {
        ImmutableList<Rule> origin = ImmutableList.of(RULE_1);

        IncrementalRuleChange incrementalRuleChange = new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(RULE_3), ImmutableList.of(RULE_2), ImmutableSet.of());

        assertThat(incrementalRuleChange.apply(origin))
            .isEqualTo(ImmutableList.of(RULE_3, RULE_1, RULE_2));
    }

    @Test
    void prependingAndPostpendingAndRemovalShouldBeWellApplied() {
        ImmutableList<Rule> origin = ImmutableList.of(RULE_1);

        IncrementalRuleChange incrementalRuleChange = new IncrementalRuleChange(AGGREGATE_ID, EVENT_ID, ImmutableList.of(RULE_3), ImmutableList.of(RULE_2), ImmutableSet.of(RULE_1.getId()));

        assertThat(incrementalRuleChange.apply(origin))
            .isEqualTo(ImmutableList.of(RULE_3, RULE_2));
    }
}