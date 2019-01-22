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

import static org.apache.james.backend.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backend.rabbitmq.Constants.DURABLE;
import static org.apache.james.mailbox.events.RabbitMQEventBus.EVENT_BUS_ID;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

class EventDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDispatcher.class);

    private final EventSerializer eventSerializer;
    private final Sender sender;
    private final MailboxListenerRegistry mailboxListenerRegistry;
    private final AMQP.BasicProperties basicProperties;
    private final MailboxListenerExecutor mailboxListenerExecutor;

    EventDispatcher(EventBusId eventBusId, EventSerializer eventSerializer, Sender sender, MailboxListenerRegistry mailboxListenerRegistry, MailboxListenerExecutor mailboxListenerExecutor) {
        this.eventSerializer = eventSerializer;
        this.sender = sender;
        this.mailboxListenerRegistry = mailboxListenerRegistry;
        this.basicProperties = new AMQP.BasicProperties.Builder()
            .headers(ImmutableMap.of(EVENT_BUS_ID, eventBusId.asString()))
            .build();
        this.mailboxListenerExecutor = mailboxListenerExecutor;
    }

    void start() {
        sender.declareExchange(ExchangeSpecification.exchange(MAILBOX_EVENT_EXCHANGE_NAME)
            .durable(DURABLE)
            .type(DIRECT_EXCHANGE))
            .block();
    }

    Mono<Void> dispatch(Event event, Set<RegistrationKey> keys) {
        Mono<byte[]> serializedEvent = Mono.just(event)
            .publishOn(Schedulers.parallel())
            .map(this::serializeEvent)
            .cache();

        Mono<Void> distantDispatchMono = doDispatch(serializedEvent, keys).cache()
            .subscribeWith(MonoProcessor.create());

        Mono<Void> localListenerDelivery = Flux.fromIterable(keys)
            .subscribeOn(Schedulers.elastic())
            .flatMap(key -> mailboxListenerRegistry.getLocalMailboxListeners(key)
                .map(listener -> Pair.of(key, listener)))
            .filter(pair -> pair.getRight().getExecutionMode().equals(MailboxListener.ExecutionMode.SYNCHRONOUS))
            .flatMap(pair -> Mono.fromRunnable(Throwing.runnable(() -> executeListener(event, pair.getRight(), pair.getLeft())))
                .doOnError(e -> structuredLogger(event, keys)
                    .log(logger -> logger.error("Exception happens when dispatching event", e)))
                .onErrorResume(e -> Mono.empty()))
            .cache()
            .then()
            .subscribeWith(MonoProcessor.create());

        return Flux.concat(localListenerDelivery, distantDispatchMono)
            .then();
    }

    private void executeListener(Event event, MailboxListener mailboxListener, RegistrationKey registrationKey) throws Exception {
        mailboxListenerExecutor.execute(mailboxListener,
            MDCBuilder.create()
                .addContext(EventBus.StructuredLoggingFields.REGISTRATION_KEY, registrationKey),
            event);
    }

    private StructuredLogger structuredLogger(Event event, Set<RegistrationKey> keys) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .addField(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId())
            .addField(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass())
            .addField(EventBus.StructuredLoggingFields.USER, event.getUser())
            .addField(EventBus.StructuredLoggingFields.REGISTRATION_KEYS, keys);
    }

    private Mono<Void> doDispatch(Mono<byte[]> serializedEvent, Set<RegistrationKey> keys) {
        Flux<RoutingKeyConverter.RoutingKey> routingKeys = Flux.concat(
            Mono.just(RoutingKeyConverter.RoutingKey.empty()),
            Flux.fromIterable(keys)
                .map(RoutingKeyConverter.RoutingKey::of));

        Flux<OutboundMessage> outboundMessages = routingKeys
            .flatMap(routingKey -> serializedEvent
                .map(payload -> new OutboundMessage(MAILBOX_EVENT_EXCHANGE_NAME, routingKey.asString(), basicProperties, payload)));

        return sender.send(outboundMessages);
    }

    private byte[] serializeEvent(Event event) {
        return eventSerializer.toJson(event).getBytes(StandardCharsets.UTF_8);
    }
}
