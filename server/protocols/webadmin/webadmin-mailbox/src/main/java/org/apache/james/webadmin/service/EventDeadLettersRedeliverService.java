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
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.events.DispatchingFailureGroup;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.task.Task;
import org.apache.james.util.streams.Limit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EventDeadLettersRedeliverService {
    public static class RunningOptions {
        public static RunningOptions DEFAULT = new RunningOptions(Limit.unlimited());
        private final Limit limit;

        public RunningOptions(Limit limit) {
            this.limit = limit;
        }

        @JsonCreator
        public RunningOptions(@JsonProperty("limit") Integer limit) {
            this.limit = Limit.from(Optional.ofNullable(limit));
        }

        @JsonProperty("limit")
        public Optional<Integer> limitValue() {
            return limit.getLimit();
        }

        public Limit limit() {
            return limit;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(EventDeadLettersRedeliverService.class);

    private final Set<EventBus> eventBuses;
    private final EventDeadLetters deadLetters;

    @Inject
    @VisibleForTesting
    public EventDeadLettersRedeliverService(Set<EventBus> eventBuses, EventDeadLetters deadLetters) {
        this.eventBuses = eventBuses;
        this.deadLetters = deadLetters;
    }

    Flux<Task.Result> redeliverEvents(EventRetriever eventRetriever, RunningOptions runningOptions) {
        return runningOptions.limit().applyOnFlux(eventRetriever.retrieveEvents(deadLetters))
            .flatMap(tuple3 -> redeliverGroupEvents(tuple3.getT1(), tuple3.getT2(), tuple3.getT3()));
    }

    private Mono<Task.Result> redeliverGroupEvents(Group group, Event event, EventDeadLetters.InsertionId insertionId) {
        return findEventBus(group)
            .map(eventBus -> eventBus.reDeliver(group, event)
                .then(deadLetters.remove(group, insertionId))
                .thenReturn(Task.Result.COMPLETED)
                .onErrorResume(e -> {
                    LOGGER.error("Error while performing redelivery of event: {} for group: {}",
                        event.getEventId().toString(), group.asString(), e);
                    return Mono.just(Task.Result.PARTIAL);
                }))
            .orElseGet(() -> {
                LOGGER.error("No eventBus associated. event: {} for group: {}",
                    event.getEventId().toString(), group.asString());
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Optional<EventBus> findEventBus(Group group) {
        if (group instanceof DispatchingFailureGroup) {
            DispatchingFailureGroup dispatchingFailureGroup = (DispatchingFailureGroup) group;
            return eventBuses.stream().filter(eventBus -> eventBus.eventBusName().equals(dispatchingFailureGroup.getEventBusName()))
                .findFirst();
        }
        return eventBuses.stream()
            .filter(eventBus -> eventBus.listRegisteredGroups().contains(group))
            .findFirst();
    }
}
