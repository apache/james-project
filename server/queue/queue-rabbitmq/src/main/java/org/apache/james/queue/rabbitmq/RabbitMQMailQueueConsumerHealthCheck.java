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
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.Channel;

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
            .map(Throwing.function(connection -> {
                try (Channel channel = connection.createChannel()) {
                    return check(channel);
                }
            })).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private Result check(Channel channel) {
        boolean queueWithoutConsumers = queueFactory.listCreatedMailQueues()
            .stream()
            .map(org.apache.james.queue.api.MailQueueName::asString)
            .map(MailQueueName::fromString)
            .map(m -> m.toWorkQueueName().asString())
            .anyMatch(Throwing.predicate(queue -> channel.consumerCount(queue) == 0));

        if (queueWithoutConsumers) {
            reconnectionHandlers.forEach(r -> connectionPool.getResilientConnection()
                .flatMap(c -> Mono.from(r.handleReconnection(c)))
                .subscribeOn(Schedulers.boundedElastic())
                .block());

            return Result.degraded(COMPONENT, "No consumers");
        } else {
            return Result.healthy(COMPONENT);
        }
    }
}
