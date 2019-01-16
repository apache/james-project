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

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.rabbitmq.client.Connection;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
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
            return new WorkQueueName(groupName(clazz));
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

    static String groupName(Class<? extends Group> clazz) {
        return clazz.getName();
    }

    static final String RETRY_COUNT = "retry-count";
    static final int DEFAULT_RETRY_COUNT = 0;

    private final MailboxListener mailboxListener;
    private final WorkQueueName queueName;
    private final Receiver receiver;
    private final Runnable unregisterGroup;
    private final Sender sender;
    private final EventSerializer eventSerializer;
    private final GroupConsumerRetry retryHandler;
    private final WaitDelayGenerator delayGenerator;
    private final RetryBackoffConfiguration retryBackoff;
    private Optional<Disposable> receiverSubscriber;

    GroupRegistration(Mono<Connection> connectionSupplier, Sender sender, EventSerializer eventSerializer,
                      MailboxListener mailboxListener, Group group, RetryBackoffConfiguration retryBackoff,
                      Runnable unregisterGroup) {
        this.eventSerializer = eventSerializer;
        this.mailboxListener = mailboxListener;
        this.queueName = WorkQueueName.of(group);
        this.sender = sender;
        this.receiver = RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionSupplier));
        this.receiverSubscriber = Optional.empty();
        this.unregisterGroup = unregisterGroup;
        this.retryHandler = new GroupConsumerRetry(sender, queueName, group, retryBackoff);
        this.retryBackoff = retryBackoff;
        this.delayGenerator = WaitDelayGenerator.of(retryBackoff);
    }

    GroupRegistration start() {
        createGroupWorkQueue()
            .then(retryHandler.createRetryExchange())
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
        receiverSubscriber = Optional.of(receiver.consumeManualAck(queueName.asString())
            .subscribeOn(Schedulers.parallel())
            .filter(delivery -> Objects.nonNull(delivery.getBody()))
            .flatMap(this::deliver)
            .subscribeOn(Schedulers.elastic())
            .subscribe());
    }

    private Mono<Void> deliver(AcknowledgableDelivery acknowledgableDelivery) {
        byte[] eventAsBytes = acknowledgableDelivery.getBody();
        Event event = eventSerializer.fromJson(new String(eventAsBytes, StandardCharsets.UTF_8)).get();
        int currentRetryCount = getRetryCount(acknowledgableDelivery);

        return delayGenerator.delayIfHaveTo(currentRetryCount)
            .flatMap(any -> Mono.fromRunnable(Throwing.runnable(() -> mailboxListener.event(event))))
            .onErrorResume(throwable -> retryHandler.handleRetry(eventAsBytes, event, currentRetryCount, throwable))
            .then(Mono.fromRunnable(acknowledgableDelivery::ack))
            .subscribeWith(MonoProcessor.create())
            .then();
    }

    static int getRetryCount(AcknowledgableDelivery acknowledgableDelivery) {
        return Optional.ofNullable(acknowledgableDelivery.getProperties().getHeaders())
            .flatMap(headers -> Optional.ofNullable(headers.get(RETRY_COUNT)))
            .filter(object -> object instanceof Integer)
            .map(object -> (Integer) object)
            .orElse(DEFAULT_RETRY_COUNT);
    }

    @Override
    public void unregister() {
        receiverSubscriber.filter(subscriber -> !subscriber.isDisposed())
            .ifPresent(subscriber -> subscriber.dispose());
        receiver.close();
        unregisterGroup.run();
    }
}