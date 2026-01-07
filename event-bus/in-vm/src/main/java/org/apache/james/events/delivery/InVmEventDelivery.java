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

package org.apache.james.events.delivery;

import static org.apache.james.events.EventBus.Metrics.timerName;
import static org.apache.james.util.ReactorUtils.context;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InVmEventDelivery implements EventDelivery {
    private static final Logger LOGGER = LoggerFactory.getLogger(InVmEventDelivery.class);

    private final MetricFactory metricFactory;
    private final EventBus.Configuration configuration;

    @VisibleForTesting
    public InVmEventDelivery(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
        this.configuration = new EventBus.Configuration(EventBus.EXECUTION_RATE, Optional.empty());
    }

    @Inject
    public InVmEventDelivery(MetricFactory metricFactory, EventBus.Configuration configuration) {
        this.metricFactory = metricFactory;
        this.configuration = configuration;
    }

    @Override
    public Mono<Void> deliver(EventListener.ReactiveEventListener listener, Event event, DeliveryOption option) {
        return deliver(listener, ImmutableList.of(event), option);
    }

    @Override
    public Mono<Void> deliver(EventListener.ReactiveEventListener listener, List<Event> events, DeliveryOption option) {
        if (isHandling(listener, events)) {
            Mono<Void> executionResult = deliverByOption(listener, events, option);

            return waitForResultIfNeeded(listener.getExecutionMode(), executionResult);
        }
        return Mono.empty();
    }

    private Mono<Void> waitForResultIfNeeded(EventListener.ExecutionMode executionMode, Mono<Void> executionResult) {
        if (executionMode.equals(EventListener.ExecutionMode.SYNCHRONOUS)) {
            return executionResult;
        }
        return Flux.merge(executionResult, Mono.empty())
            .publishNext()
            .onErrorResume(throwable -> Mono.empty());
    }

    private Mono<Void> deliverByOption(EventListener.ReactiveEventListener listener, List<Event> events, DeliveryOption deliveryOption) {
        Mono<Void> deliveryToListener = doDeliverToListener(listener, events)
            .doOnError(throwable -> structuredLogger(events, listener)
                .log(logger -> logger.error("Error while processing listener", throwable)))
            .then();

        return deliveryOption.getRetrier().doRetry(deliveryToListener, events)
            .then()
            .onErrorResume(e -> Flux.fromIterable(events)
                .concatMap(event -> deliveryOption.getPermanentFailureHandler().handle(event))
                .then());
    }

    private Mono<Void> doDeliverToListener(EventListener.ReactiveEventListener listener, List<Event> events) {
        if (events.stream().noneMatch(listener::isHandling)) {
            return Mono.empty();
        }
        Mono<Void> result = Mono.defer(() -> Mono.from(metricFactory.decoratePublisherWithTimerMetric(timerName(listener),
            listener.reactiveEvent(events))));
        return configuration.executionTimeout().map(result::timeout).orElse(result)
            .contextWrite(context("deliver", buildMDC(listener, events)));
    }

    private static boolean isHandling(EventListener.ReactiveEventListener listener, List<Event> events) {
        return events.stream().anyMatch(listener::isHandling);
    }

    private MDCBuilder buildMDC(EventListener listener, List<Event> events) {
        if (events.size() == 1) {
            return MDCBuilder.create()
                .addToContext(EventBus.StructuredLoggingFields.EVENT_ID, events.getFirst().getEventId().toString())
                .addToContext(EventBus.StructuredLoggingFields.EVENT_CLASS, events.getFirst().getClass().getCanonicalName())
                .addToContext(EventBus.StructuredLoggingFields.USER, events.getFirst().getUsername().asString())
                .addToContext(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass().getCanonicalName());
        }

        return MDCBuilder.create()
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

    private StructuredLogger structuredLogger(List<Event> events, EventListener listener) {
        if (events.size() == 1) {
            return MDCStructuredLogger.forLogger(LOGGER)
                .field(EventBus.StructuredLoggingFields.EVENT_ID, events.getFirst().getEventId().toString())
                .field(EventBus.StructuredLoggingFields.EVENT_CLASS, events.getFirst().getClass().getCanonicalName())
                .field(EventBus.StructuredLoggingFields.USER, events.getFirst().getUsername().asString())
                .field(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass().getCanonicalName());
        }
        return MDCStructuredLogger.forLogger(LOGGER)
            .field(EventBus.StructuredLoggingFields.EVENT_ID, events.stream()
                .map(e -> e.getEventId().getId().toString())
                .collect(Collectors.joining(",")))
            .field(EventBus.StructuredLoggingFields.EVENT_CLASS, events.stream()
                .map(e -> e.getClass().getCanonicalName())
                .collect(Collectors.joining(",")))
            .field(EventBus.StructuredLoggingFields.USER, events.stream()
                .map(e -> e.getUsername().asString())
                .collect(Collectors.joining(",")))
            .field(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass().getCanonicalName());

    }
}
