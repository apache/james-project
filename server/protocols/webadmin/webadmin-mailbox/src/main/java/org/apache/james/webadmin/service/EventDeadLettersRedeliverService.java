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

import javax.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EventDeadLettersRedeliverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventDeadLettersRedeliverService.class);

    private final EventBus eventBus;
    private final EventDeadLetters deadLetters;

    @Inject
    @VisibleForTesting
    public EventDeadLettersRedeliverService(EventBus eventBus, EventDeadLetters deadLetters) {
        this.eventBus = eventBus;
        this.deadLetters = deadLetters;
    }

    Flux<Task.Result> redeliverEvents(EventRetriever eventRetriever) {
        return eventRetriever.retrieveEvents(deadLetters)
            .flatMap(entry -> redeliverGroupEvents(entry.getT1(), entry.getT2(), entry.getT3()), DEFAULT_CONCURRENCY);
    }

    private Mono<Task.Result> redeliverGroupEvents(Group group, Event event, EventDeadLetters.InsertionId insertionId) {
        return eventBus.reDeliver(group, event)
            .then(deadLetters.remove(group, insertionId))
            .thenReturn(Task.Result.COMPLETED)
            .onErrorResume(e -> {
                LOGGER.error("Error while performing redelivery of event: {} for group: {}",
                    event.getEventId().toString(), group.asString(), e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }
}
