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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;

import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

class RabbitMQEventBus implements EventBus {
    static final String MAILBOX_EVENT = "mailboxEvent";
    static final String MAILBOX_EVENT_EXCHANGE_NAME = MAILBOX_EVENT + "-exchange";

    private final Mono<Connection> connectionMono;
    private final EventSerializer eventSerializer;
    private final AtomicBoolean isRunning;

    private GroupRegistrationHandler groupRegistrationHandler;
    private EventDispatcher eventDispatcher;
    private Sender sender;

    RabbitMQEventBus(RabbitMQConnectionFactory rabbitMQConnectionFactory, EventSerializer eventSerializer) {
        this.connectionMono = Mono.fromSupplier(rabbitMQConnectionFactory::create).cache();
        this.eventSerializer = eventSerializer;
        isRunning = new AtomicBoolean(false);
    }

    public void start() {
        if (!isRunning.get()) {
            sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
            groupRegistrationHandler = new GroupRegistrationHandler(eventSerializer, sender, connectionMono);
            eventDispatcher = new EventDispatcher(eventSerializer, sender);

            eventDispatcher.start();

            isRunning.set(true);
        }
    }

    @PreDestroy
    public void stop() {
        if (isRunning.get()) {
            groupRegistrationHandler.stop();
            sender.close();
            isRunning.set(false);
        }
    }

    @Override
    public Registration register(MailboxListener listener, RegistrationKey key) {
        throw new NotImplementedException("will implement latter");
    }

    @Override
    public Registration register(MailboxListener listener, Group group) {
        return groupRegistrationHandler.register(listener, group);
    }

    @Override
    public Mono<Void> dispatch(Event event, Set<RegistrationKey> key) {
        if (!event.isNoop()) {
            return eventDispatcher.dispatch(event);
        }
        return Mono.empty();
    }
}