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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.metrics.api.MetricFactory;

import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

public class RabbitMQEventBus implements EventBus {
    static final String MAILBOX_EVENT = "mailboxEvent";
    static final String MAILBOX_EVENT_EXCHANGE_NAME = MAILBOX_EVENT + "-exchange";
    static final String EVENT_BUS_ID = "eventBusId";

    private final Mono<Connection> connectionMono;
    private final EventSerializer eventSerializer;
    private final AtomicBoolean isRunning;
    private final RoutingKeyConverter routingKeyConverter;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventBusId eventBusId;
    private final EventDeadLetters eventDeadLetters;
    private final MailboxListenerExecutor mailboxListenerExecutor;

    private GroupRegistrationHandler groupRegistrationHandler;
    private KeyRegistrationHandler keyRegistrationHandler;
    private EventDispatcher eventDispatcher;
    private Sender sender;

    @Inject
    public RabbitMQEventBus(RabbitMQConnectionFactory rabbitMQConnectionFactory, EventSerializer eventSerializer,
                     RetryBackoffConfiguration retryBackoff,
                     RoutingKeyConverter routingKeyConverter,
                     EventDeadLetters eventDeadLetters, MetricFactory metricFactory) {
        this.mailboxListenerExecutor = new MailboxListenerExecutor(metricFactory);
        this.eventBusId = EventBusId.random();
        this.connectionMono = Mono.fromSupplier(rabbitMQConnectionFactory::create).cache();
        this.eventSerializer = eventSerializer;
        this.routingKeyConverter = routingKeyConverter;
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.isRunning = new AtomicBoolean(false);
    }

    public void start() {
        if (!isRunning.get()) {
            sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
            MailboxListenerRegistry mailboxListenerRegistry = new MailboxListenerRegistry();
            keyRegistrationHandler = new KeyRegistrationHandler(eventBusId, eventSerializer, sender, connectionMono, routingKeyConverter, mailboxListenerRegistry, mailboxListenerExecutor);
            groupRegistrationHandler = new GroupRegistrationHandler(eventSerializer, sender, connectionMono, retryBackoff, eventDeadLetters, mailboxListenerExecutor);
            eventDispatcher = new EventDispatcher(eventBusId, eventSerializer, sender, mailboxListenerRegistry, mailboxListenerExecutor);

            eventDispatcher.start();
            keyRegistrationHandler.start();
            isRunning.set(true);
        }
    }

    @PreDestroy
    public void stop() {
        if (isRunning.get()) {
            groupRegistrationHandler.stop();
            keyRegistrationHandler.stop();
            sender.close();
            isRunning.set(false);
        }
    }

    @Override
    public Registration register(MailboxListener listener, RegistrationKey key) {
        return keyRegistrationHandler.register(listener, key);
    }

    @Override
    public Registration register(MailboxListener listener, Group group) {
        return groupRegistrationHandler.register(listener, group);
    }

    @Override
    public Mono<Void> dispatch(Event event, Set<RegistrationKey> key) {
        if (!event.isNoop()) {
            return eventDispatcher.dispatch(event, key);
        }
        return Mono.empty();
    }
}