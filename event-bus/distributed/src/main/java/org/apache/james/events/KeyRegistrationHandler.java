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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.events.RabbitMQEventBus.EVENT_BUS_ID;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;
import reactor.util.retry.Retry;

class KeyRegistrationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyRegistrationHandler.class);
    private static final Duration EXPIRATION_TIMEOUT = Duration.ofMinutes(30);
    static final Map<String, Object> QUEUE_ARGUMENTS = ImmutableMap.of("x-expires", EXPIRATION_TIMEOUT.toMillis());

    private static final Duration TOPOLOGY_CHANGES_TIMEOUT = Duration.ofMinutes(1);

    private final EventBusId eventBusId;
    private final LocalListenerRegistry localListenerRegistry;
    private final EventSerializer eventSerializer;
    private final Sender sender;
    private final RoutingKeyConverter routingKeyConverter;
    private final RegistrationQueueName registrationQueue;
    private final RegistrationBinder registrationBinder;
    private final ListenerExecutor listenerExecutor;
    private final RetryBackoffConfiguration retryBackoff;
    private final RabbitMQConfiguration configuration;
    private final ReceiverProvider receiverProvider;
    private Optional<Disposable> receiverSubscriber;
    private Disposable newSubscription;

    KeyRegistrationHandler(NamingStrategy namingStrategy, EventBusId eventBusId, EventSerializer eventSerializer,
                           Sender sender, ReceiverProvider receiverProvider,
                           RoutingKeyConverter routingKeyConverter, LocalListenerRegistry localListenerRegistry,
                           ListenerExecutor listenerExecutor, RetryBackoffConfiguration retryBackoff, RabbitMQConfiguration configuration) {
        this.eventBusId = eventBusId;
        this.eventSerializer = eventSerializer;
        this.sender = sender;
        this.routingKeyConverter = routingKeyConverter;
        this.localListenerRegistry = localListenerRegistry;
        this.receiverProvider = receiverProvider;
        this.listenerExecutor = listenerExecutor;
        this.retryBackoff = retryBackoff;
        this.configuration = configuration;
        this.registrationQueue = namingStrategy.queueName(eventBusId);
        this.registrationBinder = new RegistrationBinder(namingStrategy, sender, registrationQueue);
        this.receiverSubscriber = Optional.empty();
    }

    void start() {
        declareQueue();

        newSubscription = Flux.using(
            receiverProvider::createReceiver,
            receiver -> receiver.consumeAutoAck(registrationQueue.asString(), new ConsumeOptions().qos(EventBus.EXECUTION_RATE)),
            Receiver::close)
            .subscribeOn(Schedulers.parallel())
            .flatMap(this::handleDelivery, EventBus.EXECUTION_RATE)
            .subscribeOn(Schedulers.elastic())
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

    void declareQueue() {
        declareQueue(sender);
    }

    private void declareQueue(Sender sender) {
        sender.declareQueue(
            QueueSpecification.queue(registrationQueue.asString())
                .durable(DURABLE)
                .exclusive(!EXCLUSIVE)
                .autoDelete(AUTO_DELETE)
                .arguments(configuration.workQueueArgumentsBuilder(AUTO_DELETE).build()))
            .timeout(TOPOLOGY_CHANGES_TIMEOUT)
            .map(AMQP.Queue.DeclareOk::getQueue)
            .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()))
            .block();
    }

    void stop() {
        sender.delete(QueueSpecification.queue(registrationQueue.asString()))
            .timeout(TOPOLOGY_CHANGES_TIMEOUT)
            .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.elastic()))
            .block();
        receiverSubscriber.filter(Predicate.not(Disposable::isDisposed))
                .ifPresent(Disposable::dispose);
    }

    Mono<Registration> register(EventListener.ReactiveEventListener listener, RegistrationKey key) {
        LocalListenerRegistry.LocalRegistration registration = localListenerRegistry.addListener(key, listener);

        return registerIfNeeded(key, registration)
            .thenReturn(new KeyRegistration(() -> {
                if (registration.unregister().lastListenerRemoved()) {
                    registrationBinder.unbind(key)
                        .timeout(TOPOLOGY_CHANGES_TIMEOUT)
                        .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.elastic()))
                        .subscribeOn(Schedulers.elastic())
                        .block();
                }
            }));
    }

    private Mono<Void> registerIfNeeded(RegistrationKey key, LocalListenerRegistry.LocalRegistration registration) {
        if (registration.isFirstListener()) {
            return registrationBinder.bind(key)
                .timeout(TOPOLOGY_CHANGES_TIMEOUT)
                .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.elastic()));
        }
        return Mono.empty();
    }

    private Mono<Void> handleDelivery(Delivery delivery) {
        if (delivery.getBody() == null) {
            return Mono.empty();
        }

        String serializedEventBusId = delivery.getProperties().getHeaders().get(EVENT_BUS_ID).toString();
        EventBusId eventBusId = EventBusId.of(serializedEventBusId);

        String routingKey = delivery.getEnvelope().getRoutingKey();
        RegistrationKey registrationKey = routingKeyConverter.toRegistrationKey(routingKey);
        Event event = toEvent(delivery);

        return localListenerRegistry.getLocalListeners(registrationKey)
            .filter(listener -> !isLocalSynchronousListeners(eventBusId, listener))
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

    private Event toEvent(Delivery delivery) {
        return eventSerializer.fromBytes(delivery.getBody());
    }

    private StructuredLogger structuredLogger(Event event, RegistrationKey key) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .field(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId().getId().toString())
            .field(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass().getCanonicalName())
            .field(EventBus.StructuredLoggingFields.USER, event.getUsername().asString())
            .field(EventBus.StructuredLoggingFields.REGISTRATION_KEY, key.asString());
    }
}
