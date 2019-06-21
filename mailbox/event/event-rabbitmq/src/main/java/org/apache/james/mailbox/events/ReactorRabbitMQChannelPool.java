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

import java.time.Duration;
import java.util.function.BiConsumer;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ChannelPool;

class ReactorRabbitMQChannelPool implements ChannelPool {

    static class ChannelFactory extends BasePooledObjectFactory<Channel> {

        private static final Logger LOGGER = LoggerFactory.getLogger(ChannelFactory.class);

        private static final int MAX_RETRIES = 5;
        private static final Duration RETRY_FIRST_BACK_OFF = Duration.ofMillis(100);
        private static final Duration FOREVER = Duration.ofMillis(Long.MAX_VALUE);

        private final Mono<Connection> connectionMono;

        ChannelFactory(Mono<Connection> connectionMono) {
            this.connectionMono = connectionMono;
        }

        @Override
        public Channel create() throws Exception {
            return connectionMono
                .flatMap(this::openChannel)
                .block();
        }

        private Mono<Channel> openChannel(Connection connection) {
            return Mono.fromCallable(connection::openChannel)
                .map(maybeChannel ->
                    maybeChannel.orElseThrow(() -> new RuntimeException("RabbitMQ reached to maximum opened channels, cannot get more channels")))
                .retryBackoff(MAX_RETRIES, RETRY_FIRST_BACK_OFF, FOREVER, Schedulers.elastic())
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

    private static final long MAXIMUM_BORROW_TIMEOUT_IN_MS = Duration.ofSeconds(5).toMillis();

    private final GenericObjectPool<Channel> pool;
    private final ChannelFactory channelFactory;

    ReactorRabbitMQChannelPool(Mono<Connection> connectionMono, int poolSize) {
        this.channelFactory = new ChannelFactory(connectionMono);

        GenericObjectPoolConfig<Channel> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(poolSize);
        this.pool = new GenericObjectPool<>(channelFactory, config);
    }

    @Override
    public Mono<? extends Channel> getChannelMono() {
        return Mono.fromCallable(() -> pool.borrowObject(MAXIMUM_BORROW_TIMEOUT_IN_MS));
    }

    @Override
    public BiConsumer<SignalType, Channel> getChannelCloseHandler() {
        return (signalType, channel) -> {
            if (!channel.isOpen() || signalType != SignalType.ON_COMPLETE) {
                invalidateObject(channel);
                return;
            }
            pool.returnObject(channel);
        };
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

    @Override
    public void close() {
        pool.close();
    }
}
