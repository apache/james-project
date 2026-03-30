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

import reactor.rabbitmq.QueueSpecification;

public record DefaultNamingStrategy(EventBusName eventBusName) implements NamingStrategy {
    @Override
    public RegistrationQueueName queueName(EventBusId eventBusId) {
        return new RegistrationQueueName(eventBusName.value() + "-eventbus-" + eventBusId.asString());
    }

    @Override
    public QueueSpecification deadLetterQueue() {
        return QueueSpecification.queue(eventBusName.value() + "-dead-letter-queue");
    }

    @Override
    public String exchange() {
        return eventBusName.value() + "-exchange";
    }

    @Override
    public String deadLetterExchange() {
        return eventBusName.value() + "-dead-letter-exchange";
    }

    @Override
    public GroupConsumerRetry.RetryExchangeName retryExchange(Group group) {
        return new GroupConsumerRetry.RetryExchangeName(eventBusName.value(), group);
    }

    @Override
    public GroupRegistration.WorkQueueName workQueue(Group group) {
        return new GroupRegistration.WorkQueueName(eventBusName.value(), group);
    }

    @Override
    public EventBusName getEventBusName() {
        return eventBusName;
    }
}
