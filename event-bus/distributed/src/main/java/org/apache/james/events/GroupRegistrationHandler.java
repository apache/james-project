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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.event.json.EventSerializer;

import reactor.rabbitmq.Sender;

class GroupRegistrationHandler {
    private final Map<Group, GroupRegistration> groupRegistrations;
    private final EventSerializer eventSerializer;
    private final ReactorRabbitMQChannelPool channelPool;
    private final Sender sender;
    private final ReceiverProvider receiverProvider;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventDeadLetters eventDeadLetters;
    private final MailboxListenerExecutor mailboxListenerExecutor;

    GroupRegistrationHandler(EventSerializer eventSerializer, ReactorRabbitMQChannelPool channelPool, Sender sender, ReceiverProvider receiverProvider,
                             RetryBackoffConfiguration retryBackoff,
                             EventDeadLetters eventDeadLetters, MailboxListenerExecutor mailboxListenerExecutor) {
        this.eventSerializer = eventSerializer;
        this.channelPool = channelPool;
        this.sender = sender;
        this.receiverProvider = receiverProvider;
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.mailboxListenerExecutor = mailboxListenerExecutor;
        this.groupRegistrations = new ConcurrentHashMap<>();
    }

    GroupRegistration retrieveGroupRegistration(Group group) {
        return Optional.ofNullable(groupRegistrations.get(group))
            .orElseThrow(() -> new GroupRegistrationNotFound(group));
    }

    void stop() {
        groupRegistrations.values().forEach(GroupRegistration::unregister);
    }

    Registration register(EventListener.ReactiveEventListener listener, Group group) {
        return groupRegistrations
            .compute(group, (groupToRegister, oldGroupRegistration) -> {
                if (oldGroupRegistration != null) {
                    throw new GroupAlreadyRegistered(group);
                }
                return newGroupRegistration(listener, groupToRegister);
            })
            .start();
    }

    private GroupRegistration newGroupRegistration(EventListener.ReactiveEventListener listener, Group group) {
        return new GroupRegistration(
            channelPool, sender,
            receiverProvider,
            eventSerializer,
            listener,
            group,
            retryBackoff,
            eventDeadLetters,
            () -> groupRegistrations.remove(group),
            mailboxListenerExecutor);
    }
}