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

package org.apache.james.mailbox.events;

import static org.apache.james.backend.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backend.rabbitmq.Constants.DURABLE;
import static org.apache.james.backend.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backend.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backend.rabbitmq.Constants.NO_ARGUMENTS;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;

import play.api.libs.json.JsResult;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;

class GroupRegistration implements Registration {
    static class WorkQueueName {
        @VisibleForTesting
        static WorkQueueName of(Class<? extends Group> clazz) {
            return new WorkQueueName(clazz.getName());
        }

        static WorkQueueName of(Group group) {
            return of(group.getClass());
        }

        static final String MAILBOX_EVENT_WORK_QUEUE_PREFIX = MAILBOX_EVENT + "-workQueue-";

        private final String name;

        private WorkQueueName(String name) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "Queue name must be specified");
            this.name = name;
        }

        String asString() {
            return MAILBOX_EVENT_WORK_QUEUE_PREFIX + name;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupRegistration.class);

    private final MailboxListener mailboxListener;
    private final WorkQueueName queueName;
    private final Receiver receiver;
    private final Runnable unregisterGroup;
    private final Sender sender;
    private final EventSerializer eventSerializer;
    private Optional<Disposable> receiverSubscriber;

    GroupRegistration(Mono<Connection> connectionSupplier, Sender sender, EventSerializer eventSerializer,
                              MailboxListener mailboxListener, Group group, Runnable unregisterGroup) {
        this.eventSerializer = eventSerializer;
        this.mailboxListener = mailboxListener;
        this.queueName = WorkQueueName.of(group);
        this.sender = sender;
        this.receiver = RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionSupplier));
        this.receiverSubscriber = Optional.empty();
        this.unregisterGroup = unregisterGroup;
    }

    GroupRegistration start() {
        createGroupWorkQueue()
            .doOnSuccess(any -> this.subscribeWorkQueue())
            .block();
        return this;
    }

    private Mono<Void> createGroupWorkQueue() {
        return Flux.concat(
            sender.declareQueue(QueueSpecification.queue(queueName.asString())
                .durable(DURABLE)
                .exclusive(!EXCLUSIVE)
                .autoDelete(!AUTO_DELETE)
                .arguments(NO_ARGUMENTS)),
            sender.bind(BindingSpecification.binding()
                .exchange(MAILBOX_EVENT_EXCHANGE_NAME)
                .queue(queueName.asString())
                .routingKey(EMPTY_ROUTING_KEY)))
            .then();
    }

    private void subscribeWorkQueue() {
        receiverSubscriber = Optional.of(receiver.consumeAutoAck(queueName.asString())
            .subscribeOn(Schedulers.parallel())
            .map(Delivery::getBody)
            .filter(Objects::nonNull)
            .map(eventInBytes -> new String(eventInBytes, StandardCharsets.UTF_8))
            .map(eventSerializer::fromJson)
            .map(JsResult::get)
            .subscribeOn(Schedulers.elastic())
            .subscribe(event -> deliverEvent(mailboxListener, event)));
    }

    private void deliverEvent(MailboxListener mailboxListener, Event event) {
        try {
            mailboxListener.event(event);
        } catch (Exception e) {
            LOGGER.error("Exception happens when handling event of user {}", event.getUser().asString(), e);
        }
    }

    @Override
    public void unregister() {
        receiverSubscriber.filter(subscriber -> !subscriber.isDisposed())
            .ifPresent(subscriber -> subscriber.dispose());
        receiver.close();
        unregisterGroup.run();
    }
}