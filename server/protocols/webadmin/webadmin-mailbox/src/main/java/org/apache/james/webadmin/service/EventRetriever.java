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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Optional;

import org.apache.james.events.Event;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

public interface EventRetriever {
    static EventRetriever allEvents() {
        return new AllEventsRetriever();
    }

    static EventRetriever groupEvents(Group group) {
        return new GroupEventsRetriever(group);
    }

    static EventRetriever singleEvent(Group group, EventDeadLetters.InsertionId insertionId) {
        return new SingleEventRetriever(group, insertionId);
    }

    Optional<Group> forGroup();

    Optional<EventDeadLetters.InsertionId> forEvent();

    Flux<Tuple3<Group, Event, EventDeadLetters.InsertionId>> retrieveEvents(EventDeadLetters deadLetters);

    default Flux<Tuple3<Group, Event, EventDeadLetters.InsertionId>> listGroupEvents(EventDeadLetters deadLetters, Group group) {
        return deadLetters.failedIds(group)
            .flatMap(insertionId -> Flux.zip(Mono.just(group), deadLetters.failedEvent(group, insertionId), Mono.just(insertionId)), DEFAULT_CONCURRENCY);
    }

    class AllEventsRetriever implements EventRetriever {
        @Override
        public Optional<Group> forGroup() {
            return Optional.empty();
        }

        @Override
        public Optional<EventDeadLetters.InsertionId> forEvent() {
            return Optional.empty();
        }

        @Override
        public Flux<Tuple3<Group, Event, EventDeadLetters.InsertionId>> retrieveEvents(EventDeadLetters deadLetters) {
            return deadLetters.groupsWithFailedEvents()
                .flatMap(group -> listGroupEvents(deadLetters, group), DEFAULT_CONCURRENCY);
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
        public Optional<EventDeadLetters.InsertionId> forEvent() {
            return Optional.empty();
        }

        @Override
        public Flux<Tuple3<Group, Event, EventDeadLetters.InsertionId>> retrieveEvents(EventDeadLetters deadLetters) {
            return listGroupEvents(deadLetters, group);
        }
    }

    class SingleEventRetriever implements EventRetriever {
        private final Group group;
        private final EventDeadLetters.InsertionId insertionId;

        SingleEventRetriever(Group group, EventDeadLetters.InsertionId insertionId) {
            this.group = group;
            this.insertionId = insertionId;
        }

        @Override
        public Optional<Group> forGroup() {
            return Optional.of(group);
        }

        @Override
        public Optional<EventDeadLetters.InsertionId> forEvent() {
            return Optional.of(insertionId);
        }

        @Override
        public Flux<Tuple3<Group, Event, EventDeadLetters.InsertionId>> retrieveEvents(EventDeadLetters deadLetters) {
            return Flux.zip(Mono.just(group), deadLetters.failedEvent(group, insertionId), Mono.just(insertionId));
        }
    }
}
