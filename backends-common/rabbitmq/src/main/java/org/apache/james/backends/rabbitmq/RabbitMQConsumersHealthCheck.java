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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RabbitMQConsumersHealthCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("RabbitMQConsumers");
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQConsumersHealthCheck.class);

    private final ImmutableList<MonitoredRabbitMQConsumers> monitoredConsumers;

    @Inject
    public RabbitMQConsumersHealthCheck(Set<MonitoredRabbitMQConsumers> monitoredConsumers) {
        this.monitoredConsumers = ImmutableList.copyOf(monitoredConsumers);
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Flux.fromIterable(monitoredConsumers)
            .flatMap(this::detect, DEFAULT_CONCURRENCY)
            .concatMap(Function.identity())
            .collectList()
            .map(results -> MergedResults.merge(COMPONENT_NAME, results));
    }

    /**
     * Detections run concurrently, each on its own thread, so that an unresponsive RabbitMQ server does not delay the
     * other consumers. The returned result restarts the consumers once subscribed, and {@link #check()} subscribes to
     * those one at a time: restarting the same component concurrently is not safe.
     */
    private Mono<Mono<Result>> detect(MonitoredRabbitMQConsumers consumers) {
        return consumers.connectionPool().getResilientConnection()
            .flatMap(connection -> Mono.fromCallable(() -> queueWithoutConsumers(consumers, connection))
                .map(queueWithoutConsumers -> queueWithoutConsumers
                    .map(queue -> restart(consumers, connection, queue))
                    .orElseGet(() -> Mono.just(Result.healthy(COMPONENT_NAME)))))
            .onErrorResume(e -> Mono.just(Mono.just(unhealthy(consumers, e))))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private Mono<Result> restart(MonitoredRabbitMQConsumers consumers, Connection connection, String queue) {
        return Mono.defer(() -> {
                LOGGER.warn("No consumers on {} of {}, restarting them", queue, consumers.name());
                return Mono.from(consumers.restart(connection));
            })
            // Restarts may block, and the previous restart may have completed on a non-blocking thread
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .thenReturn(Result.degraded(COMPONENT_NAME, String.format("No consumers on %s of %s", queue, consumers.name())))
            .onErrorResume(e -> Mono.just(unhealthy(consumers, e)));
    }

    private Result unhealthy(MonitoredRabbitMQConsumers consumers, Throwable error) {
        LOGGER.warn("Error checking the consumers of {}", consumers.name(), error);
        return Result.unhealthy(COMPONENT_NAME, "Error checking the consumers of " + consumers.name(), error);
    }

    private Optional<String> queueWithoutConsumers(MonitoredRabbitMQConsumers consumers, Connection connection) throws IOException, TimeoutException {
        try (Channel channel = connection.createChannel()) {
            return consumers.queues().stream()
                .filter(Throwing.predicate(queue -> channel.consumerCount(queue) == 0))
                .findFirst();
        }
    }
}
