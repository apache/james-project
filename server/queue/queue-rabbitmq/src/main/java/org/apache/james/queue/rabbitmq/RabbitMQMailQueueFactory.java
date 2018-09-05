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

public class RabbitMQMailQueueFactory implements MailQueueFactory<RabbitMQMailQueue> {
    private final RabbitClient rabbitClient;
    private final RabbitMQManagementApi mqManagementApi;
    private final RabbitMQMailQueue.Factory mailQueueFactory;

    @VisibleForTesting
    @Inject
    RabbitMQMailQueueFactory(RabbitClient rabbitClient, RabbitMQManagementApi mqManagementApi, RabbitMQMailQueue.Factory mailQueueFactory) throws IOException {
        this.rabbitClient = rabbitClient;
        this.mqManagementApi = mqManagementApi;
        this.mailQueueFactory = mailQueueFactory;
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
            .map(mailQueueFactory::create)
            .collect(Guavate.toImmutableSet());
    }

    private RabbitMQMailQueue attemptQueueCreation(MailQueueName mailQueueName) {
        rabbitClient.attemptQueueCreation(mailQueueName);
        return mailQueueFactory.create(mailQueueName);
    }

    private Optional<RabbitMQMailQueue> getQueue(MailQueueName name) {
        return mqManagementApi.listCreatedMailQueueNames()
            .filter(name::equals)
            .map(mailQueueFactory::create)
            .findFirst();
    }
}
