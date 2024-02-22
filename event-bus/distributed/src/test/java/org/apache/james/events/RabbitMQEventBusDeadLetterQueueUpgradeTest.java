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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.events.EventBusTestFixture.GroupA;
import org.apache.james.events.EventBusTestFixture.TestEventSerializer;
import org.apache.james.events.EventBusTestFixture.TestRegistrationKeyFactory;
import org.apache.james.events.GroupRegistration.WorkQueueName;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.rabbitmq.QueueSpecification;

class RabbitMQEventBusDeadLetterQueueUpgradeTest {
    private static final GroupA REGISTERED_GROUP = new GroupA();
    public static final NamingStrategy NAMING_STRATEGY = new NamingStrategy(new EventBusName("test"));
    private static final WorkQueueName WORK_QUEUE_NAME = NAMING_STRATEGY.workQueue(REGISTERED_GROUP);

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    private RabbitMQEventBus eventBus;

    @BeforeEach
    void setUp() throws Exception {
        MemoryEventDeadLetters memoryEventDeadLetters = new MemoryEventDeadLetters();

        EventSerializer eventSerializer = new TestEventSerializer();
        RoutingKeyConverter routingKeyConverter = RoutingKeyConverter.forFactories(new TestRegistrationKeyFactory());

        eventBus = new RabbitMQEventBus(NAMING_STRATEGY, rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(),
            eventSerializer, RETRY_BACKOFF_CONFIGURATION, routingKeyConverter,
            memoryEventDeadLetters, new RecordingMetricFactory(), rabbitMQExtension.getRabbitChannelPool(),
            EventBusId.random(), rabbitMQExtension.getRabbitMQ().getConfiguration(),
            null); // todo use redis client factory

        eventBus.start();
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
    }

    @Test
    @Tag(Unstable.TAG)
    /*
    Error
    channel error; protocol method: #method<channel.close>(reply-code=406, reply-text=PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange' for queue 'mailboxEvent-workQueue-org.apache.james.mailbox.events.EventBusTestFixture$GroupA' in vhost '/': received none but current is the value 'mailboxEvent-dead-letter-exchange' of ty..., class-id=50, method-id=10)
    Stacktrace
    com.rabbitmq.client.ShutdownSignalException: channel error; protocol method: #method<channel.close>(reply-code=406, reply-text=PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange' for queue 'mailboxEvent-workQueue-org.apache.james.mailbox.events.EventBusTestFixture$GroupA' in vhost '/': received none but current is the value 'mailboxEvent-dead-letter-exchange' of ty..., class-id=50, method-id=10)
     */
    void eventBusShouldStartWhenDeadLetterUpgradeWasNotPerformed() {
        rabbitMQExtension.getSender().delete(QueueSpecification.queue().name(WORK_QUEUE_NAME.asString())).block();
        rabbitMQExtension.getSender()
            .declareQueue(QueueSpecification.queue(WORK_QUEUE_NAME.asString())
                .durable(DURABLE)
                .exclusive(!EXCLUSIVE)
                .autoDelete(!AUTO_DELETE))
            .block();

        assertThatCode(eventBus::start).doesNotThrowAnyException();
        assertThatCode(() -> eventBus.register(new EventCollector(), REGISTERED_GROUP)).doesNotThrowAnyException();
    }

}
