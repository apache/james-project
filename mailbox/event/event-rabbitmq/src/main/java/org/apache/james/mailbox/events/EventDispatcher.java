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
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.Event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

public class EventDispatcher {
    private final EventSerializer eventSerializer;
    private final Sender sender;

    EventDispatcher(EventSerializer eventSerializer, Sender sender) {
        this.eventSerializer = eventSerializer;
        this.sender = sender;
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

        return doDispatch(serializedEvent, keys)
            .subscribeWith(MonoProcessor.create());
    }

    private Mono<Void> doDispatch(Mono<byte[]> serializedEvent, Set<RegistrationKey> keys) {
        Flux<RoutingKeyConverter.RoutingKey> routingKeys = Flux.concat(
            Mono.just(RoutingKeyConverter.RoutingKey.empty()),
            Flux.fromIterable(keys)
                .map(RoutingKeyConverter.RoutingKey::of));

        Flux<OutboundMessage> outboundMessages = routingKeys
            .flatMap(routingKey -> serializedEvent
                .map(payload -> new OutboundMessage(MAILBOX_EVENT_EXCHANGE_NAME, routingKey.asString(), payload)));

        return sender.send(outboundMessages);
    }

    private byte[] serializeEvent(Event event) {
        return eventSerializer.toJson(event).getBytes(StandardCharsets.UTF_8);
    }

}
