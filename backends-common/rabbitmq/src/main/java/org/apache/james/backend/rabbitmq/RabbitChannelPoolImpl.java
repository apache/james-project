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

import java.io.IOException;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.james.util.MemoizedSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

public class RabbitChannelPoolImpl implements RabbitMQChannelPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitChannelPoolImpl.class);

    private static class ChannelBasePooledObjectFactory extends BasePooledObjectFactory<Channel> {
        private final MemoizedSupplier<Connection> rabbitConnectionSupplier;

        public ChannelBasePooledObjectFactory(RabbitMQConnectionFactory factory) {
            this.rabbitConnectionSupplier = MemoizedSupplier.of(
                    Throwing.supplier(factory::create).sneakyThrow());
        }

        @Override
        public Channel create() throws Exception {
            return rabbitConnectionSupplier.get()
                    .createChannel();
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

        private void closeRabbitConnectionIfInitialized() {
            rabbitConnectionSupplier.ifInitialized(
                Throwing.consumer(this::closeRabbitConnection).sneakyThrow());
        }

        private void closeRabbitConnection(Connection connection) throws IOException {
            if (connection.isOpen()) {
                connection.close();
            }
        }
    }

    private final ObjectPool<Channel> pool;
    private final ChannelBasePooledObjectFactory pooledChannelsFactory;

    @Inject
    public RabbitChannelPoolImpl(RabbitMQConnectionFactory factory) {
        pooledChannelsFactory = new ChannelBasePooledObjectFactory(factory);
        pool = new GenericObjectPool<>(pooledChannelsFactory);
    }

    @Override
    public <T, E extends Throwable> T execute(RabbitFunction<T, E> f) throws E, ConnectionFailedException {
        Channel channel = borrowChannel();
        try {
            return f.execute(channel);
        } finally {
            returnChannel(channel);
        }
    }

    @Override
    public <E extends Throwable> void execute(RabbitConsumer<E> f) throws E, ConnectionFailedException {
        Channel channel = borrowChannel();
        try {
            f.execute(channel);
        } finally {
            returnChannel(channel);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        try {
            pool.close();
            pooledChannelsFactory.closeRabbitConnectionIfInitialized();
        } catch (Exception e) {
            LOGGER.error("error while closing rabbit channels & connections", e);
        }
    }

    private Channel borrowChannel() {
        try {
            return pool.borrowObject();
        } catch (Exception e) {
            throw new ConnectionFailedException(e);
        }
    }

    private void returnChannel(Channel channel) {
        try {
            pool.returnObject(channel);
        } catch (Exception ignore) {
            //ignore when return is failing
        }
    }

}
