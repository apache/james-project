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

import java.util.Collection;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import io.lettuce.core.api.reactive.RedisSetReactiveCommands;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Sender;

public class RabbitMQEventBus implements EventBus, Startable {
    private static final Set<RegistrationKey> NO_KEY = ImmutableSet.of();
    private static final String NOT_RUNNING_ERROR_MESSAGE = "Event Bus is not running";
    static final String EVENT_BUS_ID = "eventBusId";

    private final NamingStrategy namingStrategy;
    private final EventSerializer eventSerializer;
    private final RoutingKeyConverter routingKeyConverter;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventBusId eventBusId;
    private final EventDeadLetters eventDeadLetters;
    private final ListenerExecutor listenerExecutor;
    private final Sender sender;
    private final ReceiverProvider receiverProvider;
    private final ReactorRabbitMQChannelPool channelPool;
    private final RabbitMQConfiguration configuration;
    private final MetricFactory metricFactory;
    private final RedisEventBusClientFactory redisEventBusClientFactory;
    private final RedisSetReactiveCommands<String, String> redisSetReactiveCommands;
    private final RedisPubSubReactiveCommands<String, String> redisPublisher;

    private volatile boolean isRunning;
    private volatile boolean isStopping;
    private GroupRegistrationHandler groupRegistrationHandler;
    private KeyRegistrationHandler keyRegistrationHandler;
    private EventDispatcher eventDispatcher;

    @Inject
    public RabbitMQEventBus(NamingStrategy namingStrategy, Sender sender, ReceiverProvider receiverProvider, EventSerializer eventSerializer,
                            RetryBackoffConfiguration retryBackoff,
                            RoutingKeyConverter routingKeyConverter,
                            EventDeadLetters eventDeadLetters, MetricFactory metricFactory, ReactorRabbitMQChannelPool channelPool,
                            EventBusId eventBusId, RabbitMQConfiguration configuration,
                            RedisEventBusClientFactory redisEventBusClientFactory) {
        this.namingStrategy = namingStrategy;
        this.sender = sender;
        this.receiverProvider = receiverProvider;
        this.listenerExecutor = new ListenerExecutor(metricFactory);
        this.channelPool = channelPool;
        this.eventBusId = eventBusId;
        this.eventSerializer = eventSerializer;
        this.routingKeyConverter = routingKeyConverter;
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.configuration = configuration;
        this.metricFactory = metricFactory;
        this.redisEventBusClientFactory = redisEventBusClientFactory;
        this.redisSetReactiveCommands = redisEventBusClientFactory.createRedisSetCommand();
        this.redisPublisher = redisEventBusClientFactory.createRedisPubSubCommand();
        this.isRunning = false;
        this.isStopping = false;
    }

    public void start() {
        if (!isRunning && !isStopping) {

            LocalListenerRegistry localListenerRegistry = new LocalListenerRegistry();
            keyRegistrationHandler = new KeyRegistrationHandler(namingStrategy, eventBusId, eventSerializer, routingKeyConverter,
                localListenerRegistry, listenerExecutor, retryBackoff, metricFactory, redisEventBusClientFactory, redisSetReactiveCommands);
            groupRegistrationHandler = new GroupRegistrationHandler(namingStrategy, eventSerializer, channelPool, sender, receiverProvider, retryBackoff, eventDeadLetters, listenerExecutor, eventBusId, configuration);
            eventDispatcher = new EventDispatcher(namingStrategy, eventBusId, eventSerializer, sender, localListenerRegistry, listenerExecutor, eventDeadLetters, configuration,
                redisPublisher, redisSetReactiveCommands);

            eventDispatcher.start();
            keyRegistrationHandler.start();
            isRunning = true;
        }
    }

    public void restart() {
        keyRegistrationHandler.restart();
        groupRegistrationHandler.restart();
    }

    @VisibleForTesting
    void startWithoutStartingKeyRegistrationHandler() {
        if (!isRunning && !isStopping) {

            LocalListenerRegistry localListenerRegistry = new LocalListenerRegistry();
            keyRegistrationHandler = new KeyRegistrationHandler(namingStrategy, eventBusId, eventSerializer, routingKeyConverter,
                localListenerRegistry, listenerExecutor, retryBackoff, metricFactory, redisEventBusClientFactory, redisSetReactiveCommands);
            groupRegistrationHandler = new GroupRegistrationHandler(namingStrategy, eventSerializer, channelPool, sender, receiverProvider, retryBackoff, eventDeadLetters, listenerExecutor, eventBusId, configuration);
            eventDispatcher = new EventDispatcher(namingStrategy, eventBusId, eventSerializer, sender, localListenerRegistry, listenerExecutor, eventDeadLetters, configuration,
                redisPublisher, redisSetReactiveCommands);

            keyRegistrationHandler.declarePubSubChannel();

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
    public Mono<Registration> register(EventListener.ReactiveEventListener listener, RegistrationKey key) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("redis-register", keyRegistrationHandler.register(listener, key)));
    }

    @Override
    public Registration register(EventListener.ReactiveEventListener listener, Group group) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        return groupRegistrationHandler.register(listener, group);
    }

    @Override
    public Mono<Void> dispatch(Event event, Set<RegistrationKey> key) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        if (!event.isNoop()) {
            return Mono.from(metricFactory.decoratePublisherWithTimerMetric("redis-dispatch", eventDispatcher.dispatch(event, key)));
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
            if (group instanceof DispatchingFailureGroup) {
                return eventDispatcher.dispatch(event, NO_KEY);
            }
            return groupRegistrationHandler.retrieveGroupRegistration(group).reDeliver(event);
        }
        return Mono.empty();
    }

    @Override
    public EventBusName eventBusName() {
        return namingStrategy.getEventBusName();
    }

    @Override
    public Collection<Group> listRegisteredGroups() {
        return groupRegistrationHandler.registeredGroups();
    }
}