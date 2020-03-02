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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.NO_ARGUMENTS;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.util.MDCBuilder;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

class GroupRegistration implements Registration {
    static class WorkQueueName {
        static WorkQueueName of(Group group) {
            return new WorkQueueName(group);
        }

        static final String MAILBOX_EVENT_WORK_QUEUE_PREFIX = MAILBOX_EVENT + "-workQueue-";

        private final Group group;

        private WorkQueueName(Group group) {
            Preconditions.checkNotNull(group, "Group must be specified");
            this.group = group;
        }

        String asString() {
            return MAILBOX_EVENT_WORK_QUEUE_PREFIX + group.asString();
        }
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
    private final Group group;
    private final MailboxListenerExecutor mailboxListenerExecutor;
    private Optional<Disposable> receiverSubscriber;

    GroupRegistration(Sender sender, ReceiverProvider receiverProvider, EventSerializer eventSerializer,
                      MailboxListener mailboxListener, Group group, RetryBackoffConfiguration retryBackoff,
                      EventDeadLetters eventDeadLetters,
                      Runnable unregisterGroup, MailboxListenerExecutor mailboxListenerExecutor) {
        this.eventSerializer = eventSerializer;
        this.mailboxListener = mailboxListener;
        this.queueName = WorkQueueName.of(group);
        this.sender = sender;
        this.receiver = receiverProvider.createReceiver();
        this.mailboxListenerExecutor = mailboxListenerExecutor;
        this.receiverSubscriber = Optional.empty();
        this.unregisterGroup = unregisterGroup;
        this.retryHandler = new GroupConsumerRetry(sender, group, retryBackoff, eventDeadLetters, eventSerializer);
        this.delayGenerator = WaitDelayGenerator.of(retryBackoff);
        this.group = group;
    }

    GroupRegistration start() {
        receiverSubscriber = Optional
            .of(createGroupWorkQueue()
                .then(retryHandler.createRetryExchange(queueName))
                .then(Mono.fromCallable(() -> this.consumeWorkQueue()))
                .block());
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

    private Disposable consumeWorkQueue() {
        return receiver.consumeManualAck(queueName.asString(), new ConsumeOptions().qos(EventBus.EXECUTION_RATE))
            .publishOn(Schedulers.parallel())
            .filter(delivery -> Objects.nonNull(delivery.getBody()))
            .flatMap(this::deliver)
            .subscribe();
    }

    private Mono<Void> deliver(AcknowledgableDelivery acknowledgableDelivery) {
        byte[] eventAsBytes = acknowledgableDelivery.getBody();
        Event event = eventSerializer.fromJson(new String(eventAsBytes, StandardCharsets.UTF_8)).get();
        int currentRetryCount = getRetryCount(acknowledgableDelivery);

        return delayGenerator.delayIfHaveTo(currentRetryCount)
            .publishOn(Schedulers.elastic())
            .flatMap(any -> Mono.fromRunnable(Throwing.runnable(() -> runListener(event))))
            .onErrorResume(throwable -> retryHandler.handleRetry(event, currentRetryCount, throwable))
            .then(Mono.fromRunnable(acknowledgableDelivery::ack));
    }

    Mono<Void> reDeliver(Event event) {
        return retryHandler.retryOrStoreToDeadLetter(event, DEFAULT_RETRY_COUNT);
    }

    private void runListener(Event event) throws Exception {
        mailboxListenerExecutor.execute(
            mailboxListener,
            MDCBuilder.create()
                .addContext(EventBus.StructuredLoggingFields.GROUP, group),
            event);
    }

    private int getRetryCount(AcknowledgableDelivery acknowledgableDelivery) {
        return Optional.ofNullable(acknowledgableDelivery.getProperties().getHeaders())
            .flatMap(headers -> Optional.ofNullable(headers.get(RETRY_COUNT)))
            .filter(object -> object instanceof Integer)
            .map(Integer.class::cast)
            .orElse(DEFAULT_RETRY_COUNT);
    }

    @Override
    public void unregister() {
        receiverSubscriber.filter(subscriber -> !subscriber.isDisposed())
            .ifPresent(Disposable::dispose);
        receiver.close();
        unregisterGroup.run();
    }
}