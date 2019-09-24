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

package org.apache.james.backends.rabbitmq;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.rabbitmq.client.Channel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;

public class SimpleChannelPool implements RabbitMQChannelPool {
    private final AtomicReference<Channel> channelReference;
    private final Receiver rabbitFlux;
    private final SimpleConnectionPool connectionPool;

    @Inject
    @VisibleForTesting
    SimpleChannelPool(SimpleConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
        this.channelReference = new AtomicReference<>();
        this.rabbitFlux = RabbitFlux
            .createReceiver(new ReceiverOptions().connectionMono(connectionPool.getResilientConnection()));
    }

    @Override
    public Flux<AcknowledgableDelivery> receive(String queueName) {
        return rabbitFlux.consumeManualAck(queueName);
    }

    @Override
    public <T, E extends Throwable> T execute(RabbitFunction<T, E> f) throws E, ConnectionFailedException {
        return f.execute(getResilientChannel().block());
    }

    @Override
    public <E extends Throwable> void execute(RabbitConsumer<E> f) throws E, ConnectionFailedException {
        f.execute(getResilientChannel().block());
    }

    @PreDestroy
    @Override
    public void close() {
        Optional.ofNullable(channelReference.get())
            .filter(Channel::isOpen)
            .ifPresent(Throwing.<Channel>consumer(Channel::close).orDoNothing());

        try {
            rabbitFlux.close();
        } catch (Throwable ignored) {
            //ignore exception during close
        }
    }

    private Mono<Channel> getResilientChannel() {
        int numRetries = 100;
        Duration initialDelay = Duration.ofMillis(100);
        Duration forever = Duration.ofMillis(Long.MAX_VALUE);
        return Mono.defer(this::getOpenChannel)
            .retryBackoff(numRetries, initialDelay, forever, Schedulers.elastic());
    }

    private Mono<Channel> getOpenChannel() {
        Channel previous = channelReference.get();
        return Mono.justOrEmpty(previous)
            .publishOn(Schedulers.elastic())
            .filter(Channel::isOpen)
            .switchIfEmpty(connectionPool.getResilientConnection()
                .flatMap(connection -> Mono.fromCallable(connection::createChannel)))
            .flatMap(current -> replaceCurrentChannel(previous, current))
            .onErrorMap(t -> new RuntimeException("unable to create and register a new Channel", t));
    }

    private Mono<Channel> replaceCurrentChannel(Channel previous, Channel current) {
        if (channelReference.compareAndSet(previous, current)) {
            return Mono.just(current);
        } else {
            try {
                current.close();
            } catch (IOException | TimeoutException e) {
                //error below
            }
            return Mono.error(new RuntimeException("unable to create and register a new Channel"));
        }
    }

    @Override
    public boolean tryConnection() {
        try {
            return connectionPool.tryConnection() &&
                getOpenChannel()
                    .blockOptional()
                    .isPresent();
        } catch (Throwable t) {
            return false;
        }
    }
}
