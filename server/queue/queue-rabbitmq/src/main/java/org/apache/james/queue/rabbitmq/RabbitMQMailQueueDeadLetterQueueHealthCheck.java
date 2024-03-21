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

import static org.apache.james.queue.api.MailQueueFactory.SPOOL;

import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQManagementAPI;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class RabbitMQMailQueueDeadLetterQueueHealthCheck implements HealthCheck {
    public static final MailQueueName JAMES_MAIL_QUEUE_NAME = MailQueueName.fromString(SPOOL.asString());
    public static final ComponentName COMPONENT_NAME = new ComponentName("RabbitMQMailQueueDeadLetterQueueHealthCheck");
    private static final String DEFAULT_VHOST = "/";

    private final RabbitMQConfiguration configuration;
    private final RabbitMQManagementAPI api;

    @Inject
    public RabbitMQMailQueueDeadLetterQueueHealthCheck(RabbitMQConfiguration configuration) {
        this.configuration = configuration;
        this.api = RabbitMQManagementAPI.from(configuration);
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Mono.fromCallable(() -> api.queueDetails(configuration.getVhost().orElse(DEFAULT_VHOST), JAMES_MAIL_QUEUE_NAME.toDeadLetterQueueName()))
            .map(queueDetails -> {
                if (queueDetails.getQueueLength() != 0) {
                    return Result.degraded(COMPONENT_NAME, "RabbitMQ dead letter queue of the mail queue contain messages. This might indicate transient failure on mail processing.");
                }
                return Result.healthy(COMPONENT_NAME);
            })
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking RabbitMQMailQueueDeadLetterQueueHealthCheck", e)))
            .subscribeOn(Schedulers.boundedElastic());
    }
}
