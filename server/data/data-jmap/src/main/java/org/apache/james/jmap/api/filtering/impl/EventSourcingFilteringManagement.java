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

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class EventSourcingFilteringManagement implements FilteringManagement {

    private static final ImmutableSet<Subscriber> NO_SUBSCRIBER = ImmutableSet.of();

    private final EventStore eventStore;
    private final EventSourcingSystem eventSourcingSystem;

    @Inject
    public EventSourcingFilteringManagement(EventStore eventStore) {
        this.eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(new DefineRulesCommandHandler(eventStore)),
            NO_SUBSCRIBER,
            eventStore);
        this.eventStore = eventStore;
    }

    @Override
    public Publisher<Version> defineRulesForUser(Username username, List<Rule> rules, Optional<Version> ifInState) {
        return Mono.from(eventSourcingSystem.dispatch(new DefineRulesCommand(username, rules, ifInState)))
            .map(events -> Version.from(events.stream()
                .map(Event::eventId)
                .sorted(Comparator.reverseOrder())
                .findFirst()));
    }

    @Override
    public Publisher<Rules> listRulesForUser(Username username) {
        Preconditions.checkNotNull(username);

        FilteringAggregateId aggregateId = new FilteringAggregateId(username);

        return Mono.from(eventStore.getEventsOfAggregate(aggregateId))
            .map(history -> FilteringAggregate.load(aggregateId, history).listRules())
            .defaultIfEmpty(new Rules(ImmutableList.of(), Version.INITIAL));
    }

    @Override
    public Publisher<Version> getLatestVersion(Username username) {
        Preconditions.checkNotNull(username);

        FilteringAggregateId aggregateId = new FilteringAggregateId(username);

        return Mono.from(eventStore.getEventsOfAggregate(aggregateId))
            .map(History::getVersionAsJava)
            .map(eventIdOptional -> eventIdOptional.map(eventId -> new Version(eventId.value()))
                .orElse(Version.INITIAL));
    }

}
