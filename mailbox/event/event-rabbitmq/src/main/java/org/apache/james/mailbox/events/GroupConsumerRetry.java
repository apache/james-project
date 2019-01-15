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

import static org.apache.james.backend.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backend.rabbitmq.Constants.DURABLE;
import static org.apache.james.backend.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.mailbox.events.GroupRegistration.RETRY_COUNT;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT;

import org.apache.james.mailbox.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

class GroupConsumerRetry {

    static class RetryExchangeName {

        static RetryExchangeName of(Group group) {
            return new RetryExchangeName(GroupRegistration.groupName(group.getClass()));
        }

        static final String MAILBOX_EVENT_RETRY_EXCHANGE_PREFIX = MAILBOX_EVENT + "-retryExchange-";

        private final String name;

        private RetryExchangeName(String name) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "Exchange name must be specified");
            this.name = name;
        }

        String asString() {
            return MAILBOX_EVENT_RETRY_EXCHANGE_PREFIX + name;
        }
    }

    static class RetryPublisher {

        private final Sender sender;
        private final RetryExchangeName retryExchangeName;
        private final RetryBackoffConfiguration retryBackoff;

        RetryPublisher(Sender sender, RetryExchangeName retryExchangeName, RetryBackoffConfiguration retryBackoff) {
            this.sender = sender;
            this.retryExchangeName = retryExchangeName;
            this.retryBackoff = retryBackoff;
        }

        Mono<Void> publish(Event event, byte[] eventAsByte, int currentRetryCount) {
            return sender.send(createRetryMessage(eventAsByte, currentRetryCount))
                .doOnError(throwable -> LOGGER.error("Exception happens when publishing event of user {} to retry exchange," +
                        "this event will be lost forever",
                    event.getUser().asString(), throwable));
        }

        private Mono<OutboundMessage> createRetryMessage(byte[] eventAsByte, int currentRetryCount) {
            if (currentRetryCount >= retryBackoff.getMaxRetries()) {
                return Mono.empty(); // will store event to deadletter latter
            }

            return Mono.just(new OutboundMessage(
                retryExchangeName.asString(),
                EMPTY_ROUTING_KEY,
                new AMQP.BasicProperties.Builder()
                    .headers(ImmutableMap.of(RETRY_COUNT, currentRetryCount + 1))
                    .build(),
                eventAsByte));
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupConsumerRetry.class);

    private final Sender sender;
    private final GroupRegistration.WorkQueueName queueName;
    private final RetryExchangeName retryExchangeName;
    private final RetryPublisher retryPublisher;

    GroupConsumerRetry(Sender sender, GroupRegistration.WorkQueueName queueName, Group group,
                       RetryBackoffConfiguration retryBackoff) {
        this.sender = sender;
        this.queueName = queueName;
        this.retryExchangeName = RetryExchangeName.of(group);
        this.retryPublisher = new RetryPublisher(sender, retryExchangeName, retryBackoff);
    }

    Mono<Void> createRetryExchange() {
        return Flux.concat(
            sender.declareExchange(ExchangeSpecification.exchange(retryExchangeName.asString())
                .durable(DURABLE)
                .type(DIRECT_EXCHANGE)),
            sender.bind(BindingSpecification.binding()
                .exchange(retryExchangeName.asString())
                .queue(queueName.asString())
                .routingKey(EMPTY_ROUTING_KEY)))
            .then();
    }

    Mono<Void> handleRetry(byte[] eventAsBytes, Event event, int currentRetryCount, Throwable throwable) {
        LOGGER.error("Exception happens when handling event {} of user {}",
            event.getEventId().getId().toString(), event.getUser().asString(), throwable);

        return retryPublisher.publish(event, eventAsBytes, currentRetryCount);
    }
}
