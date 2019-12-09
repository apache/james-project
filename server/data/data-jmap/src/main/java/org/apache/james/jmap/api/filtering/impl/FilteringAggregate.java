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

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.jmap.api.filtering.Rule;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import scala.jdk.javaapi.CollectionConverters;

public class FilteringAggregate {

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
        CollectionConverters.asJava(history.getEvents()).forEach(this::apply);
        this.history = history;
    }

    public List<? extends Event> defineRules(List<Rule> rules) {
        Preconditions.checkArgument(shouldNotContainDuplicates(rules));
        ImmutableList<RuleSetDefined> events = ImmutableList.of(
            new RuleSetDefined(aggregateId, history.getNextEventId(), ImmutableList.copyOf(rules)));
        events.forEach(this::apply);
        return events;
    }

    private boolean shouldNotContainDuplicates(List<Rule> rules) {
        long uniqueIdCount = rules.stream()
            .map(Rule::getId)
            .distinct()
            .count();
        return uniqueIdCount == rules.size();
    }

    public List<Rule> listRules() {
        return state.rules;
    }

    private void apply(Event event) {
        if (event instanceof RuleSetDefined) {
            state = state.set(((RuleSetDefined)event).getRules());
        }
    }
}
