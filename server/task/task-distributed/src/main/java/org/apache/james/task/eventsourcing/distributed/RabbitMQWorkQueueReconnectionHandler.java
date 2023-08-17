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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;

import javax.inject.Inject;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.task.eventsourcing.EventSourcingTaskManager;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;

public class RabbitMQWorkQueueReconnectionHandler implements SimpleConnectionPool.ReconnectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQWorkQueueReconnectionHandler.class);
    private final CancelRequestQueueName cancelRequestQueueName;
    private final EventSourcingTaskManager taskManager;
    private final RabbitMQConfiguration configuration;

    @Inject
    public RabbitMQWorkQueueReconnectionHandler(CancelRequestQueueName cancelRequestQueueName, EventSourcingTaskManager taskManager, RabbitMQConfiguration configuration) {
        this.cancelRequestQueueName = cancelRequestQueueName;
        this.taskManager = taskManager;
        this.configuration = configuration;
    }

    @Override
    public Publisher<Void> handleReconnection(Connection connection) {
        return Mono.fromRunnable(() -> createCancelQueue(connection))
            .then(Mono.fromRunnable(taskManager::restart));
    }

    private void createCancelQueue(Connection connection) {
        try (Channel channel = connection.createChannel()) {
            QueueArguments.Builder builder = QueueArguments.builder();
            configuration.getQueueTTL().ifPresent(builder::queueTTL);
            channel.queueDeclare(cancelRequestQueueName.asString(), !DURABLE, !EXCLUSIVE, !AUTO_DELETE, builder.build());
        } catch (Exception e) {
            LOGGER.error("Error recovering connection", e);
        }
    }
}
