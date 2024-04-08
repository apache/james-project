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

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static org.apache.james.backends.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.events.GroupRegistration.RETRY_COUNT;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        private final String prefix;
        private final Group group;

        RetryExchangeName(String prefix, Group group) {
            this.prefix = prefix;
            this.group = group;
        }

        String asString() {
            return prefix + "-retryExchange-" + group.asString();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupConsumerRetry.class);

    private final Sender sender;
    private final RetryExchangeName retryExchangeName;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventDeadLetters eventDeadLetters;
    private final Group group;
    private final EventSerializer eventSerializer;
    private final RabbitMQConfiguration rabbitMQConfiguration;

    GroupConsumerRetry(NamingStrategy namingStrategy, Sender sender, Group group, RetryBackoffConfiguration retryBackoff,
                       EventDeadLetters eventDeadLetters, EventSerializer eventSerializer, RabbitMQConfiguration rabbitMQConfiguration) {
        this.sender = sender;
        this.rabbitMQConfiguration = rabbitMQConfiguration;
        this.retryExchangeName = namingStrategy.retryExchange(group);
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
            return eventDeadLetters.store(group, event).then();
        }
        return sendRetryMessage(event, currentRetryCount);
    }

    private Mono<Void> sendRetryMessage(Event event, int currentRetryCount) {
        byte[] eventAsBytes = eventSerializer.toJsonBytes(event);

        Mono<OutboundMessage> retryMessage = Mono.just(new OutboundMessage(
            retryExchangeName.asString(),
            EMPTY_ROUTING_KEY,
            new AMQP.BasicProperties.Builder()
                .headers(ImmutableMap.of(RETRY_COUNT, currentRetryCount + 1))
                .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
                .priority(PERSISTENT_TEXT_PLAIN.getPriority())
                .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
                .build(),
            eventAsBytes));

        return sender.send(retryMessage)
            .doOnError(throwable -> createStructuredLogger(event)
                .log(logger -> logger.error("Exception happens when publishing event to retry exchange, this event will be stored in deadLetter", throwable)))
            .onErrorResume(e -> eventDeadLetters.store(group, event).then());
    }

    private StructuredLogger createStructuredLogger(Event event) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .field(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId().getId().toString())
            .field(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass().getCanonicalName())
            .field(EventBus.StructuredLoggingFields.USER, event.getUsername().asString())
            .field(EventBus.StructuredLoggingFields.GROUP, group.asString());
    }
}
