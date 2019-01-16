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
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
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

    private enum DeliveryOption {
        NO_RETRY, WITH_RETRY
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(InVmEventDelivery.class);
    private static final Duration MAX_BACKOFF = Duration.ofMillis(Long.MAX_VALUE);

    private final MetricFactory metricFactory;
    private final RetryBackoffConfiguration retryBackoff;

    @Inject
    @VisibleForTesting
    public InVmEventDelivery(MetricFactory metricFactory, RetryBackoffConfiguration retryBackoff) {
        this.metricFactory = metricFactory;
        this.retryBackoff = retryBackoff;
    }

    public InVmEventDelivery(MetricFactory metricFactory) {
        this(metricFactory, RetryBackoffConfiguration.DEFAULT);
    }

    @Override
    public ExecutionStages deliver(Collection<MailboxListener> mailboxListeners, Event event) {
        return deliverByOption(mailboxListeners, event, DeliveryOption.NO_RETRY);
    }

    @Override
    public ExecutionStages deliverWithRetries(Collection<MailboxListener> mailboxListeners, Event event) {
        return deliverByOption(mailboxListeners, event, DeliveryOption.WITH_RETRY);
    }

    private ExecutionStages deliverByOption(Collection<MailboxListener> mailboxListeners, Event event, DeliveryOption deliveryOption) {
        Mono<Void> synchronousListeners = doDeliver(
            filterByExecutionMode(mailboxListeners, MailboxListener.ExecutionMode.SYNCHRONOUS), event, deliveryOption)
            .subscribeWith(MonoProcessor.create());
        Mono<Void> asyncListener = doDeliver(
            filterByExecutionMode(mailboxListeners, MailboxListener.ExecutionMode.ASYNCHRONOUS), event, deliveryOption)
            .subscribeWith(MonoProcessor.create());

        return new ExecutionStages(synchronousListeners, asyncListener);
    }

    private Stream<MailboxListener> filterByExecutionMode(Collection<MailboxListener> mailboxListeners, MailboxListener.ExecutionMode executionMode) {
        return mailboxListeners.stream()
            .filter(listener -> listener.getExecutionMode() == executionMode);
    }

    private Mono<Void> doDeliver(Stream<MailboxListener> mailboxListeners, Event event, DeliveryOption deliveryOption) {
        return Flux.fromStream(mailboxListeners)
            .flatMap(mailboxListener -> deliveryWithRetries(event, mailboxListener, deliveryOption))
            .then()
            .subscribeOn(Schedulers.elastic());
    }

    private Mono<Void> deliveryWithRetries(Event event, MailboxListener mailboxListener, DeliveryOption deliveryOption) {
        Mono<Void> firstDelivery = Mono.fromRunnable(() -> doDeliverToListener(mailboxListener, event))
            .doOnError(throwable -> LOGGER.error("Error while processing listener {} for {}",
                listenerName(mailboxListener),
                eventName(event),
                throwable))
            .subscribeOn(Schedulers.elastic())
            .then();

        if (deliveryOption == DeliveryOption.NO_RETRY) {
            return firstDelivery;
        }

        return firstDelivery
            .retryBackoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff(), MAX_BACKOFF, retryBackoff.getJitterFactor())
            .doOnError(throwable -> LOGGER.error("listener {} exceeded maximum retry({}) to handle event {}",
                listenerName(mailboxListener),
                retryBackoff.getMaxRetries(),
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
