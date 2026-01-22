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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.GenericGroup;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

class EventRetrieverTest {
    private static class TestEvent implements Event {
        private final EventId eventId;

        private TestEvent(String id) {
            this.eventId = EventId.of(UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public Username getUsername() {
            return Username.of("user");
        }

        @Override
        public boolean isNoop() {
            return false;
        }

        @Override
        public EventId getEventId() {
            return eventId;
        }
    }

    @Test
    void allEventsRetrieverShouldDeprioritizeNonCriticalGroups() {
        EventDeadLetters deadLetters = mock(EventDeadLetters.class);
        Group nonCriticalGroup = new GenericGroup("non-critical");
        Group criticalGroup = new GenericGroup("critical");
        EventDeadLetters.InsertionId nonCriticalId = EventDeadLetters.InsertionId.of(UUID.fromString("71fd8b41-a2bb-4a95-9d45-78f359bc058f"));
        EventDeadLetters.InsertionId criticalId = EventDeadLetters.InsertionId.of(UUID.fromString("fd951fe8-84ed-4af7-8340-f854321b76ad"));

        when(deadLetters.groupsWithFailedEvents()).thenReturn(Flux.just(nonCriticalGroup, criticalGroup));
        when(deadLetters.failedIds(nonCriticalGroup)).thenReturn(Flux.just(nonCriticalId));
        when(deadLetters.failedIds(criticalGroup)).thenReturn(Flux.just(criticalId));
        when(deadLetters.failedEvent(nonCriticalGroup, nonCriticalId)).thenReturn(Mono.just(new TestEvent("ignored")));
        when(deadLetters.failedEvent(criticalGroup, criticalId)).thenReturn(Mono.just(new TestEvent("normal")));

        Set<Group> nonCriticalGroups = Set.of(nonCriticalGroup);
        EventRetriever eventRetriever = EventRetriever.allEvents(nonCriticalGroups);

        List<Group> retrievedGroups = eventRetriever.retrieveEvents(deadLetters)
            .map(Tuple2::getT1)
            .collectList()
            .block();

        // should return non-critical groups last
        assertThat(retrievedGroups).containsExactly(criticalGroup, nonCriticalGroup);
    }
}
