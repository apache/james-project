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

import java.util.Set;

import javax.inject.Inject;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class RabbitMQMailQueueConsumerHealthCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("RabbitMQMailQueueConsumersHealthCheck");
    public static final ComponentName COMPONENT = new ComponentName("MailQueueConsumers");

    private final RabbitMQMailQueueFactory queueFactory;
    private final Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers;
    private final SimpleConnectionPool connectionPool;

    @Inject
    public RabbitMQMailQueueConsumerHealthCheck(RabbitMQMailQueueFactory queueFactory, Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers, SimpleConnectionPool connectionPool) {
        this.queueFactory = queueFactory;
        this.reconnectionHandlers = reconnectionHandlers;
        this.connectionPool = connectionPool;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return connectionPool.getResilientConnection()
            .flatMap(connection -> Mono.using(connection::createChannel,
                channel -> check(connection, channel),
                Throwing.consumer(Channel::close)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Result> check(Connection connection, Channel channel) {
        boolean queueWithoutConsumers = queueFactory.listCreatedMailQueues()
            .stream()
            .map(org.apache.james.queue.api.MailQueueName::asString)
            .map(MailQueueName::fromString)
            .map(m -> m.toWorkQueueName().asString())
            .anyMatch(Throwing.predicate(queue -> channel.consumerCount(queue) == 0));

        if (queueWithoutConsumers) {
            return Mono.fromRunnable(() -> reconnectionHandlers.forEach(r -> r.handleReconnection(connection)))
                .thenReturn(Result.degraded(COMPONENT, "No consumers"));
        } else {
            return Mono.just(Result.healthy(COMPONENT));
        }
    }
}
