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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InVmEventDelivery implements EventDelivery {
    private static final Logger LOGGER = LoggerFactory.getLogger(InVmEventDelivery.class);

    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    public InVmEventDelivery(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public Mono<Void> deliver(EventListener.ReactiveEventListener listener, Event event, DeliveryOption option) {
        Mono<Void> executionResult = deliverByOption(listener, event, option);

        return waitForResultIfNeeded(listener.getExecutionMode(), executionResult);
    }

    private Mono<Void> waitForResultIfNeeded(EventListener.ExecutionMode executionMode, Mono<Void> executionResult) {
        if (executionMode.equals(EventListener.ExecutionMode.SYNCHRONOUS)) {
            return executionResult;
        }
        return Flux.merge(executionResult, Mono.empty())
            .publishNext()
            .onErrorResume(throwable -> Mono.empty());
    }

    private Mono<Void> deliverByOption(EventListener.ReactiveEventListener listener, Event event, DeliveryOption deliveryOption) {
        Mono<Void> deliveryToListener = doDeliverToListener(listener, event)
            .doOnError(throwable -> structuredLogger(event, listener)
                .log(logger -> logger.error("Error while processing listener", throwable)))
            .then();

        return deliveryOption.getRetrier().doRetry(deliveryToListener, event)
            .onErrorResume(throwable -> deliveryOption.getPermanentFailureHandler().handle(event))
            .then();
    }

    private Mono<Void> doDeliverToListener(EventListener.ReactiveEventListener listener, Event event) {
        if (listener.isHandling(event)) {
            return Mono.defer(() -> Mono.from(metricFactory.decoratePublisherWithTimerMetric(timerName(listener),
                    listener.reactiveEvent(event))))
                .contextWrite(context("deliver", buildMDC(listener, event)));
        }
        return Mono.empty();
    }

    private MDCBuilder buildMDC(EventListener listener, Event event) {
        return MDCBuilder.create()
            .addToContext(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId().toString())
            .addToContext(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass().getCanonicalName())
            .addToContext(EventBus.StructuredLoggingFields.USER, event.getUsername().asString())
            .addToContext(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass().getCanonicalName());
    }

    private StructuredLogger structuredLogger(Event event, EventListener listener) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .field(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId().getId().toString())
            .field(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass().getCanonicalName())
            .field(EventBus.StructuredLoggingFields.USER, event.getUsername().asString())
            .field(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass().getCanonicalName());
    }
}
