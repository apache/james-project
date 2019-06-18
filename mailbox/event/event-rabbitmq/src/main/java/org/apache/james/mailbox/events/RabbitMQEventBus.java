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

import org.apache.james.backend.rabbitmq.SimpleConnectionPool;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.api.MetricFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.ChannelPool;
import reactor.rabbitmq.ChannelPoolFactory;
import reactor.rabbitmq.ChannelPoolOptions;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

public class RabbitMQEventBus implements EventBus, Startable {
    private static final int MAX_CHANNELS_NUMBER = 5;
    private static final String NOT_RUNNING_ERROR_MESSAGE = "Event Bus is not running";
    static final String MAILBOX_EVENT = "mailboxEvent";
    static final String MAILBOX_EVENT_EXCHANGE_NAME = MAILBOX_EVENT + "-exchange";
    static final String EVENT_BUS_ID = "eventBusId";

    private final Mono<Connection> connectionMono;
    private final EventSerializer eventSerializer;
    private final RoutingKeyConverter routingKeyConverter;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventBusId eventBusId;
    private final EventDeadLetters eventDeadLetters;
    private final MailboxListenerExecutor mailboxListenerExecutor;

    private volatile boolean isRunning;
    private volatile boolean isStopping;
    private ChannelPool channelPool;
    private GroupRegistrationHandler groupRegistrationHandler;
    private KeyRegistrationHandler keyRegistrationHandler;
    EventDispatcher eventDispatcher;
    private Sender sender;

    @Inject
    public RabbitMQEventBus(SimpleConnectionPool simpleConnectionPool, EventSerializer eventSerializer,
                     RetryBackoffConfiguration retryBackoff,
                     RoutingKeyConverter routingKeyConverter,
                     EventDeadLetters eventDeadLetters, MetricFactory metricFactory) {
        this.mailboxListenerExecutor = new MailboxListenerExecutor(metricFactory);
        this.eventBusId = EventBusId.random();
        this.connectionMono = simpleConnectionPool.getResilientConnection();
        this.eventSerializer = eventSerializer;
        this.routingKeyConverter = routingKeyConverter;
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.isRunning = false;
        this.isStopping = false;
    }

    public void start() {
        if (!isRunning && !isStopping) {
            this.channelPool = ChannelPoolFactory.createChannelPool(
                    connectionMono,
                    new ChannelPoolOptions().maxCacheSize(MAX_CHANNELS_NUMBER)
            );
            sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono).channelPool(channelPool)
                .resourceManagementChannelMono(connectionMono.map(Throwing.function(Connection::createChannel))));
            LocalListenerRegistry localListenerRegistry = new LocalListenerRegistry();
            keyRegistrationHandler = new KeyRegistrationHandler(eventBusId, eventSerializer, sender, connectionMono, routingKeyConverter, localListenerRegistry, mailboxListenerExecutor);
            groupRegistrationHandler = new GroupRegistrationHandler(eventSerializer, sender, connectionMono, retryBackoff, eventDeadLetters, mailboxListenerExecutor);
            eventDispatcher = new EventDispatcher(eventBusId, eventSerializer, sender, localListenerRegistry, mailboxListenerExecutor);

            eventDispatcher.start();
            keyRegistrationHandler.start();
            isRunning = true;
        }
    }

    @PreDestroy
    public void stop() {
        if (isRunning && !isStopping) {
            isStopping = true;
            isRunning = false;
            groupRegistrationHandler.stop();
            keyRegistrationHandler.stop();
            channelPool.close();
            sender.close();
        }
    }

    @Override
    public Registration register(MailboxListener listener, RegistrationKey key) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        return keyRegistrationHandler.register(listener, key);
    }

    @Override
    public Registration register(MailboxListener listener, Group group) {
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
            return groupRegistrationHandler.retrieveGroupRegistration(group).reDeliver(event);
        }
        return Mono.empty();
    }
}