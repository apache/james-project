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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.lettuce.core.api.reactive.RedisSetReactiveCommands;
import io.lettuce.core.pubsub.api.reactive.ChannelMessage;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

class KeyRegistrationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyRegistrationHandler.class);
    private static final Duration TOPOLOGY_CHANGES_TIMEOUT = Duration.ofMinutes(1);

    private final EventBusId eventBusId;
    private final LocalListenerRegistry localListenerRegistry;
    private final EventSerializer eventSerializer;
    private final RoutingKeyConverter routingKeyConverter;
    private final RegistrationChannelName registrationChannel;
    private final KeyRegistrationBinder registrationBinder;
    private final ListenerExecutor listenerExecutor;
    private final RetryBackoffConfiguration retryBackoff;
    private Optional<Disposable> receiverSubscriber;
    private final MetricFactory metricFactory;
    private final RedisPubSubReactiveCommands<String, String> redisSubscriber;
    private Scheduler scheduler;
    private Disposable newSubscription;

    KeyRegistrationHandler(NamingStrategy namingStrategy, EventBusId eventBusId, EventSerializer eventSerializer,
                           RoutingKeyConverter routingKeyConverter, LocalListenerRegistry localListenerRegistry,
                           ListenerExecutor listenerExecutor, RetryBackoffConfiguration retryBackoff, MetricFactory metricFactory,
                           RedisEventBusClientFactory redisEventBusClientFactory,
                           RedisSetReactiveCommands<String, String> redisSetReactiveCommands) {
        this.eventBusId = eventBusId;
        this.eventSerializer = eventSerializer;
        this.routingKeyConverter = routingKeyConverter;
        this.localListenerRegistry = localListenerRegistry;
        this.listenerExecutor = listenerExecutor;
        this.retryBackoff = retryBackoff;
        this.metricFactory = metricFactory;
        this.registrationChannel = namingStrategy.channelName(eventBusId);
        this.registrationBinder = new KeyRegistrationBinder(redisSetReactiveCommands, registrationChannel);
        this.receiverSubscriber = Optional.empty();
        this.redisSubscriber = redisEventBusClientFactory.createRedisPubSubCommand();
    }

    void start() {
        scheduler = Schedulers.newBoundedElastic(EventBus.EXECUTION_RATE, ReactorUtils.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "keys-handler"); // worker thread pool for each key queue

        declarePubSubChannel();

        newSubscription = Mono.from(redisSubscriber.subscribe(registrationChannel.asString()))
            .thenMany(redisSubscriber.observeChannels())
                .flatMap(this::handleChannelMessage, EventBus.EXECUTION_RATE)
            .subscribeOn(scheduler)
            .subscribe();

        receiverSubscriber = Optional.of(newSubscription);
    }

    void restart() {
        Optional<Disposable> previousReceiverSubscriber = receiverSubscriber;
        receiverSubscriber = Optional.of(newSubscription);
        previousReceiverSubscriber
            .filter(Predicate.not(Disposable::isDisposed))
            .ifPresent(Disposable::dispose);
    }

    void declarePubSubChannel() {
        // Pub/sub channel only dynamically declares upon subscribe/publish. No need to declare it ahead of time.
    }

    void stop() {
        // delete the Pub/Sub channel: Redis Channels are ephemeral and automatically expire when they have no more subscribers.
        redisSubscriber.unsubscribe(registrationChannel.asString())
            .block();
        receiverSubscriber.filter(Predicate.not(Disposable::isDisposed))
                .ifPresent(Disposable::dispose);
        Optional.ofNullable(scheduler).ifPresent(Scheduler::dispose);
    }

    Mono<Registration> register(EventListener.ReactiveEventListener listener, RegistrationKey key) {
        // RabbitMQ impl: register the binding mapping registration key (routing key) -> target queue on RabbitMQ
        // Redis impl: there is no routing key concept in Redis Pub/Sub (and Redis Streams).
        // Solution: store binding registration key - target channel mapping in Redis under key-value. Upon dispatching event, check the mapping to know the target publish channel.

        LocalListenerRegistry.LocalRegistration registration = localListenerRegistry.addListener(key, listener);

        return registerIfNeeded(key, registration)
            .thenReturn(new KeyRegistration(() -> {
                if (registration.unregister().lastListenerRemoved()) {
                    return Mono.from(metricFactory.decoratePublisherWithTimerMetric("redis-unregister", registrationBinder.unbind(key)
                        .timeout(TOPOLOGY_CHANGES_TIMEOUT)
                        .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.boundedElastic()))));
                }
                return Mono.empty();
            }));
    }

    private Mono<Void> registerIfNeeded(RegistrationKey key, LocalListenerRegistry.LocalRegistration registration) {
        if (registration.isFirstListener()) {
            return registrationBinder.bind(key)
                .timeout(TOPOLOGY_CHANGES_TIMEOUT)
                .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.boundedElastic()));
        }
        return Mono.empty();
    }

    private Mono<Void> handleChannelMessage(ChannelMessage<String, String> channelMessage) {
        if (channelMessage.getMessage() == null) {
            return Mono.empty();
        }

        // Redis Pub/Sub does not support headers for message. Therefore, likely we need to embed the eventBusId into the message.
        // Or store the messages headers in Redis: not viable because an EventId can be mapped to many routing key.
        String[] parts = StringUtils.split(channelMessage.getMessage(), EventDispatcher.REDIS_CHANNEL_MESSAGE_DELIMITER);

        String serializedEventBusId = parts[1];
        EventBusId eventBusId = EventBusId.of(serializedEventBusId);

        String routingKey = parts[2];
        RegistrationKey registrationKey = routingKeyConverter.toRegistrationKey(routingKey);

        List<EventListener.ReactiveEventListener> listenersToCall = localListenerRegistry.getLocalListeners(registrationKey)
            .stream()
            .filter(listener -> !isLocalSynchronousListeners(eventBusId, listener))
            .collect(ImmutableList.toImmutableList());

        if (listenersToCall.isEmpty()) {
            return Mono.empty();
        }

        String eventAsJson = parts[0];
        Event event = toEvent(eventAsJson);

        return Flux.fromIterable(listenersToCall)
            .flatMap(listener -> executeListener(listener, event, registrationKey), EventBus.EXECUTION_RATE)
            .then();
    }

    private Mono<Void> executeListener(EventListener.ReactiveEventListener listener, Event event, RegistrationKey key) {
        MDCBuilder mdcBuilder = MDCBuilder.create()
            .addToContext(EventBus.StructuredLoggingFields.REGISTRATION_KEY, key.asString());

        return listenerExecutor.execute(listener, mdcBuilder, event)
            .doOnError(e -> structuredLogger(event, key)
                .log(logger -> logger.error("Exception happens when handling event", e)))
            .onErrorResume(e -> Mono.empty())
            .then();
    }

    private boolean isLocalSynchronousListeners(EventBusId eventBusId, EventListener listener) {
        return eventBusId.equals(this.eventBusId) &&
            listener.getExecutionMode().equals(EventListener.ExecutionMode.SYNCHRONOUS);
    }

    private Event toEvent(String eventAsJson) {
        return eventSerializer.asEvent(eventAsJson);
    }

    private StructuredLogger structuredLogger(Event event, RegistrationKey key) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .field(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId().getId().toString())
            .field(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass().getCanonicalName())
            .field(EventBus.StructuredLoggingFields.USER, event.getUsername().asString())
            .field(EventBus.StructuredLoggingFields.REGISTRATION_KEY, key.asString());
    }
}
