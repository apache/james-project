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

import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQManagementAPI;

import com.google.common.annotations.VisibleForTesting;

public class RabbitMQMailQueueManagement {

    private final RabbitMQManagementAPI api;
    private final Optional<String> vhost;

    @Inject
    RabbitMQMailQueueManagement(RabbitMQConfiguration configuration) {
        this.api = RabbitMQManagementAPI.from(configuration);
        this.vhost = configuration.getVhost();
    }

    @VisibleForTesting
    RabbitMQMailQueueManagement(RabbitMQManagementAPI api) {
        this.api = api;
        this.vhost = Optional.empty();
    }

    Stream<MailQueueName> listCreatedMailQueueNames() {
        return vhost.map(api::listVhostQueues)
            .orElse(api.listQueues())
            .stream()
            .map(RabbitMQManagementAPI.MessageQueue::getName)
            .map(MailQueueName::fromRabbitWorkQueueName)
            .flatMap(Optional::stream)
            .distinct();
    }

    @VisibleForTesting
    public void deleteAllQueues() {
        api.listQueues()
            .forEach(queue -> api.deleteQueue("/", queue.getName()));
    }
}