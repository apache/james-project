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

import static org.apache.james.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.Sender;

class RegistrationBinder {
    private final Sender sender;
    private final RegistrationQueueName registrationQueue;

    RegistrationBinder(Sender sender, RegistrationQueueName registrationQueue) {
        this.sender = sender;
        this.registrationQueue = registrationQueue;
    }

    Mono<Void> bind(RegistrationKey key) {
        return sender.bind(bindingSpecification(key))
            .then();
    }

    Mono<Void> unbind(RegistrationKey key) {
        return sender.unbind(bindingSpecification(key))
            .then();
    }

    private BindingSpecification bindingSpecification(RegistrationKey key) {
        RoutingKeyConverter.RoutingKey routingKey = RoutingKeyConverter.RoutingKey.of(key);
        return BindingSpecification.binding()
            .exchange(MAILBOX_EVENT_EXCHANGE_NAME)
            .queue(registrationQueue.asString())
            .routingKey(routingKey.asString());
    }
}