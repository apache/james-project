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

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class RabbitMQDeadLetterQueuesHealthCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("RabbitMQDeadLetterQueues");
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQDeadLetterQueuesHealthCheck.class);
    private static final String DEFAULT_VHOST = "/";

    private final ImmutableList<MonitoredDeadLetterQueue> deadLetterQueues;
    private final Map<RabbitMQConfiguration, RabbitMQManagementAPI> managementAPIs;

    @Inject
    public RabbitMQDeadLetterQueuesHealthCheck(Set<MonitoredDeadLetterQueue> deadLetterQueues) {
        this.deadLetterQueues = ImmutableList.copyOf(deadLetterQueues);
        this.managementAPIs = deadLetterQueues.stream()
            .map(MonitoredDeadLetterQueue::configuration)
            .distinct()
            .collect(ImmutableMap.toImmutableMap(Function.identity(), RabbitMQManagementAPI::from));
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Flux.fromIterable(deadLetterQueues)
            .flatMapSequential(this::check, DEFAULT_CONCURRENCY)
            .collectList()
            .map(results -> MergedResults.merge(COMPONENT_NAME, results));
    }

    private Mono<Result> check(MonitoredDeadLetterQueue deadLetterQueue) {
        return Mono.fromCallable(() -> queueLength(deadLetterQueue))
            .map(queueLength -> {
                if (queueLength != 0) {
                    return Result.degraded(COMPONENT_NAME, String.format("RabbitMQ dead letter queue %s contains %d messages. This might indicate transient failure on processing.",
                        deadLetterQueue.queue(), queueLength));
                }
                return Result.healthy(COMPONENT_NAME);
            })
            .onErrorResume(e -> {
                LOGGER.warn("Error checking RabbitMQ dead letter queue {}", deadLetterQueue.queue(), e);
                return Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking RabbitMQ dead letter queue " + deadLetterQueue.queue(), e));
            })
            .subscribeOn(Schedulers.boundedElastic()); // Reading the management API is blocking: each queue on its own thread
    }

    private long queueLength(MonitoredDeadLetterQueue deadLetterQueue) {
        RabbitMQConfiguration configuration = deadLetterQueue.configuration();
        return managementAPIs.get(configuration)
            .queueDetails(configuration.getVhost().orElse(DEFAULT_VHOST), deadLetterQueue.queue())
            .getQueueLength();
    }
}
