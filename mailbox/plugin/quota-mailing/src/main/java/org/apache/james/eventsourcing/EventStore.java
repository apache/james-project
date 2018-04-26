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

package org.apache.james.eventsourcing;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public interface EventStore {

    class EventStoreFailedException extends RuntimeException {

    }

    class History {
        public static History empty() {
            return new History(ImmutableList.of());
        }

        public static History of(List<Event> events) {
            return new History(ImmutableList.copyOf(events));
        }

        public static History of(Event... events) {
            return of(ImmutableList.copyOf(events));
        }

        private final List<Event> events;

        private History(List<Event> events) {
            if (hasEventIdDuplicates(events)) {
                throw new EventStoreFailedException();
            }
            this.events = events;
        }

        public boolean hasEventIdDuplicates(List<Event> events) {
            Set<EventId> eventIds = events.stream()
                .map(Event::eventId)
                .collect(Guavate.toImmutableSet());

            return eventIds.size() != events.size();
        }

        public Optional<EventId> getVersion() {
            return events.stream()
                .map(Event::eventId)
                .max(Comparator.naturalOrder());
        }

        public List<Event> getEvents() {
            return events;
        }

        public EventId getNextEventId() {
            return getVersion()
                .map(EventId::next)
                .orElse(EventId.first());
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof History) {
                History history = (History) o;

                return Objects.equals(this.events, history.events);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(events);
        }
    }

    default void append(Event event) {
        appendAll(ImmutableList.of(event));
    }

    default void appendAll(Event... events) {
        appendAll(ImmutableList.copyOf(events));
    }

    /**
     * This method should check that no input event has an id already stored and throw otherwise
     * It should also check that all events belong to the same aggregate
     */
    void appendAll(List<Event> events);

    History getEventsOfAggregate(AggregateId aggregateId);
}
