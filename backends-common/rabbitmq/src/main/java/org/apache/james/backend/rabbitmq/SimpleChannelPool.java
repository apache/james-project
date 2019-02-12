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

package org.apache.james.backend.rabbitmq;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import reactor.core.publisher.Flux;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;

public class SimpleChannelPool implements RabbitMQChannelPool {
    private final AtomicReference<Channel> channelReference;
    private final AtomicReference<Connection> connectionReference;
    private final RabbitMQConnectionFactory connectionFactory;
    private final Receiver rabbitFlux;

    @Inject
    @VisibleForTesting
    SimpleChannelPool(RabbitMQConnectionFactory factory) {
        this.connectionFactory = factory;
        this.connectionReference = new AtomicReference<>();
        this.channelReference = new AtomicReference<>();
        this.rabbitFlux = RabbitFlux
            .createReceiver(new ReceiverOptions().connectionMono(connectionFactory.connectionMono()));
    }

    @Override
    public Flux<AcknowledgableDelivery> receive(String queueName) {
        return rabbitFlux.consumeManualAck(queueName);
    }

    @Override
    public synchronized <T, E extends Throwable> T execute(RabbitFunction<T, E> f) throws E, ConnectionFailedException {
        return f.execute(getResilientChannel());
    }

    @Override
    public synchronized <E extends Throwable> void execute(RabbitConsumer<E> f) throws E, ConnectionFailedException {
        f.execute(getResilientChannel());
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        Optional.ofNullable(channelReference.get())
            .filter(Channel::isOpen)
            .ifPresent(Throwing.<Channel>consumer(Channel::close).orDoNothing());

        Optional.ofNullable(connectionReference.get())
            .filter(Connection::isOpen)
            .ifPresent(Throwing.<Connection>consumer(Connection::close).orDoNothing());

        try {
            rabbitFlux.close();
        } catch (Throwable ignored) {
            //ignore exception during close
        }
    }

    private Connection getResilientConnection() {
        return connectionReference.updateAndGet(this::getOpenConnection);
    }

    private Connection getOpenConnection(Connection checkedConnection) {
        return Optional.ofNullable(checkedConnection)
            .filter(Connection::isOpen)
            .orElseGet(connectionFactory::create);
    }

    private Channel getResilientChannel() {
        return channelReference.updateAndGet(Throwing.unaryOperator(this::getOpenChannel));
    }

    private Channel getOpenChannel(Channel checkedChannel) {
        return Optional.ofNullable(checkedChannel)
            .filter(Channel::isOpen)
            .orElseGet(Throwing.supplier(() -> getResilientConnection().createChannel())
                .sneakyThrow());
    }
}
