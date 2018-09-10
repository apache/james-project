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

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

public class RabbitChannelPool {

    private static class ChannelBasePooledObjectFactory extends BasePooledObjectFactory<Channel> {
        private final Connection connection;

        public ChannelBasePooledObjectFactory(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Channel create() throws Exception {
            return connection.createChannel();
        }

        @Override
        public PooledObject<Channel> wrap(Channel obj) {
            return new DefaultPooledObject<>(obj);
        }
    }

    @FunctionalInterface
    public interface RabbitFunction<T, E extends Throwable> {
        T execute(Channel channel) throws E;
    }

    @FunctionalInterface
    public interface RabbitConsumer<E extends Throwable> {
        void execute(Channel channel) throws E;
    }

    private final ObjectPool<Channel> pool;

    public RabbitChannelPool(Connection connection) {
        pool = new GenericObjectPool<>(
            new ChannelBasePooledObjectFactory(connection));
    }

    public <T, E extends Throwable> T execute(RabbitFunction<T, E> f) throws E {
        Channel channel = borrowChannel();
        try {
            return f.execute(channel);
        } finally {
            returnChannel(channel);
        }
    }


    public <E extends Throwable> void execute(RabbitConsumer<E> f) throws E {
        Channel channel = borrowChannel();
        try {
            f.execute(channel);
        } finally {
            returnChannel(channel);
        }
    }

    private Channel borrowChannel() {
        try {
            return pool.borrowObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void returnChannel(Channel channel) {
        try {
            pool.returnObject(channel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
