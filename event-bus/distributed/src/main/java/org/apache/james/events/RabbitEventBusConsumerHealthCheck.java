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

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.Channel;

import reactor.core.publisher.Mono;

public class RabbitEventBusConsumerHealthCheck implements HealthCheck {
    public static final String COMPONENT = "EventbusConsumers";

    private final RabbitMQEventBus eventBus;
    private final NamingStrategy namingStrategy;
    private final SimpleConnectionPool connectionPool;

    public RabbitEventBusConsumerHealthCheck(RabbitMQEventBus eventBus, NamingStrategy namingStrategy,
                                             SimpleConnectionPool connectionPool) {
        this.eventBus = eventBus;
        this.namingStrategy = namingStrategy;
        this.connectionPool = connectionPool;
    }

    @Override
    public ComponentName componentName() {
        return new ComponentName(COMPONENT + "-" + namingStrategy.getEventBusName().value());
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
        Stream<Group> groups = Stream.concat(
            eventBus.listRegisteredGroups().stream(),
            Stream.of(new GroupRegistrationHandler.GroupRegistrationHandlerGroup()));

        Optional<String> queueWithoutConsumers = groups
            .map(namingStrategy::workQueue)
            .map(GroupRegistration.WorkQueueName::asString)
            .filter(Throwing.predicate(queue -> channel.consumerCount(queue) == 0))
            .findAny();

        if (queueWithoutConsumers.isPresent()) {
            eventBus.restart();
            return Result.degraded(componentName(), "No consumers on " + queueWithoutConsumers.get());
        } else {
            return Result.healthy(componentName());
        }
    }
}
