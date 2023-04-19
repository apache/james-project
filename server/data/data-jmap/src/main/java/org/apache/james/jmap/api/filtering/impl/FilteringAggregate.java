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
import java.util.Optional;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.jmap.api.exception.StateMismatchException;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class FilteringAggregate {
    private static final boolean ENABLE_INCREMENTS = Boolean.parseBoolean(System.getProperty("james.jmap.filters.eventsource.increments.enabled", "true"));
    private static final boolean ENABLE_SNAPSHOTS = Boolean.parseBoolean(System.getProperty("james.jmap.filters.eventsource.snapshots.enabled", "true"));

    public static FilteringAggregate load(FilteringAggregateId aggregateId, History eventsOfAggregate) {
        return new FilteringAggregate(aggregateId, eventsOfAggregate);
    }

    private static class State {

        static State initial() {
            return new State(ImmutableList.of());
        }

        final ImmutableList<Rule> rules;

        private State(ImmutableList<Rule> rules) {
            this.rules = rules;
        }

        State set(ImmutableList<Rule> rules) {
            return new State(rules);
        }
    }

    private final FilteringAggregateId aggregateId;
    private final History history;
    private State state;

    private FilteringAggregate(FilteringAggregateId aggregateId, History history) {
        this.aggregateId = aggregateId;
        this.state = State.initial();
        history.getEventsJava().forEach(this::apply);
        this.history = history;
    }

    public List<? extends Event> defineRules(DefineRulesCommand storeCommand) {
        Preconditions.checkArgument(shouldNotContainDuplicates(storeCommand.getRules()));
        StateMismatchException.checkState(expectedState(storeCommand.getIfInState()), "Provided state must be as same as the current state");
        ImmutableList<Event> events = generateEvents(storeCommand);
        events.forEach(this::apply);
        return events;
    }

    private ImmutableList<Event> generateEvents(DefineRulesCommand storeCommand) {
        EventId nextEventId = history.getNextEventId();
        if (ENABLE_INCREMENTS) {
            // SNAPSHOT periodically
            if (ENABLE_SNAPSHOTS && history.getEvents().size() >= 100) {
                return resetRules(storeCommand, nextEventId);
            }
            return IncrementalRuleChange.ofDiff(aggregateId, nextEventId, state.rules, storeCommand.getRules())
                .map(ImmutableList::<Event>of)
                .orElseGet(() -> resetRules(storeCommand, nextEventId));
        } else {
            return resetRules(storeCommand, nextEventId);
        }
    }

    private ImmutableList<Event> resetRules(DefineRulesCommand storeCommand, EventId nextEventId) {
        return ImmutableList.of(new RuleSetDefined(aggregateId, nextEventId, ImmutableList.copyOf(storeCommand.getRules())));
    }

    private boolean shouldNotContainDuplicates(List<Rule> rules) {
        long uniqueIdCount = rules.stream()
            .map(Rule::getId)
            .distinct()
            .count();
        return uniqueIdCount == rules.size();
    }

    private boolean expectedState(Optional<Version> ifInState) {
        return ifInState.map(requestedVersion -> history.getVersionAsJava()
                .map(eventId -> new Version(eventId.value()))
                .orElse(Version.INITIAL)
                .equals(requestedVersion))
            .orElse(true);
    }

    public Rules listRules() {
        return new Rules(state.rules,
            history.getVersionAsJava().map(EventId::value).map(Version::new).orElse(Version.INITIAL));
    }

    private void apply(Event event) {
        if (event instanceof RuleSetDefined) {
            state = state.set(((RuleSetDefined)event).getRules());
        }
        if (event instanceof IncrementalRuleChange) {
            state = state.set(((IncrementalRuleChange)event).apply(state.rules));
        }
    }
}
