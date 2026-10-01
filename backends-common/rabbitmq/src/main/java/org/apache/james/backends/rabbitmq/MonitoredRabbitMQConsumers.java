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

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import com.rabbitmq.client.Connection;

/**
 * RabbitMQ consumers watched by {@link RabbitMQConsumersHealthCheck}: they are restarted when one of their queues has
 * no consumer.
 */
public interface MonitoredRabbitMQConsumers {
    static MonitoredRabbitMQConsumers of(String name, SimpleConnectionPool connectionPool,
                                         Supplier<List<String>> queues, Function<Connection, Publisher<Void>> restart) {
        return new MonitoredRabbitMQConsumers() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public SimpleConnectionPool connectionPool() {
                return connectionPool;
            }

            @Override
            public List<String> queues() {
                return queues.get();
            }

            @Override
            public Publisher<Void> restart(Connection connection) {
                return restart.apply(connection);
            }
        };
    }

    /**
     * Shown in the health check cause.
     */
    String name();

    /**
     * Connection pool of the RabbitMQ server hosting the queues.
     */
    SimpleConnectionPool connectionPool();

    /**
     * Called on each check, as the monitored queues can change over time.
     */
    List<String> queues();

    /**
     * Restarts the consumers, given the connection used by the check.
     */
    Publisher<Void> restart(Connection connection);
}
