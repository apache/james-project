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

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.queue.api.MailQueueFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;


public class RabbitMQMailQueueFactory implements MailQueueFactory<RabbitMQMailQueue> {

    private static final String ROUTING_KEY = "";
    private static final boolean RABBIT_OPTION_DURABLE = true;
    private static final boolean RABBIT_OPTION_EXCLUSIVE = true;
    private static final boolean RABBIT_OPTION_AUTO_DELETE = true;
    private static final ImmutableMap<String, Object> RABBIT_OPTION_NO_ARGUMENTS = ImmutableMap.of();

    private final Channel channel;
    private final RabbitMQManagementApi mqManagementApi;

    @VisibleForTesting
    @Inject
    RabbitMQMailQueueFactory(Connection connection, RabbitMQManagementApi mqManagementApi) throws IOException {
        this.channel = connection.createChannel();
        this.mqManagementApi = mqManagementApi;
    }

    @Override
    public Optional<RabbitMQMailQueue> getQueue(String name) {
        return getQueue(MailQueueName.fromString(name));
    }

    @Override
    public RabbitMQMailQueue createQueue(String name) {
        MailQueueName mailQueueName = MailQueueName.fromString(name);
        return getQueue(mailQueueName)
            .orElseGet(() -> attemptQueueCreation(mailQueueName));
    }

    @Override
    public Set<RabbitMQMailQueue> listCreatedMailQueues() {
        return mqManagementApi.listCreatedMailQueueNames()
            .map(RabbitMQMailQueue::new)
            .collect(Guavate.toImmutableSet());
    }

    private Optional<RabbitMQMailQueue> getQueue(MailQueueName name) {
        return mqManagementApi.listCreatedMailQueueNames()
            .filter(name::equals)
            .map(RabbitMQMailQueue::new)
            .findFirst();
    }

    private RabbitMQMailQueue attemptQueueCreation(MailQueueName name) {
        try {
            channel.exchangeDeclare(name.toRabbitExchangeName().asString(), "direct", RABBIT_OPTION_DURABLE);
            channel.queueDeclare(name.toWorkQueueName().asString(), RABBIT_OPTION_DURABLE, !RABBIT_OPTION_EXCLUSIVE, !RABBIT_OPTION_AUTO_DELETE, RABBIT_OPTION_NO_ARGUMENTS);
            channel.queueBind(name.toWorkQueueName().asString(), name.toRabbitExchangeName().asString(), ROUTING_KEY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new RabbitMQMailQueue(name);
    }

}
