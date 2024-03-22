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

package org.apache.james.events;

import jakarta.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;

import reactor.core.publisher.Mono;

public class EventDeadLettersHealthCheck implements HealthCheck {
    private static final ComponentName COMPONENT_NAME = new ComponentName("EventDeadLettersHealthCheck");

    private final EventDeadLetters eventDeadLetters;

    @Inject
    public EventDeadLettersHealthCheck(EventDeadLetters eventDeadLetters) {
        this.eventDeadLetters = eventDeadLetters;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return eventDeadLetters.containEvents()
            .map(containEvents -> {
                if (containEvents) {
                    return Result.degraded(COMPONENT_NAME, "EventDeadLetters contain events. This might indicate transient failure on event processing.");
                }

                return Result.healthy(COMPONENT_NAME);
            })
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking EventDeadLettersHealthCheck", e)));
    }
}
