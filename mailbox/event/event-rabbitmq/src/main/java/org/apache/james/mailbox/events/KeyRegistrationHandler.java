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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.NO_ARGUMENTS;
import static org.apache.james.mailbox.events.RabbitMQEventBus.EVENT_BUS_ID;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

class KeyRegistrationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyRegistrationHandler.class);

    private final EventBusId eventBusId;
    private final LocalListenerRegistry localListenerRegistry;
    private final EventSerializer eventSerializer;
    private final Sender sender;
    private final RoutingKeyConverter routingKeyConverter;
    private final Receiver receiver;
    private final RegistrationQueueName registrationQueue;
    private final RegistrationBinder registrationBinder;
    private final MailboxListenerExecutor mailboxListenerExecutor;
    private Optional<Disposable> receiverSubscriber;

    KeyRegistrationHandler(EventBusId eventBusId, EventSerializer eventSerializer, ReactorRabbitMQChannelPool reactorRabbitMQChannelPool, RoutingKeyConverter routingKeyConverter, LocalListenerRegistry localListenerRegistry, MailboxListenerExecutor mailboxListenerExecutor) {
        this.eventBusId = eventBusId;
        this.eventSerializer = eventSerializer;
        this.sender = reactorRabbitMQChannelPool.getSender();
        this.routingKeyConverter = routingKeyConverter;
        this.localListenerRegistry = localListenerRegistry;
        this.receiver = reactorRabbitMQChannelPool.createReceiver();
        this.mailboxListenerExecutor = mailboxListenerExecutor;
        this.registrationQueue = new RegistrationQueueName();
        this.registrationBinder = new RegistrationBinder(sender, registrationQueue);
    }

    void start() {
        sender.declareQueue(QueueSpecification.queue(eventBusId.asString())
            .durable(DURABLE)
            .exclusive(!EXCLUSIVE)
            .autoDelete(!AUTO_DELETE)
            .arguments(NO_ARGUMENTS))
            .map(AMQP.Queue.DeclareOk::getQueue)
            .doOnSuccess(registrationQueue::initialize)
            .block();

        receiverSubscriber = Optional.of(receiver.consumeAutoAck(registrationQueue.asString(), new ConsumeOptions().qos(EventBus.EXECUTION_RATE))
            .subscribeOn(Schedulers.parallel())
            .flatMap(this::handleDelivery)
            .subscribe());
    }

    void stop() {
        receiverSubscriber.filter(subscriber -> !subscriber.isDisposed())
            .ifPresent(Disposable::dispose);
        receiver.close();
        sender.delete(QueueSpecification.queue(registrationQueue.asString())).block();
    }

    Registration register(MailboxListener listener, RegistrationKey key) {
        LocalListenerRegistry.LocalRegistration registration = localListenerRegistry.addListener(key, listener);
        if (registration.isFirstListener()) {
            registrationBinder.bind(key).block();
        }
        return new KeyRegistration(() -> {
            if (registration.unregister().lastListenerRemoved()) {
                registrationBinder.unbind(key).block();
            }
        });
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

        return localListenerRegistry.getLocalMailboxListeners(registrationKey)
            .filter(listener -> !isLocalSynchronousListeners(eventBusId, listener))
            .flatMap(listener -> Mono.fromRunnable(Throwing.runnable(() -> executeListener(listener, event, registrationKey)))
                .doOnError(e -> structuredLogger(event, registrationKey)
                    .log(logger -> logger.error("Exception happens when handling event", e)))
                .onErrorResume(e -> Mono.empty())
                .then())
            .subscribeOn(Schedulers.elastic())
            .then();
    }

    private void executeListener(MailboxListener listener, Event event, RegistrationKey key) throws Exception {
        mailboxListenerExecutor.execute(listener,
            MDCBuilder.create()
                .addContext(EventBus.StructuredLoggingFields.REGISTRATION_KEY, key),
            event);
    }

    private boolean isLocalSynchronousListeners(EventBusId eventBusId, MailboxListener listener) {
        return eventBusId.equals(this.eventBusId) &&
            listener.getExecutionMode().equals(MailboxListener.ExecutionMode.SYNCHRONOUS);
    }

    private Event toEvent(Delivery delivery) {
        return eventSerializer.fromJson(new String(delivery.getBody(), StandardCharsets.UTF_8)).get();
    }

    private StructuredLogger structuredLogger(Event event, RegistrationKey key) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .addField(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId())
            .addField(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass())
            .addField(EventBus.StructuredLoggingFields.USER, event.getUsername())
            .addField(EventBus.StructuredLoggingFields.REGISTRATION_KEY, key);
    }
}
