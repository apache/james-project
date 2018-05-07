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

package org.apache.james.eventsource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventStore;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class InMemoryEventStore implements EventStore {

    private final ConcurrentHashMap<AggregateId, History> store;

    public InMemoryEventStore() {
        this.store = new ConcurrentHashMap<>();
    }

    @Override
    public void appendAll(List<Event> events) {
        if (events.isEmpty()) {
            return;
        }
        AggregateId aggregateId = getAggregateId(events);

        if (!store.containsKey(aggregateId)) {
            appendToEmptyHistory(aggregateId, events);
        } else {
            appendToExistingHistory(aggregateId, events);
        }
    }

    private AggregateId getAggregateId(List<? extends Event> events) {
        Preconditions.checkArgument(!events.isEmpty());
        Preconditions.checkArgument(Event.belongsToSameAggregate(events));

        return events.stream()
            .map(Event::getAggregateId)
            .findFirst()
            .get();
    }

    private void appendToEmptyHistory(AggregateId aggregateId, List<Event> events) {
        History newHistory = History.of(events);

        History previousHistory = store.putIfAbsent(aggregateId, newHistory);
        if (previousHistory != null) {
            throw new EventStore.EventStoreFailedException();
        }
    }

    private void appendToExistingHistory(AggregateId aggregateId, List<? extends Event> events) {
        History currentHistory = store.get(aggregateId);
        List<Event> updatedEvents = updatedEvents(currentHistory, events);
        History updatedHistory = History.of(updatedEvents);

        boolean isReplaced = store.replace(aggregateId, currentHistory, updatedHistory);
        if (!isReplaced) {
            throw new EventStore.EventStoreFailedException();
        }
    }

    private List<Event> updatedEvents(History currentHistory, List<? extends Event> newEvents) {
        return ImmutableList.<Event>builder()
            .addAll(currentHistory.getEvents())
            .addAll(newEvents)
            .build();
    }

    @Override
    public History getEventsOfAggregate(AggregateId aggregateId) {
        return Optional.ofNullable(store.get(aggregateId))
            .orElse(History.empty());
    }
}
