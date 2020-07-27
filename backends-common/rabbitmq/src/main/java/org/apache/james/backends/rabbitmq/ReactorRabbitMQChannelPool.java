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
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiConsumer;

import javax.annotation.PreDestroy;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ChannelPool;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;
import reactor.util.retry.Retry;

public class ReactorRabbitMQChannelPool implements ChannelPool, Startable {

    private static class ChannelClosedException extends IOException {
        ChannelClosedException(String message) {
            super(message);
        }
    }

    static class ChannelFactory extends BasePooledObjectFactory<Channel> {

        private static final Logger LOGGER = LoggerFactory.getLogger(ChannelFactory.class);

        private final Mono<Connection> connectionMono;
        private final Duration minBorrowDelay;
        private final int retries;

        ChannelFactory(Mono<Connection> connectionMono, Duration minBorrowDelay, int retries) {
            this.connectionMono = connectionMono;
            this.minBorrowDelay = minBorrowDelay;
            this.retries = retries;
        }

        @Override
        public Channel create() {
            return connectionMono
                .flatMap(this::openChannel)
                .block();
        }

        private Mono<Channel> openChannel(Connection connection) {
            return Mono.fromCallable(connection::openChannel)
                .map(maybeChannel ->
                    maybeChannel.orElseThrow(() -> new RuntimeException("RabbitMQ reached to maximum opened channels, cannot get more channels")))
                .retryWhen(Retry.backoff(retries, minBorrowDelay).scheduler(Schedulers.elastic()))
                .doOnError(throwable -> LOGGER.error("error when creating new channel", throwable));
        }

        @Override
        public PooledObject<Channel> wrap(Channel obj) {
            return new DefaultPooledObject<>(obj);
        }

        @Override
        public void destroyObject(PooledObject<Channel> pooledObject) throws Exception {
            Channel channel = pooledObject.getObject();
            if (channel.isOpen()) {
                channel.close();
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactorRabbitMQChannelPool.class);
    private static final long MAXIMUM_BORROW_TIMEOUT_IN_MS = Duration.ofSeconds(5).toMillis();
    private static final int MAX_CHANNELS_NUMBER = 3;
    private static final int MAX_BORROW_RETRIES = 3;
    private static final Duration MIN_BORROW_DELAY = Duration.ofMillis(50);

    private final Mono<Connection> connectionMono;
    private final GenericObjectPool<Channel> pool;
    private final ConcurrentSkipListSet<Channel> borrowedChannels;
    private final Duration minBorrowDelay;
    private final int retries;
    private Sender sender;

    public ReactorRabbitMQChannelPool(SimpleConnectionPool simpleConnectionPool) {
        this(simpleConnectionPool.getResilientConnection(), MAX_CHANNELS_NUMBER, MIN_BORROW_DELAY, MAX_BORROW_RETRIES);
    }

    public ReactorRabbitMQChannelPool(Mono<Connection> connectionMono, int poolSize, Duration minBorrowDelay, int retries) {
        this.connectionMono = connectionMono;
        this.retries = retries;
        ChannelFactory channelFactory = new ChannelFactory(connectionMono, minBorrowDelay, retries);

        GenericObjectPoolConfig<Channel> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(poolSize);
        this.pool = new GenericObjectPool<>(channelFactory, config);
        this.borrowedChannels = new ConcurrentSkipListSet<>(Comparator.comparingInt(System::identityHashCode));
        this.minBorrowDelay = minBorrowDelay;
    }

    public void start() {
        sender = createSender();
    }

    public Sender getSender() {
        return sender;
    }

    public Receiver createReceiver() {
        return RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionMono));
    }

    @Override
    public Mono<? extends Channel> getChannelMono() {
        return borrow();
    }

    private Mono<Channel> borrow() {
        return tryBorrowFromPool()
            .doOnError(throwable -> LOGGER.warn("Cannot borrow channel", throwable))
            .retryWhen(Retry.backoff(retries, minBorrowDelay).scheduler(Schedulers.elastic()))
            .onErrorMap(this::propagateException)
            .subscribeOn(Schedulers.elastic())
            .doOnNext(borrowedChannels::add);
    }

    private Mono<Channel> tryBorrowFromPool() {
        return Mono.fromCallable(this::borrowFromPool);
    }

    private Throwable propagateException(Throwable throwable) {
        if (throwable instanceof IllegalStateException
            && throwable.getMessage().contains("Retries exhausted")) {
            return throwable.getCause();
        }

        return throwable;
    }

    private Channel borrowFromPool() throws Exception {
        Channel channel = pool.borrowObject(MAXIMUM_BORROW_TIMEOUT_IN_MS);
        if (!channel.isOpen()) {
            invalidateObject(channel);
            throw new ChannelClosedException("borrowed channel is already closed");
        }
        return channel;
    }

    @Override
    public BiConsumer<SignalType, Channel> getChannelCloseHandler() {
        return (signalType, channel) -> {
            borrowedChannels.remove(channel);
            if (!channel.isOpen() || signalType != SignalType.ON_COMPLETE) {
                invalidateObject(channel);
                return;
            }
            pool.returnObject(channel);
        };
    }

    private Sender createSender() {
       return RabbitFlux.createSender(new SenderOptions()
           .connectionMono(connectionMono)
           .channelPool(this)
           .resourceManagementChannelMono(
               connectionMono.map(Throwing.function(Connection::createChannel)).cache()));
    }

    private void invalidateObject(Channel channel) {
        try {
            pool.invalidateObject(channel);
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        sender.close();
        borrowedChannels.forEach(channel -> getChannelCloseHandler().accept(SignalType.ON_NEXT, channel));
        borrowedChannels.clear();
        pool.close();
    }

    public Mono<Boolean> tryChannel() {
        return Mono.usingWhen(borrow(),
            channel -> Mono.just(channel.isOpen()),
            channel -> {
                if (channel != null) {
                    borrowedChannels.remove(channel);
                    pool.returnObject(channel);
                }
                return Mono.empty();
            })
            .onErrorResume(any -> Mono.just(false));
    }
}
