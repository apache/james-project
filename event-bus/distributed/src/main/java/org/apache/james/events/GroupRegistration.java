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
import static org.apache.james.backends.rabbitmq.Constants.REQUEUE;
import static org.apache.james.backends.rabbitmq.Constants.evaluateAutoDelete;
import static org.apache.james.backends.rabbitmq.Constants.evaluateDurable;
import static org.apache.james.backends.rabbitmq.Constants.evaluateExclusive;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

class GroupRegistration implements Registration {

    static class WorkQueueName {
        private final String prefix;
        private final Group group;

        WorkQueueName(String prefix, Group group) {
            this.prefix = prefix;
            Preconditions.checkNotNull(group, "Group must be specified");
            this.group = group;
        }

        String asString() {
            return prefix + "-workQueue-" + group.asString();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupRegistration.class);
    static final String RETRY_COUNT = "retry-count";
    static final int DEFAULT_RETRY_COUNT = 0;

    private final NamingStrategy namingStrategy;
    private final ReactorRabbitMQChannelPool channelPool;
    private final EventListener.ReactiveEventListener listener;
    private final WorkQueueName queueName;
    private final Runnable unregisterGroup;
    private final EventSerializer eventSerializer;
    private final GroupConsumerRetry retryHandler;
    private final WaitDelayGenerator delayGenerator;
    private final Group group;
    private final ListenerExecutor listenerExecutor;
    private final RabbitMQEventBus.Configurations configurations;
    private Optional<Disposable> receiverSubscriber;
    private final ReceiverProvider receiverProvider;
    private Scheduler scheduler;

    GroupRegistration(NamingStrategy namingStrategy, ReactorRabbitMQChannelPool channelPool, Sender sender, ReceiverProvider receiverProvider, EventSerializer eventSerializer,
                      EventListener.ReactiveEventListener listener, Group group, EventDeadLetters eventDeadLetters, Runnable unregisterGroup,
                      ListenerExecutor listenerExecutor, RabbitMQEventBus.Configurations configurations) {
        this.namingStrategy = namingStrategy;
        this.channelPool = channelPool;
        this.eventSerializer = eventSerializer;
        this.listener = listener;
        this.configurations = configurations;
        this.queueName = namingStrategy.workQueue(group);
        this.receiverProvider = receiverProvider;
        this.listenerExecutor = listenerExecutor;
        this.receiverSubscriber = Optional.empty();
        this.unregisterGroup = unregisterGroup;
        this.retryHandler = new GroupConsumerRetry(namingStrategy, sender, group, configurations.retryBackoff(), eventDeadLetters, eventSerializer);
        this.delayGenerator = WaitDelayGenerator.of(configurations.retryBackoff());
        this.group = group;
    }

    GroupRegistration start() {
        scheduler = Schedulers.newBoundedElastic(configurations.eventBusConfiguration().maxConcurrency(), ReactorUtils.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "group-handler");
        receiverSubscriber = Optional
            .of(createGroupWorkQueue()
                .then(retryHandler.createRetryExchange(queueName))
                .then(Mono.fromCallable(this::consumeWorkQueue))
                .retryWhen(configurations.retryBackoff().asReactorRetry().scheduler(Schedulers.boundedElastic()))
                .block());
        return this;
    }

    void restart() {
        Optional<Disposable> previousSubscriber = this.receiverSubscriber;
        receiverSubscriber = Optional.of(consumeWorkQueue());
        previousSubscriber
            .filter(Predicate.not(Disposable::isDisposed))
            .ifPresent(Disposable::dispose);
    }

    private Mono<Void> createGroupWorkQueue() {
        return channelPool.createWorkQueue(
            QueueSpecification.queue(queueName.asString())
                .durable(evaluateDurable(DURABLE, configurations.rabbitMQConfiguration().isQuorumQueuesUsed()))
                .exclusive(evaluateExclusive(!EXCLUSIVE, configurations.rabbitMQConfiguration().isQuorumQueuesUsed()))
                .autoDelete(evaluateAutoDelete(!AUTO_DELETE, configurations.rabbitMQConfiguration().isQuorumQueuesUsed()))
                .arguments(configurations.rabbitMQConfiguration().workQueueArgumentsBuilder()
                    .deadLetter(namingStrategy.deadLetterExchange())
                    .build()));
    }

    private Disposable consumeWorkQueue() {
        return Flux.using(
                receiverProvider::createReceiver,
                receiver -> receiver.consumeManualAck(queueName.asString(), new ConsumeOptions().qos(configurations.eventBusConfiguration().maxConcurrency())),
                Receiver::close)
            .publishOn(Schedulers.parallel())
            .filter(delivery -> Objects.nonNull(delivery.getBody()))
            .flatMap(this::deliver, configurations.eventBusConfiguration().maxConcurrency())
            .subscribeOn(scheduler)
            .subscribe();
    }

    private Mono<Void> deliver(AcknowledgableDelivery acknowledgableDelivery) {
        byte[] eventAsBytes = acknowledgableDelivery.getBody();
        int currentRetryCount = getRetryCount(acknowledgableDelivery);

        return deserializeEvent(eventAsBytes)
            .flatMap(event -> delayGenerator.delayIfHaveTo(currentRetryCount)
                .flatMap(any -> runListenerReliably(currentRetryCount, event))
                .then(Mono.<Void>fromRunnable(acknowledgableDelivery::ack).subscribeOn(Schedulers.boundedElastic())))
            .onErrorResume(e -> {
                LOGGER.error("Unable to process delivery for group {}", group, e);
                return Mono.fromRunnable(() -> acknowledgableDelivery.nack(!REQUEUE))
                    .subscribeOn(Schedulers.boundedElastic())
                    .then();
            });
    }

    public Mono<Void> runListenerReliably(int currentRetryCount, Event event) {
        return runListener(event)
            .onErrorResume(throwable -> retryHandler.handleRetry(event, currentRetryCount, throwable));
    }

    public Mono<Void> runListenerReliably(int currentRetryCount, List<Event> events) {
        return runListener(events)
            .onErrorResume(throwable -> Flux.fromIterable(events)
                .concatMap(event -> retryHandler.handleRetry(event, currentRetryCount, throwable))
                .then());
    }

    private Mono<Event> deserializeEvent(byte[] eventAsBytes) {
        return Mono.fromCallable(() -> eventSerializer.fromBytes(eventAsBytes))
            .subscribeOn(Schedulers.parallel());
    }

    Mono<Void> reDeliver(Event event) {
        return retryHandler.retryOrStoreToDeadLetter(event, DEFAULT_RETRY_COUNT);
    }

    private Mono<Void> runListener(Event event) {
        MDCBuilder mdc = MDCBuilder.create().addToContext(EventBus.StructuredLoggingFields.GROUP, group.asString());
        Mono<Void> result = listenerExecutor.execute(listener, mdc, event);
        return configurations.eventBusConfiguration().executionTimeout().map(result::timeout).orElse(result);
    }

    private Mono<Void> runListener(List<Event> events) {
        MDCBuilder mdc = MDCBuilder.create().addToContext(EventBus.StructuredLoggingFields.GROUP, group.asString());
        Mono<Void> result = listenerExecutor.execute(listener, mdc, events);
        return configurations.eventBusConfiguration().executionTimeout().map(result::timeout).orElse(result);
    }

    private int getRetryCount(AcknowledgableDelivery acknowledgableDelivery) {
        return Optional.ofNullable(acknowledgableDelivery.getProperties().getHeaders())
            .flatMap(headers -> Optional.ofNullable(headers.get(RETRY_COUNT)))
            .filter(Integer.class::isInstance)
            .map(Integer.class::cast)
            .orElse(DEFAULT_RETRY_COUNT);
    }

    @Override
    public Mono<Void> unregister() {
        return Mono.fromRunnable(() -> {
            receiverSubscriber.filter(Predicate.not(Disposable::isDisposed))
                .ifPresent(Disposable::dispose);
            unregisterGroup.run();
            scheduler.dispose();
        });
    }
}