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

import java.util.UUID;

import javax.inject.Inject;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class EventDeadLettersService {
    private final EventDeadLetters deadLetters;
    private final EventSerializer eventSerializer;

    @Inject
    public EventDeadLettersService(EventDeadLetters deadLetters, EventSerializer eventSerializer) {
        this.deadLetters = deadLetters;
        this.eventSerializer = eventSerializer;
    }

    public ImmutableList<String> listGroups() {
        return deadLetters.groupsWithFailedEvents()
            .map(Group::asString)
            .collect(Guavate.toImmutableList())
            .block();
    }

    public ImmutableList<String> listGroupEvents(Group group) {
        return deadLetters.failedEventIds(group)
            .map(Event.EventId::getId)
            .map(UUID::toString)
            .collect(Guavate.toImmutableList())
            .block();
    }

    public String getSerializedEvent(Group group, Event.EventId eventId) {
        return deadLetters.failedEvent(group, eventId)
            .map(eventSerializer::toJson)
            .block();
    }

    public void deleteEvent(Group group, Event.EventId eventId) {
        deadLetters.remove(group, eventId).block();
    }
}
