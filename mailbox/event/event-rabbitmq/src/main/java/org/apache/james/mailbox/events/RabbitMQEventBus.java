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

package org.apache.james.mailbox.events;

import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.Sender;

public class RabbitMQEventBus implements EventBus, Startable {
    private static final Set<RegistrationKey> NO_KEY = ImmutableSet.of();
    private static final String NOT_RUNNING_ERROR_MESSAGE = "Event Bus is not running";
    static final String MAILBOX_EVENT = "mailboxEvent";
    static final String MAILBOX_EVENT_DEAD_LETTER_QUEUE = MAILBOX_EVENT + "-dead-letter-queue";
    static final String MAILBOX_EVENT_EXCHANGE_NAME = MAILBOX_EVENT + "-exchange";
    static final String MAILBOX_EVENT_DEAD_LETTER_EXCHANGE_NAME = MAILBOX_EVENT + "-dead-letter-exchange";
    static final String EVENT_BUS_ID = "eventBusId";

    private final EventSerializer eventSerializer;
    private final RoutingKeyConverter routingKeyConverter;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventBusId eventBusId;
    private final EventDeadLetters eventDeadLetters;
    private final MailboxListenerExecutor mailboxListenerExecutor;
    private final Sender sender;
    private final ReceiverProvider receiverProvider;

    private volatile boolean isRunning;
    private volatile boolean isStopping;
    private GroupRegistrationHandler groupRegistrationHandler;
    private KeyRegistrationHandler keyRegistrationHandler;
    private EventDispatcher eventDispatcher;

    @Inject
    public RabbitMQEventBus(Sender sender, ReceiverProvider receiverProvider, EventSerializer eventSerializer,
                            RetryBackoffConfiguration retryBackoff,
                            RoutingKeyConverter routingKeyConverter,
                            EventDeadLetters eventDeadLetters, MetricFactory metricFactory) {
        this.sender = sender;
        this.receiverProvider = receiverProvider;
        this.mailboxListenerExecutor = new MailboxListenerExecutor(metricFactory);
        this.eventBusId = EventBusId.random();
        this.eventSerializer = eventSerializer;
        this.routingKeyConverter = routingKeyConverter;
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.isRunning = false;
        this.isStopping = false;
    }

    public void start() {
        if (!isRunning && !isStopping) {

            LocalListenerRegistry localListenerRegistry = new LocalListenerRegistry();
            keyRegistrationHandler = new KeyRegistrationHandler(eventBusId, eventSerializer, sender, receiverProvider, routingKeyConverter, localListenerRegistry, mailboxListenerExecutor, retryBackoff);
            groupRegistrationHandler = new GroupRegistrationHandler(eventSerializer, sender, receiverProvider, retryBackoff, eventDeadLetters, mailboxListenerExecutor);
            eventDispatcher = new EventDispatcher(eventBusId, eventSerializer, sender, localListenerRegistry, mailboxListenerExecutor, eventDeadLetters);

            eventDispatcher.start();
            keyRegistrationHandler.start();
            isRunning = true;
        }
    }

    @VisibleForTesting
    void startWithoutStartingKeyRegistrationHandler() {
        if (!isRunning && !isStopping) {

            LocalListenerRegistry localListenerRegistry = new LocalListenerRegistry();
            keyRegistrationHandler = new KeyRegistrationHandler(eventBusId, eventSerializer, sender, receiverProvider, routingKeyConverter, localListenerRegistry, mailboxListenerExecutor, retryBackoff);
            groupRegistrationHandler = new GroupRegistrationHandler(eventSerializer, sender, receiverProvider, retryBackoff, eventDeadLetters, mailboxListenerExecutor);
            eventDispatcher = new EventDispatcher(eventBusId, eventSerializer, sender, localListenerRegistry, mailboxListenerExecutor, eventDeadLetters);

            keyRegistrationHandler.declareQueue();

            eventDispatcher.start();
            isRunning = true;
        }
    }

    @VisibleForTesting
    void startKeyRegistrationHandler() {
        keyRegistrationHandler.start();
    }

    @PreDestroy
    public void stop() {
        if (isRunning && !isStopping) {
            isStopping = true;
            isRunning = false;
            groupRegistrationHandler.stop();
            keyRegistrationHandler.stop();
        }
    }

    @Override
    public Mono<Registration> register(MailboxListener.ReactiveMailboxListener listener, RegistrationKey key) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        return keyRegistrationHandler.register(listener, key);
    }

    @Override
    public Registration register(MailboxListener.ReactiveMailboxListener listener, Group group) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        return groupRegistrationHandler.register(listener, group);
    }

    @Override
    public Mono<Void> dispatch(Event event, Set<RegistrationKey> key) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        if (!event.isNoop()) {
            return eventDispatcher.dispatch(event, key);
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> reDeliver(Group group, Event event) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        if (!event.isNoop()) {
            /*
            if the eventBus.dispatch() gets error while dispatching an event (rabbitMQ network outage maybe),
            which means all the group consumers will not be receiving that event.

            We store the that event in the dead letter and expecting in the future, it will be dispatched
            again not only for a specific consumer but all.

            That's why it is special, and we need to check event type before processing further.
            */
            if (group instanceof EventDispatcher.DispatchingFailureGroup) {
                return eventDispatcher.dispatch(event, NO_KEY);
            }
            return groupRegistrationHandler.retrieveGroupRegistration(group).reDeliver(event);
        }
        return Mono.empty();
    }
}