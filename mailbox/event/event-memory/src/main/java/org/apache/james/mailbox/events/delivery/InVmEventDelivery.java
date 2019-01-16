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

import java.time.Duration;
import java.util.Collection;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.scheduler.Schedulers;

public class InVmEventDelivery implements EventDelivery {
    private static final Logger LOGGER = LoggerFactory.getLogger(InVmEventDelivery.class);
    private static final int MAX_RETRIES = 3;
    private static final Duration FIRST_BACKOFF = Duration.ofMillis(100);
    private static final Duration MAX_BACKOFF = Duration.ofMillis(Long.MAX_VALUE);
    private static final double DEFAULT_JITTER_FACTOR = 0.5;

    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    public InVmEventDelivery(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public ExecutionStages deliver(Collection<MailboxListener> mailboxListeners, Event event) {
        Mono<Void> synchronousListeners = doDeliver(filterByExecutionMode(mailboxListeners, MailboxListener.ExecutionMode.SYNCHRONOUS), event)
            .subscribeWith(MonoProcessor.create());
        Mono<Void> asyncListener = doDeliver(filterByExecutionMode(mailboxListeners, MailboxListener.ExecutionMode.ASYNCHRONOUS), event)
            .subscribeWith(MonoProcessor.create());

        return new ExecutionStages(synchronousListeners, asyncListener);
    }

    private Stream<MailboxListener> filterByExecutionMode(Collection<MailboxListener> mailboxListeners, MailboxListener.ExecutionMode executionMode) {
        return mailboxListeners.stream()
            .filter(listener -> listener.getExecutionMode() == executionMode);
    }

    private Mono<Void> doDeliver(Stream<MailboxListener> mailboxListeners, Event event) {
        return Flux.fromStream(mailboxListeners)
            .flatMap(mailboxListener -> deliveryWithRetries(event, mailboxListener))
            .then()
            .subscribeOn(Schedulers.elastic());
    }

    private Mono<Void> deliveryWithRetries(Event event, MailboxListener mailboxListener) {
        return Mono.fromRunnable(() -> doDeliverToListener(mailboxListener, event))
            .doOnError(throwable -> LOGGER.error("Error while processing listener {} for {}",
                listenerName(mailboxListener),
                eventName(event),
                throwable))
            .retryBackoff(MAX_RETRIES, FIRST_BACKOFF, MAX_BACKOFF, DEFAULT_JITTER_FACTOR)
            .doOnError(throwable -> LOGGER.error("listener {} exceeded maximum retry({}) to handle event {}",
                listenerName(mailboxListener),
                MAX_RETRIES,
                eventName(event),
                throwable))
            .then();
    }

    private void doDeliverToListener(MailboxListener mailboxListener, Event event) {
        TimeMetric timer = metricFactory.timer("mailbox-listener-" + mailboxListener.getClass().getSimpleName());
        try {
            mailboxListener.event(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            timer.stopAndPublish();
        }
    }

    private String listenerName(MailboxListener mailboxListener) {
        return mailboxListener.getClass().getCanonicalName();
    }

    private String eventName(Event event) {
        return event.getClass().getCanonicalName();
    }
}
