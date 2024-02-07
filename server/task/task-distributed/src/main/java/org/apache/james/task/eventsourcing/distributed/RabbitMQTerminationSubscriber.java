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

package org.apache.james.task.eventsourcing.distributed;

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.util.ReactorUtils.publishIfPresent;
import static reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.task.eventsourcing.TerminationSubscriber;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

public class RabbitMQTerminationSubscriber implements TerminationSubscriber, Startable, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQTerminationSubscriber.class);
    static final String EXCHANGE_NAME = "terminationSubscriberExchange";
    static final String QUEUE_NAME_PREFIX = "terminationSubscriber";
    static final String ROUTING_KEY = "terminationSubscriberRoutingKey";

    private final TerminationQueueName queueName;
    private final JsonEventSerializer serializer;
    private final Sender sender;
    private final ReceiverProvider receiverProvider;
    private final RabbitMQConfiguration rabbitMQConfiguration;
    private Sinks.Many<OutboundMessage> sendQueue;
    private Sinks.Many<Event> listener;
    private Disposable sendQueueHandle;
    private Disposable listenQueueHandle;

    @Inject
    RabbitMQTerminationSubscriber(TerminationQueueName queueName, Sender sender, ReceiverProvider receiverProvider, JsonEventSerializer serializer, RabbitMQConfiguration rabbitMQConfiguration) {
        this.queueName = queueName;
        this.sender = sender;
        this.receiverProvider = receiverProvider;
        this.serializer = serializer;
        this.rabbitMQConfiguration = rabbitMQConfiguration;
    }

    public void start() {
        sender.declareExchange(ExchangeSpecification.exchange(EXCHANGE_NAME)).block();
        QueueArguments.Builder builder = QueueArguments.builder();
        rabbitMQConfiguration.getQueueTTL().ifPresent(builder::queueTTL);
        sender.declare(QueueSpecification.queue(queueName.asString()).durable(!DURABLE).autoDelete(!AUTO_DELETE).arguments(builder.build())).block();
        sender.bind(BindingSpecification.binding(EXCHANGE_NAME, ROUTING_KEY, queueName.asString())).block();
        sendQueue = Sinks.many().unicast().onBackpressureBuffer();
        sendQueueHandle = sender
            .send(sendQueue.asFlux())
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();

        listener = Sinks.many().multicast().directBestEffort();
        listenQueueHandle = consumeTerminationQueue();
    }

    public void restart() {
        Disposable previousHandler = listenQueueHandle;
        listenQueueHandle = consumeTerminationQueue();
        previousHandler.dispose();
    }

    private Disposable consumeTerminationQueue() {
        return Flux.using(
                receiverProvider::createReceiver,
                receiver -> receiver.consumeAutoAck(queueName.asString()),
                Receiver::close)
            .subscribeOn(Schedulers.boundedElastic())
            .map(this::toEvent)
            .handle(publishIfPresent())
            .subscribe(e -> listener.emitNext(e, FAIL_FAST));
    }

    @Override
    public void addEvent(Event event) {
        try {
            byte[] payload = serializer.serialize(event).getBytes(StandardCharsets.UTF_8);
            AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder().build();
            OutboundMessage message = new OutboundMessage(EXCHANGE_NAME, ROUTING_KEY, basicProperties, payload);
            sendQueue.emitNext(message, FAIL_FAST);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Publisher<Event> listenEvents() {
        return listener.asFlux();
    }

    private Optional<Event> toEvent(Delivery delivery) {
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
        try {
            Event event = serializer.deserialize(message);
            return Optional.of(event);
        } catch (Exception e) {
            LOGGER.error("Unable to deserialize '{}'", message, e);
            return Optional.empty();
        }
    }

    @Override
    @PreDestroy
    public void close() {
        Optional.ofNullable(sendQueueHandle).ifPresent(Disposable::dispose);
        Optional.ofNullable(listenQueueHandle).ifPresent(Disposable::dispose);
    }
}
