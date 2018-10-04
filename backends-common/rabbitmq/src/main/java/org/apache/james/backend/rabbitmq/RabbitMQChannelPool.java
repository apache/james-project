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

import com.rabbitmq.client.Channel;

public interface RabbitMQChannelPool {
    class ConnectionFailedException extends RuntimeException {
        public ConnectionFailedException(Throwable cause) {
            super(cause);
        }
    }

    @FunctionalInterface
    interface RabbitFunction<T, E extends Throwable> {
        T execute(Channel channel) throws E;
    }

    @FunctionalInterface
    interface RabbitConsumer<E extends Throwable> {
        void execute(Channel channel) throws E;
    }

    <T, E extends Throwable> T execute(RabbitFunction<T, E> f)
        throws E, ConnectionFailedException;


    <E extends Throwable> void execute(RabbitConsumer<E> f)
        throws E, ConnectionFailedException;
}
