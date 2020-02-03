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

package org.apache.james.mailbox.events.delivery;

import static org.apache.james.mailbox.events.EventBus.Metrics.timerName;

import java.io.Closeable;

import javax.inject.Inject;

import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

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
    public Mono<Void> deliver(MailboxListener listener, Event event, DeliveryOption option) {
        Mono<Void> executionResult = deliverByOption(listener, event, option);

        return waitForResultIfNeeded(listener.getExecutionMode(), executionResult);
    }

    private Mono<Void> waitForResultIfNeeded(MailboxListener.ExecutionMode executionMode, Mono<Void> executionResult) {
        if (executionMode.equals(MailboxListener.ExecutionMode.SYNCHRONOUS)) {
            return executionResult;
        }
        return executionResult.or(Mono.empty()).onErrorResume(throwable -> Mono.empty());
    }

    private Mono<Void> deliverByOption(MailboxListener listener, Event event, DeliveryOption deliveryOption) {
        Mono<Void> deliveryToListener = Mono.fromRunnable(() -> doDeliverToListener(listener, event))
            .doOnError(throwable -> structuredLogger(event, listener)
                .log(logger -> logger.error("Error while processing listener", throwable)))
            .then();

        return deliveryOption.getRetrier().doRetry(deliveryToListener, event)
            .onErrorResume(throwable -> deliveryOption.getPermanentFailureHandler().handle(event))
            .then();
    }

    private void doDeliverToListener(MailboxListener mailboxListener, Event event) {
        if (mailboxListener.isHandling(event)) {
            TimeMetric timer = metricFactory.timer(timerName(mailboxListener));
            try (Closeable mdc = buildMDC(mailboxListener, event)) {
                mailboxListener.event(event);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                timer.stopAndPublish();
            }
        }
    }

    private Closeable buildMDC(MailboxListener mailboxListener, Event event) {
        return MDCBuilder.create()
            .addContext(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId())
            .addContext(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass())
            .addContext(EventBus.StructuredLoggingFields.USER, event.getUsername())
            .addContext(EventBus.StructuredLoggingFields.LISTENER_CLASS, mailboxListener.getClass())
            .build();
    }

    private StructuredLogger structuredLogger(Event event, MailboxListener mailboxListener) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .addField(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId())
            .addField(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass())
            .addField(EventBus.StructuredLoggingFields.USER, event.getUsername())
            .addField(EventBus.StructuredLoggingFields.LISTENER_CLASS, mailboxListener.getClass());
    }
}
