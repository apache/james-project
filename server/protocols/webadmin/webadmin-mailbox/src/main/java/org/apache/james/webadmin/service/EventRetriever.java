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

package org.apache.james.webadmin.service;

import java.util.Optional;

import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public interface EventRetriever {
    static EventRetriever allEvents() {
        return new AllEventsRetriever();
    }

    static EventRetriever groupEvents(Group group) {
        return new GroupEventsRetriever(group);
    }

    static EventRetriever singleEvent(Group group, Event.EventId eventId) {
        return new SingleEventRetriever(group, eventId);
    }

    Optional<Group> forGroup();

    Optional<Event.EventId> forEvent();

    Flux<Tuple2<Group, Event>> retrieveEvents(EventDeadLetters deadLetters);

    default Flux<Tuple2<Group, Event>> listGroupEvents(EventDeadLetters deadLetters, Group group) {
        return deadLetters.failedEventIds(group)
            .flatMap(eventId -> deadLetters.failedEvent(group, eventId))
            .flatMap(event -> Flux.zip(Mono.just(group), Mono.just(event)));
    }

    class AllEventsRetriever implements EventRetriever {
        @Override
        public Optional<Group> forGroup() {
            return Optional.empty();
        }

        @Override
        public Optional<Event.EventId> forEvent() {
            return Optional.empty();
        }

        @Override
        public Flux<Tuple2<Group, Event>> retrieveEvents(EventDeadLetters deadLetters) {
            return deadLetters.groupsWithFailedEvents()
                .flatMap(group -> listGroupEvents(deadLetters, group));
        }
    }

    class GroupEventsRetriever implements EventRetriever {
        private final Group group;

        GroupEventsRetriever(Group group) {
            this.group = group;
        }

        @Override
        public Optional<Group> forGroup() {
            return Optional.of(group);
        }

        @Override
        public Optional<Event.EventId> forEvent() {
            return Optional.empty();
        }

        @Override
        public Flux<Tuple2<Group, Event>> retrieveEvents(EventDeadLetters deadLetters) {
            return listGroupEvents(deadLetters, group);
        }
    }

    class SingleEventRetriever implements EventRetriever {
        private final Group group;
        private final Event.EventId eventId;

        SingleEventRetriever(Group group, Event.EventId eventId) {
            this.group = group;
            this.eventId = eventId;
        }

        @Override
        public Optional<Group> forGroup() {
            return Optional.of(group);
        }

        @Override
        public Optional<Event.EventId> forEvent() {
            return Optional.of(eventId);
        }

        @Override
        public Flux<Tuple2<Group, Event>> retrieveEvents(EventDeadLetters deadLetters) {
            return Flux.just(group).zipWith(deadLetters.failedEvent(group, eventId));
        }
    }
}
