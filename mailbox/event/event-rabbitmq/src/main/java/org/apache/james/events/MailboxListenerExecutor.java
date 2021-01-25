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

import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;

import reactor.core.publisher.Mono;

class MailboxListenerExecutor {
    private final MetricFactory metricFactory;

    MailboxListenerExecutor(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    Mono<Void> execute(EventListener.ReactiveEventListener listener, MDCBuilder mdcBuilder, Event event) {
        if (listener.isHandling(event)) {
            return Mono.from(metricFactory.decoratePublisherWithTimerMetric(timerName(listener),
                Mono.from(listener.reactiveEvent(event))
                    .subscriberContext(ReactorUtils.context("MailboxListenerExecutor", mdc(listener, mdcBuilder, event)))));
        }
        return Mono.empty();
    }

    private MDCBuilder mdc(EventListener listener, MDCBuilder mdcBuilder, Event event) {
        return mdcBuilder
            .addContext(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId())
            .addContext(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass())
            .addContext(EventBus.StructuredLoggingFields.USER, event.getUsername())
            .addContext(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass());
    }
}
