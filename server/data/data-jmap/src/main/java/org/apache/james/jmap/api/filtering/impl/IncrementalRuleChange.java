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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.jmap.api.filtering.Rule;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class IncrementalRuleChange implements Event {
    public static Optional<IncrementalRuleChange> ofDiff(FilteringAggregateId aggregateId, EventId eventId, List<Rule> before, List<Rule> after) {
        ImmutableSet<Rule.Id> idsBefore = before.stream().map(Rule::getId).collect(ImmutableSet.toImmutableSet());
        ImmutableSet<Rule.Id> idsAfter = after.stream().map(Rule::getId).collect(ImmutableSet.toImmutableSet());

        ImmutableMap<Rule.Id, Rule> beforeIndexed = before.stream()
            .collect(ImmutableMap.toImmutableMap(Rule::getId, rule -> rule));

        ImmutableMap<Rule.Id, Rule> afterIndexed = after.stream()
            .collect(ImmutableMap.toImmutableMap(Rule::getId, rule -> rule));

        // Deleted elements appears in
        ImmutableSet<Rule.Id> deleted = Sets.difference(idsBefore, idsAfter).immutableCopy();

        List<Rule.Id> commonElements = ImmutableList.copyOf(Sets.intersection(idsBefore, idsAfter).immutableCopy());

        ImmutableList<Rule.Id> idsAfterList = idsAfter.asList();
        ImmutableList.Builder<Rule> updatedRules = ImmutableList.builder();
        int prependedItems = 0;
        int postPendedItems = 0;
        boolean inPrepended = true;
        boolean inCommonSection = false;
        boolean inPostpended = false;
        int position = 0;
        while (position < idsAfter.size()) {
            Rule.Id id = idsAfterList.get(position);
            if (inPrepended) {
                if (commonElements.contains(id)) {
                    inPrepended = false;
                    inCommonSection = true;
                    continue;
                } else {
                    prependedItems++;
                    position++;
                    continue;
                }
            }
            if (inPostpended) {
                if (commonElements.contains(id)) {
                    return Optional.empty();
                } else {
                    postPendedItems++;
                    position++;
                    continue;
                }
            }
            if (inCommonSection) {
                if (!commonElements.contains(id)) {
                    inCommonSection = false;
                    inPostpended = true;
                    continue;
                }
                int positionInCommonElements = position - prependedItems;
                if (positionInCommonElements > commonElements.size()) {
                    // Safeguard
                    return Optional.empty();
                }
                if (!commonElements.get(positionInCommonElements).equals(id)) {
                    // Order of commons items changed
                    return Optional.empty();
                }
                if (!beforeIndexed.get(id).equals(afterIndexed.get(id))) {
                    updatedRules.add(afterIndexed.get(id));
                    position++;
                    continue;
                }
                // All fine
                position++;
                continue;
            }
            throw new RuntimeException("Unexpected status");
        }

        ImmutableList<Rule> preprended = idsAfter.stream()
            .limit(prependedItems)
            .map(afterIndexed::get)
            .collect(ImmutableList.toImmutableList());

        ImmutableList<Rule> postPended = idsAfter.asList()
            .reverse()
            .stream()
            .limit(postPendedItems)
            .map(afterIndexed::get)
            .collect(ImmutableList.toImmutableList())
            .reverse();

        return Optional.of(new IncrementalRuleChange(aggregateId, eventId,
            preprended, postPended, deleted, updatedRules.build()));
    }

    private final FilteringAggregateId aggregateId;
    private final EventId eventId;
    private final ImmutableList<Rule> rulesPrepended;
    private final ImmutableList<Rule> rulesPostpended;
    private final ImmutableSet<Rule.Id> rulesDeleted;
    private final ImmutableList<Rule> rulesUpdated;

    public IncrementalRuleChange(FilteringAggregateId aggregateId, EventId eventId, ImmutableList<Rule> rulesPrepended, ImmutableList<Rule> rulesPostpended, ImmutableSet<Rule.Id> rulesDeleted, ImmutableList<Rule> rulesUpdated) {
        this.aggregateId = aggregateId;
        this.eventId = eventId;
        this.rulesPrepended = rulesPrepended;
        this.rulesPostpended = rulesPostpended;
        this.rulesDeleted = rulesDeleted;
        this.rulesUpdated = rulesUpdated;
    }

    @Override
    public EventId eventId() {
        return eventId;
    }

    @Override
    public AggregateId getAggregateId() {
        return aggregateId;
    }

    public ImmutableList<Rule> getRulesPrepended() {
        return rulesPrepended;
    }

    public ImmutableList<Rule> getRulesPostPended() {
        return rulesPostpended;
    }

    public ImmutableSet<Rule.Id> getRulesDeleted() {
        return rulesDeleted;
    }

    public ImmutableList<Rule> apply(ImmutableList<Rule> rules) {
        ImmutableMap<Rule.Id, Rule> indexedUpdates = rulesUpdated.stream()
            .collect(ImmutableMap.toImmutableMap(
                Rule::getId,
                rule -> rule));

        return ImmutableList.<Rule>builder()
            .addAll(rulesPrepended)
            .addAll(rules.stream()
                .filter(rule -> !rulesDeleted.contains(rule.getId()))
                .map(rule -> Optional.ofNullable(indexedUpdates.get(rule.getId()))
                    .orElse(rule))
                .collect(ImmutableList.toImmutableList()))
            .addAll(rulesPostpended)
            .build();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IncrementalRuleChange that = (IncrementalRuleChange) o;
        return Objects.equals(aggregateId, that.aggregateId) &&
            Objects.equals(eventId, that.eventId) &&
            Objects.equals(rulesDeleted, that.rulesDeleted) &&
            Objects.equals(rulesPrepended, that.rulesPrepended) &&
            Objects.equals(rulesUpdated, that.rulesUpdated) &&
            Objects.equals(rulesPostpended, that.rulesPostpended);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(aggregateId, eventId, rulesPrepended, rulesPostpended, rulesDeleted, rulesDeleted);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("aggregateId", aggregateId)
            .add("eventId", eventId)
            .add("rulesDeleted", rulesDeleted)
            .add("rulesPrepended", rulesPrepended)
            .add("rulesPostpended", rulesPostpended)
            .add("rulesUpdated", rulesUpdated)
            .toString();
    }
}
