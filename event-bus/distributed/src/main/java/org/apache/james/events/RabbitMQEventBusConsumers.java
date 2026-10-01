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

package org.apache.james.events;

import java.util.List;
import java.util.stream.Stream;

import org.apache.james.backends.rabbitmq.MonitoredRabbitMQConsumers;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;

/**
 * The group consumers of a RabbitMQ event bus, restarted with the event bus.
 */
public class RabbitMQEventBusConsumers implements MonitoredRabbitMQConsumers {
    private final EventBus eventBus;
    private final NamingStrategy namingStrategy;
    private final SimpleConnectionPool connectionPool;
    private final Group groupRegistrationHandlerGroup;

    public RabbitMQEventBusConsumers(EventBus eventBus, NamingStrategy namingStrategy,
                                     SimpleConnectionPool connectionPool,
                                     Group groupRegistrationHandlerGroup) {
        this.eventBus = eventBus;
        this.namingStrategy = namingStrategy;
        this.connectionPool = connectionPool;
        this.groupRegistrationHandlerGroup = groupRegistrationHandlerGroup;
    }

    @Override
    public String name() {
        return namingStrategy.getEventBusName().value() + " event bus";
    }

    @Override
    public SimpleConnectionPool connectionPool() {
        return connectionPool;
    }

    @Override
    public List<String> queues() {
        return Stream.concat(
                eventBus.listRegisteredGroups().stream(),
                Stream.of(groupRegistrationHandlerGroup))
            .map(namingStrategy::workQueue)
            .map(GroupRegistration.WorkQueueName::asString)
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Publisher<Void> restart(Connection connection) {
        return Mono.fromRunnable(eventBus::restart);
    }
}
