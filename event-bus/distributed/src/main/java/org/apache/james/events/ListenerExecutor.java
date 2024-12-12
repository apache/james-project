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

import static org.apache.james.events.EventBus.Metrics.timerName;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;

import reactor.core.publisher.Mono;

class ListenerExecutor {
    // JAMES-3955 RabbitMQ handles timeout by closing channels thus
    // causing event consumption to halt. We thus need to handle timeout beforehand.
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final MetricFactory metricFactory;

    ListenerExecutor(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    Mono<Void> execute(EventListener.ReactiveEventListener listener, MDCBuilder mdcBuilder, Event event) {
        if (listener.isHandling(event)) {
            return Mono.from(metricFactory.decoratePublisherWithTimerMetric(timerName(listener),
                Mono.from(listener.reactiveEvent(event))
                    .contextWrite(ReactorUtils.context("ListenerExecutor", mdc(listener, mdcBuilder, event)))
                    .timeout(TIMEOUT)));
        }
        return Mono.empty();
    }

    Mono<Void> execute(EventListener.ReactiveEventListener listener, MDCBuilder mdcBuilder, List<Event> events) {
        if (events.size() == 1) {
            return execute(listener, mdcBuilder, events.getFirst());
        }
        if (events.stream().noneMatch(listener::isHandling)) {
            return Mono.empty();
        }
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric(timerName(listener),
            Mono.from(listener.reactiveEvent(events))
                .contextWrite(ReactorUtils.context("ListenerExecutor", mdc(listener, mdcBuilder, events)))
                .timeout(TIMEOUT)));
    }

    private MDCBuilder mdc(EventListener listener, MDCBuilder mdcBuilder, Event event) {
        return mdcBuilder
            .addToContext(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId().getId().toString())
            .addToContext(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass().getCanonicalName())
            .addToContext(EventBus.StructuredLoggingFields.USER, event.getUsername().asString())
            .addToContext(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass().getCanonicalName());
    }

    private MDCBuilder mdc(EventListener listener, MDCBuilder mdcBuilder, List<Event> events) {
        if (events.size() == 1) {
            return mdcBuilder
                .addToContext(EventBus.StructuredLoggingFields.EVENT_ID, events.getFirst().getEventId().toString())
                .addToContext(EventBus.StructuredLoggingFields.EVENT_CLASS, events.getFirst().getClass().getCanonicalName())
                .addToContext(EventBus.StructuredLoggingFields.USER, events.getFirst().getUsername().asString())
                .addToContext(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass().getCanonicalName());
        }

        return mdcBuilder
            .addToContext(EventBus.StructuredLoggingFields.EVENT_ID, events.stream()
                .map(e -> e.getEventId().getId().toString())
                .collect(Collectors.joining(",")))
            .addToContext(EventBus.StructuredLoggingFields.EVENT_CLASS, events.stream()
                .map(e -> e.getClass().getCanonicalName())
                .collect(Collectors.joining(",")))
            .addToContext(EventBus.StructuredLoggingFields.USER, events.stream()
                .map(e -> e.getUsername().asString())
                .collect(Collectors.joining(",")))
            .addToContext(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass().getCanonicalName());
    }
}
