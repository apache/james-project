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

package org.apache.james.dlp.eventsourcing.aggregates;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.james.dlp.api.DLPConfigurationItem;
import org.apache.james.dlp.api.DLPRules;
import org.apache.james.dlp.eventsourcing.events.ConfigurationItemsAdded;
import org.apache.james.dlp.eventsourcing.events.ConfigurationItemsRemoved;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.eventstore.History;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class DLPDomainConfiguration {

    public static DLPDomainConfiguration load(DLPAggregateId aggregateId, History history) {
        return new DLPDomainConfiguration(aggregateId, history);
    }

    private static class State {

        static State initial() {
            return new State(ImmutableSet.of());
        }

        final ImmutableSet<DLPConfigurationItem> rules;

        private State(ImmutableSet<DLPConfigurationItem> rules) {
            this.rules = rules;
        }

        State add(List<DLPConfigurationItem> toAdd) {
            ImmutableSet<DLPConfigurationItem> union = Stream.concat(this.rules.stream(), toAdd.stream()).collect(ImmutableSet.toImmutableSet());
            return new State(union);
        }

        State remove(List<DLPConfigurationItem> toRemove) {
            ImmutableSet<DLPConfigurationItem> filtered = rules.stream().filter(rule -> !toRemove.contains(rule)).collect(ImmutableSet.toImmutableSet());
            return new State(filtered);
        }
    }

    private final DLPAggregateId aggregateId;
    private final History history;
    private State state;

    private DLPDomainConfiguration(DLPAggregateId aggregateId, History history) {
        this.aggregateId = aggregateId;
        this.state = State.initial();
        history.getEventsJava().forEach(this::apply);
        this.history = history;
    }

    public DLPRules retrieveRules() {
        return new DLPRules(ImmutableList.copyOf(state.rules));
    }

    public List<EventWithState> clear() {
        ImmutableList<DLPConfigurationItem> rules = retrieveRules().getItems();
        if (!rules.isEmpty()) {
            Event event = new ConfigurationItemsRemoved(aggregateId, history.getNextEventId(), rules);
            apply(event);
            return ImmutableList.of(EventWithState.noState(event));
        } else {
            return ImmutableList.of();
        }
    }

    public List<EventWithState> store(DLPRules updatedRules) {
        ImmutableSet<DLPConfigurationItem> existingRules = retrieveRules().getItems().stream().collect(ImmutableSet.toImmutableSet());
        ImmutableSet<DLPConfigurationItem> updatedRulesSet = ImmutableSet.copyOf(updatedRules);

        Optional<Event> removedRulesEvent = generateRemovedRulesEvent(existingRules, updatedRulesSet);
        Optional<Event> addedRulesEvent = generateAddedRulesEvent(existingRules, updatedRulesSet, computeNextEventId(removedRulesEvent));

        ImmutableList<EventWithState> events = Stream
            .of(removedRulesEvent, addedRulesEvent)
            .flatMap(Optional::stream)
            .map(EventWithState::noState)
            .collect(ImmutableList.toImmutableList());

        events.forEach(e -> apply(e.event()));
        return events;
    }

    private EventId computeNextEventId(Optional<Event> removedRulesEvent) {
        return removedRulesEvent
            .map(Event::eventId)
            .map(EventId::next)
            .orElseGet(history::getNextEventId);
    }

    private Optional<Event> generateRemovedRulesEvent(ImmutableSet<DLPConfigurationItem> existingRules, ImmutableSet<DLPConfigurationItem> updateRulesSet) {
        Set<DLPConfigurationItem> removedRules = Sets.difference(existingRules, updateRulesSet);
        if (!removedRules.isEmpty()) {
            return Optional.of(new ConfigurationItemsRemoved(aggregateId, history.getNextEventId(), removedRules));
        }
        return Optional.empty();
    }

    private Optional<Event> generateAddedRulesEvent(Set<DLPConfigurationItem> existingRules, Set<DLPConfigurationItem> updateRulesSet, EventId nextEventId) {
        Set<DLPConfigurationItem> addedRules = Sets.difference(updateRulesSet, existingRules);
        if (!addedRules.isEmpty()) {
            return Optional.of(new ConfigurationItemsAdded(aggregateId, nextEventId, addedRules));
        }
        return Optional.empty();
    }

    private void apply(Event event) {
        if (event instanceof ConfigurationItemsAdded) {
            state = state.add(((ConfigurationItemsAdded) event).getRules());
        }
        if (event instanceof ConfigurationItemsRemoved) {
            state = state.remove(((ConfigurationItemsRemoved) event).getRules());
        }
    }

}
