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

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;
import org.apache.james.task.Task;

import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Mono;

public class EventDeadLettersService {
    private final EventDeadLettersRedeliverService redeliverService;
    private final EventDeadLetters deadLetters;

    @Inject
    public EventDeadLettersService(EventDeadLettersRedeliverService redeliverService, EventDeadLetters deadLetters) {
        this.redeliverService = redeliverService;
        this.deadLetters = deadLetters;
    }

    public List<String> listGroupsAsStrings() {
        return deadLetters.groupsWithFailedEvents()
            .map(Group::asString)
            .collect(Guavate.toImmutableList())
            .block();
    }

    public List<String> listGroupsEventIdsAsStrings(Group group) {
        return deadLetters.failedEventIds(group)
            .map(Event.EventId::getId)
            .map(UUID::toString)
            .collect(Guavate.toImmutableList())
            .block();
    }

    public Mono<Event> getEvent(Group group, Event.EventId eventId) {
        return deadLetters.failedEvent(group, eventId);
    }

    public void deleteEvent(Group group, Event.EventId eventId) {
        deadLetters.remove(group, eventId).block();
    }

    public Task redeliverAllEvents() {
        return new EventDeadLettersRedeliverTask(redeliverService, EventRetriever.allEvents());
    }

    public Task redeliverGroupEvents(Group group) {
        return new EventDeadLettersRedeliverTask(redeliverService, EventRetriever.groupEvents(group));
    }

    public Task redeliverSingleEvent(Group group, Event.EventId eventId) {
        return new EventDeadLettersRedeliverTask(redeliverService, EventRetriever.singleEvent(group, eventId));
    }
}
