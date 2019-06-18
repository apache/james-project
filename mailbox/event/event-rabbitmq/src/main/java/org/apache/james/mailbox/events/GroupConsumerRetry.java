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

import java.nio.charset.StandardCharsets;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.StructuredLogger;
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
            return new RetryExchangeName(group.asString());
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

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupConsumerRetry.class);

    private final Sender sender;
    private final RetryExchangeName retryExchangeName;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventDeadLetters eventDeadLetters;
    private final Group group;
    private final EventSerializer eventSerializer;

    GroupConsumerRetry(Sender sender, Group group, RetryBackoffConfiguration retryBackoff,
                       EventDeadLetters eventDeadLetters, EventSerializer eventSerializer) {
        this.sender = sender;
        this.retryExchangeName = RetryExchangeName.of(group);
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.group = group;
        this.eventSerializer = eventSerializer;
    }

    Mono<Void> createRetryExchange(GroupRegistration.WorkQueueName queueName) {
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

    Mono<Void> handleRetry(Event event, int currentRetryCount, Throwable throwable) {
        createStructuredLogger(event).log(logger -> logger.error("Exception happens when handling event after {} retries", currentRetryCount, throwable));

        return retryOrStoreToDeadLetter(event, currentRetryCount);
    }

    Mono<Void> retryOrStoreToDeadLetter(Event event, int currentRetryCount) {
        if (currentRetryCount >= retryBackoff.getMaxRetries()) {
            return eventDeadLetters.store(group, event, EventDeadLetters.InsertionId.random());
        }
        return sendRetryMessage(event, currentRetryCount);
    }

    private Mono<Void> sendRetryMessage(Event event, int currentRetryCount) {
        byte[] eventAsBytes = eventSerializer.toJson(event).getBytes(StandardCharsets.UTF_8);

        Mono<OutboundMessage> retryMessage = Mono.just(new OutboundMessage(
            retryExchangeName.asString(),
            EMPTY_ROUTING_KEY,
            new AMQP.BasicProperties.Builder()
                .headers(ImmutableMap.of(RETRY_COUNT, currentRetryCount + 1))
                .build(),
            eventAsBytes));

        return sender.send(retryMessage)
            .doOnError(throwable -> createStructuredLogger(event)
                .log(logger -> logger.error("Exception happens when publishing event to retry exchange, this event will be stored in deadLetter", throwable)))
            .onErrorResume(e -> eventDeadLetters.store(group, event, EventDeadLetters.InsertionId.random()));
    }

    private StructuredLogger createStructuredLogger(Event event) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .addField(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId())
            .addField(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass())
            .addField(EventBus.StructuredLoggingFields.USER, event.getUser())
            .addField(EventBus.StructuredLoggingFields.GROUP, group.asString());
    }
}
