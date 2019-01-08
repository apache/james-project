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
import static org.apache.james.mailbox.events.EventBusTestFixture.ALL_GROUPS;
import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT;
import static org.apache.james.mailbox.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

class RabbitMQEventBusTest implements GroupContract.SingleEventBusGroupContract {

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();

    private RabbitMQEventBus eventBus;
    private Sender sender;
    private RabbitMQConnectionFactory connectionFactory;
    private EventSerializer eventSerializer;

    @BeforeEach
    void setUp() {
        connectionFactory = rabbitMQExtension.getConnectionFactory();
        Mono<Connection> connectionMono = Mono.fromSupplier(connectionFactory::create).cache();

        TestId.Factory mailboxIdFactory = new TestId.Factory();
        eventSerializer = new EventSerializer(mailboxIdFactory, new TestMessageId.Factory());

        eventBus = new RabbitMQEventBus(connectionFactory, eventSerializer);
        eventBus.start();
        sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
        ALL_GROUPS.stream()
            .map(groupClass -> GroupRegistration.WorkQueueName.of(groupClass).asString())
            .forEach(queueName -> sender.delete(QueueSpecification.queue(queueName)).block());
    }

    @Override
    public EventBus eventBus() {
        return eventBus;
    }

    @Nested
    class PublishingTest {
        private static final String MAILBOX_WORK_QUEUE_NAME = MAILBOX_EVENT + "-workQueue";

        @BeforeEach
        void setUp() {
            createQueue();
        }

        private void createQueue() {
            SenderOptions senderOption = new SenderOptions()
                .connectionMono(Mono.fromSupplier(connectionFactory::create));
            Sender sender = RabbitFlux.createSender(senderOption);

            sender.declareQueue(QueueSpecification.queue(MAILBOX_WORK_QUEUE_NAME)
                .durable(DURABLE)
                .exclusive(!EXCLUSIVE)
                .autoDelete(!AUTO_DELETE)
                .arguments(NO_ARGUMENTS))
                .block();
            sender.bind(BindingSpecification.binding()
                .exchange(MAILBOX_EVENT_EXCHANGE_NAME)
                .queue(MAILBOX_WORK_QUEUE_NAME)
                .routingKey(EMPTY_ROUTING_KEY))
                .block();
        }

        @Test
        void dispatchShouldPublishSerializedEventToRabbitMQ() {
            eventBus.dispatch(EVENT, NO_KEYS).block();

            assertThat(dequeueEvent()).isEqualTo(EVENT);
        }

        @Test
        void dispatchShouldPublishSerializedEventToRabbitMQWhenNotBlocking() {
            eventBus.dispatch(EVENT, NO_KEYS);

            assertThat(dequeueEvent()).isEqualTo(EVENT);
        }

        private Event dequeueEvent() {
            RabbitMQConnectionFactory connectionFactory = rabbitMQExtension.getConnectionFactory();
            Receiver receiver = RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(Mono.just(connectionFactory.create())));

            byte[] eventInBytes = receiver.consumeAutoAck(MAILBOX_WORK_QUEUE_NAME)
                .blockFirst()
                .getBody();

            return eventSerializer.fromJson(new String(eventInBytes, StandardCharsets.UTF_8))
                .get();
        }
    }
}