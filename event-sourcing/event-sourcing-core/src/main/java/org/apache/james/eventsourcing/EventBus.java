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

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.EventStoreFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import org.apache.james.eventsourcing.Event;

public class EventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBus.class);

    private final EventStore eventStore;
    private final Set<Subscriber> subscribers;

    @Inject
    public EventBus(EventStore eventStore, Set<Subscriber> subscribers) {
        this.eventStore = eventStore;
        this.subscribers = ImmutableSet.copyOf(subscribers);
    }

    public void publish(List<Event> events) throws EventStoreFailedException {
        eventStore.appendAll(events);
        events.stream()
            .flatMap(event -> subscribers.stream().map(subscriber -> Pair.of(event, subscriber)))
            .forEach(this::handle);
    }

    private void handle(Pair<Event, Subscriber> pair) {
        Subscriber subscriber = pair.getRight();
        Event event = pair.getLeft();
        try {
            subscriber.handle(event);
        } catch (Exception e) {
            LOGGER.error("Error while calling {} for {}", subscriber, event, e);
        }
    }
}
