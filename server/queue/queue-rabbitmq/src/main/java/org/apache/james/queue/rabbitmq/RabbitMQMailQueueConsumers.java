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

package org.apache.james.queue.rabbitmq;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.MonitoredRabbitMQConsumers;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Flux;

/**
 * The consumers of the mail queues. Restarting them runs every {@link SimpleConnectionPool.ReconnectionHandler}.
 */
public class RabbitMQMailQueueConsumers implements MonitoredRabbitMQConsumers {
    private final RabbitMQMailQueueFactory queueFactory;
    private final Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers;
    private final SimpleConnectionPool connectionPool;

    @Inject
    public RabbitMQMailQueueConsumers(RabbitMQMailQueueFactory queueFactory, Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers,
                                      SimpleConnectionPool connectionPool) {
        this.queueFactory = queueFactory;
        this.reconnectionHandlers = reconnectionHandlers;
        this.connectionPool = connectionPool;
    }

    @Override
    public String name() {
        return "mail queues";
    }

    @Override
    public SimpleConnectionPool connectionPool() {
        return connectionPool;
    }

    @Override
    public List<String> queues() {
        return queueFactory.listCreatedMailQueues()
            .stream()
            .map(org.apache.james.queue.api.MailQueueName::asString)
            .map(MailQueueName::fromString)
            .map(mailQueueName -> mailQueueName.toWorkQueueName().asString())
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Publisher<Void> restart(Connection connection) {
        return Flux.fromIterable(reconnectionHandlers)
            .concatMap(reconnectionHandler -> reconnectionHandler.handleReconnection(connection))
            .then();
    }
}
