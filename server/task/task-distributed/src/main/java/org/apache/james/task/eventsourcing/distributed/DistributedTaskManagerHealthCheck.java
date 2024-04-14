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

package org.apache.james.task.eventsourcing.distributed;

import static org.apache.james.task.eventsourcing.distributed.RabbitMQWorkQueue.QUEUE_NAME;

import java.io.IOException;

import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.task.eventsourcing.EventSourcingTaskManager;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.Channel;

import reactor.core.publisher.Mono;

public class DistributedTaskManagerHealthCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("DistributedTaskManagerConsumersHealthCheck");
    public static final ComponentName COMPONENT = new ComponentName("DistributedTaskManagerConsumers");

    private final EventSourcingTaskManager taskManager;
    private final SimpleConnectionPool connectionPool;

    @Inject
    public DistributedTaskManagerHealthCheck(EventSourcingTaskManager taskManager, SimpleConnectionPool connectionPool) {
        this.taskManager = taskManager;
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

    private Result check(Channel channel) throws IOException {
        if (channel.consumerCount(QUEUE_NAME) == 0) {
            taskManager.restart();

            return Result.degraded(COMPONENT, "No consumers");
        } else {
            return Result.healthy(COMPONENT);
        }
    }
}
